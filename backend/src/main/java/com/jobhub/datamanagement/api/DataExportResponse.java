package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.DataExport;

public record DataExportResponse(
	String id,
	String status,
	String downloadUrl,
	String failureReason,
	String createdAt
) {
	public static DataExportResponse from(DataExport export) {
		String downloadUrl = "SUCCEEDED".equals(export.getStatus()) && export.getDownloadPath() != null
			? "/api/data-exports/" + export.getId() + "/download"
			: null;
		return new DataExportResponse(
			export.getId(),
			export.getStatus(),
			downloadUrl,
			export.getFailureReason(),
			export.getCreatedAt()
		);
	}
}
