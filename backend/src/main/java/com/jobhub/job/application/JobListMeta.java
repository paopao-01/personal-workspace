package com.jobhub.job.application;

public class JobListMeta {
	private String jobId;
	private long confirmedRequirementCount;
	private long pendingRequirementCount;
	private long notMetCount;
	private long insufficientInfoCount;
	private boolean hasActiveApplication;
	public String getJobId() { return jobId; }
	public void setJobId(String value) { jobId = value; }
	public long getConfirmedRequirementCount() { return confirmedRequirementCount; }
	public void setConfirmedRequirementCount(long value) { confirmedRequirementCount = value; }
	public long getPendingRequirementCount() { return pendingRequirementCount; }
	public void setPendingRequirementCount(long value) { pendingRequirementCount = value; }
	public long getNotMetCount() { return notMetCount; }
	public void setNotMetCount(long value) { notMetCount = value; }
	public long getInsufficientInfoCount() { return insufficientInfoCount; }
	public void setInsufficientInfoCount(long value) { insufficientInfoCount = value; }
	public boolean isHasActiveApplication() { return hasActiveApplication; }
	public void setHasActiveApplication(boolean value) { hasActiveApplication = value; }
}
