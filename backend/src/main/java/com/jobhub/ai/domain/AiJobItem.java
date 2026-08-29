package com.jobhub.ai.domain;

/**
 * AI 候选变更条目：逐项接受（可编辑）/拒绝；接受时创建 source_type=AI 的 PENDING 岗位要求并回链。
 */
public class AiJobItem {
	private String id;
	private String aiJobId;
	private String payloadJson;
	private String editedPayloadJson;
	private AiJobItemStatus status;
	private String requirementId;
	private int sortOrder;
	private String createdAt;
	private String updatedAt;

	public static AiJobItem create(String id, String aiJobId, String payloadJson, String now) {
		AiJobItem item = new AiJobItem();
		item.id = id;
		item.aiJobId = aiJobId;
		item.payloadJson = payloadJson;
		item.status = AiJobItemStatus.PROPOSED;
		item.createdAt = now;
		item.updatedAt = now;
		return item;
	}

	public void accept(String editedPayloadJson, String requirementId, String now) {
		this.editedPayloadJson = editedPayloadJson;
		this.requirementId = requirementId;
		this.status = AiJobItemStatus.ACCEPTED;
		this.updatedAt = now;
	}

	public void reject(String now) {
		this.status = AiJobItemStatus.REJECTED;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public String getAiJobId() { return aiJobId; }
	public String getPayloadJson() { return payloadJson; }
	public String getEditedPayloadJson() { return editedPayloadJson; }
	public AiJobItemStatus getStatus() { return status; }
	public String getRequirementId() { return requirementId; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
}
