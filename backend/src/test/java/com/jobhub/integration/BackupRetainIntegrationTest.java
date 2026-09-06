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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-43 备份按数量保留清理：保留最近 N 条（created_at DESC），物理删除其余全部记录与密文文件，
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）；
 * X-Confirm-Permanent-Delete 确认头防误清；keepLast 必填 ≥1；
 * keepLast ≥ 现有总数 deletedCount=0（不报 404）；keepLast 与 olderThanDays 互斥；
 * Idempotency-Key 保证幂等回放。
 */
class BackupRetainIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BackupScheduleService scheduleService;

	@BeforeEach
	void disarmBeforeEach() {
		scheduleService.disarm();
	}

	/** 造一个岗位并生成一份加密备份，返回响应体与落盘 .enc 文件路径。 */
	private CreatedBackup createBackup(String passphrase) {
		String jobBody = TestFixtures.createJobBody("保留示例", "Java 后端");
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

	/** DELETE /api/backups?keepLast=N 携带确认头（可选 Idempotency-Key）。 */
	private ResponseEntity<String> retain(int keepLast, String idempotencyKey, boolean withConfirm) {
		HttpHeaders h = new HttpHeaders();
		if (idempotencyKey != null) {
			h.add("Idempotency-Key", idempotencyKey);
		}
		if (withConfirm) {
			h.add("X-Confirm-Permanent-Delete", "true");
		}
		String uri = url("/backups?keepLast=" + keepLast);
		return restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
	}

	@Test
	void AT43_keepLastRetainsNewestNAndDeletesRestWithFilesAndLastBackupId() {
		// 造 5 份备份，列表最新优先（最后造的 created_at 最晚，排最前）
		CreatedBackup b1 = createBackup("test1234");
		CreatedBackup b2 = createBackup("test1234");
		CreatedBackup b3 = createBackup("test1234");
		CreatedBackup b4 = createBackup("test1234");
		CreatedBackup b5 = createBackup("test1234");

		// 将 last_backup_id 指向将被删的最旧备份 b1，验证清理后软引用置空
		jdbc.update("UPDATE backup_schedule SET last_backup_id = ? WHERE id = 'singleton'", b1.id);

		// keepLast=2：保留 b5、b4（最新两条），删除 b3、b2、b1
		ResponseEntity<String> res = retain(2, TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "deletedCount")).isEqualTo(3);
		assertThat(JsonProbe.intVal(body, "filesCleaned")).isEqualTo(3);
		assertThat(JsonProbe.str(body, "lastBackupIdCleared")).isEqualTo("true");

		// 被删记录消失，保留的仍在
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).doesNotContain(b1.id);
		assertThat(list).doesNotContain(b2.id);
		assertThat(list).doesNotContain(b3.id);
		assertThat(list).contains(b4.id);
		assertThat(list).contains(b5.id);

		// 被删 .enc 文件已清理，保留的文件仍在
		assertThat(Files.exists(b1.file)).isFalse();
		assertThat(Files.exists(b2.file)).isFalse();
		assertThat(Files.exists(b3.file)).isFalse();
		assertThat(Files.exists(b4.file)).isTrue();
		assertThat(Files.exists(b5.file)).isTrue();

		// last_backup_id 软引用置空
		String lastId = jdbc.queryForObject(
			"SELECT last_backup_id FROM backup_schedule WHERE id = 'singleton'", String.class);
		assertThat(lastId).isNull();

		// passphrase 不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();

		// 清理保留的备份
		jdbc.update("DELETE FROM backup_record WHERE id IN (?, ?)", b4.id, b5.id);
	}

	@Test
	void retainWithoutConfirmHeaderReturns400() {
		CreatedBackup b = createBackup("test1234");
		ResponseEntity<String> bad = retain(2, TestFixtures.newKey(), false);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 记录与文件仍在（未删除）
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).contains(b.id);
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void retainWithInvalidKeepLastReturns400() {
		// keepLast=0 → @Min(1) 校验失败 → 400
		ResponseEntity<String> bad = retain(0, TestFixtures.newKey(), true);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void retainWithBothParamsReturns400() {
		// keepLast 与 olderThanDays 同时出现 → 400
		HttpHeaders h = new HttpHeaders();
		h.add("X-Confirm-Permanent-Delete", "true");
		h.add("Idempotency-Key", TestFixtures.newKey());
		String uri = url("/backups?keepLast=2&olderThanDays=5");
		ResponseEntity<String> bad = restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(h), String.class);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void retainWithoutAnyParamReturns400() {
		// 缺 keepLast 与 olderThanDays → 400
		HttpHeaders h = new HttpHeaders();
		h.add("X-Confirm-Permanent-Delete", "true");
		h.add("Idempotency-Key", TestFixtures.newKey());
		ResponseEntity<String> bad = restTemplate.exchange(url("/backups"), HttpMethod.DELETE,
			new HttpEntity<>(h), String.class);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void keepLastExceedingTotalReturns200WithZero() {
		// 仅有 1 份备份，keepLast=10 ≥ 总数 → 200 deletedCount=0，全部保留
		CreatedBackup b = createBackup("test1234");
		ResponseEntity<String> res = retain(10, TestFixtures.newKey(), true);
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
	void keepLastEqualToTotalReturns200WithZero() {
		// keepLast = 现有总数 → 全部保留 deletedCount=0
		CreatedBackup b1 = createBackup("test1234");
		CreatedBackup b2 = createBackup("test1234");
		ResponseEntity<String> res = retain(2, TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(res.getBody(), "deletedCount")).isZero();
		assertThat(Files.exists(b1.file)).isTrue();
		assertThat(Files.exists(b2.file)).isTrue();
	}

	@Test
	void retainIsIdempotentWithSameKey() {
		CreatedBackup b1 = createBackup("test1234");
		CreatedBackup b2 = createBackup("test1234");
		CreatedBackup b3 = createBackup("test1234");
		String key = TestFixtures.newKey();

		// keepLast=1：保留 b3（最新），删除 b2、b1
		ResponseEntity<String> first = retain(1, key, true);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(first.getBody(), "deletedCount")).isEqualTo(2);
		assertThat(Files.exists(b1.file)).isFalse();
		assertThat(Files.exists(b2.file)).isFalse();

		// 相同 Idempotency-Key 重复清理 → 幂等回放，返回首次缓存的相同摘要（deletedCount=2），
		// 不重新执行清理、不产生额外副作用
		ResponseEntity<String> second = retain(1, key, true);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(second.getBody(), "deletedCount")).isEqualTo(2);
	}

	@SuppressWarnings("unused")
	private record CreatedBackup(String id, String fileName, Path file) { }
}
