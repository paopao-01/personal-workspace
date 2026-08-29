package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.application.EmailDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道投递调度器：扫描待投递记录并尝试 SMTP 发送。
 * 间隔由 jobhub.channel-scan-delay-ms 控制（默认 60s；e2e 置 1s；test 置 1h 并直调服务）。
 */
@Component
public class ChannelDeliveryScheduler {
	private final EmailDeliveryService emailDeliveryService;

	public ChannelDeliveryScheduler(EmailDeliveryService emailDeliveryService) {
		this.emailDeliveryService = emailDeliveryService;
	}

	@Scheduled(fixedDelayString = "${jobhub.channel-scan-delay-ms:60000}",
			initialDelayString = "${jobhub.channel-scan-initial-delay-ms:1000}")
	public void attemptPending() {
		emailDeliveryService.attemptPending();
	}
}
