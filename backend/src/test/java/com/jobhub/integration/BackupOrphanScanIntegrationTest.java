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
import java.util.Map;

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
		CreatedBackup legit1 = createBackup("TestPass1234!plus");
		CreatedBackup legit2 = createBackup("TestPass1234!plus");
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
		CreatedBackup b = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", b.id);
		ResponseEntity<String> bad = cleanOrphans(TestFixtures.newKey(), false);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// 文件仍在（未删除）
		assertThat(Files.exists(b.file)).isTrue();
	}

	@Test
	void cleanOrphansNoOrphansReturns200AllZero() {
		CreatedBackup b = createBackup("TestPass1234!plus");
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
		CreatedBackup b = createBackup("TestPass1234!plus");
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
		CreatedBackup b = createBackup("TestPass1234!plus");
		// 将 last_backup_id 指向合法备份，验证清理孤儿不联动该软引用
		jdbc.update("UPDATE backup_schedule SET last_backup_id = ? WHERE id = 'singleton'", b.id);
		// 制造一个孤儿（删另一份记录留文件）
		CreatedBackup orphan = createBackup("TestPass1234!plus");
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

	@Test
	void AT47_cleanOrphansWritesAuditLogPerDeletedOrphan() {
		// 造 2 份合法备份，删除两份的 backup_record 行但保留 .enc 文件 → 2 个孤儿
		CreatedBackup orphan1 = createBackup("TestPass1234!plus");
		CreatedBackup orphan2 = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan1.id);
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan2.id);

		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.intVal(body, "deletedFiles")).isEqualTo(2);
		assertThat(JsonProbe.lng(body, "freedBytes")).isGreaterThan(0L);

		// audit_log 为每个被删孤儿追加一条：resource_type=BACKUP_FILE、resource_id=被删文件 UUID、
		// action=BACKUP_ORPHAN_CLEANED、before/after 快照为 null、reason 不含 freedBytes 子串、freed_bytes 列有值、occurred_at 非空
		Map<String, Object> row1 = jdbc.queryForMap(
			"SELECT resource_type, resource_id, action, before_snapshot_json, after_snapshot_json, reason, freed_bytes, occurred_at "
				+ "FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED' AND resource_id = ?", orphan1.id);
		assertThat(row1.get("resource_type")).isEqualTo("BACKUP_FILE");
		assertThat(row1.get("action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(row1.get("before_snapshot_json")).isNull();
		assertThat(row1.get("after_snapshot_json")).isNull();
		assertThat(String.valueOf(row1.get("reason"))).doesNotContain("freedBytes=");
		assertThat(((Number) row1.get("freed_bytes")).longValue()).isGreaterThan(0L);
		assertThat(row1.get("occurred_at")).asString().isNotEmpty();
		assertThat(row1.get("resource_id")).isEqualTo(orphan1.id);

		Map<String, Object> row2 = jdbc.queryForMap(
			"SELECT resource_type, resource_id, action FROM audit_log "
				+ "WHERE action = 'BACKUP_ORPHAN_CLEANED' AND resource_id = ?", orphan2.id);
		assertThat(row2.get("resource_type")).isEqualTo("BACKUP_FILE");

		// 恰好 2 条审计记录（不多不少）
		Integer auditCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED'", Integer.class);
		assertThat(auditCount).isEqualTo(2);

		// 响应不回显审计信息（BackupOrphanCleanSummary 不含审计字段）
		assertThat(body).doesNotContain("auditLog").doesNotContain("BACKUP_ORPHAN_CLEANED");
		// passphrase 不落库
		Integer passCol = jdbc.queryForObject(
			"SELECT COUNT(*) FROM pragma_table_info('backup_record') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	@Test
	void AT47_noOrphansWritesNoAuditLog() {
		// 无孤儿：文件与记录都在
		createBackup("TestPass1234!plus");
		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(res.getBody(), "deletedFiles")).isZero();
		// 空操作无可追溯，audit_log 未新增
		Integer auditCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED'", Integer.class);
		assertThat(auditCount).isZero();
	}

	@Test
	void AT47_nonUuidSkippedWritesNoAuditLog() {
		// 只放一个非 UUID .enc 文件（被跳过不删），无孤儿删除
		placeNonUuidEncFile();
		ResponseEntity<String> res = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(res.getBody(), "skippedFiles")).isEqualTo(1);
		assertThat(JsonProbe.intVal(res.getBody(), "deletedFiles")).isZero();
		// 被跳过的文件不写审计（未删除）
		Integer auditCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED'", Integer.class);
		assertThat(auditCount).isZero();
	}

	@Test
	void AT47_idempotentReplayWritesNoDuplicateAuditLog() {
		CreatedBackup orphan = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan.id);
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = cleanOrphans(key, true);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(first.getBody(), "deletedFiles")).isEqualTo(1);
		Integer firstCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED'", Integer.class);
		assertThat(firstCount).isEqualTo(1);

		// 相同 Idempotency-Key 回放 → 幂等，返回首次缓存摘要，不重新执行清理 → 不重复写审计
		ResponseEntity<String> second = cleanOrphans(key, true);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(second.getBody(), "deletedFiles")).isEqualTo(1);
		Integer secondCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_ORPHAN_CLEANED'", Integer.class);
		assertThat(secondCount).isEqualTo(1);
	}

	@Test
	void AT49_listOrphanAuditReturnsPagedEntries() {
		// 造 2 个孤儿 → clean 产生 2 条 BACKUP_ORPHAN_CLEANED 审计
		CreatedBackup orphan1 = createBackup("TestPass1234!plus");
		CreatedBackup orphan2 = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan1.id);
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan2.id);
		ResponseEntity<String> clean = cleanOrphans(TestFixtures.newKey(), true);
		assertThat(clean.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(clean.getBody(), "deletedFiles")).isEqualTo(2);

		// GET /api/backups/orphans/audit 只读（不携带确认头与幂等键）
		ResponseEntity<String> res = listOrphanAudit(1, 20);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		// 分页字段 + 2 条记录
		assertThat(JsonProbe.intVal(body, "page")).isEqualTo(1);
		assertThat(JsonProbe.intVal(body, "pageSize")).isEqualTo(20);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(2L);
		assertThat(JsonProbe.intVal(body, "totalPages")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(2);
		// 每条字段：id 非空 UUID、resourceId 为被删文件 UUID、action 固定、reason 不含 freedBytes 子串、freedBytes 结构化字段>0、occurredAt 非空
		String a0 = JsonProbe.arrStr(body, "items", 0, "action");
		assertThat(a0).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(JsonProbe.arrStr(body, "items", 0, "id")).isNotBlank();
		assertThat(JsonProbe.arrStr(body, "items", 0, "reason")).doesNotContain("freedBytes=");
		assertThat(JsonProbe.arrLng(body, "items", 0, "freedBytes")).isGreaterThan(0L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "occurredAt")).isNotBlank();
		// resourceId 必为两个孤儿 id 之一（排序后首条是最新删除的那个）
		String r0 = JsonProbe.arrStr(body, "items", 0, "resourceId");
		assertThat(r0).isIn(orphan1.id, orphan2.id);

		// 不含 passphrase（审计记录从不存 passphrase）
		assertThat(body).doesNotContain("passphrase");
		// 省略固定 resourceType 与快照字段
		assertThat(body).doesNotContain("resourceType").doesNotContain("beforeSnapshotJson");
	}

	@Test
	void AT49_listOrphanAuditEmptyReturnsZeroItems() {
		// 无孤儿无审计记录 → 空表
		ResponseEntity<String> res = listOrphanAudit(1, 20);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isZero();
		assertThat(JsonProbe.lng(body, "total")).isZero();
		assertThat(JsonProbe.intVal(body, "totalPages")).isZero();
	}

	@Test
	void AT49_listOrphanAuditPageSizeOneSplitsPages() {
		// 造 2 个孤儿 → 2 条审计，pageSize=1 → totalPages=2，首页仅 1 条（最新）
		CreatedBackup orphan1 = createBackup("TestPass1234!plus");
		CreatedBackup orphan2 = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan1.id);
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan2.id);
		cleanOrphans(TestFixtures.newKey(), true);

		ResponseEntity<String> res = listOrphanAudit(1, 1);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(2L);
		assertThat(JsonProbe.intVal(body, "totalPages")).isEqualTo(2);
	}

	@Test
	void AT49_listOrphanAuditRejectsInvalidPaging() {
		// page=0 非法（最小 1）
		assertThat(listOrphanAudit(0, 20).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// pageSize=0 非法（最小 1）
		assertThat(listOrphanAudit(1, 0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// pageSize=101 非法（最大 100）
		assertThat(listOrphanAudit(1, 101).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT49_listOrphanAuditCoversRestoreLinkedSource() {
		// 恢复联动的 cleanOrphans 同样写 BACKUP_ORPHAN_CLEANED，查询端点不区分来源
		// 这里验证造孤儿后 clean（无论独立还是恢复联动来源），查询端点都能返回该 action
		CreatedBackup orphan = createBackup("TestPass1234!plus");
		jdbc.update("DELETE FROM backup_record WHERE id = ?", orphan.id);
		cleanOrphans(TestFixtures.newKey(), true);

		ResponseEntity<String> res = listOrphanAudit(1, 20);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceId")).isEqualTo(orphan.id);
	}


	/** GET /api/backups/orphans/audit 只读分页查询（不带确认头/幂等键）。 */
	private ResponseEntity<String> listOrphanAudit(Integer page, Integer pageSize) {
		StringBuilder qs = new StringBuilder();
		if (page != null) qs.append(qs.isEmpty() ? "?page=" : "&page=").append(page);
		if (pageSize != null) qs.append(qs.isEmpty() ? "?pageSize=" : "&pageSize=").append(pageSize);
		return restTemplate.getForEntity(url("/backups/orphans/audit" + qs), String.class);
	}

	@SuppressWarnings("unused")
	private record CreatedBackup(String id, String fileName, Path file) { }
}
