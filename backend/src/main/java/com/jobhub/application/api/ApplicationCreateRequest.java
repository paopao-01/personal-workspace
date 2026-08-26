package com.jobhub.application.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建投递请求。jobId/appliedAt/channel 必填（01-page-spec：创建投递时必填岗位与投递日期）。
 * allowDuplicate=true 的二次投递创建因 V1 唯一索引限制本切片不支持，仅做 409 拒绝。
 */
public class ApplicationCreateRequest {

	@NotBlank
	@Size(max = 100)
	private String jobId;

	@NotBlank
	@Size(max = 100)
	private String appliedAt;

	@NotBlank
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

	private boolean allowDuplicate;

	@Size(max = 5000)
	private String notes;

	public String getJobId() { return jobId; }
	public void setJobId(String jobId) { this.jobId = jobId; }
	public String getAppliedAt() { return appliedAt; }
	public void setAppliedAt(String appliedAt) { this.appliedAt = appliedAt; }
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
	public boolean isAllowDuplicate() { return allowDuplicate; }
	public void setAllowDuplicate(boolean allowDuplicate) { this.allowDuplicate = allowDuplicate; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
}
