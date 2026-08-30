package com.jobhub.datamanagement.api;

/** 逐行恢复动作，便于用户审计复杂数据包的最终影响。 */
public record ImportRowResult(String tableName, String rowId, String action, String detail) {
	public static final String INSERTED = "INSERTED";
	public static final String DUPLICATE_IDENTICAL = "DUPLICATE_IDENTICAL";
	public static final String CONFLICT = "CONFLICT";
	public static final String MISSING_PARENT = "MISSING_PARENT";
	public static final String FAILED = "FAILED";
	public static final String INVALID_PACKAGE = "INVALID_PACKAGE";
	public static final String UNKNOWN_TABLE = "UNKNOWN_TABLE";
}
