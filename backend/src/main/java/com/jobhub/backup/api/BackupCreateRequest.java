package com.jobhub.backup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 加密备份生成请求。passphrase 仅写入不回显，用于 PBKDF2 派生 AES-256-GCM 密钥。
 */
public record BackupCreateRequest(
		@NotBlank @Size(min = 8, max = 256) String passphrase) { }
