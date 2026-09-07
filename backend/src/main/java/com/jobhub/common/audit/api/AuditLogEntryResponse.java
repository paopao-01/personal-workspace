package com.jobhub.common.audit.api;

import com.jobhub.common.audit.AuditLogEntry;

/**
 * 全量审计日志单条记录的只读视图（audit_log 行，跨域通用查询）。
 * 含 resourceType（全量查询不固定资源类型，与 {@code BackupOrphanAuditEntryResponse} 省略 resourceType 不同）；
 * 省略 before/afterSnapshotJson（当前所有审计记录快照恒为 null，无展示价值，与既有审计查询端点一致）。
 * 审计记录从不存 passphrase，本响应不含 passphrase。
 */
public record AuditLogEntryResponse(
		String id, String resourceType, String resourceId, String action, String reason, String occurredAt) {

	static AuditLogEntryResponse from(AuditLogEntry entry) {
		return new AuditLogEntryResponse(
				entry.getId(), entry.getResourceType(), entry.getResourceId(),
				entry.getAction(), entry.getReason(), entry.getOccurredAt());
	}
}
