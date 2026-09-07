package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-50 全量审计日志查询（GET /api/audit-logs 只读分页查询，可按 action/resourceType 过滤）。
 * 跨域通用查询入口，承接 GET /api/backups/orphans/audit（仅 BACKUP_ORPHAN_CLEANED 的备份域便捷入口）。
 * 只读：不写 audit_log、不需确认头/幂等键、不动任何业务表；响应不含 passphrase、省略恒 null 的快照字段。
 * 审计行用 jdbc 直接插入模拟既有写入值（查询端点行为与写入来源无关），聚焦过滤/分页/排序/字段语义。
 */
class AuditLogQueryIntegrationTest extends AbstractIntegrationTest {

	/** 直接向 audit_log 插入一条记录（模拟既有写入值），返回生成的 id。快照字段恒 null。 */
	private String insertAuditRow(String action, String resourceType, String resourceId,
			String reason, String occurredAt) {
		String id = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO audit_log (id, resource_type, resource_id, action, "
				+ "before_snapshot_json, after_snapshot_json, reason, occurred_at) "
				+ "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)",
				id, resourceType, resourceId, action, reason, occurredAt);
		return id;
	}

	/** GET /api/audit-logs 只读分页查询（query 形如 "?page=1&pageSize=20&action=X&resourceType=Y"）。 */
	private ResponseEntity<String> listAuditLogs(String query) {
		return restTemplate.getForEntity(url("/audit-logs" + query), String.class);
	}

	@Test
	void AT50_listAllReturnsEveryActionWithFieldsAndDescOrder() {
		// 造 4 种 action 的审计行，occurred_at 递增，验证 DESC 排序（最新优先）
		String idApp = insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION",
				UUID.randomUUID().toString(), "User confirmed a secondary active application via allowDuplicate=true.",
				"2026-09-07T10:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"Merged into requirement " + UUID.randomUUID() + ".", "2026-09-07T11:00:00Z");
		insertAuditRow("REQUIREMENT_UPDATED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"User edited or confirmed requirement.", "2026-09-07T12:00:00Z");
		String idOrphan = insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"Orphan .enc file removed (freedBytes=1024).", "2026-09-07T13:00:00Z");

		// 无过滤查询（只读，不携带确认头与幂等键）
		ResponseEntity<String> res = listAuditLogs("?page=1&pageSize=20");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		// 分页字段
		assertThat(JsonProbe.intVal(body, "page")).isEqualTo(1);
		assertThat(JsonProbe.intVal(body, "pageSize")).isEqualTo(20);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(4L);
		assertThat(JsonProbe.intVal(body, "totalPages")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(4);
		// 全部 action 类型都返回
		List<String> actions = JsonProbe.collectArrayField(body, "items", "action");
		assertThat(actions).containsExactlyInAnyOrder(
				"SECONDARY_APPLICATION_CONFIRMED", "REQUIREMENT_MERGED",
				"REQUIREMENT_UPDATED", "BACKUP_ORPHAN_CLEANED");
		// 每条字段：id 非空、resourceType 非空、resourceId 非空、action 已知之一、reason 非空、occurredAt 非空
		for (int i = 0; i < 4; i++) {
			assertThat(JsonProbe.arrStr(body, "items", i, "id")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "items", i, "resourceType")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "items", i, "resourceId")).isNotBlank();
			assertThat(actions).contains(JsonProbe.arrStr(body, "items", i, "action"));
			assertThat(JsonProbe.arrStr(body, "items", i, "reason")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "items", i, "occurredAt")).isNotBlank();
		}
		// 按 occurred_at DESC 排序（最新优先）：首条 occurredAt 为 13:00（最新）
		assertThat(JsonProbe.arrStr(body, "items", 0, "occurredAt")).isEqualTo("2026-09-07T13:00:00Z");
		assertThat(JsonProbe.arrStr(body, "items", 0, "id")).isEqualTo(idOrphan);
		// 末条 occurredAt 为 10:00（最旧）
		assertThat(JsonProbe.arrStr(body, "items", 3, "occurredAt")).isEqualTo("2026-09-07T10:00:00Z");
		assertThat(JsonProbe.arrStr(body, "items", 3, "id")).isEqualTo(idApp);
		// 不含 passphrase、不含快照字段（恒 null 省略）
		assertThat(body).doesNotContain("passphrase").doesNotContain("beforeSnapshotJson").doesNotContain("afterSnapshotJson");
	}

	@Test
	void AT50_filterByActionBackupOrphanCleanedMatchesOrphanAuditEndpoint() {
		// 造 1 条 BACKUP_ORPHAN_CLEANED + 1 条其他 action，验证 action 过滤只返回孤儿清理记录
		String orphanId = insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"Orphan .enc file removed (freedBytes=2048).", "2026-09-07T09:00:00Z");
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"User confirmed a secondary active application.", "2026-09-07T08:00:00Z");

		ResponseEntity<String> res = listAuditLogs("?action=BACKUP_ORPHAN_CLEANED");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceType")).isEqualTo("BACKUP_FILE");
		assertThat(JsonProbe.arrStr(body, "items", 0, "id")).isEqualTo(orphanId);

		// 与 GET /api/backups/orphans/audit 返回的记录 id 集合一致（字段含 resourceType，其余语义一致）
		ResponseEntity<String> orphanRes = restTemplate.getForEntity(url("/backups/orphans/audit?page=1&pageSize=20"), String.class);
		assertThat(orphanRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<String> auditLogIds = JsonProbe.collectArrayField(body, "items", "id");
		List<String> orphanEndpointIds = JsonProbe.collectArrayField(orphanRes.getBody(), "items", "id");
		assertThat(auditLogIds).containsExactlyInAnyOrderElementsOf(orphanEndpointIds);
	}

	@Test
	void AT50_filterByResourceTypeApplication() {
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"User confirmed a secondary active application.", "2026-09-07T09:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"Orphan .enc file removed.", "2026-09-07T08:00:00Z");

		ResponseEntity<String> res = listAuditLogs("?resourceType=APPLICATION");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("SECONDARY_APPLICATION_CONFIRMED");
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceType")).isEqualTo("APPLICATION");
	}

	@Test
	void AT50_filterByActionAndResourceTypeCombined() {
		// 造 REQUIREMENT_MERGED + REQUIREMENT_UPDATED（同为 JOB_REQUIREMENT），组合过滤只返回 MERGED
		String mergedId = insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"Merged into requirement " + UUID.randomUUID() + ".", "2026-09-07T09:00:00Z");
		insertAuditRow("REQUIREMENT_UPDATED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"User edited or confirmed requirement.", "2026-09-07T08:00:00Z");

		ResponseEntity<String> res = listAuditLogs("?action=REQUIREMENT_MERGED&resourceType=JOB_REQUIREMENT");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("REQUIREMENT_MERGED");
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceType")).isEqualTo("JOB_REQUIREMENT");
		assertThat(JsonProbe.arrStr(body, "items", 0, "id")).isEqualTo(mergedId);
	}

	@Test
	void AT50_filterNoMatchReturnsEmpty() {
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"Orphan .enc file removed.", "2026-09-07T08:00:00Z");

		// 过滤无匹配（不报错，返回空 items）
		ResponseEntity<String> res = listAuditLogs("?action=NONEXISTENT_ACTION");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isZero();
		assertThat(JsonProbe.lng(body, "total")).isZero();
		assertThat(JsonProbe.intVal(body, "totalPages")).isZero();
	}

	@Test
	void AT50_emptyTableReturnsEmpty() {
		// audit_log 表为空（@BeforeEach 已清表）
		ResponseEntity<String> res = listAuditLogs("?page=1");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isZero();
		assertThat(JsonProbe.lng(body, "total")).isZero();
		assertThat(JsonProbe.intVal(body, "totalPages")).isZero();
	}

	@Test
	void AT50_pageSizeOneSplitsPages() {
		// 造 3 条记录，pageSize=1 → totalPages=3，首页仅 1 条（最新）
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"first", "2026-09-07T10:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"second", "2026-09-07T11:00:00Z");
		String newestId = insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"third", "2026-09-07T12:00:00Z");

		ResponseEntity<String> res = listAuditLogs("?page=1&pageSize=1");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(1);
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(3L);
		assertThat(JsonProbe.intVal(body, "totalPages")).isEqualTo(3);
		// 首条为最新（occurred_at DESC）
		assertThat(JsonProbe.arrStr(body, "items", 0, "id")).isEqualTo(newestId);
	}

	@Test
	void AT50_rejectsInvalidPaging() {
		// page=0 非法（最小 1）
		assertThat(listAuditLogs("?page=0&pageSize=20").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// pageSize=0 非法（最小 1）
		assertThat(listAuditLogs("?page=1&pageSize=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		// pageSize=101 非法（最大 100）
		assertThat(listAuditLogs("?page=1&pageSize=101").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT50_filterByResourceTypeBackupRecordReturnsAllDeletionActions() {
		// 造 3 类 BACKUP_RECORD 审计行（单条删除/按龄清理/按数量保留清理），验证 resourceType 过滤
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record deleted by single delete.", "2026-09-07T10:00:00Z");
		insertAuditRow("BACKUP_PURGED_BY_AGE", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record purged by age olderThanDays=5.", "2026-09-07T11:00:00Z");
		insertAuditRow("BACKUP_PURGED_BY_COUNT", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record purged by count keepLast=2.", "2026-09-07T12:00:00Z");
		// 一条其他 resourceType 的记录不应被返回
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"Orphan .enc file removed (freedBytes=1024).", "2026-09-07T13:00:00Z");

		String body = listAuditLogs("?page=1&pageSize=20&resourceType=BACKUP_RECORD").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(3L);
		List<String> actions = JsonProbe.collectArrayField(body, "items", "action");
		assertThat(actions).containsExactlyInAnyOrder(
				"BACKUP_DELETED", "BACKUP_PURGED_BY_AGE", "BACKUP_PURGED_BY_COUNT");
		// 每条 resourceType=BACKUP_RECORD
		for (int i = 0; i < 3; i++) {
			assertThat(JsonProbe.arrStr(body, "items", i, "resourceType")).isEqualTo("BACKUP_RECORD");
		}
	}

	@Test
	void AT50_filterByActionBackupPurgedByAge() {
		insertAuditRow("BACKUP_PURGED_BY_AGE", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record purged by age olderThanDays=5.", "2026-09-07T10:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"single delete.", "2026-09-07T11:00:00Z");

		String body = listAuditLogs("?action=BACKUP_PURGED_BY_AGE").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("BACKUP_PURGED_BY_AGE");
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceType")).isEqualTo("BACKUP_RECORD");
	}
}
