package com.jobhub.datamanagement.api;

/**
 * 恢复结果报告（PRD 9.5）：只插入缺失行，重复/冲突/缺父级行一律跳过并列出。
 */
public record ImportResultResponse(
	int inserted,
	int skippedIdentical,
	int skippedConflict,
	int skippedMissingParent,
	int failed,
	java.util.List<TableResult> tableResults,
	java.util.List<ImportIssueResponse> issues
) {
	public record TableResult(
		String tableName,
		int inserted,
		int skippedIdentical,
		int skippedConflict,
		int skippedMissingParent,
		int failed
	) { }
}
