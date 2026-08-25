package com.jobhub.job.application;

import com.jobhub.job.domain.JobDecisionStatus;

/**
 * 岗位更新命令。basicInfo 与 decision 可独立提交（按 OpenAPI JobUpdateRequest，全部字段可选）。
 * status 字段不可通过 PUT 修改，仅由 archive/restore 改变。
 */
public record JobUpdateCommand(
		String companyName,
		String title,
		String jdRawText,
		String source,
		String sourceUrl,
		String location,
		String salaryRange,
		String notes,
		JobDecisionStatus decisionStatus,
		String decisionReason
) {

	public boolean containsBasicInfo() {
		return companyName != null || title != null || jdRawText != null || source != null
				|| sourceUrl != null || location != null || salaryRange != null || notes != null;
	}

	public boolean containsDecision() {
		return decisionStatus != null || decisionReason != null;
	}
}
