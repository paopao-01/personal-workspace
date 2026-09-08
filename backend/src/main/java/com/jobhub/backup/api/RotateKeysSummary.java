package com.jobhub.backup.api;

import java.util.List;

/**
 * 批量密钥轮换结果摘要。total 为处理备份数（去重后），rotated 为成功轮换数，
 * failed 为轮换失败数（oldPassphrase 错/文件损坏/记录不存在等，不阻塞其他）。
 * results 为每个待轮换备份的处理结果，顺序与去重后 backupIds 一致。
 */
public record RotateKeysSummary(int total, int rotated, int failed, List<RotateKeyResult> results) { }
