package com.jobhub.backup.application;

import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.infrastructure.BackupRecordMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.datamanagement.application.ExportService;
import com.jobhub.datamanagement.domain.DataExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 加密备份生成：复用 ExportService 标准数据包 → AES-256-GCM 加密 → 落盘 → 记录。
 * passphrase 仅传入不持久化；salt 与 iv 落库供未来恢复派生密钥。
 */
@Service
public class BackupService {
	static final String ALGORITHM = "AES_256_GCM_PBKDF2";

	private final ExportService exportService;
	private final EncryptionService encryption;
	private final BackupRecordMapper mapper;
	private final IdGenerator ids;
	private final UtcTime time;
	private final String backupDir;

	public BackupService(ExportService exportService, EncryptionService encryption,
			BackupRecordMapper mapper, IdGenerator ids, UtcTime time,
			@Value("${jobhub.backup-dir:./data/backups}") String backupDir) {
		this.exportService = exportService;
		this.encryption = encryption;
		this.mapper = mapper;
		this.ids = ids;
		this.time = time;
		this.backupDir = backupDir;
	}

	@Transactional
	public BackupRecord create(String passphrase) {
		DataExport export = exportService.create("JSON");
		if (!"SUCCEEDED".equals(export.getStatus())) {
			throw new BusinessRuleException("数据包生成失败：" + export.getFailureReason());
		}
		byte[] plaintext = exportService.readExportFile(export);
		EncryptionService.EncryptedPayload payload = encryption.encrypt(plaintext, passphrase);

		String id = ids.newId();
		String fileName = id + ".enc";
		Path dir = Paths.get(backupDir);
		Path file = dir.resolve(fileName);
		try {
			Files.createDirectories(dir);
			Files.write(file, composeFileBytes(payload));
		} catch (Exception ex) {
			throw new BusinessRuleException("备份文件写入失败：" + ex.getMessage());
		}

		BackupRecord record = new BackupRecord();
		record.setId(id);
		record.setCreatedAt(time.now());
		record.setAlgorithm(ALGORITHM);
		record.setPbkdf2Iterations(EncryptionService.ITERATIONS);
		record.setSalt(payload.salt());
		record.setIv(payload.iv());
		record.setDataExportId(export.getId());
		record.setFilePath(file.toString());
		record.setFileName(fileName);
		record.setSizeBytes(file.toFile().length());
		mapper.insert(record);
		return record;
	}

	public List<BackupRecord> list() {
		return mapper.selectList();
	}

	public BackupRecord get(String id) {
		BackupRecord record = mapper.selectById(id);
		if (record == null) {
			throw new ResourceNotFoundException("BackupRecord", id);
		}
		return record;
	}

	public byte[] readFileBytes(BackupRecord record) {
		Path file = Paths.get(record.getFilePath());
		if (!Files.exists(file)) {
			throw new ResourceNotFoundException("BackupFile", record.getId());
		}
		try {
			return Files.readAllBytes(file);
		} catch (Exception ex) {
			throw new ResourceNotFoundException("BackupFile", record.getId());
		}
	}

	private byte[] composeFileBytes(EncryptionService.EncryptedPayload payload) {
		byte[] salt = payload.salt();
		byte[] iv = payload.iv();
		byte[] ct = payload.ciphertext();
		byte[] out = new byte[salt.length + iv.length + ct.length];
		System.arraycopy(salt, 0, out, 0, salt.length);
		System.arraycopy(iv, 0, out, salt.length, iv.length);
		System.arraycopy(ct, 0, out, salt.length + iv.length, ct.length);
		return out;
	}
}
