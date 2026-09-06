package com.jobhub.integration;

import com.jobhub.backup.application.BackupScheduleService;
import com.jobhub.backup.infrastructure.BackupScheduleMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-39 加密备份删除：物理删除记录行 + 落盘 .enc 文件清理 + last_backup_id 软引用置空。
 * 不可恢复、不进入最近删除；X-Confirm-Permanent-Delete 确认头防误删；passphrase 不参与删除验证。
 * 幂等性由 Idempotency-Key 保证（重复删除命中回放，未命中则资源已不存在返回 404）。
 */
class BackupDeletionIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BackupScheduleService scheduleService;

	@Autowired
	private BackupScheduleMapper scheduleMapper;

	@BeforeEach
	void disarmBeforeEach() {
		// armed 是单例 bean 内存字段，跨方法不随 DatabaseCleaner 重置，显式解除避免串扰
		scheduleService.disarm();
	}

	/** 造一个岗位并生成一份加密备份，返回响应体与落盘 .enc 文件路径。 */
	private CreatedBackup createBackup(String passphrase) throws Exception {
		String jobBody = TestFixtures.createJobBody("删除示例", "Java 后端");
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
		Path file = Paths.get("./target/backups", fileName);
		assertThat(Files.exists(file)).isTrue();
		return new CreatedBackup(id, fileName, file);
	}

	/** DELETE /backups/{id} 携带确认头。 */
	private ResponseEntity<String> delete(String id, String idempotencyKey, boolean withConfirm) {
		HttpHeaders h = new HttpHeaders();
		if (idempotencyKey != null) {
			h.add("Idempotency-Key", idempotencyKey);
		}
		if (withConfirm) {
			h.add("X-Confirm-Permanent-Delete", "true");
		}
		return restTemplate.exchange(url("/backups/" + id), HttpMethod.DELETE, new HttpEntity<>(h), String.class);
	}

	@Test
	void AT39_deleteRemovesRecordFileAndClearsLastBackupId() throws Exception {
		CreatedBackup b = createBackup("TestPass1234");

		// 将 last_backup_id 指向该备份，验证删除后软引用被置空
		scheduleMapper.updateLastRun("2026-09-06T00:00:00Z", "SUCCESS", null, b.id);
		String lastIdBefore = jdbc.queryForObject(
			"SELECT last_backup_id FROM backup_schedule WHERE id = 'singleton'", String.class);
		assertThat(lastIdBefore).isEqualTo(b.id);

		ResponseEntity<String> deleted = delete(b.id, TestFixtures.newKey(), true);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// 列表不再包含该记录
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).doesNotContain(b.id);

		// 下载返回 404
		ResponseEntity<String> dl = restTemplate.getForEntity(
			url("/backups/" + b.id + "/download"), String.class);
		assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// 落盘 .enc 文件已清理
		assertThat(Files.exists(b.file)).isFalse();

		// last_backup_id 软引用置空
		String lastIdAfter = jdbc.queryForObject(
			"SELECT last_backup_id FROM backup_schedule WHERE id = 'singleton'", String.class);
		assertThat(lastIdAfter).isNull();

		// passphrase 不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void deleteMissingIdReturns404() {
		ResponseEntity<String> missing = delete(
			"99999999-9999-9999-9999-999999999999", TestFixtures.newKey(), true);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deleteWithoutConfirmHeaderReturns400() throws Exception {
		CreatedBackup b = createBackup("TestPass1234");
		ResponseEntity<String> bad = delete(b.id, TestFixtures.newKey(), false);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 记录与文件仍在（未删除）
		String list = restTemplate.getForEntity(url("/backups"), String.class).getBody();
		assertThat(list).contains(b.id);
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void deleteIsIdempotentWithSameKey() throws Exception {
		CreatedBackup b = createBackup("TestPass1234");
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = delete(b.id, key, true);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// 相同 Idempotency-Key 重复删除 → 幂等回放 204，不产生副作用
		ResponseEntity<String> second = delete(b.id, key, true);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// 文件确已不存在
		assertThat(Files.exists(b.file)).isFalse();
	}

	private record CreatedBackup(String id, String fileName, Path file) { }
}
