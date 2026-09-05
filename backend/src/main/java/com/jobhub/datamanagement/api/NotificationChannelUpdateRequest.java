package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.EmailChannelConfig;
import com.jobhub.datamanagement.application.WebhookChannelConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 渠道更新请求。EMAIL config 的 password 与 WEBHOOK config 的 secret 仅写入；
 * null/省略表示保留既有凭据。按 path channelType 决定使用哪组 config 字段。
 */
public record NotificationChannelUpdateRequest(
		Boolean enabled,
		ChannelConfigInput config,
		WebhookConfigInput webhookConfig
) {
	public record ChannelConfigInput(
		String smtpHost,
		@Min(value = 1, message = "SMTP 端口必须在 1-65535 之间")
		@Max(value = 65535, message = "SMTP 端口必须在 1-65535 之间")
		Integer smtpPort,
		String username,
		String password,
		String fromAddress,
		String toAddress,
		Boolean useStartTls
	) {
		public EmailChannelConfig toEmailConfig() {
			return new EmailChannelConfig(smtpHost, smtpPort, username, password, fromAddress, toAddress, useStartTls);
		}
	}

	public record WebhookConfigInput(
		String url,
		String secret,
		String providerType
	) {
		public WebhookChannelConfig toWebhookConfig() {
			return new WebhookChannelConfig(url, secret, providerType);
		}
	}
}
