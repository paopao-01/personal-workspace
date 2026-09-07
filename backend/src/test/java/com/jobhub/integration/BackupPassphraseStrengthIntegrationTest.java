package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-45 passphrase 强度强制门槛（要求 strong）：创建备份与武装调度的 service 入口强制校验
 * （与前端同一算法，阈值弱<40/中40–69/强≥70，要求 strong），未达 strong（score<70，即弱或中）返回 400
 * VALIDATION_ERROR 含 score 与「≥70」与失败规则，不进行加密/落盘/武装；恢复端点豁免（passphrase 已与备份绑定）。
 * AT-48 强制 strong 门槛升级：中口令同样被拒（不再放行），只有 score≥70 放行；恢复后重设提示扩到 weak+fair。
 * passphrase 不落盘/不进日志/不回显，不新增评估端点。
 */
class BackupPassphraseStrengthIntegrationTest extends AbstractIntegrationTest {

	private static final Path BACKUP_DIR = Paths.get("./target/backups");

	/** 弱口令（纯重复，score=0）；创建与武装应拒绝。 */
	private static final String WEAK_ALL_SAME = "aaaaaaaa";
	/** 弱口令（命中黑名单前缀 "password"，score 不足 40）；创建应拒绝。 */
	private static final String WEAK_COMMON = "password12345";
	/** 中等 passphrase（≥12 位 + 大小写 + 数字，无符号，score=55，fair，未达 strong）应被拒。 */
	private static final String FAIR_PASSPHRASE = "CorrectHorse42";
	/** 强 passphrase（含符号，score≥70）应放行。 */
	private static final String STRONG_PASSPHRASE = "CorrectHorse42!battery";

	@BeforeEach
	void disarmAndCleanBackupDir() {
		// 清空 backup-dir 避免上一方法残留 .enc 干扰
		if (Files.isDirectory(BACKUP_DIR)) {
			try (var stream = Files.list(BACKUP_DIR)) {
				stream.forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (Exception ignored) {
					}
				});
			} catch (Exception ignored) {
			}
		}
	}

	/** POST /backups 弱口令（纯重复）→ 400 VALIDATION_ERROR，message 含 score 与 ≥70；不生成记录/文件。 */
	@Test
	void AT45_createRejectsWeakAllSamePassphrase() {
		seedJob();
		ResponseEntity<String> bad = createBackup(WEAK_ALL_SAME);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(bad.getBody()).isNotNull();
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		assertThat(bad.getBody()).contains("score=");
		assertThat(bad.getBody()).contains("≥70");
		// 不生成 backup_record，不落盘 .enc 文件
		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(rows).isZero();
		// backup-dir 下无 .enc 文件（加密前拦截）
		int encCount = 0;
		if (Files.isDirectory(BACKUP_DIR)) {
			try (var stream = Files.list(BACKUP_DIR)) {
				encCount = (int) stream.filter(p -> p.getFileName().toString().endsWith(".enc")).count();
			} catch (Exception ignored) {
			}
		}
		assertThat(encCount).isZero();
	}

	/** POST /backups 弱口令（命中黑名单）→ 400；不生成记录/文件。 */
	@Test
	void AT45_createRejectsCommonWeakPassphrase() {
		seedJob();
		ResponseEntity<String> bad = createBackup(WEAK_COMMON);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(rows).isZero();
	}

	/** POST /backups 中 passphrase → 400（AT-48：中口令同样被拒，未达 strong）；不生成记录/文件。 */
	@Test
	void AT48_createRejectsFairPassphrase() {
		seedJob();
		ResponseEntity<String> bad = createBackup(FAIR_PASSPHRASE);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		assertThat(bad.getBody()).contains("score=");
		assertThat(bad.getBody()).contains("≥70");
		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(rows).isZero();
	}

	/** POST /backups 强 passphrase → 201；passphrase 不回显、不落库。 */
	@Test
	void AT45_createAcceptsStrongPassphrase() {
		seedJob();
		ResponseEntity<String> strong = createBackup(STRONG_PASSPHRASE);
		assertThat(strong.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(strong.getBody()).doesNotContain(STRONG_PASSPHRASE);
		String strongId = JsonProbe.str(strong.getBody(), "id");
		assertThat(strongId).isNotNull();

		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(rows).isEqualTo(1);
		// passphrase 永不落库（backup_record 无 passphrase 列）
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	/** POST /backups/schedule/arm 弱口令 → 400；armed 仍为 false（不写入内存武装）。 */
	@Test
	void AT45_armRejectsWeakPassphrase() {
		// 先懒初始化单行配置
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		ResponseEntity<String> bad = arm(WEAK_ALL_SAME);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		assertThat(bad.getBody()).contains("score=");

		// armed 仍为 false（弱口令未武装）
		String sched = restTemplate.getForEntity(url("/backups/schedule"), String.class).getBody();
		assertThat(JsonProbe.str(sched, "armed")).isEqualTo("false");
	}

	/** POST /backups/schedule/arm 中 passphrase → 400（AT-48：未达 strong 一律拒，armed 仍 false）。 */
	@Test
	void AT48_armRejectsFairPassphrase() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		ResponseEntity<String> bad = arm(FAIR_PASSPHRASE);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		assertThat(bad.getBody()).contains("≥70");
		String sched = restTemplate.getForEntity(url("/backups/schedule"), String.class).getBody();
		assertThat(JsonProbe.str(sched, "armed")).isEqualTo("false");
	}

	/** POST /backups/schedule/arm 强 passphrase → 200 armed=true。 */
	@Test
	void AT45_armAcceptsStrongPassphrase() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		ResponseEntity<String> strong = arm(STRONG_PASSPHRASE);
		assertThat(strong.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(strong.getBody(), "armed")).isEqualTo("true");
		assertThat(strong.getBody()).doesNotContain(STRONG_PASSPHRASE);

		// passphrase 永不落库（backup_schedule 无 passphrase 列）
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_schedule') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	/** POST /backups/restore 不强制门槛：弱口令恢复不返回强度 400，而是按解密结果 422（passphrase 不匹配）。 */
	@Test
	void AT45_restoreExemptFromStrengthGate() throws Exception {
		// 先用强 passphrase 生成一份合法备份
		seedJob();
		ResponseEntity<String> created = createBackup(STRONG_PASSPHRASE);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String fileName = JsonProbe.str(created.getBody(), "fileName");
		Path encFile = BACKUP_DIR.resolve(fileName);
		assertThat(Files.exists(encFile)).isTrue();
		byte[] enc = Files.readAllBytes(encFile);

		// 用弱口令恢复：不返回强度 400，而是 422（弱口令非该备份的 passphrase，GCM 认证失败）
		ResponseEntity<String> weakAttempt = restore(enc, WEAK_ALL_SAME);
		assertThat(weakAttempt.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		// 确认不是强度门槛拒绝（无 score 字段）
		assertThat(weakAttempt.getBody()).doesNotContain("score=");

		// 用正确（强）passphrase 恢复成功，进一步证明恢复端点豁免门槛
		ResponseEntity<String> ok = restore(enc, STRONG_PASSPHRASE);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).doesNotContain(STRONG_PASSPHRASE);
	}

	/** AT-48：中 passphrase 恢复→豁免门槛不返强度 400，按解密结果处理；强 passphrase 创建的备份用中 passphrase 恢复失败 422。 */
	@Test
	void AT48_restoreExemptFairNotStrength400() throws Exception {
		seedJob();
		// 用强 passphrase 生成合法备份
		ResponseEntity<String> created = createBackup(STRONG_PASSPHRASE);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String fileName = JsonProbe.str(created.getBody(), "fileName");
		byte[] enc = Files.readAllBytes(BACKUP_DIR.resolve(fileName));

		// 用中 passphrase 恢复该备份：中 passphrase 非该备份的 passphrase，422 GCM 失败，且不含强度 400 的 score 字段
		ResponseEntity<String> fairAttempt = restore(enc, FAIR_PASSPHRASE);
		assertThat(fairAttempt.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(fairAttempt.getBody()).doesNotContain("score=");

		// 用正确强 passphrase 恢复成功，响应不含 score/level
		ResponseEntity<String> ok = restore(enc, STRONG_PASSPHRASE);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).doesNotContain(STRONG_PASSPHRASE);
		assertThat(ok.getBody()).doesNotContain("\"score\"");
		assertThat(ok.getBody()).doesNotContain("\"level\"");
	}

	private void seedJob() {
		String jobBody = TestFixtures.createJobBody("强度门槛示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);
	}

	private ResponseEntity<String> createBackup(String passphrase) {
		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		return restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
	}

	private ResponseEntity<String> arm(String passphrase) {
		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		return restTemplate.exchange(url("/backups/schedule/arm"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
	}

	private ResponseEntity<String> restore(byte[] encBytes, String passphrase) {
		org.springframework.web.client.RestClient client = org.springframework.web.client.RestClient.builder().build();
		org.springframework.util.LinkedMultiValueMap<String, Object> parts = new org.springframework.util.LinkedMultiValueMap<>();
		parts.add("file", new org.springframework.core.io.ByteArrayResource(encBytes) {
			@Override
			public String getFilename() {
				return "backup.enc";
			}
		});
		parts.add("passphrase", passphrase);
		return client.post()
			.uri(url("/backups/restore"))
			.contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
			.body(parts)
			.exchange((req, res) -> new ResponseEntity<>(res.bodyTo(String.class), res.getStatusCode()));
	}
}
