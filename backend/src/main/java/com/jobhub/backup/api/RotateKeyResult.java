package com.jobhub.backup.api;

/**
 * 批量密钥轮换中单个备份的处理结果。status=SUCCESS 表示该条已就地重加密（reason 省略）；
 * status=FAILED 表示该条轮换失败（reason 含可读失败原因，如「passphrase 错误或备份文件损坏」「备份记录不存在」）。
 */
public record RotateKeyResult(String backupId, String status, String reason) {

	public static RotateKeyResult success(String backupId) {
		return new RotateKeyResult(backupId, "SUCCESS", null);
	}

	public static RotateKeyResult failure(String backupId, String reason) {
		return new RotateKeyResult(backupId, "FAILED", reason);
	}
}
