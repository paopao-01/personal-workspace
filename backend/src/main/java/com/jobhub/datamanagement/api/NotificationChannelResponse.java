package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.EmailChannelConfig;
import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.NotificationChannel;

/**
 * 渠道配置响应。config 永远不回显 password；是否已设置凭据见 hasCredential。
 */
public record NotificationChannelResponse(
	String channelType,
	boolean enabled,
	ConfigView config,
	boolean hasCredential,
	long version
) {
	public record ConfigView(
		String smtpHost,
		Integer smtpPort,
		String username,
		String fromAddress,
		String toAddress,
		Boolean useStartTls
	) {
		static ConfigView from(EmailChannelConfig config) {
			if (config == null) {
				return null;
			}
			return new ConfigView(config.smtpHost(), config.smtpPort(), config.username(), config.fromAddress(),
				config.toAddress(), config.useStartTls());
		}
	}

	public static NotificationChannelResponse from(NotificationChannel channel) {
		EmailChannelConfig config = channel.getChannelType() == ChannelType.EMAIL && channel.getConfigJson() != null
			? parseConfig(channel.getConfigJson())
			: null;
		return new NotificationChannelResponse(
			channel.getChannelType().name(),
			channel.isEnabled(),
			ConfigView.from(config),
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
}
