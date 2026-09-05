package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.EmailChannelConfig;
import com.jobhub.datamanagement.application.WebhookChannelConfig;
import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.NotificationChannel;

/**
 * 渠道配置响应。config 永远不回显 password 与 secret；是否已设置凭据见 hasCredential。
 * 响应 config 携带 channelType 鉴别字段以满足 OpenAPI discriminator。
 */
public record NotificationChannelResponse(
		String channelType,
		boolean enabled,
		Object config,
		boolean hasCredential,
		long version
) {
	public record EmailConfigView(
			String channelType,
			String smtpHost,
			Integer smtpPort,
			String username,
			String fromAddress,
			String toAddress,
			Boolean useStartTls
	) {
		static EmailConfigView from(EmailChannelConfig config) {
			if (config == null) {
				return null;
			}
			return new EmailConfigView(ChannelType.EMAIL.name(), config.smtpHost(), config.smtpPort(),
					config.username(), config.fromAddress(), config.toAddress(), config.useStartTls());
		}
	}

	public record WebhookConfigView(
			String channelType,
			String url,
			String providerType
	) {
		static WebhookConfigView from(WebhookChannelConfig config) {
			if (config == null) {
				return null;
			}
			return new WebhookConfigView(ChannelType.WEBHOOK.name(), config.url(), config.providerType());
		}
	}

	public static NotificationChannelResponse from(NotificationChannel channel) {
		if (channel.getChannelType() == ChannelType.WEBHOOK) {
			WebhookChannelConfig config = channel.getConfigJson() != null
				? parseWebhookConfig(channel.getConfigJson()) : null;
			return new NotificationChannelResponse(
				channel.getChannelType().name(),
				channel.isEnabled(),
				WebhookConfigView.from(config),
				config != null && config.hasCredential(),
				channel.getVersion()
			);
		}
		EmailChannelConfig config = channel.getChannelType() == ChannelType.EMAIL && channel.getConfigJson() != null
			? parseConfig(channel.getConfigJson()) : null;
		return new NotificationChannelResponse(
			channel.getChannelType().name(),
			channel.isEnabled(),
			EmailConfigView.from(config),
			config != null && config.hasCredential(),
			channel.getVersion()
		);
	}

	private static EmailChannelConfig parseConfig(String configJson) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(configJson, EmailChannelConfig.class);
		} catch (Exception ex) {
			return null;
		}
	}

	private static WebhookChannelConfig parseWebhookConfig(String configJson) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().readValue(configJson, WebhookChannelConfig.class);
		} catch (Exception ex) {
			return null;
		}
	}
}
