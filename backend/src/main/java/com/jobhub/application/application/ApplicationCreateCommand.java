package com.jobhub.application.application;

/**
 * 创建投递命令。allowDuplicate=true 表示用户确认创建同岗位的第二条活动投递（AT-08）。
 */
public record ApplicationCreateCommand(
		String jobId,
		String appliedAt,
		String channel,
		String resumeVersion,
		String expectedSalary,
		String contact,
		String nextAction,
		String nextActionDueAt,
		boolean allowDuplicate,
		String notes
) { }
