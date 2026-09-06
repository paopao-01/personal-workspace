package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 武装调度器请求：passphrase 仅写入内存（volatile），应用重启后清空，永不回显或存储。
 */
public class BackupArmRequest {
	@NotBlank
	@Size(min = 8, max = 256)
	private String passphrase;

	public String getPassphrase() { return passphrase; }
	public void setPassphrase(String passphrase) { this.passphrase = passphrase; }
}
