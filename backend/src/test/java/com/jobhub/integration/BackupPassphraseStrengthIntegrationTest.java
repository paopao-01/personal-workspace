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
 * AT-45 passphrase 强度强制门槛：创建备份与武装调度的 service 入口强制校验
 * （与前端同一算法，阈值弱<40），弱口令返回 400 VALIDATION_ERROR 含 score 与失败规则，
 * 不进行加密/落盘/武装；恢复端点豁免（passphrase 已与备份绑定）。
 * passphrase 不落盘/不进日志/不回显，不新增评估端点。
 */
class BackupPassphraseStrengthIntegrationTest extends AbstractIntegrationTest {

	private static final Path BACKUP_DIR = Paths.get("./target/backups");

	/** 弱口令（纯重复，score=0）；创建与武装应拒绝。 */
	private static final String WEAK_ALL_SAME = "aaaaaaaa";
	/** 弱口令（命中黑名单前缀 "password"，score 不足 40）；创建应拒绝。 */
	private static final String WEAK_COMMON = "password12345";
	/** 中等 passphrase（≥12 位 + 大小写 + 数字，score=55，fair）应放行。 */
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

	/** POST /backups 弱口令（纯重复）→ 400 VALIDATION_ERROR，message 含 score 与 ≥40；不生成记录/文件。 */
	@Test
	void AT45_createRejectsWeakAllSamePassphrase() {
		seedJob();
		ResponseEntity<String> bad = createBackup(WEAK_ALL_SAME);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(bad.getBody()).isNotNull();
		assertThat(JsonProbe.str(bad.getBody(), "code")).isEqualTo("VALIDATION_ERROR");
		assertThat(bad.getBody()).contains("score=");
		assertThat(bad.getBody()).contains("≥40");
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

	/** POST /backups 中等/强 passphrase → 201；passphrase 不回显、不落库。 */
	@Test
	void AT45_createAcceptsFairAndStrongPassphrase() {
		seedJob();
		ResponseEntity<String> fair = createBackup(FAIR_PASSPHRASE);
		assertThat(fair.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(fair.getBody()).doesNotContain(FAIR_PASSPHRASE);
		String fairId = JsonProbe.str(fair.getBody(), "id");
		assertThat(fairId).isNotNull();

		ResponseEntity<String> strong = createBackup(STRONG_PASSPHRASE);
		assertThat(strong.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(strong.getBody()).doesNotContain(STRONG_PASSPHRASE);

		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(rows).isEqualTo(2);
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

	/** POST /backups/schedule/arm 中等/强 passphrase → 200 armed=true。 */
	@Test
	void AT45_armAcceptsFairAndStrongPassphrase() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		ResponseEntity<String> fair = arm(FAIR_PASSPHRASE);
		assertThat(fair.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(fair.getBody(), "armed")).isEqualTo("true");
		assertThat(fair.getBody()).doesNotContain(FAIR_PASSPHRASE);

		// 换强 passphrase 武装（覆盖）
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
		// 先用中等 passphrase 生成一份合法备份
		seedJob();
		ResponseEntity<String> created = createBackup(FAIR_PASSPHRASE);
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

		// 用正确（中等）passphrase 恢复成功，进一步证明恢复端点豁免门槛
		ResponseEntity<String> ok = restore(enc, FAIR_PASSPHRASE);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).doesNotContain(FAIR_PASSPHRASE);
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
