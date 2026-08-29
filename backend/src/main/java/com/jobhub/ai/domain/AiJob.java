package com.jobhub.ai.domain;

/**
 * AI 异步任务（PRD 9.2）：保存任务类型、业务对象及版本、模型与提示词版本、
 * 重试次数、失败原因、开始/完成时间、输入快照与输出。
 */
public class AiJob {
	private String id;
	private AiJobType jobType;
	private String objectId;
	private long objectVersion;
	private AiJobStatus status;
	private String providerId;
	private String providerType;
	private String model;
	private String promptVersion;
	private int attemptCount;
	private String failureReason;
	private String inputSnapshot;
	private String outputJson;
	private String startedAt;
	private String finishedAt;
	private String createdAt;
	private String updatedAt;
	private java.util.List<AiJobItem> items = java.util.List.of();

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public AiJobType getJobType() { return jobType; }
	public void setJobType(AiJobType jobType) { this.jobType = jobType; }
	public String getObjectId() { return objectId; }
	public void setObjectId(String objectId) { this.objectId = objectId; }
	public long getObjectVersion() { return objectVersion; }
	public void setObjectVersion(long objectVersion) { this.objectVersion = objectVersion; }
	public AiJobStatus getStatus() { return status; }
	public void setStatus(AiJobStatus status) { this.status = status; }
	public String getProviderId() { return providerId; }
	public void setProviderId(String providerId) { this.providerId = providerId; }
	public String getProviderType() { return providerType; }
	public void setProviderType(String providerType) { this.providerType = providerType; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public String getPromptVersion() { return promptVersion; }
	public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
	public int getAttemptCount() { return attemptCount; }
	public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
	public String getFailureReason() { return failureReason; }
	public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
	public String getInputSnapshot() { return inputSnapshot; }
	public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }
	public String getOutputJson() { return outputJson; }
	public void setOutputJson(String outputJson) { this.outputJson = outputJson; }
	public String getStartedAt() { return startedAt; }
	public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
	public String getFinishedAt() { return finishedAt; }
	public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
	public java.util.List<AiJobItem> getItems() { return items == null ? java.util.List.of() : items; }
	public void setItems(java.util.List<AiJobItem> items) {
		this.items = items == null ? java.util.List.of() : items;
	}
}
