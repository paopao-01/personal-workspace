package com.jobhub.backup.domain;

/**
 * 加密定时备份调度配置（单行可变元数据，singleton）。
 * cronExpression + enabled + version 经 If-Match-Version 乐观锁更新；
 * last_run_* 由调度器系统写入（不 bump version、不参与乐观锁）；
 * armed 与 passphrase 仅存进程内存，不落盘（硬规则：passphrase 与派生密钥永不持久化）。
 */
public class BackupSchedule {
	public static final String SINGLETON_ID = "singleton";
	public static final String DEFAULT_CRON = "0 3 * * *";

	private String id;
	private String cronExpression;
	private boolean enabled;
	private long version;
	private String lastRunAt;
	private String lastRunStatus; // SUCCESS | FAILED | SKIPPED_DISARMED | null
	private String lastRunError;
	private String lastBackupId;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getCronExpression() { return cronExpression; }
	public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
	public String getLastRunAt() { return lastRunAt; }
	public void setLastRunAt(String lastRunAt) { this.lastRunAt = lastRunAt; }
	public String getLastRunStatus() { return lastRunStatus; }
	public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }
	public String getLastRunError() { return lastRunError; }
	public void setLastRunError(String lastRunError) { this.lastRunError = lastRunError; }
	public String getLastBackupId() { return lastBackupId; }
	public void setLastBackupId(String lastBackupId) { this.lastBackupId = lastBackupId; }
}
