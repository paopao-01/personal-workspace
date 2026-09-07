package com.jobhub.common.audit;

/**
 * 关键用户确认的不可覆盖审计记录（对应 audit_log 表）。
 */
public class AuditLogEntry {

	/** audit_log.action 取值：孤儿 .enc 文件清理。独立孤儿端点与恢复联动 cleanOrphans 均写此值。 */
	public static final String ACTION_BACKUP_ORPHAN_CLEANED = "BACKUP_ORPHAN_CLEANED";
	/** audit_log.action 取值：单条删除 backup_record（DELETE /backups/{backupId}）。 */
	public static final String ACTION_BACKUP_DELETED = "BACKUP_DELETED";
	/** audit_log.action 取值：按龄批量清理 backup_record（DELETE /backups?olderThanDays=N）。 */
	public static final String ACTION_BACKUP_PURGED_BY_AGE = "BACKUP_PURGED_BY_AGE";
	/** audit_log.action 取值：按数量保留清理 backup_record（DELETE /backups?keepLast=N）。 */
	public static final String ACTION_BACKUP_PURGED_BY_COUNT = "BACKUP_PURGED_BY_COUNT";

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

	/**
	 * 单条删除备份审计：delete(id) 在 deleteById 返回非 0 后于事务内追加一条记录。
	 * 仅追加，best-effort 写入失败不阻塞删除。resourceType=BACKUP_RECORD，resourceId=被删备份 id，
	 * 不存快照（before/after 均为 null），reason 含可读说明，审计随事务提交/回滚（强一致）。
	 */
	public static AuditLogEntry backupDeleted(String id, String backupId, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "BACKUP_RECORD";
		entry.resourceId = backupId;
		entry.action = ACTION_BACKUP_DELETED;
		entry.reason = "Backup record deleted by single delete.";
		entry.occurredAt = occurredAt;
		return entry;
	}

	/**
	 * 按龄批量清理审计：purgeOlderThan(days) 在 deleteById 返回非 0 后于事务内逐被删记录追加一条。
	 * 仅追加，best-effort 写入失败不阻塞清理。resourceType=BACKUP_RECORD，resourceId=被删备份 id，
	 * 不存快照，reason 含 olderThanDays 便于追溯，审计随事务提交/回滚（强一致）。
	 */
	public static AuditLogEntry backupPurgedByAge(String id, String backupId, int olderThanDays, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "BACKUP_RECORD";
		entry.resourceId = backupId;
		entry.action = ACTION_BACKUP_PURGED_BY_AGE;
		entry.reason = "Backup record purged by age olderThanDays=" + olderThanDays + ".";
		entry.occurredAt = occurredAt;
		return entry;
	}

	/**
	 * 按数量保留清理审计：purgeKeepingLast(keepLast) 在 deleteById 返回非 0 后于事务内逐被删记录追加一条。
	 * 仅追加，best-effort 写入失败不阻塞清理。resourceType=BACKUP_RECORD，resourceId=被删备份 id，
	 * 不存快照，reason 含 keepLast 便于追溯，审计随事务提交/回滚（强一致）。
	 */
	public static AuditLogEntry backupPurgedByCount(String id, String backupId, int keepLast, String occurredAt) {
		AuditLogEntry entry = new AuditLogEntry();
		entry.id = id;
		entry.resourceType = "BACKUP_RECORD";
		entry.resourceId = backupId;
		entry.action = ACTION_BACKUP_PURGED_BY_COUNT;
		entry.reason = "Backup record purged by count keepLast=" + keepLast + ".";
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
