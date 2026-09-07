package com.jobhub.backup.api;

import com.jobhub.common.audit.AuditLogEntry;

import java.util.List;

/**
 * 孤儿清理审计日志分页包装（对齐 PageJobResponse：items+total+page+pageSize+totalPages）。
 * 只读查询结果；totalPages 由 total/pageSize 向上取整计算。
 */
public record PageBackupOrphanAuditEntryResponse(
		List<BackupOrphanAuditEntryResponse> items, long total, int page, int pageSize, int totalPages) {

	public static PageBackupOrphanAuditEntryResponse from(List<AuditLogEntry> entries,
			long total, int page, int pageSize) {
		List<BackupOrphanAuditEntryResponse> dtos = entries.stream()
				.map(BackupOrphanAuditEntryResponse::from).toList();
		return new PageBackupOrphanAuditEntryResponse(dtos, total, page, pageSize,
				(int) Math.ceil((double) total / pageSize));
	}
}
