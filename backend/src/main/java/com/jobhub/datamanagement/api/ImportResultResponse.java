package com.jobhub.datamanagement.api;

import com.jobhub.backup.api.BackupOrphanCleanSummary;

/**
 * 恢复结果报告（PRD 9.5）：只插入缺失行，重复/冲突/缺父级行一律跳过并列出。
 * orphanCleanSummary 仅在 {@code POST /backups/restore} 恢复成功后自动触发孤儿清理时填充；
 * 标准数据恢复 {@code POST /data-imports/restore} 不触发孤儿清理，该字段恒为 null。
 * passphraseResetRecommended 仅在 {@code POST /backups/restore} 恢复成功后内存评估 passphrase 为弱
 * （score<40）时为 true（建议用强口令新建备份替换，非阻塞）；强/中为 false；标准数据恢复不评估为 null。
 * 响应不回显 passphrase 或 score/level。
 */
public record ImportResultResponse(
	String reportId,
	String restoredAt,
	String packageFingerprint,
	String status,
	int inserted,
	int skippedIdentical,
	int skippedConflict,
	int skippedMissingParent,
	int failed,
	java.util.List<TableResult> tableResults,
	java.util.List<ImportIssueResponse> issues,
	java.util.List<ImportRowResult> rowResults,
	BackupOrphanCleanSummary orphanCleanSummary,
	Boolean passphraseResetRecommended
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
