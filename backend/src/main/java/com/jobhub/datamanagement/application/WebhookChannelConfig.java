package com.jobhub.datamanagement.application;

/**
 * WEBHOOK 渠道配置（存于 notification_channel.config_json，含凭据；不参与导出与导入）。
 * secret 序列化保存，但任何响应中不回显。providerType 仅存档透传，不驱动签名或消息模板。
 */
public record WebhookChannelConfig(
		String url,
		String secret,
		String providerType
) {
	public boolean hasCredential() {
		return secret != null && !secret.isBlank();
	}
}
