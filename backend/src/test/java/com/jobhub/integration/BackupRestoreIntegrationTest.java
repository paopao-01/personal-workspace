package com.jobhub.integration;

import com.jobhub.backup.application.EncryptionService;
import com.jobhub.datamanagement.application.ExportService;
import com.jobhub.datamanagement.domain.DataExport;
import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-37 加密备份恢复：上传 .enc 文件 + passphrase，解密后行级幂等恢复。
 * 验证：缺失行被插入、重复恢复幂等、错误 passphrase/损坏文件 422、短 passphrase 400。
 * passphrase 与派生密钥不回显、不落库。
 */
class BackupRestoreIntegrationTest extends AbstractIntegrationTest {

	private static final String PASSPHRASE = "TestPass1234!plus";

	private static final Path BACKUP_DIR = Paths.get("./target/backups");

	@Autowired
	private ExportService exportService;

	@Autowired
	private EncryptionService encryption;

	@BeforeEach
	void disarmAndCleanBackupDir() {
		// 清空 backup-dir 避免上一方法残留 .enc 干扰孤儿计数（无孤儿测试尤其敏感）
		if (Files.isDirectory(BACKUP_DIR)) {
			try (var stream = Files.list(BACKUP_DIR)) {
				stream.forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (Exception ignored) { }
				});
			} catch (Exception ignored) { }
		}
	}

	/** 生成一份加密备份并返回落盘 .enc 文件的字节数组。 */
	private byte[] createBackupEncFile() throws Exception {
		// 先造一个岗位，保证导出有可导出数据
		String jobBody = TestFixtures.createJobBody("恢复示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		// 生成加密备份
		String reqBody = "{\"passphrase\":\"" + PASSPHRASE + "\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String fileName = JsonProbe.str(created.getBody(), "fileName");

		Path file = Paths.get("./target/backups", fileName);
		assertThat(Files.exists(file)).isTrue();
		return Files.readAllBytes(file);
	}

	/** 上传 .enc 与 passphrase 调用恢复端点，返回响应（错误响应不抛异常，直接返回状态与体）。 */
	private ResponseEntity<String> restore(byte[] encBytes, String passphrase) {
		// 用 RestClient 的 exchange 捕获 4xx/5xx 响应体而非抛异常
		RestClient client = RestClient.builder().build();
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("file", new ByteArrayResource(encBytes) {
			@Override
			public String getFilename() {
				return "backup.enc";
			}
		});
		parts.add("passphrase", passphrase);
		return client.post()
			.uri(url("/backups/restore"))
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(parts)
			.exchange((req, res) -> new ResponseEntity<>(res.bodyTo(String.class), res.getStatusCode()));
	}

	@Test
	void AT37_restoreInsertsMissingRowsAndIsIdempotent() throws Exception {
		// 1. 生成备份（此时数据库有 1 个岗位及若干候选要求）
		byte[] enc = createBackupEncFile();

		// 备份时 job_posting 应有 1 条；knowledge_point 等表因 DatabaseCleaner 不清可能累积，
		// 但不影响恢复语义验证。
		Integer jobsBeforeClear = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsBeforeClear).isEqualTo(1);

		// 2. 清空业务数据（模拟数据库丢失/部分清空）。注意 DatabaseCleaner 不清 knowledge_point，
		// 故备份中的 knowledge_point 行在恢复时会被识别为重复跳过——这正是行级幂等恢复的语义。
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");
		Integer jobsAfterClear = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterClear).isZero();

		// 3. 恢复：缺失的 job_posting 行应被重新插入
		ResponseEntity<String> restored = restore(enc, PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).isNotNull();
		assertThat(body).doesNotContain(PASSPHRASE);
		assertThat(JsonProbe.str(body, "status")).isNotNull();
		int inserted = JsonProbe.intVal(body, "inserted");
		assertThat(inserted).isPositive();
		Integer jobsAfterRestore = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterRestore).isEqualTo(1);

		// 4. 再次恢复：幂等——inserted=0（无新增行），job_posting 不产生重复行
		ResponseEntity<String> restoredAgain = restore(enc, PASSPHRASE);
		assertThat(restoredAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body2 = restoredAgain.getBody();
		assertThat(JsonProbe.intVal(body2, "inserted")).isZero();
		// 第二次恢复的 skippedIdentical 至少覆盖第一次插入的行（job_posting + 已存在表）
		assertThat(JsonProbe.intVal(body2, "skippedIdentical")).isGreaterThanOrEqualTo(inserted);
		Integer jobsAfterSecond = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterSecond).isEqualTo(1);

		// 5. passphrase/派生密钥不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void wrongPassphraseReturns422() throws Exception {
		byte[] enc = createBackupEncFile();
		// 清空业务数据避免恢复插入
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> bad = restore(enc, "wrong-passphrase");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		// 不进行任何插入
		Integer jobs = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobs).isZero();
	}

	@Test
	void truncatedFileReturns422() {
		// 不足 28 字节（缺 salt/iv）
		byte[] truncated = new byte[]{1, 2, 3};
		ResponseEntity<String> bad = restore(truncated, PASSPHRASE);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void shortPassphraseReturns400() throws Exception {
		byte[] enc = createBackupEncFile();
		// passphrase 短于 8 位 → 400
		ResponseEntity<String> bad = restore(enc, "short");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/**
	 * AT-44 恢复后自动孤儿清理联动：恢复成功后于事务内同步触发 cleanOrphans，
	 * 摘要随响应 orphanCleanSummary 返回，孤儿文件被物理删除，合法文件保留，
	 * backup_record 无变更；无孤儿时全 0；恢复失败不触发；幂等回放返回首次摘要。
	 */
	@Test
	void AT44_restoreAutoCleansOrphansAndReturnsSummary() throws Exception {
		// 造一份合法备份（记录与文件都在）
		byte[] enc = createBackupEncFile();
		// 取其落盘文件名，制造一个孤儿：复制为另一 UUID 命名 .enc 但不写 backup_record
		String legitFileName = JsonProbe.str(
			restTemplate.getForEntity(url("/backups"), String.class).getBody(), "0.fileName");
		assertThat(legitFileName).isNotNull();
		Path backupDir = Paths.get("./target/backups");
		String orphanId = java.util.UUID.randomUUID().toString();
		String orphanFileName = orphanId + ".enc";
		Path orphanFile = backupDir.resolve(orphanFileName);
		Files.createDirectories(backupDir);
		Files.copy(backupDir.resolve(legitFileName), orphanFile);
		assertThat(Files.exists(orphanFile)).isTrue();

		// 清空业务数据，使恢复能插入缺失行
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		// 恢复：应触发孤儿清理
		ResponseEntity<String> restored = restore(enc, PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).isNotNull();
		assertThat(body).doesNotContain(PASSPHRASE);
		// orphanCleanSummary 反映孤儿已被删
		assertThat(JsonProbe.intVal(body, "orphanCleanSummary.deletedFiles")).isGreaterThan(0);
		assertThat(JsonProbe.intVal(body, "orphanCleanSummary.orphanFiles")).isGreaterThan(0);
		assertThat(JsonProbe.lng(body, "orphanCleanSummary.freedBytes")).isGreaterThan(0L);
		// 孤儿文件已删
		assertThat(Files.exists(orphanFile)).isFalse();
		// 合法备份文件保留
		assertThat(Files.exists(backupDir.resolve(legitFileName))).isTrue();
		// backup_record 无变更（恢复不写、清理只删文件）
		Integer backupCount = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(backupCount).isEqualTo(1);

		// AT-47 恢复联动 cleanOrphans 删除孤儿后，audit_log 同样为被删孤儿写一条审计记录
		Map<String, Object> auditRow = jdbc.queryForMap(
			"SELECT resource_type, resource_id, action, reason, freed_bytes FROM audit_log "
				+ "WHERE action = 'BACKUP_ORPHAN_CLEANED' AND resource_id = ?", orphanId);
		assertThat(auditRow.get("resource_type")).isEqualTo("BACKUP_FILE");
		assertThat(auditRow.get("resource_id")).isEqualTo(orphanId);
		assertThat(String.valueOf(auditRow.get("reason"))).doesNotContain("freedBytes=");
		assertThat(((Number) auditRow.get("freed_bytes")).longValue()).isGreaterThan(0L);
	}

	@Test
	void AT44_restoreWithNoOrphansReturnsAllZeroSummary() throws Exception {
		byte[] enc = createBackupEncFile();
		// backup-dir 仅含合法备份，无孤儿
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(JsonProbe.intVal(body, "orphanCleanSummary.orphanFiles")).isZero();
		assertThat(JsonProbe.intVal(body, "orphanCleanSummary.deletedFiles")).isZero();
	}

	@Test
	void AT44_nonUuidEncFileIsSkippedDuringAutoClean() throws Exception {
		byte[] enc = createBackupEncFile();
		// 放一个非 UUID 命名的 .enc（应跳过不删）
		Path backupDir = Paths.get("./target/backups");
		Files.createDirectories(backupDir);
		Path nonUuid = backupDir.resolve("notes.enc");
		Files.writeString(nonUuid, "not-a-backup");

		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(JsonProbe.intVal(body, "orphanCleanSummary.skippedFiles")).isGreaterThan(0);
		// 非 UUID 文件保留
		assertThat(Files.exists(nonUuid)).isTrue();
	}

	@Test
	void AT44_failedRestoreDoesNotTriggerOrphanClean() throws Exception {
		byte[] enc = createBackupEncFile();
		// 放一个孤儿
		Path backupDir = Paths.get("./target/backups");
		String orphanFileName = java.util.UUID.randomUUID() + ".enc";
		Path orphanFile = backupDir.resolve(orphanFileName);
		Files.createDirectories(backupDir);
		Files.writeString(orphanFile, "orphan-bytes");

		// 错误 passphrase → 422，不触发清理（孤儿仍在）
		ResponseEntity<String> bad = restore(enc, "wrong-passphrase");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(Files.exists(orphanFile)).isTrue();
	}

	/**
	 * 构造一份用指定 passphrase 加密的合法 .enc 备份文件字节数组。
	 * 不经 POST /backups（那样会被强度门槛拦未达 strong 的口令），而是直接调 ExportService + EncryptionService，
	 * 把密文按 salt(16)||iv(12)||ciphertext 布局拼成 .enc 字节，并以 <uuid>.enc 落盘 + 写 backup_record，
	 * 使其能被恢复端点识别为合法备份。用于 AT-46 弱/中口令备份恢复（门槛上线前用弱/中口令创建的历史备份，只能这样造）。
	 */
	private byte[] createBackupEncFileWithPassphrase(String passphrase) throws Exception {
		// 先造一个岗位，保证导出有可导出数据
		String jobBody = TestFixtures.createJobBody("恢复示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		DataExport export = exportService.create("JSON");
		if (!"SUCCEEDED".equals(export.getStatus())) {
			throw new IllegalStateException("数据包生成失败：" + export.getFailureReason());
		}
		byte[] plaintext = exportService.readExportFile(export);
		EncryptionService.EncryptedPayload payload = encryption.encrypt(plaintext, passphrase);

		String id = java.util.UUID.randomUUID().toString();
		String fileName = id + ".enc";
		Files.createDirectories(BACKUP_DIR);
		Path file = BACKUP_DIR.resolve(fileName);
		byte[] salt = payload.salt();
		byte[] iv = payload.iv();
		byte[] ct = payload.ciphertext();
		byte[] out = new byte[salt.length + iv.length + ct.length];
		System.arraycopy(salt, 0, out, 0, salt.length);
		System.arraycopy(iv, 0, out, salt.length, iv.length);
		System.arraycopy(ct, 0, out, salt.length + iv.length, ct.length);
		Files.write(file, out);

		// 写 backup_record 行（合法备份记录），使恢复端点能识别
		String now = java.time.Instant.now().toString();
		jdbc.update("INSERT INTO backup_record(id, created_at, algorithm, pbkdf2_iterations, salt, iv, "
				+ "data_export_id, file_path, file_name, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			id, now, "AES_256_GCM_PBKDF2", 100_000,
			payload.salt(), payload.iv(), export.getId(), file.toString(), fileName, (long) out.length);
		return out;
	}

	/**
	 * AT-46 恢复后弱/中口令重设提示：恢复成功后对本次 passphrase 内存评估，未达 strong（score<70，即弱或中）置
	 * passphraseResetRecommended=true；强为 false；恢复端点仍豁免门槛（仅提示不阻塞）；
	 * 错误 passphrase 422 不评估；幂等回放返回首次值；响应不回显 passphrase/score/level。
	 */
	@Test
	void AT46_weakPassphraseRestoreReturnsRecommended() throws Exception {
		// 弱 passphrase（"aaaaaaaa"，score<40，纯重复字符扣分）
		String weakPassphrase = "aaaaaaaa";
		byte[] enc = createBackupEncFileWithPassphrase(weakPassphrase);

		// 清空业务数据使恢复能插入缺失行
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, weakPassphrase);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).isNotNull();
		// 恢复本身不拒绝（端点豁免门槛，仅提示）
		assertThat(body).doesNotContain(weakPassphrase);
		// 弱口令 → passphraseResetRecommended=true
		assertThat(JsonProbe.bool(body, "passphraseResetRecommended")).isTrue();
		// 响应不回显 score/level（避免经响应侧信道泄露 passphrase 特征）
		assertThat(body).doesNotContain("\"score\"");
		assertThat(body).doesNotContain("\"level\"");
	}

	/** 中 passphrase（score 40–69，未达 strong）恢复 → passphraseResetRecommended=true（AT-46 扩 fair）。 */
	@Test
	void AT46_fairPassphraseRestoreReturnsRecommended() throws Exception {
		// 中 passphrase（"CorrectHorse42"，无符号，score 40–69，fair，未达 strong）
		String fairPassphrase = "CorrectHorse42";
		byte[] enc = createBackupEncFileWithPassphrase(fairPassphrase);

		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, fairPassphrase);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).doesNotContain(fairPassphrase);
		// 中口令未达 strong → passphraseResetRecommended=true（AT-46 扩 fair）
		assertThat(JsonProbe.bool(body, "passphraseResetRecommended")).isTrue();
		assertThat(body).doesNotContain("\"score\"");
		assertThat(body).doesNotContain("\"level\"");
	}

	@Test
	void AT46_strongPassphraseRestoreReturnsFalse() throws Exception {
		// 强 passphrase（score≥70）→ passphraseResetRecommended=false
		String strongPassphrase = "CorrectHorse42!battery";
		byte[] enc = createBackupEncFileWithPassphrase(strongPassphrase);

		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, strongPassphrase);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = restored.getBody();
		assertThat(body).doesNotContain(strongPassphrase);
		assertThat(JsonProbe.bool(body, "passphraseResetRecommended")).isFalse();
	}

	@Test
	void AT46_failedRestoreDoesNotEvaluate() throws Exception {
		String weakPassphrase = "aaaaaaaa";
		byte[] enc = createBackupEncFileWithPassphrase(weakPassphrase);

		// 错误 passphrase → 422，不进行强度评估（响应不含 passphraseResetRecommended，不产生提示副作用）
		ResponseEntity<String> bad = restore(enc, "wrong-passphrase");
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		String body = bad.getBody();
		// 错误响应体不含 passphraseResetRecommended 字段（评估未发生）
		if (body != null) {
			assertThat(body).doesNotContain("passphraseResetRecommended");
		}
	}

	@Test
	void AT46_repeatRestoreIsDeterministicAndIdempotent() throws Exception {
		// 恢复为行级幂等：重复恢复不重复插入；passphraseResetRecommended 两次结果一致（确定性）。
		// 注：multipart 请求不缓存幂等响应（IdempotencyBodyCachingFilter 设计跳过 multipart），
		// 故此处验证自然幂等性而非 HTTP 缓存回放——弱口令两次恢复均返回 recommended=true。
		String weakPassphrase = "aaaaaaaa";
		byte[] enc = createBackupEncFileWithPassphrase(weakPassphrase);

		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		// 第一次恢复：插入缺失行，recommended=true
		ResponseEntity<String> first = restore(enc, weakPassphrase);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.bool(first.getBody(), "passphraseResetRecommended")).isTrue();
		Integer jobsAfterFirst = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterFirst).isEqualTo(1);

		// 第二次恢复：行级幂等，不重复插入；recommended 仍为 true（确定性，与首次一致）
		ResponseEntity<String> second = restore(enc, weakPassphrase);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.bool(second.getBody(), "passphraseResetRecommended")).isTrue();
		assertThat(JsonProbe.intVal(second.getBody(), "inserted")).isZero();
		Integer jobsAfterSecond = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterSecond).isEqualTo(1);
	}

	@Test
	void AT46_standardDataImportRestoreReturnsNull() throws Exception {
		// 标准数据恢复 POST /data-imports/restore 不评估 passphrase → passphraseResetRecommended 为 null
		// 先造数据并导出标准 JSON 包
		String jobBody = TestFixtures.createJobBody("标准恢复示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);
		DataExport export = exportService.create("JSON");
		assertThat(export.getStatus()).isEqualTo("SUCCEEDED");
		byte[] packageBytes = exportService.readExportFile(export);

		// 清空业务数据
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		// 调标准数据恢复（不带 passphrase，无 multipart）
		RestClient client = RestClient.builder().build();
		ResponseEntity<String> restored = client.post()
			.uri(url("/data-imports/restore"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(packageBytes)
			.exchange((req, res) -> new ResponseEntity<>(res.bodyTo(String.class), res.getStatusCode()));
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		// 标准恢复不评估 → 字段缺省（JSON 中无 passphraseResetRecommended，或为 null）
		String body = restored.getBody();
		// 字段应缺省或 null（JsonProbe.bool 对缺省/null 返回 null）
		assertThat(JsonProbe.bool(body, "passphraseResetRecommended")).isNull();
		// 标准恢复响应也不含 passphrase/score/level
		assertThat(body).doesNotContain("\"score\"");
		assertThat(body).doesNotContain("\"level\"");
	}

	@Test
	void AT46_passphraseNeverPersistedOrEchoed() throws Exception {
		String weakPassphrase = "aaaaaaaa";
		byte[] enc = createBackupEncFileWithPassphrase(weakPassphrase);

		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");

		ResponseEntity<String> restored = restore(enc, weakPassphrase);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		// passphrase 不回显
		assertThat(restored.getBody()).doesNotContain(weakPassphrase);
		// passphrase 不落库（backup_record 无 passphrase 列）
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}
}
