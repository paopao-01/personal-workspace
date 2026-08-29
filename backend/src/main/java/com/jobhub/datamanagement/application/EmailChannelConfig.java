package com.jobhub.datamanagement.application;

/**
 * EMAIL 渠道配置（存于 notification_channel.config_json，含凭据；不参与导出与导入）。
 * password 序列化保存，但任何响应中不回显。
 */
public record EmailChannelConfig(
	String smtpHost,
	Integer smtpPort,
	String username,
	String password,
	String fromAddress,
	String toAddress,
	Boolean useStartTls
) {
	public boolean hasCredential() {
		return password != null && !password.isBlank();
	}
}
