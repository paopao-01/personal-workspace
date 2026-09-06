package com.jobhub.backup.api;

import com.jobhub.backup.domain.BackupRecord;

/**
 * 备份记录响应。不暴露 salt/iv（仅恢复所需，列表/详情不输出）。
 */
public record BackupRecordResponse(String id, String createdAt, String algorithm,
		int pbkdf2Iterations, String dataExportId, String fileName, long sizeBytes) {

	public static BackupRecordResponse from(BackupRecord r) {
		return new BackupRecordResponse(r.getId(), r.getCreatedAt(), r.getAlgorithm(),
			r.getPbkdf2Iterations(), r.getDataExportId(), r.getFileName(), r.getSizeBytes());
	}
}
