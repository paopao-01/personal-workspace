package com.jobhub.backup.api;

import com.jobhub.backup.domain.BackupSchedule;

/**
 * 定时备份调度配置视图。armed 反映内存武装状态，不回显 passphrase。
 */
public class BackupScheduleResponse {
	private final String cronExpression;
	private final boolean enabled;
	private final boolean armed;
	private final long version;
	private final String lastRunAt;
	private final String lastRunStatus;
	private final String lastRunError;
	private final String lastBackupId;

	public BackupScheduleResponse(String cronExpression, boolean enabled, boolean armed, long version,
			String lastRunAt, String lastRunStatus, String lastRunError, String lastBackupId) {
		this.cronExpression = cronExpression;
		this.enabled = enabled;
		this.armed = armed;
		this.version = version;
		this.lastRunAt = lastRunAt;
		this.lastRunStatus = lastRunStatus;
		this.lastRunError = lastRunError;
		this.lastBackupId = lastBackupId;
	}

	public static BackupScheduleResponse from(BackupSchedule schedule, boolean armed) {
		return new BackupScheduleResponse(
			schedule.getCronExpression(),
			schedule.isEnabled(),
			armed,
			schedule.getVersion(),
			schedule.getLastRunAt(),
			schedule.getLastRunStatus(),
			schedule.getLastRunError(),
			schedule.getLastBackupId());
	}

	public String getCronExpression() { return cronExpression; }
	public boolean isEnabled() { return enabled; }
	public boolean isArmed() { return armed; }
	public long getVersion() { return version; }
	public String getLastRunAt() { return lastRunAt; }
	public String getLastRunStatus() { return lastRunStatus; }
	public String getLastRunError() { return lastRunError; }
	public String getLastBackupId() { return lastBackupId; }
}
