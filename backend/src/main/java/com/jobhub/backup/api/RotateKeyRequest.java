package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 密钥轮换请求：oldPassphrase 用于解密既有 .enc 密文（解密成功即授权，豁免强度门槛），
 * newPassphrase 用于派生新 AES-256-GCM 密钥重新加密明文（强制强度门槛，要求 strong）。
 * 两者均仅写入，永不回显或存储。
 */
public class RotateKeyRequest {
	@NotBlank
	@Size(min = 8, max = 256)
	private String oldPassphrase;

	@NotBlank
	@Size(min = 8, max = 256)
	private String newPassphrase;

	public String getOldPassphrase() { return oldPassphrase; }
	public void setOldPassphrase(String oldPassphrase) { this.oldPassphrase = oldPassphrase; }

	public String getNewPassphrase() { return newPassphrase; }
	public void setNewPassphrase(String newPassphrase) { this.newPassphrase = newPassphrase; }
}
