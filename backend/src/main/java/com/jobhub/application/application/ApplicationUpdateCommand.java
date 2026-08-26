package com.jobhub.application.application;

/**
 * 更新投递元数据与下一步行动命令。不改 status（状态转换走 transition 端点）。
 * 全字段覆盖写（与 job updateBasicInfo 一致）：null 表示清空该字段（nextAction/nextActionDueAt/rejectionReason）。
 */
public record ApplicationUpdateCommand(
		String channel,
		String resumeVersion,
		String expectedSalary,
		String contact,
		String nextAction,
		String nextActionDueAt,
		String rejectionReason,
		String notes
) {
	public boolean isEmpty() {
		return channel == null && resumeVersion == null && expectedSalary == null
				&& contact == null && nextAction == null && nextActionDueAt == null
				&& rejectionReason == null && notes == null;
	}
}
