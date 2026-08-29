package com.jobhub.datamanagement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.error.VersionConflictException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.ChannelDelivery;
import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.DeliveryStatus;
import com.jobhub.datamanagement.domain.Notification;
import com.jobhub.datamanagement.domain.NotificationChannel;
import com.jobhub.datamanagement.infrastructure.ChannelDeliveryMapper;
import com.jobhub.datamanagement.infrastructure.NotificationChannelMapper;
import com.jobhub.datamanagement.infrastructure.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知渠道（PRD 9.3）：用户主动授权后开启，支持测试通知；渠道投递记录独立保存发送状态与失败原因；
 * 站内通知始终先行保留，渠道失败不回滚、不阻塞手工流程。
 */
@Service
public class NotificationChannelService {
	public static final int EMAIL_MAX_ATTEMPTS = 3;
	private static final String NOT_PERSISTED_ID = "not-persisted";
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Logger log = LoggerFactory.getLogger(NotificationChannelService.class);

	private final NotificationChannelMapper channelMapper;
	private final ChannelDeliveryMapper deliveryMapper;
	private final NotificationMapper notificationMapper;
	private final EmailDeliveryService emailDeliveryService;
	private final IdGenerator ids;
	private final UtcTime time;

	public NotificationChannelService(NotificationChannelMapper channelMapper, ChannelDeliveryMapper deliveryMapper,
			NotificationMapper notificationMapper, EmailDeliveryService emailDeliveryService, IdGenerator ids,
			UtcTime time) {
		this.channelMapper = channelMapper;
		this.deliveryMapper = deliveryMapper;
		this.notificationMapper = notificationMapper;
		this.emailDeliveryService = emailDeliveryService;
		this.ids = ids;
		this.time = time;
	}

	public List<NotificationChannel> list() {
		return List.of(requireProjection(ChannelType.BROWSER), requireProjection(ChannelType.EMAIL));
	}

	public NotificationChannel get(ChannelType channelType) {
		return requireProjection(channelType);
	}

	@Transactional
	public NotificationChannel update(ChannelType channelType, long expectedVersion, boolean enabled,
			EmailChannelConfig incomingConfig) {
		NotificationChannel channel = requireProjection(channelType);
		EmailChannelConfig merged = mergeConfig(channel, incomingConfig);
		if (channelType == ChannelType.EMAIL && enabled) {
			if (merged == null || isBlank(merged.smtpHost()) || isBlank(merged.toAddress())) {
				throw new BusinessRuleException("启用邮件渠道前必须配置 SMTP 主机与收件地址");
			}
		}
		String now = time.now();
		String configJson = channelType == ChannelType.EMAIL && merged != null ? toJson(merged) : channel.getConfigJson();
		if (NOT_PERSISTED_ID.equals(channel.getId())) {
			// 首次保存：以 0 作为 If-Match-Version；并发创建冲突按版本冲突处理
			if (expectedVersion != 0) {
				throw new VersionConflictException(0);
			}
			NotificationChannel created = NotificationChannel.create(ids.newId(), channelType, enabled, configJson, now);
			if (channelMapper.insert(created) == 0) {
				throw new VersionConflictException(0);
			}
			return requirePersisted(channelType);
		}
		channel.update(enabled, configJson, now);
		VersionCheck.requireAffected(channelMapper.update(channel, expectedVersion), channel.getVersion());
		return requirePersisted(channelType);
	}

	/** 通知创建后为全部已启用渠道生成 PENDING 投递记录；渠道失败不影响通知本身。 */
	public void createDeliveriesForEnabledChannels(String notificationId) {
		for (String enabledType : channelMapper.selectEnabledTypes()) {
			createDeliveryIfAbsent(notificationId, ChannelType.valueOf(enabledType));
		}
	}

	/** 测试通知：站内通知始终创建；EMAIL 同步投递并返回最终状态（未配置则 422），BROWSER 由前端展示后回执。 */
	@Transactional
	public ChannelDelivery test(ChannelType channelType) {
		if (channelType == ChannelType.EMAIL) {
			NotificationChannel channel = channelMapper.selectByType(ChannelType.EMAIL);
			EmailChannelConfig config = channel == null ? null : parseConfig(channel.getConfigJson());
			if (config == null || isBlank(config.smtpHost()) || isBlank(config.toAddress())) {
				throw new BusinessRuleException("邮件渠道尚未配置 SMTP 主机与收件地址，无法发送测试通知");
			}
		}
		String now = time.now();
		String notificationId = ids.newId();
		String label = channelType == ChannelType.EMAIL ? "邮件" : "浏览器";
		notificationMapper.insert(notificationId, null, "测试通知：" + label,
				"这是一条 " + label + " 渠道测试通知，发送时间 " + now + "。站内通知始终保留。", now);
		ChannelDelivery delivery = createDeliveryIfAbsent(notificationId, channelType);
		if (channelType == ChannelType.EMAIL) {
			emailDeliveryService.deliver(delivery.getId());
			return requireDelivery(delivery.getId());
		}
		return delivery;
	}

	/** 浏览器通知回执：前端展示系统通知后调用；幂等；仅 BROWSER。 */
	@Transactional
	public void ackBrowserDelivery(String notificationId) {
		Notification notification = notificationMapper.selectById(notificationId);
		VersionCheck.requireFound(notification, "Notification", notificationId);
		String now = time.now();
		ChannelDelivery existing = deliveryMapper.selectByNotificationAndType(notificationId, ChannelType.BROWSER.name());
		if (existing == null) {
			ChannelDelivery delivery = ChannelDelivery.create(ids.newId(), notificationId, ChannelType.BROWSER, now);
			delivery.markSent(now);
			deliveryMapper.insertSent(delivery);
			return;
		}
		if (existing.getStatus() == DeliveryStatus.PENDING) {
			deliveryMapper.markSent(existing.getId(), now);
		}
	}

	private ChannelDelivery createDeliveryIfAbsent(String notificationId, ChannelType channelType) {
		ChannelDelivery existing = deliveryMapper.selectByNotificationAndType(notificationId, channelType.name());
		if (existing != null) {
			return existing;
		}
		ChannelDelivery delivery = ChannelDelivery.create(ids.newId(), notificationId, channelType, time.now());
		deliveryMapper.insert(delivery);
		return delivery;
	}

	private EmailChannelConfig mergeConfig(NotificationChannel channel, EmailChannelConfig incoming) {
		EmailChannelConfig stored = parseConfig(channel.getConfigJson());
		if (incoming == null) {
			return stored;
		}
		if (stored == null) {
			return incoming;
		}
		// password 请求中省略/为 null 表示保留既有凭据
		String password = incoming.password() != null ? incoming.password() : stored.password();
		return new EmailChannelConfig(
			incoming.smtpHost() != null ? incoming.smtpHost() : stored.smtpHost(),
			incoming.smtpPort() != null ? incoming.smtpPort() : stored.smtpPort(),
			incoming.username() != null ? incoming.username() : stored.username(),
			password,
			incoming.fromAddress() != null ? incoming.fromAddress() : stored.fromAddress(),
			incoming.toAddress() != null ? incoming.toAddress() : stored.toAddress(),
			incoming.useStartTls() != null ? incoming.useStartTls() : stored.useStartTls());
	}

	private EmailChannelConfig parseConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			return null;
		}
		try {
			return JSON.readValue(configJson, EmailChannelConfig.class);
		} catch (Exception ex) {
			log.warn("Failed to parse channel config, treating as empty", ex);
			return null;
		}
	}

	private String toJson(EmailChannelConfig config) {
		try {
			return JSON.writeValueAsString(config);
		} catch (Exception ex) {
			throw new BusinessRuleException("渠道配置序列化失败：" + ex.getMessage());
		}
	}

	private NotificationChannel requireProjection(ChannelType channelType) {
		NotificationChannel channel = channelMapper.selectByType(channelType);
		if (channel == null) {
			// 未配置渠道投影为默认禁用；NOT_PERSISTED_ID 标记首次保存走插入路径，version 0 表示以 0 作为 If-Match-Version
			return NotificationChannel.create(NOT_PERSISTED_ID, channelType, false, null, "");
		}
		return channel;
	}

	private NotificationChannel requirePersisted(ChannelType channelType) {
		NotificationChannel channel = channelMapper.selectByType(channelType);
		if (channel == null) {
			throw new ResourceNotFoundException("NotificationChannel", channelType.name());
		}
		return channel;
	}

	private ChannelDelivery requireDelivery(String deliveryId) {
		ChannelDelivery delivery = deliveryMapper.selectById(deliveryId);
		if (delivery == null) {
			throw new ResourceNotFoundException("ChannelDelivery", deliveryId);
		}
		return delivery;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
