package com.jobhub.datamanagement.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 数据包冲突预检与恢复预览（只读，不写库）。
 * 语义见 OpenAPI /data-imports/validate：重复行与冲突行恢复时跳过，缺父级行恢复时跳过。
 */
public record ImportValidationResponse(
	boolean valid,
	String exportedAt,
	int totalRows,
	int insertableRows,
	java.util.List<TablePreview> tablePreviews,
	java.util.List<ImportIssueResponse> issues
) {
	public record TablePreview(
		String tableName,
		int packageRows,
		int toInsert,
		int duplicateIdentical,
		int conflict,
		int missingParent
	) { }
}
