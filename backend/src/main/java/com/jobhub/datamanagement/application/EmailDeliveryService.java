package com.jobhub.datamanagement.application;

import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.time.UtcTime;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * EMAIL 渠道投递：按渠道配置动态构建 JavaMailSender 逐条投递。
 * 发送失败记录失败原因并递增尝试次数（最多 EMAIL_MAX_ATTEMPTS 次），站内通知始终保留（PRD 9.3）。
 */
@Service
public class EmailDeliveryService {
	private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

	private final ChannelDeliveryMapper deliveryMapper;
	private final NotificationChannelMapper channelMapper;
	private final NotificationMapper notificationMapper;
	private final UtcTime time;

	public EmailDeliveryService(ChannelDeliveryMapper deliveryMapper, NotificationChannelMapper channelMapper,
			NotificationMapper notificationMapper, UtcTime time) {
		this.deliveryMapper = deliveryMapper;
		this.channelMapper = channelMapper;
		this.notificationMapper = notificationMapper;
		this.time = time;
	}

	/** 调度入口：扫描待投递的 EMAIL 记录（未达最大尝试次数）逐条投递。返回本轮 SENT 条数。 */
	public int attemptPending() {
		int sent = 0;
		for (ChannelDelivery delivery : deliveryMapper.selectPendingEmail(NotificationChannelService.EMAIL_MAX_ATTEMPTS)) {
			try {
				if (deliver(delivery.getId()).getStatus() == DeliveryStatus.SENT) {
					sent++;
				}
			} catch (Exception ex) {
				log.warn("Email delivery {} failed unexpectedly", delivery.getId(), ex);
			}
		}
		return sent;
	}

	/** 投递单条记录：PENDING → SENT/FAILED；非 PENDING 原样返回（幂等）。 */
	public ChannelDelivery deliver(String deliveryId) {
		ChannelDelivery delivery = requireDelivery(deliveryId);
		if (delivery.getStatus() != DeliveryStatus.PENDING) {
			return delivery;
		}
		NotificationChannel channel = channelMapper.selectByType(ChannelType.EMAIL);
		Notification notification = notificationMapper.selectById(delivery.getNotificationId());
		if (channel == null || notification == null) {
			deliveryMapper.markFailed(deliveryId, "渠道配置或通知不存在", time.now(),
					NotificationChannelService.EMAIL_MAX_ATTEMPTS);
			return requireDelivery(deliveryId);
		}
		String now = time.now();
		try {
			EmailChannelConfig config = parseConfig(channel.getConfigJson());
			if (config == null || isBlank(config.smtpHost()) || isBlank(config.toAddress())) {
				throw new IllegalArgumentException("SMTP 主机或收件地址未配置");
			}
			JavaMailSenderImpl sender = buildSender(config);
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(isBlank(config.fromAddress()) ? "jobhub@localhost" : config.fromAddress());
			message.setTo(config.toAddress());
			message.setSubject(notification.getTitle());
			message.setText(notification.getContent());
			sender.send(message);
			deliveryMapper.markSent(deliveryId, now);
			log.info("Email notification {} delivered via SMTP {}:{}", notification.getId(), config.smtpHost(),
					config.smtpPort());
		} catch (Exception ex) {
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > 500) {
				reason = reason.substring(0, 500);
			}
			// 失败保持 PENDING 以便重试，达到最大尝试次数后置 FAILED（PRD 9.3 保留站内提醒兜底）
			deliveryMapper.markFailed(deliveryId, reason, now, NotificationChannelService.EMAIL_MAX_ATTEMPTS);
			log.info("Email delivery {} failed: {}", deliveryId, reason);
		}
		return requireDelivery(deliveryId);
	}

	private JavaMailSenderImpl buildSender(EmailChannelConfig config) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(config.smtpHost());
		sender.setPort(config.smtpPort() != null ? config.smtpPort() : 25);
		if (!isBlank(config.username())) {
			sender.setUsername(config.username());
			sender.setPassword(config.password() == null ? "" : config.password());
		}
		java.util.Properties props = sender.getJavaMailProperties();
		props.put("mail.smtp.auth", Boolean.toString(!isBlank(config.username())));
		props.put("mail.smtp.starttls.enable", Boolean.toString(Boolean.TRUE.equals(config.useStartTls())));
		props.put("mail.smtp.connectiontimeout", "10000");
		props.put("mail.smtp.timeout", "10000");
		props.put("mail.smtp.writetimeout", "10000");
		return sender;
	}

	private EmailChannelConfig parseConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			return null;
		}
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(configJson, EmailChannelConfig.class);
		} catch (Exception ex) {
			return null;
		}
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
