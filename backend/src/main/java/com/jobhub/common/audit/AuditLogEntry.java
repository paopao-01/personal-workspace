package com.jobhub.common.audit;

/**
 * 关键用户确认的不可覆盖审计记录（对应 audit_log 表）。
 */
public class AuditLogEntry {

	/** audit_log.action 取值：孤儿 .enc 文件清理。独立孤儿端点与恢复联动 cleanOrphans 均写此值。 */
	public static final String ACTION_BACKUP_ORPHAN_CLEANED = "BACKUP_ORPHAN_CLEANED";

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

	/**
	 * 孤儿 .enc 文件清理审计：cleanOrphans 删除一个无 backup_record 对应的孤儿文件后追加一条记录。
	 * 仅追加，best-effort 写入失败不阻塞清理。resourceType=BACKUP_FILE，resourceId=被删文件名去 .enc 的 UUID，
	 * 不存快照（before/after 均为 null，与既有审计用法一致），reason 含释放字节数便于追溯。
	 */
	public static AuditLogEntry backupOrphanCleaned(String id, String fileId, long freedBytes, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "BACKUP_FILE";
		entry.resourceId = fileId;
		entry.action = ACTION_BACKUP_ORPHAN_CLEANED;
		entry.reason = "Orphan .enc file with no matching backup_record, removed by orphan scan cleanup (freedBytes="
				+ freedBytes + ").";
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
