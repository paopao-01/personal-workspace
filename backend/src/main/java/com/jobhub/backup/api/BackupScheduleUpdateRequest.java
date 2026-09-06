package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 更新定时备份调度配置请求。cron 表达式 + enabled + version（乐观锁）。
 * 本端点不接收 passphrase——passphrase 经 arm 端点单独武装，仅存内存。
 */
public class BackupScheduleUpdateRequest {
	@NotBlank
	private String cronExpression;

	@NotNull
	private Boolean enabled;

	@NotNull
	private Long version;

	public String getCronExpression() { return cronExpression; }
	public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
	public Boolean getEnabled() { return enabled; }
	public void setEnabled(Boolean enabled) { this.enabled = enabled; }
	public Long getVersion() { return version; }
	public void setVersion(Long version) { this.version = version; }
}
