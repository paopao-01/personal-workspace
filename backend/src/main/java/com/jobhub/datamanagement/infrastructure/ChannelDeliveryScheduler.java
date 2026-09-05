package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.application.EmailDeliveryService;
import com.jobhub.datamanagement.application.WebhookDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后端投递渠道调度器：扫描待投递记录并尝试发送。
 * EMAIL 走 SMTP，WEBHOOK 走 HTTP POST；BROWSER 由前端 ack 回执旁路。
 * 间隔由 jobhub.channel-scan-delay-ms 控制（默认 60s；e2e 置 1s；test 置 1h 并直调服务）。
 */
@Component
public class ChannelDeliveryScheduler {
	private final EmailDeliveryService emailDeliveryService;
	private final WebhookDeliveryService webhookDeliveryService;

	public ChannelDeliveryScheduler(EmailDeliveryService emailDeliveryService,
			WebhookDeliveryService webhookDeliveryService) {
		this.emailDeliveryService = emailDeliveryService;
		this.webhookDeliveryService = webhookDeliveryService;
	}

	@Scheduled(fixedDelayString = "${jobhub.channel-scan-delay-ms:60000}",
			initialDelayString = "${jobhub.channel-scan-initial-delay-ms:1000}")
	public void attemptPending() {
		emailDeliveryService.attemptPending();
		webhookDeliveryService.attemptPending();
	}
}
