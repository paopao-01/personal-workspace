package com.jobhub.backup.api;

import com.jobhub.common.audit.AuditLogEntry;

/**
 * 孤儿清理审计日志单条记录的只读视图（audit_log 行，action=BACKUP_ORPHAN_CLEANED）。
 * 省略固定 resourceType=BACKUP_FILE（路径 /backups/orphans/audit 已隐含）与恒 null 的
 * before/afterSnapshotJson（孤儿清理不存快照，无展示价值）；reason 含 freedBytes=N 子串。
 * 审计记录从不存 passphrase，本响应不含 passphrase。
 */
public record BackupOrphanAuditEntryResponse(
		String id, String resourceId, String action, String reason, String occurredAt) {

	static BackupOrphanAuditEntryResponse from(AuditLogEntry entry) {
		return new BackupOrphanAuditEntryResponse(
				entry.getId(), entry.getResourceId(), entry.getAction(),
				entry.getReason(), entry.getOccurredAt());
	}
}
