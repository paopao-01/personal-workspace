package com.jobhub.datamanagement.domain;

import java.util.List;

/**
 * 最近删除记录。resourceType 取值：PROJECT_CASE、EVIDENCE、INTERVIEW_QUESTION。
 * impactSummaryJson 为影响摘要字符串数组的 JSON；恢复与永久删除以 restored_at/purged_at 标记。
 */
public class TrashItem {
	private String id;
	private String resourceType;
	private String resourceId;
	private String displayName;
	private String impactSummaryJson;
	private String deletedAt;
	private String expiresAt;
	private String restoredAt;
	private String purgedAt;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getResourceType() { return resourceType; }
	public void setResourceType(String resourceType) { this.resourceType = resourceType; }
	public String getResourceId() { return resourceId; }
	public void setResourceId(String resourceId) { this.resourceId = resourceId; }
	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }
	public String getImpactSummaryJson() { return impactSummaryJson; }
	public void setImpactSummaryJson(String impactSummaryJson) { this.impactSummaryJson = impactSummaryJson; }
	public List<String> impactSummary() {
		if (impactSummaryJson == null || impactSummaryJson.isBlank()) return List.of();
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper()
				.readValue(impactSummaryJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
		} catch (Exception ex) {
			return List.of();
		}
	}
	public String getDeletedAt() { return deletedAt; }
	public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
	public String getExpiresAt() { return expiresAt; }
	public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
	public String getRestoredAt() { return restoredAt; }
	public void setRestoredAt(String restoredAt) { this.restoredAt = restoredAt; }
	public String getPurgedAt() { return purgedAt; }
	public void setPurgedAt(String purgedAt) { this.purgedAt = purgedAt; }
}
