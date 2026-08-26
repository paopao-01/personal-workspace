package com.jobhub.application.application;

/**
 * 创建投递命令。allowDuplicate=true 用于 AT-08 二次投递确认（本切片搁置实现，详见 ApplicationService）。
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
