package com.jobhub.datamanagement.domain;

/**
 * 通知渠道（PRD 9.3）：用户主动授权后开启；config_json 仅 EMAIL 使用（SMTP 配置，含凭据，不导出）。
 */
public class NotificationChannel {
	private String id;
	private ChannelType channelType;
	private boolean enabled;
	private String configJson;
	private String createdAt;
	private String updatedAt;
	private long version;

	public static NotificationChannel create(String id, ChannelType channelType, boolean enabled, String configJson,
			String now) {
		NotificationChannel channel = new NotificationChannel();
		channel.id = id;
		channel.channelType = channelType;
		channel.enabled = enabled;
		channel.configJson = configJson;
		channel.createdAt = now;
		channel.updatedAt = now;
		return channel;
	}

	public void update(boolean enabled, String configJson, String now) {
		this.enabled = enabled;
		this.configJson = configJson;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public ChannelType getChannelType() { return channelType; }
	public boolean isEnabled() { return enabled; }
	public String getConfigJson() { return configJson; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
