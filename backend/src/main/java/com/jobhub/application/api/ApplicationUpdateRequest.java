package com.jobhub.application.api;

import jakarta.validation.constraints.Size;

/**
 * 更新投递元数据与下一步行动。不改变当前状态（状态转换走 transition 端点）。
 * 全字段可选：nextAction/nextActionDueAt/rejectionReason 传 null 即清空（全字段覆盖写）。
 */
public class ApplicationUpdateRequest {

	@Size(max = 100)
	private String channel;

	@Size(max = 200)
	private String resumeVersion;

	@Size(max = 100)
	private String expectedSalary;

	@Size(max = 200)
	private String contact;

	@Size(max = 500)
	private String nextAction;

	@Size(max = 100)
	private String nextActionDueAt;

	@Size(max = 1000)
	private String rejectionReason;

	@Size(max = 5000)
	private String notes;

	public String getChannel() { return channel; }
	public void setChannel(String channel) { this.channel = channel; }
	public String getResumeVersion() { return resumeVersion; }
	public void setResumeVersion(String resumeVersion) { this.resumeVersion = resumeVersion; }
	public String getExpectedSalary() { return expectedSalary; }
	public void setExpectedSalary(String expectedSalary) { this.expectedSalary = expectedSalary; }
	public String getContact() { return contact; }
	public void setContact(String contact) { this.contact = contact; }
	public String getNextAction() { return nextAction; }
	public void setNextAction(String nextAction) { this.nextAction = nextAction; }
	public String getNextActionDueAt() { return nextActionDueAt; }
	public void setNextActionDueAt(String nextActionDueAt) { this.nextActionDueAt = nextActionDueAt; }
	public String getRejectionReason() { return rejectionReason; }
	public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
}
