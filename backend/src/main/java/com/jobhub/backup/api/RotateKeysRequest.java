package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量密钥轮换请求：对一组 backup_record 用同一 oldPassphrase 解密、同一 newPassphrase 重新加密。
 * backupIds 非空、去重；oldPassphrase 豁免强度门槛（解密成功即授权），newPassphrase 强制强度门槛（要求 strong）。
 * 两者均仅写入，永不回显或存储。适用于多个备份共享同一旧口令的批量换口令场景。
 */
public class RotateKeysRequest {
	@NotEmpty
	private List<String> backupIds;

	@NotBlank
	@Size(min = 8, max = 256)
	private String oldPassphrase;

	@NotBlank
	@Size(min = 8, max = 256)
	private String newPassphrase;

	public List<String> getBackupIds() { return backupIds; }
	public void setBackupIds(List<String> backupIds) { this.backupIds = backupIds; }

	public String getOldPassphrase() { return oldPassphrase; }
	public void setOldPassphrase(String oldPassphrase) { this.oldPassphrase = oldPassphrase; }

	public String getNewPassphrase() { return newPassphrase; }
	public void setNewPassphrase(String newPassphrase) { this.newPassphrase = newPassphrase; }
}
