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
 * AT-42 孤儿 .enc 文件扫描清理：扫描 backup-dir 下全部 .enc 文件，物理删除无 backup_record 对应的孤儿。
 * 补偿单条删除/按龄清理在 afterCommit 文件清理前崩溃残留的孤儿（或 DB 直接删行绕过服务）。
 * 判定规则：UUID 命名且 backup_record 无对应 file_name 的 .enc 即孤儿；非 UUID 命名的 .enc 跳过不删。
 * X-Confirm-Permanent-Delete 确认头防误清；不写记录、不联动 last_backup_id/data_export；
 * 无孤儿/backup-dir 不存在返回全 0（不报 404）；Idempotency-Key 保证幂等回放返回首次摘要。
 */
class BackupOrphanScanIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BackupScheduleService scheduleService;

	private static final Path BACKUP_DIR = Paths.get("./target/backups");

	@BeforeEach
	void disarmAndCleanBackupDir() {
		scheduleService.disarm();
		// 清空 backup-dir，避免上一方法残留 .enc 文件干扰扫描计数
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
	private CreatedBackup createBackup(String passphrase) {
		String jobBody = TestFixtures.createJobBody("孤儿示例", "Java 后端");
		restTemplate.postForEntity(url("/jobs"), TestFixtures.httpJson(jobBody), String.class);

		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		ResponseEntity<String> created = restTemplate.exchange(url("/backups"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = created.getBody();
		String id = JsonProbe.str(body, "id");
		String fileName = JsonProbe.str(body, "fileName");
		Path file = BACKUP_DIR.resolve(fileName);
		assertThat(Files.exists(file)).isTrue();
		return new CreatedBackup(id, fileName, file);
	}

	/** 在 backup-dir 下放一个非 UUID 命名的 .enc 文件（模拟用户随手放入的无关文件）。 */
	private void placeNonUuidEncFile() {
		try {
			Files.createDirectories(BACKUP_DIR);
			Files.writeString(BACKUP_DIR.resolve("notes.enc"), "not-a-backup");
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** POST /api/backups/orphans/clean 携带确认头（可选 Idempotency-Key）。 */
	private ResponseEntity<String> cleanOrphans(String idempotencyKey, boolean withConfirm) {
		HttpHeaders h = new HttpHeaders();
		if (idempotencyKey != null) {
			h.add("Idempotency-Key", idempotencyKey);
		}
		if (withConfirm) {
			h.add("X-Confirm-Permanent-Delete", "true");
		}
		return restTemplate.exchange(url("/backups/orphans/clean"), HttpMethod.POST,
			new HttpEntity<>(h), String.class);
	}

	@Test
	void AT42_cleanOrphansDeletesOrphansKeepsLegitAndNonUuid() {
		// 造 2 份合法备份（文件与记录都在），再放 1 个非 UUID .enc
		CreatedBackup legit1 = createBackup("test1234");
		CreatedBackup legit2 = createBackup("test1234");
		placeNonUuidEncFile();

		// 制造孤儿：删除 legit1 的 backup_record 行但保留其 .enc 文件（模拟 afterCommit 崩溃残留）
		jdbc.update("DELETE FROM backup_record WHERE id = ?", legit1.id);
		assertThat(Files.exists(legit1.file)).isTrue();

		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		// 扫描到 3 个 .enc（legit1 孤儿 + legit2 合法 + notes.enc）
		assertThat(JsonProbe.intVal(body, "scannedFiles")).isEqualTo(3);
		assertThat(JsonProbe.intVal(body, "orphanFiles")).isEqualTo(1);
		assertThat(JsonProbe.intVal(body, "deletedFiles")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "freedBytes")).isGreaterThan(0L);
		// 非 UUID 命名的 notes.enc 跳过不删
		assertThat(JsonProbe.intVal(body, "skippedFiles")).isEqualTo(1);

		// 孤儿文件已删，合法文件保留，非 UUID 文件保留
		assertThat(Files.exists(legit1.file)).isFalse();
		assertThat(Files.exists(legit2.file)).isTrue();
		assertThat(Files.exists(BACKUP_DIR.resolve("notes.enc"))).isTrue();

		// backup_record 表未变更（孤儿本就无记录；合法记录仍在）
		Integer legitCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM backup_record WHERE id = ?", Integer.class, legit2.id);
		assertThat(legitCount).isEqualTo(1);

		// passphrase 不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void cleanOrphansWithoutConfirmHeaderReturns400() {
		CreatedBackup b = createBackup("test1234");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", b.id);
		ResponseEntity<String> bad = cleanOrphans(TestFixtures.newKey(), false);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 文件仍在（未删除）
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void cleanOrphansNoOrphansReturns200AllZero() {
		CreatedBackup b = createBackup("test1234");
		// 无孤儿：文件与记录都在
		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "scannedFiles")).isEqualTo(1);
		assertThat(JsonProbe.intVal(body, "orphanFiles")).isZero();
		assertThat(JsonProbe.intVal(body, "deletedFiles")).isZero();
		assertThat(JsonProbe.lng(body, "freedBytes")).isZero();
		assertThat(JsonProbe.intVal(body, "skippedFiles")).isZero();
		// 合法文件仍在
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void cleanOrphansWhenBackupDirMissingReturns200Zero() {
		// backup-dir 在 @BeforeEach 已清空，删除目录本身
		try {
			Files.deleteIfExists(BACKUP_DIR);
		} catch (Exception ignored) { }
		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "scannedFiles")).isZero();
		assertThat(JsonProbe.intVal(body, "orphanFiles")).isZero();
		assertThat(JsonProbe.intVal(body, "deletedFiles")).isZero();
	}

	@Test
	void cleanOrphansIsIdempotentWithSameKey() {
		CreatedBackup b = createBackup("test1234");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", b.id);
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = cleanOrphans(key, true);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(first.getBody(), "deletedFiles")).isEqualTo(1);
		assertThat(Files.exists(b.file)).isFalse();

		// 相同 Idempotency-Key 重复清理 → 幂等回放，返回首次缓存的相同摘要（deletedFiles=1），
		// 不重新执行清理、不产生额外副作用（文件已不存在）
		ResponseEntity<String> second = cleanOrphans(key, true);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(second.getBody(), "deletedFiles")).isEqualTo(1);
		assertThat(Files.exists(b.file)).isFalse();
	}

	@Test
	void cleanOrphansDoesNotModifyBackupRecordOrLastBackupId() {
		CreatedBackup b = createBackup("test1234");
		// 将 last_backup_id 指向合法备份，验证清理孤儿不联动该软引用
		jdbc.update("UPDATE backup_schedule SET last_backup_id = ? WHERE id = 'singleton'", b.id);
		// 制造一个孤儿（删另一份记录留文件）
		CreatedBackup orphan = createBackup("test1234");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan.id);

		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

		// last_backup_id 未被置空（本端点不联动 last_backup_id）
		String lastId = jdbc.queryForObject(
			"SELECT last_backup_id FROM backup_schedule WHERE id = 'singleton'", String.class);
		assertThat(lastId).isEqualTo(b.id);
		// 合法记录仍在
		Integer legitCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM backup_record WHERE id = ?", Integer.class, b.id);
		assertThat(legitCount).isEqualTo(1);
	}

	@SuppressWarnings("unused")
	private record CreatedBackup(String id, String fileName, Path file) { }
}
