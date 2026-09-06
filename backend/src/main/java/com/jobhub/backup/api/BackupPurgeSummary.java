package com.jobhub.backup.api;

/**
 * 按龄批量清理加密备份的结果摘要。deletedCount 为实际删行数；
 * filesCleaned 为清理的 .enc 文件数（文件不存在不计数）；
 * lastBackupIdCleared 表示被删集合是否包含 last_backup_id 指向的记录。
 */
public record BackupPurgeSummary(int deletedCount, int filesCleaned, boolean lastBackupIdCleared) { }
