package com.jobhub.backup.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.backup.api.BackupOrphanCleanSummary;
import com.jobhub.backup.api.BackupPurgeSummary;
import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.infrastructure.BackupRecordMapper;
import com.jobhub.backup.infrastructure.BackupScheduleMapper;
import com.jobhub.common.audit.AuditLogEntry;
import com.jobhub.common.audit.infrastructure.AuditLogMapper;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
	private final BackupScheduleMapper scheduleMapper;
	private final AuditLogMapper auditLogMapper;
	private final IdGenerator ids;
	private final UtcTime time;
	private final String backupDir;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public BackupService(ExportService exportService, ImportService importService, EncryptionService encryption,
			BackupRecordMapper mapper, BackupScheduleMapper scheduleMapper, AuditLogMapper auditLogMapper,
			IdGenerator ids, UtcTime time,
			@Value("${jobhub.backup-dir:./data/backups}") String backupDir) {
		this.exportService = exportService;
		this.importService = importService;
		this.encryption = encryption;
		this.mapper = mapper;
		this.scheduleMapper = scheduleMapper;
		this.auditLogMapper = auditLogMapper;
		this.ids = ids;
		this.time = time;
		this.backupDir = backupDir;
	}

	@Transactional
	public BackupRecord create(String passphrase) {
		// 强度门槛（强制，要求 strong）：score<70（弱或中）在加密前拦截，返回 400，不落盘不进日志。
		PassphraseStrengthValidator.requireAcceptable(passphrase);
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
		ImportResultResponse result = importService.restore(packageJson);
		// 恢复成功后自动触发孤儿 .enc 文件扫描清理（best-effort 防御性补偿）：cleanOrphans 只读 DB 查
		// file_name 集合 + 删文件，无 DB 写，对恢复事务无影响；清理失败用 try-catch 包裹不影响恢复结果。
		BackupOrphanCleanSummary orphanSummary;
		try {
			orphanSummary = cleanOrphans();
		} catch (Exception ex) {
			// 清理失败不影响已成功的恢复：记日志，摘要置 null（响应仍正常返回恢复结果）
			System.getLogger(BackupService.class.getName())
				.log(System.Logger.Level.WARNING, "恢复后自动孤儿清理失败", ex);
			orphanSummary = null;
		}
		// 恢复成功后对本次 passphrase 做内存强度评估：未达 strong（score<70，即弱或中）置 passphraseResetRecommended=true，
		// 提示用户用强口令新建备份替换（系统无全局 passphrase 可重设）。恢复端点仍豁免门槛（仅提示不阻塞）；
		// 评估在内存进行，passphrase 不落盘/不进日志/不回显，响应仅返回布尔不回显 score/level。
		boolean resetRecommended =
			PassphraseStrengthValidator.evaluate(passphrase).level() != PassphraseStrengthValidator.Level.STRONG;
		return new ImportResultResponse(result.reportId(), result.restoredAt(), result.packageFingerprint(),
			result.status(), result.inserted(), result.skippedIdentical(), result.skippedConflict(),
			result.skippedMissingParent(), result.failed(), result.tableResults(), result.issues(),
			result.rowResults(), orphanSummary, resetRecommended);
	}

	public List<BackupRecord> list() {
		return mapper.selectList();
	}

	/**
	 * 按龄批量清理：物理删除 created_at 早于「当前 UTC − days 天」的全部 backup_record + 落盘 .enc 文件，
	 * 若被删集合含 last_backup_id 则置空该软引用。语义同单条 delete：文件清理在事务提交后执行
	 * （afterCommit），文件清理失败仅记日志不回滚 DB。filesCleaned 为删行时文件存在的数量（将在 afterCommit 清理）。
	 * 不联动删 data_export 行（独立历史快照）。无匹配记录返回 deletedCount=0（不报 404）。
	 */
	@Transactional
	public BackupPurgeSummary purgeOlderThan(int days) {
		if (days < 1) {
			throw new BusinessRuleException(com.jobhub.common.error.ErrorCode.VALIDATION_ERROR,
				"olderThanDays 必须 ≥ 1");
		}
		String cutoff = time.nowMinusDays(days);
		List<BackupRecord> targets = mapper.selectByCreatedBefore(cutoff);
		if (targets.isEmpty()) {
			return new BackupPurgeSummary(0, 0, false);
		}

		int deleted = 0;
		int filesToClean = 0;
		boolean lastCleared = false;
		List<BackupRecord> filesToCleanup = new java.util.ArrayList<>();
		for (BackupRecord r : targets) {
			int affected = mapper.deleteById(r.getId());
			if (affected == 0) {
				// 并发：记录刚被另一事务删除，跳过
				continue;
			}
			deleted++;
			if (scheduleMapper.clearLastBackupIdIfMatch(r.getId()) > 0) {
				lastCleared = true;
			}
			Path filePath = Paths.get(r.getFilePath());
			if (Files.exists(filePath)) {
				filesToClean++;
				filesToCleanup.add(r);
			}
		}

		final int finalDeleted = deleted;
		final int finalFilesToClean = filesToClean;
		final boolean finalLastCleared = lastCleared;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (BackupRecord r : filesToCleanup) {
					cleanupFileQuietly(r.getFilePath());
				}
			}
		});
		return new BackupPurgeSummary(finalDeleted, finalFilesToClean, finalLastCleared);
	}

	/**
	 * 按数量保留：保留最近 keepLast 条（按 created_at DESC，即 {@link #list()} 既有顺序），
	 * 物理删除其余全部 backup_record + 落盘 .enc 文件，若被删集合含 last_backup_id 则置空该软引用。
	 * 语义同按龄清理与单条 delete：文件清理在事务提交后执行（afterCommit），文件清理失败仅记日志不回滚 DB。
	 * 不联动删 data_export 行（独立历史快照）。keepLast ≥ 现有总数时无备份被删，返回 deletedCount=0（不报 404）。
	 */
	@Transactional
	public BackupPurgeSummary purgeKeepingLast(int keepLast) {
		if (keepLast < 1) {
			throw new BusinessRuleException(com.jobhub.common.error.ErrorCode.VALIDATION_ERROR,
				"keepLast 必须 ≥ 1");
		}
		List<BackupRecord> all = mapper.selectList(); // 已 created_at DESC
		if (all.size() <= keepLast) {
			// 保留集覆盖全部，无备份被删
			return new BackupPurgeSummary(0, 0, false);
		}
		// 前 keepLast 条为保留集，其余为删除目标
		List<BackupRecord> targets = new ArrayList<>(all.subList(keepLast, all.size()));

		int deleted = 0;
		int filesToClean = 0;
		boolean lastCleared = false;
		List<BackupRecord> filesToCleanup = new java.util.ArrayList<>();
		for (BackupRecord r : targets) {
			int affected = mapper.deleteById(r.getId());
			if (affected == 0) {
				// 并发：记录刚被另一事务删除，跳过
				continue;
			}
			deleted++;
			if (scheduleMapper.clearLastBackupIdIfMatch(r.getId()) > 0) {
				lastCleared = true;
			}
			Path filePath = Paths.get(r.getFilePath());
			if (Files.exists(filePath)) {
				filesToClean++;
				filesToCleanup.add(r);
			}
		}

		final int finalDeleted = deleted;
		final int finalFilesToClean = filesToClean;
		final boolean finalLastCleared = lastCleared;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (BackupRecord r : filesToCleanup) {
					cleanupFileQuietly(r.getFilePath());
				}
			}
		});
		return new BackupPurgeSummary(finalDeleted, finalFilesToClean, finalLastCleared);
	}

	public BackupRecord get(String id) {
		BackupRecord record = mapper.selectById(id);
		if (record == null) {
			throw new ResourceNotFoundException("BackupRecord", id);
		}
		return record;
	}

	/**
	 * 扫描并清理孤儿 .enc 密文文件：扫描 backup-dir 下全部 .enc 文件，物理删除其中无 backup_record 对应的孤儿。
	 * 判定规则：备份文件名恒为 {@code <id>.enc}（id 为 UUID），取文件名去 {@code .enc} 得 candidate id；
	 * 合法 UUID 且 backup_record 中无对应 file_name 的文件即孤儿，删除之；非 UUID 命名的 .enc（如用户随手放入的
	 * 无关文件）跳过不删，计入 skippedFiles，避免误删。
	 * 本方法不写 backup_record、不联动 last_backup_id、不联动删 data_export；无 @Transactional、
	 * 无 afterCommit（与单条删除/按龄清理先提交 DB 行再清文件不同——本方法不动 DB 行，直接删文件即可）。
	 * 删除每个孤儿文件成功后 best-effort 向既有 audit_log 表追加一条审计记录（见 AuditLogEntry.backupOrphanCleaned），
	 * 写入失败仅记日志不阻塞清理、不影响摘要计数。
	 * backup-dir 不存在时返回 scannedFiles=0（不报错）；幂等性由 Idempotency-Key 保证（重复回放返回首次缓存摘要）。
	 */
	public BackupOrphanCleanSummary cleanOrphans() {
		Path dir = Paths.get(backupDir);
		if (!Files.isDirectory(dir)) {
			return new BackupOrphanCleanSummary(0, 0, 0, 0L, 0);
		}
		List<Path> encFiles = new ArrayList<>();
		try (var stream = Files.list(dir)) {
			stream.filter(p -> {
				String name = p.getFileName().toString();
				return Files.isRegularFile(p) && name.endsWith(".enc");
			}).forEach(encFiles::add);
		} catch (Exception ex) {
			throw new BusinessRuleException("备份目录扫描失败：" + ex.getMessage());
		}

		Set<String> dbFileNames = Set.copyOf(mapper.selectAllFileNames());

		int scanned = encFiles.size();
		int orphan = 0;
		int deleted = 0;
		int skipped = 0;
		long freed = 0L;
		for (Path file : encFiles) {
			String fileName = file.getFileName().toString();
			String candidateId = fileName.substring(0, fileName.length() - ".enc".length());
			if (!looksLikeUuid(candidateId)) {
				// 非 UUID 命名规则的 .enc 文件（如用户随手放入的无关文件），跳过不删，避免误删
				skipped++;
				continue;
			}
			if (dbFileNames.contains(fileName)) {
				// 有对应 backup_record 行，合法备份文件，保留
				continue;
			}
			orphan++;
			long size = 0L;
			try {
				size = Files.size(file);
			} catch (Exception ignored) {
				// 文件可能在统计大小与删除之间被外部移除，按 0 计
			}
			if (deleteFile(file)) {
				deleted++;
				freed += size;
				// best-effort 审计：删除孤儿文件成功后向 audit_log 追加一条记录，失败仅记日志不阻塞清理、
				// 不影响计数（同 restore 对 cleanOrphans 的 best-effort 容错）。candidateId 为文件名去 .enc 的 UUID。
				try {
					auditLogMapper.insert(AuditLogEntry.backupOrphanCleaned(
							ids.newId(), candidateId, size, time.now()));
				} catch (Exception auditEx) {
					// 审计写入失败不影响清理结果与摘要计数
				}
			}
		}
		return new BackupOrphanCleanSummary(scanned, orphan, deleted, freed, skipped);
	}

	/** 备份 id 由 {@link IdGenerator} 生成为标准 UUID（{@code UUID.randomUUID().toString()}，8-4-4-4-4）。 */
	private static boolean looksLikeUuid(String candidate) {
		try {
			// fromString 对非标准格式（如 "notes"）抛 IllegalArgumentException
			UUID.fromString(candidate);
			return true;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	/**
	 * 物理删除加密备份记录：删除 backup_record 行 + 清理落盘 .enc 密文文件 + 清空 last_backup_id 软引用。
	 * 不可恢复，不进入最近删除；passphrase 不参与删除验证（删除即销毁密钥材料）。
	 * 文件清理在事务提交后执行（在事务内事务后置回调），文件清理失败仅记日志不回滚 DB，
	 * 避免悬留已删记录却残留文件；文件不存在视为已清理不报错。
	 */
	@Transactional
	public void delete(String id) {
		BackupRecord record = mapper.selectById(id);
		if (record == null) {
			throw new ResourceNotFoundException("BackupRecord", id);
		}
		int affected = mapper.deleteById(id);
		if (affected == 0) {
			// 并发删除：记录刚被另一事务删除，对调用方表现为不存在
			throw new ResourceNotFoundException("BackupRecord", id);
		}
		scheduleMapper.clearLastBackupIdIfMatch(id);
		// 文件清理在事务提交后执行，避免悬留已删记录却残留文件
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				cleanupFileQuietly(record.getFilePath());
			}
		});
	}

	private void cleanupFileQuietly(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			return;
		}
		deleteFile(Paths.get(filePath));
	}

	/** 物理删除文件；不存在视为已删除返回 false（不计入删除数），删除成功返回 true，失败记日志返回 false。 */
	private boolean deleteFile(Path file) {
		try {
			return Files.deleteIfExists(file);
		} catch (Exception ex) {
			// 文件清理失败不回滚已提交的 DB 删除，避免悬留已删记录却丢失文件的一致性问题；
			// 记录日志便于人工排查孤儿文件
			System.getLogger(BackupService.class.getName())
				.log(System.Logger.Level.WARNING, "备份密文文件清理失败：" + file, ex);
			return false;
		}
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
