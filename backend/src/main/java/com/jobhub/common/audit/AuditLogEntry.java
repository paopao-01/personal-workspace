package com.jobhub.common.audit;

/**
 * 关键用户确认的不可覆盖审计记录（对应 audit_log 表）。
 */
public class AuditLogEntry {

	private String id;
	private String resourceType;
	private String resourceId;
	private String action;
	private String beforeSnapshotJson;
	private String afterSnapshotJson;
	private String reason;
	private String occurredAt;

	public AuditLogEntry() { }

	public static AuditLogEntry secondaryApplicationConfirmation(String id, String applicationId,
			String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "APPLICATION";
		entry.resourceId = applicationId;
		entry.action = "SECONDARY_APPLICATION_CONFIRMED";
		entry.reason = "User confirmed a secondary active application via allowDuplicate=true.";
		entry.occurredAt = occurredAt;
		return entry;
	}

	public static AuditLogEntry requirementMerged(String id, String sourceRequirementId,
			String targetRequirementId, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "JOB_REQUIREMENT";
		entry.resourceId = sourceRequirementId;
		entry.action = "REQUIREMENT_MERGED";
		entry.reason = "Merged into requirement " + targetRequirementId + ".";
		entry.occurredAt = occurredAt;
		return entry;
	}

	public static AuditLogEntry requirementChanged(String id, String requirementId, String action,
			String reason, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "JOB_REQUIREMENT";
		entry.resourceId = requirementId;
		entry.action = action;
		entry.reason = reason;
		entry.occurredAt = occurredAt;
		return entry;
	}

	public String getId() { return id; }
	public String getResourceType() { return resourceType; }
	public String getResourceId() { return resourceId; }
	public String getAction() { return action; }
	public String getBeforeSnapshotJson() { return beforeSnapshotJson; }
	public String getAfterSnapshotJson() { return afterSnapshotJson; }
	public String getReason() { return reason; }
	public String getOccurredAt() { return occurredAt; }
}
