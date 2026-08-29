package com.jobhub.datamanagement.api;

/**
 * 数据导入问题明细（预检与恢复结果共用）。
 */
public record ImportIssueResponse(
	String type,
	String tableName,
	String rowId,
	String detail
) {
	public static final String TYPE_INVALID_PACKAGE = "INVALID_PACKAGE";
	public static final String TYPE_UNKNOWN_TABLE = "UNKNOWN_TABLE";
	public static final String TYPE_CONFLICT = "CONFLICT";
	public static final String TYPE_MISSING_PARENT = "MISSING_PARENT";
	public static final String TYPE_ROW_FAILED = "ROW_FAILED";
}
