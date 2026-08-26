package com.jobhub.application.domain;

/**
 * 投递状态机（02-state-machines.md §3）。
 *
 * 活动状态：DRAFT、APPLIED、RESUME_PASSED、INTERVIEWING、ON_HOLD
 * 终止状态：OFFER、REJECTED、WITHDRAWN（V0.1 不支持直接恢复，用户应创建二次投递）
 */
public enum ApplicationStatus {
	DRAFT,
	APPLIED,
	RESUME_PASSED,
	INTERVIEWING,
	OFFER,
	REJECTED,
	WITHDRAWN,
	ON_HOLD
}
