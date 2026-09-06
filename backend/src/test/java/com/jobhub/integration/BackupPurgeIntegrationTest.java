package com.jobhub.integration;

import com.jobhub.backup.application.BackupScheduleService;
import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-41 备份按龄批量清理：物理删除早于阈值的全部记录与密文文件，
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）；
 * X-Confirm-Permanent-Delete 确认头防误清；olderThanDays 必填 ≥1；
 * 无匹配记录 deletedCount=0（不报 404）；Idempotency-Key 保证幂等回放。
 */
class BackupPurgeIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BackupScheduleService scheduleService;

	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

	@BeforeEach
	void disarmBeforeEach() {
		scheduleService.disarm();
	}

	/** 造一个岗位并生成一份加密备份，返回响应体与落盘 .enc 文件路径。 */
	private CreatedBackup createBackup(String passphrase) {
		String jobBody = TestFixtures.createJobBody("清理示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = created.getBody();
		String id = JsonProbe.str(body, "id");
		String fileName = JsonProbe.str(body, "fileName");
		Path file = Paths.get("./target/backups", fileName);
		assertThat(Files.exists(file)).isTrue();
		return new CreatedBackup(id, fileName, file);
	}

	/** 将指定备份的 created_at 改为「N 天前」，模拟早于阈值。 */
	private void ageBackup(String id, long daysAgo) {
		String aged = ISO.format(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
		jdbc.update("UPDATE backup_record SET created_at = ? WHERE id = ?", aged, id);
	}

	/** DELETE /api/backups?olderThanDays=N 携带确认头（可选 Idempotency-Key）。 */
	private ResponseEntity<String> purge(int olderThanDays, String idempotencyKey, boolean withConfirm) {
		HttpHeaders h = new HttpHeaders();
		if (idempotencyKey != null) {
			h.add("Idempotency-Key", idempotencyKey);
		}
		if (withConfirm) {
			h.add("X-Confirm-Permanent-Delete", "true");
		}
		String uri = url("/backups?olderThanDays=" + olderThanDays);
		return restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
	}

	@Test
	void AT41_purgeDeletesOldRecordsFilesAndClearsLastBackupId() {
		// 造 3 份备份，其中 2 份改旧（10 天前），1 份保持最新
		CreatedBackup old1 = createBackup("test1234");
		CreatedBackup old2 = createBackup("test1234");
		CreatedBackup recent = createBackup("test1234");
		ageBackup(old1.id, 10);
		ageBackup(old2.id, 10);

		// 将 last_backup_id 指向其中一份旧备份，验证清理后软引用置空
		jdbc.update("UPDATE backup_schedule SET last_backup_id = ? WHERE id = 'singleton'", old1.id);

		// olderThanDays=5：cutoff = now - 5 天，10 天前的两份被删，最新的保留
		ResponseEntity<String> res = purge(5, TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "deletedCount")).isEqualTo(2);
		assertThat(JsonProbe.intVal(body, "filesCleaned")).isEqualTo(2);
		assertThat(JsonProbe.str(body, "lastBackupIdCleared")).isEqualTo("true");

		// 旧记录消失，新记录保留
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).doesNotContain(old1.id);
		assertThat(list).doesNotContain(old2.id);
		assertThat(list).contains(recent.id);

		// 旧 .enc 文件已清理，新文件保留
		assertThat(Files.exists(old1.file)).isFalse();
		assertThat(Files.exists(old2.file)).isFalse();
		assertThat(Files.exists(recent.file)).isTrue();

		// last_backup_id 软引用置空
		String lastId = jdbc.queryForObject(
			"SELECT last_backup_id FROM backup_schedule WHERE id = 'singleton'", String.class);
		assertThat(lastId).isNull();

		// passphrase 不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void purgeWithoutConfirmHeaderReturns400() {
		CreatedBackup b = createBackup("test1234");
		ageBackup(b.id, 10);
		ResponseEntity<String> bad = purge(5, TestFixtures.newKey(), false);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 记录与文件仍在（未删除）
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).contains(b.id);
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void purgeMissingOlderThanDaysParamReturns400() {
		// 缺 olderThanDays 参数 → 400
		HttpHeaders h = new HttpHeaders();
		h.add("X-Confirm-Permanent-Delete", "true");
		ResponseEntity<String> bad = restTemplate.exchange(url("/backups"), HttpMethod.DELETE,
			new HttpEntity<>(h), String.class);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void purgeWithInvalidOlderThanDaysReturns400() {
		// olderThanDays=0 → @Min(1) 校验失败 → 400
		ResponseEntity<String> bad = purge(0, TestFixtures.newKey(), true);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void purgeNoMatchReturns200WithZero() {
		// 仅有最新备份，阈值内无匹配 → 200 deletedCount=0，不报 404
		CreatedBackup b = createBackup("test1234");
		ResponseEntity<String> res = purge(5, TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "deletedCount")).isZero();
		assertThat(JsonProbe.intVal(body, "filesCleaned")).isZero();
		assertThat(JsonProbe.str(body, "lastBackupIdCleared")).isEqualTo("false");
		// 记录与文件仍在
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).contains(b.id);
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void purgeIsIdempotentWithSameKey() {
		CreatedBackup b = createBackup("test1234");
		ageBackup(b.id, 10);
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = purge(5, key, true);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(first.getBody(), "deletedCount")).isEqualTo(1);
		assertThat(Files.exists(b.file)).isFalse();

		// 相同 Idempotency-Key 重复清理 → 幂等回放，返回首次缓存的相同摘要（deletedCount=1），
		// 不重新执行清理、不产生额外副作用（记录与文件仍不存在）
		ResponseEntity<String> second = purge(5, key, true);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(second.getBody(), "deletedCount")).isEqualTo(1);
		assertThat(Files.exists(b.file)).isFalse();
	}

	@SuppressWarnings("unused")
	private record CreatedBackup(String id, String fileName, Path file) { }
}
