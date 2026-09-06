package com.jobhub.backup.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.infrastructure.BackupRecordMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.datamanagement.application.ExportService;
import com.jobhub.datamanagement.application.ImportService;
import com.jobhub.datamanagement.api.ImportResultResponse;
import com.jobhub.datamanagement.domain.DataExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 加密备份生成与恢复：复用 ExportService 标准数据包 → AES-256-GCM 加密 → 落盘 → 记录；
 * 恢复时从上传 .enc 文件拆出 salt/iv 解密，委托 ImportService 行级幂等恢复。
 * passphrase 仅传入不持久化；salt 与 iv 落库供未来恢复派生密钥。
 */
@Service
public class BackupService {
	static final String ALGORITHM = "AES_256_GCM_PBKDF2";
	static final int SALT_BYTES = EncryptionService.SALT_BYTES;
	static final int IV_BYTES = EncryptionService.IV_BYTES;

	private final ExportService exportService;
	private final ImportService importService;
	private final EncryptionService encryption;
	private final BackupRecordMapper mapper;
	private final IdGenerator ids;
	private final UtcTime time;
	private final String backupDir;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public BackupService(ExportService exportService, ImportService importService, EncryptionService encryption,
			BackupRecordMapper mapper, IdGenerator ids, UtcTime time,
			@Value("${jobhub.backup-dir:./data/backups}") String backupDir) {
		this.exportService = exportService;
		this.importService = importService;
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

	/**
	 * 恢复加密备份：上传 .enc 文件 + passphrase，按密文布局拆出 salt/iv，
	 * 解密为标准 JSON 数据包，委托 ImportService.restore 行级幂等恢复。
	 * 不改写 backup_record，不持久化 passphrase 或派生密钥。
	 */
	@Transactional
	public ImportResultResponse restore(MultipartFile file, String passphrase) {
		if (file == null || file.isEmpty()) {
			throw new BusinessRuleException("备份文件为空");
		}
		byte[] encBytes;
		try {
			encBytes = file.getBytes();
		} catch (Exception ex) {
			throw new BusinessRuleException("备份文件读取失败：" + ex.getMessage());
		}
		if (encBytes.length < SALT_BYTES + IV_BYTES) {
			throw new BusinessRuleException("备份文件格式无效：缺少 salt/iv");
		}
		byte[] salt = new byte[SALT_BYTES];
		byte[] iv = new byte[IV_BYTES];
		byte[] ciphertext = new byte[encBytes.length - SALT_BYTES - IV_BYTES];
		System.arraycopy(encBytes, 0, salt, 0, SALT_BYTES);
		System.arraycopy(encBytes, SALT_BYTES, iv, 0, IV_BYTES);
		System.arraycopy(encBytes, SALT_BYTES + IV_BYTES, ciphertext, 0, ciphertext.length);

		byte[] plaintext;
		try {
			plaintext = encryption.decrypt(
				new EncryptionService.EncryptedPayload(salt, iv, ciphertext), passphrase);
		} catch (BusinessRuleException ex) {
			throw new BusinessRuleException("passphrase 错误或备份文件损坏");
		}
		JsonNode packageJson;
		try {
			packageJson = objectMapper.readTree(plaintext);
		} catch (Exception ex) {
			throw new BusinessRuleException("解密后的内容不是合法 JSON 数据包");
		}
		return importService.restore(packageJson);
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
