package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-53 密钥轮换（就地重加密）：POST /backups/{backupId}/rotate-key 用旧口令解密 → 新口令重新加密，
 * 覆盖原 .enc 文件并就地更新 backup_record 的 salt/iv/size_bytes。备份 id 与明文数据不变。
 *
 * 验证：成功轮换（salt/iv 变、id/created_at/algorithm 不变、旧口令解密 422、新口令可恢复、
 * last_backup_id 不受影响、审计写 BACKUP_KEY_ROTATED、临时文件已清、passphrase 不回显不落库）；
 * 旧口令错误 422 无副作用；新口令弱 400 无副作用（fail fast）；404；幂等回放不重复写审计；
 * 相同口令仍可重加密；全量审计可按 action=BACKUP_KEY_ROTATED / resourceType=BACKUP_RECORD 过滤查询。
 */
class BackupKeyRotationIntegrationTest extends AbstractIntegrationTest {

	private static final String OLD_PASSPHRASE = "TestPass1234!plus";
	private static final String NEW_PASSPHRASE = "AnotherStrong42!key";

	private static final Path BACKUP_DIR = Paths.get("./target/backups");

	@BeforeEach
	void cleanBackupDir() {
		// 清空 backup-dir 避免上一方法残留 .enc 干扰临时文件与孤儿判定
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

	/** 造一个岗位并生成一份加密备份，返回响应体与落盘 .enc 文件路径。 */
	private CreatedBackup createBackup(String passphrase) throws Exception {
		String jobBody = TestFixtures.createJobBody("轮换示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = created.getBody();
		String id = JsonProbe.str(body, "id");
		String fileName = JsonProbe.str(body, "fileName");
		assertThat(id).isNotNull();
		assertThat(fileName).isEqualTo(id + ".enc");
		Path file = BACKUP_DIR.resolve(fileName);
		assertThat(Files.exists(file)).isTrue();
		return new CreatedBackup(id, fileName, file, JsonProbe.lng(body, "sizeBytes"));
	}

	/** POST /backups/{id}/rotate-key 调用，返回响应（4xx/5xx 不抛异常，直接返回状态与体）。 */
	private ResponseEntity<String> rotateKey(String id, String oldPass, String newPass, String idempotencyKey) {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		if (idempotencyKey != null) {
			h.add("Idempotency-Key", idempotencyKey);
		}
		String body = "{\"oldPassphrase\":\"" + oldPass + "\",\"newPassphrase\":\"" + newPass + "\"}";
		return restTemplate.exchange(url("/backups/" + id + "/rotate-key"), HttpMethod.POST,
			new HttpEntity<>(body, h), String.class);
	}

	/** 上传 .enc 与 passphrase 调用恢复端点，返回响应。 */
	private ResponseEntity<String> restore(byte[] encBytes, String passphrase) {
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

	private record CreatedBackup(String id, String fileName, Path file, Long sizeBytes) { }

	@Test
	void AT53_rotateKeyReEncryptsInPlaceAndKeepsIdUnchanged() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		// 记录轮换前的 salt/iv/size_bytes/created_at
		Map<String, Object> before = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes, created_at, algorithm, pbkdf2_iterations, data_export_id, file_path, file_name "
				+ "FROM backup_record WHERE id = ?", b.id);
		byte[] saltBefore = (byte[]) before.get("salt");
		byte[] ivBefore = (byte[]) before.get("iv");
		Long sizeBefore = ((Number) before.get("size_bytes")).longValue();

		ResponseEntity<String> rotated = rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, TestFixtures.newKey());
		assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = rotated.getBody();
		assertThat(body).isNotNull();
		// 响应字段：id/createdAt/algorithm/pbkdf2Iterations/dataExportId/fileName 不变；sizeBytes 为新密文大小
		assertThat(JsonProbe.str(body, "id")).isEqualTo(b.id);
		assertThat(JsonProbe.str(body, "createdAt")).isEqualTo(before.get("created_at"));
		assertThat(JsonProbe.str(body, "algorithm")).isEqualTo("AES_256_GCM_PBKDF2");
		assertThat(JsonProbe.intVal(body, "pbkdf2Iterations")).isEqualTo(100_000);
		assertThat(JsonProbe.str(body, "dataExportId")).isEqualTo(before.get("data_export_id"));
		assertThat(JsonProbe.str(body, "fileName")).isEqualTo(b.fileName);
		// 响应不回显 passphrase/salt/iv/score/level
		assertThat(body).doesNotContain(OLD_PASSPHRASE);
		assertThat(body).doesNotContain(NEW_PASSPHRASE);
		assertThat(body).doesNotContain("\"salt\"");
		assertThat(body).doesNotContain("\"iv\"");
		assertThat(body).doesNotContain("\"score\"");
		assertThat(body).doesNotContain("\"level\"");

		// DB：salt/iv 已变，size_bytes 反映新密文大小；id/created_at/algorithm/iterations/data_export_id/file_path/file_name 不变
		Map<String, Object> after = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes, created_at, algorithm, pbkdf2_iterations, data_export_id, file_path, file_name "
				+ "FROM backup_record WHERE id = ?", b.id);
		assertThat(after.get("salt")).isNotEqualTo(saltBefore);
		assertThat(after.get("iv")).isNotEqualTo(ivBefore);
		assertThat(((Number) after.get("size_bytes")).longValue()).isEqualTo(JsonProbe.lng(body, "sizeBytes"));
		assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
		assertThat(after.get("algorithm")).isEqualTo(before.get("algorithm"));
		assertThat(after.get("pbkdf2_iterations")).isEqualTo(before.get("pbkdf2_iterations"));
		assertThat(after.get("data_export_id")).isEqualTo(before.get("data_export_id"));
		assertThat(after.get("file_path")).isEqualTo(before.get("file_path"));
		assertThat(after.get("file_name")).isEqualTo(before.get("file_name"));

		// 落盘 .enc 文件已用新口令重新加密（文件仍存在，临时文件已清理）
		assertThat(Files.exists(b.file)).isTrue();
		assertThat(Files.exists(Paths.get(b.file.toString() + ".tmp"))).isFalse();

		// 旧口令恢复 → 422（旧口令已不可解密）
		byte[] newEnc = Files.readAllBytes(b.file);
		ResponseEntity<String> restoreOld = restore(newEnc, OLD_PASSPHRASE);
		assertThat(restoreOld.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		// 新口令恢复 → 200 成功（明文数据未变，行级幂等恢复插入缺失行）
		// 先清空业务数据使恢复能插入缺失行验证明文可还原
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");
		ResponseEntity<String> restoreNew = restore(newEnc, NEW_PASSPHRASE);
		assertThat(restoreNew.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(restoreNew.getBody(), "inserted")).isPositive();
		Integer jobsAfterRestore = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobsAfterRestore).isEqualTo(1);

		// 下载用新口令链路可达（GET 不需 passphrase，返回 200）
		ResponseEntity<String> download = restTemplate.getForEntity(url("/backups/" + b.id + "/download"), String.class);
		assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void AT53_rotateKeyWritesBackupKeyRotatedAuditLog() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, TestFixtures.newKey());

		Map<String, Object> row = jdbc.queryForMap(
			"SELECT resource_type, resource_id, action, before_snapshot_json, after_snapshot_json, reason, occurred_at "
				+ "FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED' AND resource_id = ?", b.id);
		assertThat(row.get("resource_type")).isEqualTo("BACKUP_RECORD");
		assertThat(row.get("action")).isEqualTo("BACKUP_KEY_ROTATED");
		assertThat(row.get("resource_id")).isEqualTo(b.id);
		assertThat(row.get("before_snapshot_json")).isNull();
		assertThat(row.get("after_snapshot_json")).isNull();
		assertThat(String.valueOf(row.get("reason"))).contains("rotated");
		assertThat(row.get("occurred_at")).isNotNull();
	}

	@Test
	void AT53_wrongOldPassphraseReturns422NoSideEffect() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		Map<String, Object> before = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		byte[] fileBefore = Files.readAllBytes(b.file);
		long auditBefore = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);

		ResponseEntity<String> bad = rotateKey(b.id, "wrong-old-passphrase", NEW_PASSPHRASE, TestFixtures.newKey());
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		// 无副作用：salt/iv/size_bytes 未变，文件未变，审计无新增
		Map<String, Object> after = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		assertThat(after.get("salt")).isEqualTo(before.get("salt"));
		assertThat(after.get("iv")).isEqualTo(before.get("iv"));
		assertThat(after.get("size_bytes")).isEqualTo(before.get("size_bytes"));
		assertThat(Files.readAllBytes(b.file)).isEqualTo(fileBefore);
		long auditAfter = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);
		assertThat(auditAfter).isEqualTo(auditBefore);
	}

	@Test
	void AT53_weakNewPassphraseReturns400NoSideEffect() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		Map<String, Object> before = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		byte[] fileBefore = Files.readAllBytes(b.file);
		long auditBefore = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);

		// 弱口令（纯重复字符，score<40<70）
		ResponseEntity<String> bad = rotateKey(b.id, OLD_PASSPHRASE, "aaaaaaaa", TestFixtures.newKey());
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(bad.getBody()).contains("score");

		// 无副作用：fail fast（强度不足时不解密不落盘不写审计）
		Map<String, Object> after = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		assertThat(after.get("salt")).isEqualTo(before.get("salt"));
		assertThat(after.get("iv")).isEqualTo(before.get("iv"));
		assertThat(Files.readAllBytes(b.file)).isEqualTo(fileBefore);
		long auditAfter = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);
		assertThat(auditAfter).isEqualTo(auditBefore);
	}

	@Test
	void AT53_missingBackupReturns404() {
		String missingId = java.util.UUID.randomUUID().toString();
		ResponseEntity<String> bad = rotateKey(missingId, OLD_PASSPHRASE, NEW_PASSPHRASE, TestFixtures.newKey());
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		// 无审计写入
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED' AND resource_id = ?",
			Integer.class, missingId);
		assertThat(count).isZero();
	}

	@Test
	void AT53_idempotentReplayDoesNotReRotateOrDuplicateAudit() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, key);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> afterFirst = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		long auditFirst = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);

		// 幂等回放：返回首次缓存响应，salt/iv 不再变化，审计不重复写入
		ResponseEntity<String> replay = rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, key);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> afterReplay = jdbc.queryForMap(
			"SELECT salt, iv, size_bytes FROM backup_record WHERE id = ?", b.id);
		assertThat(afterReplay.get("salt")).isEqualTo(afterFirst.get("salt"));
		assertThat(afterReplay.get("iv")).isEqualTo(afterFirst.get("iv"));
		long auditReplay = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED'", Long.class);
		assertThat(auditReplay).isEqualTo(auditFirst);
	}

	@Test
	void AT53_samePassphraseStillReEncrypts() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		byte[] saltBefore = (byte[]) jdbc.queryForMap(
			"SELECT salt FROM backup_record WHERE id = ?", b.id).get("salt");

		// newPassphrase == oldPassphrase 不拒绝，仍刷新 salt/iv
		ResponseEntity<String> rotated = rotateKey(b.id, OLD_PASSPHRASE, OLD_PASSPHRASE, TestFixtures.newKey());
		assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);

		byte[] saltAfter = (byte[]) jdbc.queryForMap(
			"SELECT salt FROM backup_record WHERE id = ?", b.id).get("salt");
		assertThat(saltAfter).isNotEqualTo(saltBefore);

		// 旧口令==新口令仍可恢复（同一口令解密新密文）
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");
		ResponseEntity<String> restored = restore(Files.readAllBytes(b.file), OLD_PASSPHRASE);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(restored.getBody(), "inserted")).isPositive();

		// 审计有一条新 BACKUP_KEY_ROTATED 记录
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED' AND resource_id = ?",
			Integer.class, b.id);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void AT53_auditLogQueryFiltersByBackupKeyRotated() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, TestFixtures.newKey());

		// GET /audit-logs?action=BACKUP_KEY_ROTATED 可查到
		ResponseEntity<String> byAction = restTemplate.getForEntity(
			url("/audit-logs?action=BACKUP_KEY_ROTATED"), String.class);
		assertThat(byAction.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(byAction.getBody(), "total")).isGreaterThanOrEqualTo(1);
		boolean found = com.jobhub.integration.support.JsonProbe.collectArrayField(
				byAction.getBody(), "items", "resourceId")
			.stream().anyMatch(b.id::equals);
		assertThat(found).isTrue();

		// GET /audit-logs?resourceType=BACKUP_RECORD 含 BACKUP_KEY_ROTATED（与其他 BACKUP_RECORD action 共存）
		ResponseEntity<String> byType = restTemplate.getForEntity(
			url("/audit-logs?resourceType=BACKUP_RECORD"), String.class);
		assertThat(byType.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(byType.getBody(), "total")).isGreaterThanOrEqualTo(1);
	}

	@Test
	void AT53_passphraseNeverPersistedOrEchoed() throws Exception {
		CreatedBackup b = createBackup(OLD_PASSPHRASE);
		rotateKey(b.id, OLD_PASSPHRASE, NEW_PASSPHRASE, TestFixtures.newKey());

		// backup_record 无 passphrase 列
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
		// 审计记录从不存 passphrase（快照恒 null）
		Integer passInAudit = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_KEY_ROTATED' AND resource_id = ? "
				+ "AND (before_snapshot_json IS NOT NULL OR after_snapshot_json IS NOT NULL)",
			Integer.class, b.id);
		assertThat(passInAudit).isZero();
	}
}
