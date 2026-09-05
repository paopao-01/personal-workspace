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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * WEBHOOK 渠道投递：按渠道配置动态构造 HTTP 客户端同步 POST 到用户配置的 URL。
 * 成功（2xx）置 SENT；失败（非 2xx 或异常）记录原因并递增尝试次数（最多 EMAIL_MAX_ATTEMPTS 次），
 * 站内通知始终保留（PRD 9.3）。secret 非空时作为 X-Webhook-Secret 头透传，不实现 IM 专有签名。
 */
@Service
public class WebhookDeliveryService {
	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

	private final ChannelDeliveryMapper deliveryMapper;
	private final NotificationChannelMapper channelMapper;
	private final NotificationMapper notificationMapper;
	private final UtcTime time;

	public WebhookDeliveryService(ChannelDeliveryMapper deliveryMapper, NotificationChannelMapper channelMapper,
			NotificationMapper notificationMapper, UtcTime time) {
		this.deliveryMapper = deliveryMapper;
		this.channelMapper = channelMapper;
		this.notificationMapper = notificationMapper;
		this.time = time;
	}

	/** 调度入口：扫描待投递的 WEBHOOK 记录（未达最大尝试次数）逐条投递。返回本轮 SENT 条数。 */
	public int attemptPending() {
		int sent = 0;
		for (ChannelDelivery delivery : deliveryMapper.selectPendingWebhook(NotificationChannelService.EMAIL_MAX_ATTEMPTS)) {
			try {
				if (deliver(delivery.getId()).getStatus() == DeliveryStatus.SENT) {
					sent++;
				}
			} catch (Exception ex) {
				log.warn("Webhook delivery {} failed unexpectedly", delivery.getId(), ex);
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
		NotificationChannel channel = channelMapper.selectByType(ChannelType.WEBHOOK);
		Notification notification = notificationMapper.selectById(delivery.getNotificationId());
		if (channel == null || notification == null) {
			deliveryMapper.markFailed(deliveryId, "渠道配置或通知不存在", time.now(),
					NotificationChannelService.EMAIL_MAX_ATTEMPTS);
			return requireDelivery(deliveryId);
		}
		String now = time.now();
		try {
			WebhookChannelConfig config = parseConfig(channel.getConfigJson());
			if (config == null || isBlank(config.url())) {
				throw new IllegalArgumentException("webhook URL 未配置");
			}
			RestClient client = buildClient(config);
			String body = new com.fasterxml.jackson.databind.ObjectMapper()
					.writeValueAsString(java.util.Map.of("title", notification.getTitle(), "content", notification.getContent()));
			if (!isBlank(config.secret())) {
				client.post().header("X-Webhook-Secret", config.secret())
						.header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity();
			} else {
				client.post().header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity();
			}
			deliveryMapper.markSent(deliveryId, now);
			log.info("Webhook notification {} delivered to {}", notification.getId(), config.url());
		} catch (Exception ex) {
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > 500) {
				reason = reason.substring(0, 500);
			}
			// 失败保持 PENDING 以便重试，达到最大尝试次数后置 FAILED（PRD 9.3 保留站内提醒兜底）
			deliveryMapper.markFailed(deliveryId, reason, now, NotificationChannelService.EMAIL_MAX_ATTEMPTS);
			log.info("Webhook delivery {} failed: {}", deliveryId, reason);
		}
		return requireDelivery(deliveryId);
	}

	private RestClient buildClient(WebhookChannelConfig config) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(10_000);
		return RestClient.builder().baseUrl(config.url()).requestFactory(factory).build();
	}

	private WebhookChannelConfig parseConfig(String configJson) {
		if (configJson == null || configJson.isBlank()) {
			return null;
		}
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(configJson, WebhookChannelConfig.class);
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
