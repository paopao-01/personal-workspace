package com.jobhub.common.audit.api;

import com.jobhub.common.audit.AuditLogEntry;

import java.util.List;

/**
 * 全量审计日志分页包装（对齐 PageJob/PageBackupOrphanAuditEntryResponse：items+total+page+pageSize+totalPages）。
 * 只读查询结果；totalPages 由 total/pageSize 向上取整计算。
 */
public record PageAuditLogEntryResponse(
		List<AuditLogEntryResponse> items, long total, int page, int pageSize, int totalPages) {

	public static PageAuditLogEntryResponse from(List<AuditLogEntry> entries,
			long total, int page, int pageSize) {
		List<AuditLogEntryResponse> dtos = entries.stream()
				.map(AuditLogEntryResponse::from).toList();
		return new PageAuditLogEntryResponse(dtos, total, page, pageSize,
				(int) Math.ceil((double) total / pageSize));
	}
}
