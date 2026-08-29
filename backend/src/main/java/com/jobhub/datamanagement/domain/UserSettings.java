package com.jobhub.datamanagement.domain;

import java.util.List;

/**
 * 本地单用户设置。时区用于 UI 显示（存储 IANA 名称），默认提醒节点（分钟）用于创建/改期面试时生成站内提醒。
 * 单用户固定使用 V1 种子 user_profile（00000000-0000-0000-0000-000000000001）。
 */
public class UserSettings {
	private String id;
	private String timeZone;
	private String defaultReminderOffsetsJson;
	private long version;
	private String createdAt;
	private String updatedAt;

	public static UserSettings create(String id, String now) {
		UserSettings settings = new UserSettings();
		settings.id = id;
		settings.timeZone = "Asia/Shanghai";
		settings.defaultReminderOffsetsJson = "[1440,120,30]";
		settings.createdAt = now;
		settings.updatedAt = now;
		return settings;
	}

	public void update(String timeZone, List<Integer> offsetsMinutes, String now) {
		this.timeZone = timeZone;
		this.defaultReminderOffsetsJson = toJson(offsetsMinutes);
		this.updatedAt = now;
	}

	public List<Integer> offsetsMinutes() {
		if (defaultReminderOffsetsJson == null || defaultReminderOffsetsJson.isBlank()) return List.of();
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper()
				.readValue(defaultReminderOffsetsJson, new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() { });
		} catch (Exception ex) {
			return List.of();
		}
	}

	public static String toJson(List<Integer> offsets) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(offsets);
		} catch (Exception ex) {
			return "[]";
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTimeZone() { return timeZone; }
	public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
	public String getDefaultReminderOffsetsJson() { return defaultReminderOffsetsJson; }
	public void setDefaultReminderOffsetsJson(String defaultReminderOffsetsJson) { this.defaultReminderOffsetsJson = defaultReminderOffsetsJson; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
