package com.jobhub.job.domain;

/**
 * 岗位投递决定。nullable；初始为空表示"未决定"。
 * 不是流程状态，不替代投递记录的当前状态。
 */
public enum JobDecisionStatus {
	TO_APPLY,
	APPLY,
	DEFER,
	IGNORE
}
