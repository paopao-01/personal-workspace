package com.jobhub.backup.api;

/**
 * 孤儿 .enc 密文文件扫描清理的结果摘要。scannedFiles 为扫描到的 backup-dir 下 .enc 文件总数；
 * orphanFiles 为判定的孤儿数（UUID 命名且 backup_record 无对应 file_name）；
 * deletedFiles 为实际删除数（文件不存在不计数）；freedBytes 为删除前统计的孤儿文件字节数之和；
 * skippedFiles 为非 UUID 命名规则的 .enc 文件数（跳过不删，避免误删无关文件）。
 */
public record BackupOrphanCleanSummary(
		int scannedFiles, int orphanFiles, int deletedFiles, long freedBytes, int skippedFiles) { }
