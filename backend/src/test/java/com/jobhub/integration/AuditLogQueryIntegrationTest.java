package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-50 全量审计日志查询（GET /api/audit-logs 只读分页查询，可按 action/resourceType 过滤）。
 * 跨域通用查询入口，承接 GET /api/backups/orphans/audit（仅 BACKUP_ORPHAN_CLEANED 的备份域便捷入口）。
 * 只读：不写 audit_log、不需确认头/幂等键、不动任何业务表；响应不含 passphrase、省略恒 null 的快照字段。
 * 审计行用 jdbc 直接插入模拟既有写入值（查询端点行为与写入来源无关），聚焦过滤/分页/排序/字段语义。
 */
class AuditLogQueryIntegrationTest extends AbstractIntegrationTest {

	/** 直接向 audit_log 插入一条记录（模拟既有写入值），返回生成的 id。快照字段恒 null，freed_bytes 为 null（非孤儿清理行）。 */
	private String insertAuditRow(String action, String resourceType, String resourceId,
			String reason, String occurredAt) {
		String id = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO audit_log (id, resource_type, resource_id, action, "
				+ "before_snapshot_json, after_snapshot_json, reason, freed_bytes, occurred_at) "
				+ "VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, ?)",
				id, resourceType, resourceId, action, reason, occurredAt);
		return id;
	}

	/** 向 audit_log 插入一条 BACKUP_ORPHAN_CLEANED 记录（模拟孤儿清理写入值），返回生成的 id。
	 *  写入 freed_bytes 结构化列（V27），reason 不含 freedBytes= 子串（与真实 backupOrphanCleaned 工厂一致）。 */
	private String insertOrphanAuditRow(String resourceId, String reason, long freedBytes, String occurredAt) {
		String id = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO audit_log (id, resource_type, resource_id, action, "
				+ "before_snapshot_json, after_snapshot_json, reason, freed_bytes, occurred_at) "
				+ "VALUES (?, 'BACKUP_FILE', ?, 'BACKUP_ORPHAN_CLEANED', NULL, NULL, ?, ?, ?)",
				id, resourceId, reason, freedBytes, occurredAt);
		return id;
	}

	/** GET /api/audit-logs 只读分页查询（query 形如 "?page=1&pageSize=20&action=X&resourceType=Y"）。 */
	private ResponseEntity<String> listAuditLogs(String query) {
		return restTemplate.getForEntity(url("/audit-logs" + query), String.class);
	}

	/** GET /api/audit-logs/export 只读即时下载（query 形如 "?format=json&action=X"）。 */
	private ResponseEntity<byte[]> exportAuditLogs(String query) {
		return restTemplate.getForEntity(url("/audit-logs/export" + query), byte[].class);
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
		String idOrphan = insertOrphanAuditRow(UUID.randomUUID().toString(),
				"Orphan .enc file removed.", 1024L, "2026-09-07T13:00:00Z");

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
		String orphanId = insertOrphanAuditRow(UUID.randomUUID().toString(),
				"Orphan .enc file removed.", 2048L, "2026-09-07T09:00:00Z");
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
		insertOrphanAuditRow(UUID.randomUUID().toString(),
				"Orphan .enc file removed.", 1024L, "2026-09-07T13:00:00Z");

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

	@Test
	void AT52_filterByFromReturnsOnlyOnOrAfter() {
		// 造 4 条 occurred_at 各异的记录
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"first", "2026-09-01T08:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"second", "2026-09-03T10:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"third", "2026-09-05T12:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"fourth", "2026-09-07T14:00:00Z");

		// from=2026-09-03T00:00:00Z → 仅 09-03/05/07 三条（>= from）
		String body = listAuditLogs("?from=2026-09-03T00:00:00Z").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(3L);
		List<String> occurredAts = JsonProbe.collectArrayField(body, "items", "occurredAt");
		assertThat(occurredAts).doesNotContain("2026-09-01T08:00:00Z");
		assertThat(occurredAts).contains("2026-09-03T10:00:00Z", "2026-09-05T12:00:00Z", "2026-09-07T14:00:00Z");
		// DESC 排序：首条最新
		assertThat(JsonProbe.arrStr(body, "items", 0, "occurredAt")).isEqualTo("2026-09-07T14:00:00Z");
	}

	@Test
	void AT52_filterByToReturnsOnlyOnOrBefore() {
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"first", "2026-09-01T08:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"second", "2026-09-03T10:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"third", "2026-09-05T12:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"fourth", "2026-09-07T14:00:00Z");

		// to=2026-09-05T23:59:59Z → 仅 09-01/03/05 三条（<= to）
		String body = listAuditLogs("?to=2026-09-05T23:59:59Z").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(3L);
		List<String> occurredAts = JsonProbe.collectArrayField(body, "items", "occurredAt");
		assertThat(occurredAts).doesNotContain("2026-09-07T14:00:00Z");
	}

	@Test
	void AT52_filterByFromAndToRange() {
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"first", "2026-09-01T08:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"second", "2026-09-03T10:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"third", "2026-09-05T12:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"fourth", "2026-09-07T14:00:00Z");

		// from=09-03 ~ to=09-05 → 仅 09-03/05 两条
		String body = listAuditLogs("?from=2026-09-03T00:00:00Z&to=2026-09-05T23:59:59Z").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(2L);
		List<String> occurredAts = JsonProbe.collectArrayField(body, "items", "occurredAt");
		assertThat(occurredAts).containsExactlyInAnyOrder("2026-09-03T10:00:00Z", "2026-09-05T12:00:00Z");
	}

	@Test
	void AT52_fromBoundaryInclusive() {
		// from 恰等于某记录的 occurred_at → 该记录被包含（>=）
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"exact", "2026-09-03T10:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"later", "2026-09-05T12:00:00Z");

		String body = listAuditLogs("?from=2026-09-03T10:00:00Z").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(2L);
		List<String> occurredAts = JsonProbe.collectArrayField(body, "items", "occurredAt");
		assertThat(occurredAts).contains("2026-09-03T10:00:00Z");
	}

	@Test
	void AT52_fromAndToCombinedWithAction() {
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"earlier delete", "2026-09-01T08:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"in-range delete", "2026-09-05T12:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"in-range but other action", "2026-09-05T13:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"later delete", "2026-09-07T14:00:00Z");

		// from=09-03 & action=BACKUP_DELETED → 仅 09-05 与 09-07 两条 BACKUP_DELETED（09-01 < from 排除）
		String body = listAuditLogs("?from=2026-09-03T00:00:00Z&action=BACKUP_DELETED").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(2L);
		List<String> actions = JsonProbe.collectArrayField(body, "items", "action");
		assertThat(actions).containsOnly("BACKUP_DELETED");
		List<String> occurredAts = JsonProbe.collectArrayField(body, "items", "occurredAt");
		assertThat(occurredAts).containsExactlyInAnyOrder("2026-09-05T12:00:00Z", "2026-09-07T14:00:00Z");
	}

	@Test
	void AT52_fromGreaterThanToReturnsEmpty() {
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"in range", "2026-09-05T12:00:00Z");
		// from > to → 空结果（合法但无匹配，不报 400）
		String body = listAuditLogs("?from=2026-09-07T00:00:00Z&to=2026-09-01T00:00:00Z").getBody();
		assertThat(JsonProbe.lng(body, "total")).isZero();
		assertThat(JsonProbe.arraySize(body, "items")).isZero();
	}

	@Test
	void AT52_invalidFromFormatReturns400() {
		// 缺时间部分，Instant.parse 拒绝
		assertThat(listAuditLogs("?from=2026-09-03").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(listAuditLogs("?from=not-a-date").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(listAuditLogs("?to=2026/09/05").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT52_fromAndToCombinedWithResourceType() {
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"in range record", "2026-09-05T12:00:00Z");
		insertAuditRow("BACKUP_ORPHAN_CLEANED", "BACKUP_FILE", UUID.randomUUID().toString(),
				"in range file", "2026-09-05T13:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"out of range record", "2026-09-07T14:00:00Z");

		// from=09-04 & to=09-06 & resourceType=BACKUP_RECORD → 仅 09-05 一条
		String body = listAuditLogs("?from=2026-09-04T00:00:00Z&to=2026-09-06T23:59:59Z&resourceType=BACKUP_RECORD").getBody();
		assertThat(JsonProbe.lng(body, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "resourceType")).isEqualTo("BACKUP_RECORD");
		assertThat(JsonProbe.arrStr(body, "items", 0, "occurredAt")).isEqualTo("2026-09-05T12:00:00Z");
	}

	// ==================== AT-54 审计日志导出（GET /audit-logs/export 即时下载 CSV/JSON） ====================

	/** 造覆盖多 action/resourceType/occurred_at 的审计行，供导出用例复用。 */
	private void seedExportRows() {
		insertAuditRow("SECONDARY_APPLICATION_CONFIRMED", "APPLICATION", UUID.randomUUID().toString(),
				"User confirmed a secondary active application.", "2026-09-07T10:00:00Z");
		insertAuditRow("REQUIREMENT_MERGED", "JOB_REQUIREMENT", UUID.randomUUID().toString(),
				"Merged into requirement " + UUID.randomUUID() + ".", "2026-09-07T11:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record deleted by single delete.", "2026-09-07T12:00:00Z");
		insertOrphanAuditRow(UUID.randomUUID().toString(),
				"Orphan .enc file removed.", 1024L, "2026-09-07T13:00:00Z");
	}

	@Test
	void AT54_exportJsonReturnsArrayWithFieldsAndDescOrder() {
		seedExportRows();
		ResponseEntity<byte[]> res = exportAuditLogs("?format=json");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).contains("attachment").contains("audit-logs-").contains(".json");
		String body = new String(res.getBody(), StandardCharsets.UTF_8);
		// JSON 数组根
		assertThat(body).startsWith("[").endsWith("]");
		int size = JsonProbe.arraySize(body, "");
		assertThat(size).isEqualTo(4);
		// 每元素含 6 字段，省略快照，不含 passphrase
		for (int i = 0; i < size; i++) {
			assertThat(JsonProbe.arrStr(body, "", i, "id")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "", i, "resourceType")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "", i, "resourceId")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "", i, "action")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "", i, "reason")).isNotBlank();
			assertThat(JsonProbe.arrStr(body, "", i, "occurredAt")).isNotBlank();
		}
		assertThat(body).doesNotContain("passphrase")
				.doesNotContain("beforeSnapshotJson").doesNotContain("afterSnapshotJson");
		// DESC 排序：首条最新（13:00）
		assertThat(JsonProbe.arrStr(body, "", 0, "occurredAt")).isEqualTo("2026-09-07T13:00:00Z");
		assertThat(JsonProbe.arrStr(body, "", size - 1, "occurredAt")).isEqualTo("2026-09-07T10:00:00Z");
	}

	/** 把 CSV 响应体解码为字符串并剥掉开头的 UTF-8 BOM（U+FEFF），便于断言正文。 */
	private static String csvText(byte[] body) {
		String text = new String(body, StandardCharsets.UTF_8);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		return text;
	}

	@Test
	void AT54_exportCsvReturnsBomHeaderAndRows() {
		seedExportRows();
		ResponseEntity<byte[]> res = exportAuditLogs("?format=csv");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(res.getHeaders().getContentType()).isNotNull();
		assertThat(res.getHeaders().getContentType().toString()).contains("text/csv");
		String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).contains("attachment").contains("audit-logs-").contains(".csv");
		byte[] body = res.getBody();
		// UTF-8 BOM（原始字节）
		assertThat(body[0]).isEqualTo((byte) 0xEF);
		assertThat(body[1]).isEqualTo((byte) 0xBB);
		assertThat(body[2]).isEqualTo((byte) 0xBF);
		String text = csvText(body);
		// 首行为表头
		assertThat(text).startsWith("id,resourceType,resourceId,action,reason,freedBytes,occurredAt");
		// CRLF 行尾
		assertThat(text).contains("\r\n");
		// 数据行（除表头外非空行）= 4
		String[] lines = text.replace("\r\n", "\n").split("\n", -1);
		long dataRows = java.util.Arrays.stream(lines)
				.filter(l -> !l.isEmpty() && !l.startsWith("id,")).count();
		assertThat(dataRows).isEqualTo(4);
		// 不含 passphrase
		assertThat(text).doesNotContain("passphrase");
	}

	@Test
	void AT54_exportJsonFiltersByAction() {
		seedExportRows();
		String body = new String(exportAuditLogs("?format=json&action=BACKUP_ORPHAN_CLEANED").getBody(), StandardCharsets.UTF_8);
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(body, "", 0, "action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
	}

	@Test
	void AT54_exportCsvFiltersByResourceType() {
		seedExportRows();
		byte[] body = exportAuditLogs("?format=csv&resourceType=BACKUP_RECORD").getBody();
		String text = new String(body, StandardCharsets.UTF_8);
		// 仅 BACKUP_DELETED 一条是 BACKUP_RECORD
		assertThat(text).contains("BACKUP_RECORD").contains("BACKUP_DELETED");
		assertThat(text).doesNotContain("APPLICATION").doesNotContain("JOB_REQUIREMENT").doesNotContain("BACKUP_FILE");
	}

	@Test
	void AT54_exportJsonFiltersByFromToRange() {
		seedExportRows();
		String body = new String(exportAuditLogs(
				"?format=json&from=2026-09-07T11:00:00Z&to=2026-09-07T12:00:00Z").getBody(), StandardCharsets.UTF_8);
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(2);
		List<String> occurredAts = JsonProbe.collectArrayField(body, "", "occurredAt");
		assertThat(occurredAts).containsExactlyInAnyOrder("2026-09-07T11:00:00Z", "2026-09-07T12:00:00Z");
	}

	@Test
	void AT54_exportJsonEmptyMatchReturnsEmptyArray() {
		seedExportRows();
		String body = new String(exportAuditLogs("?format=json&action=NONEXISTENT").getBody(), StandardCharsets.UTF_8);
		assertThat(body).isEqualTo("[]");
	}

	@Test
	void AT54_exportCsvEmptyMatchReturnsHeaderOnly() {
		seedExportRows();
		byte[] body = exportAuditLogs("?format=csv&action=NONEXISTENT").getBody();
		assertThat(body[0]).isEqualTo((byte) 0xEF);
		String text = csvText(body);
		assertThat(text).startsWith("id,resourceType,resourceId,action,reason,freedBytes,occurredAt");
		// 仅表头 + CRLF，无数据行
		assertThat(text.trim()).isEqualTo("id,resourceType,resourceId,action,reason,freedBytes,occurredAt");
	}

	@Test
	void AT54_exportEmptyTableJsonReturnsEmptyArray() {
		// audit_log 空表（@BeforeEach 已清表）
		String body = new String(exportAuditLogs("?format=json").getBody(), StandardCharsets.UTF_8);
		assertThat(body).isEqualTo("[]");
	}

	@Test
	void AT54_exportEmptyTableCsvReturnsHeaderOnly() {
		byte[] body = exportAuditLogs("?format=csv").getBody();
		assertThat(body[0]).isEqualTo((byte) 0xEF);
		String text = csvText(body);
		assertThat(text.trim()).isEqualTo("id,resourceType,resourceId,action,reason,freedBytes,occurredAt");
	}

	@Test
	void AT54_exportRejectsInvalidFormat() {
		assertThat(exportAuditLogs("?format=xml").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT54_exportRejectsInvalidFromTo() {
		assertThat(exportAuditLogs("?from=not-a-date").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exportAuditLogs("?to=2026/09/05").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT54_exportFromGreaterThanToReturnsEmptyNot400() {
		seedExportRows();
		ResponseEntity<byte[]> res = exportAuditLogs("?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = new String(res.getBody(), StandardCharsets.UTF_8);
		assertThat(body).isEqualTo("[]");
	}

	@Test
	void AT54_exportDefaultsToJsonWhenFormatOmitted() {
		seedExportRows();
		ResponseEntity<byte[]> res = exportAuditLogs("");
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		String body = new String(res.getBody(), StandardCharsets.UTF_8);
		assertThat(body).startsWith("[").endsWith("]");
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(4);
	}

	// ==================== AT-55 audit_log occurred_at 二级索引（V26 迁移补索引，行为不变） ====================

	/** PRAGMA index_list('audit_log') 返回的索引信息行（name 在第 2 列，origin 在第 4 列：c=create index）。 */
	private List<Map<String, Object>> auditLogIndexes() {
		return jdbc.queryForList("PRAGMA index_list('audit_log')");
	}

	/** PRAGMA index_info(<indexName>) 返回建索引的列（name 在第 3 列）。 */
	private List<String> indexColumns(String indexName) {
		return jdbc.queryForList("PRAGMA index_info('" + indexName + "')").stream()
				.map(m -> String.valueOf(m.get("name")))
				.toList();
	}

	@Test
	void AT55_occurred_at_indexExistsAfterV26Migration() {
		// V26 迁移后 audit_log 表应有 idx_audit_log_occurred_at 索引，建索引列为 occurred_at
		List<String> indexNames = auditLogIndexes().stream()
				.map(m -> String.valueOf(m.get("name")))
				.toList();
		assertThat(indexNames).contains("idx_audit_log_occurred_at");
		assertThat(indexColumns("idx_audit_log_occurred_at")).containsExactly("occurred_at");
		// 非唯一索引（audit_log 允许多条相同 occurred_at；PRAGMA index_list 第 3 列 unique 为 0）
		Map<String, Object> idx = auditLogIndexes().stream()
				.filter(m -> "idx_audit_log_occurred_at".equals(String.valueOf(m.get("name"))))
				.findFirst().orElseThrow();
		assertThat(String.valueOf(idx.get("unique"))).isEqualTo("0");
	}

	@Test
	void AT55_queryAndExportBehaviorUnchangedAfterIndex() {
		// 索引为纯性能优化，不改变语义结果：造多条 occurred_at 递增的记录，验证查询/导出排序与范围过滤不变
		seedExportRows();  // 4 条 occurred_at 10/11/12/13:00
		// 查询 DESC 排序不变（首条最新 13:00）
		String queryBody = listAuditLogs("?page=1&pageSize=20").getBody();
		assertThat(JsonProbe.arraySize(queryBody, "items")).isEqualTo(4);
		assertThat(JsonProbe.arrStr(queryBody, "items", 0, "occurredAt")).isEqualTo("2026-09-07T13:00:00Z");
		// 范围过滤不变（from=11:00 → 3 条）
		String rangeBody = listAuditLogs("?from=2026-09-07T11:00:00Z").getBody();
		assertThat(JsonProbe.lng(rangeBody, "total")).isEqualTo(3L);
		// 导出 DESC 排序不变
		String exportBody = new String(exportAuditLogs("?format=json").getBody(), StandardCharsets.UTF_8);
		assertThat(JsonProbe.arraySize(exportBody, "")).isEqualTo(4);
		assertThat(JsonProbe.arrStr(exportBody, "", 0, "occurredAt")).isEqualTo("2026-09-07T13:00:00Z");
	}

	// ==================== AT-57 freedBytes 结构化字段（V27 新增 freed_bytes 列） ====================

	/** PRAGMA table_info('audit_log') 返回的列信息（name 在第 2 列，type 在第 3 列，notnull 在第 4 列）。 */
	private List<Map<String, Object>> auditLogColumns() {
		return jdbc.queryForList("PRAGMA table_info('audit_log')");
	}

	@Test
	void AT57_freedBytesColumnExistsAfterV27Migration() {
		// V27 迁移后 audit_log 表应有 freed_bytes 列，类型 INTEGER，可空（notnull=0）
		List<String> names = auditLogColumns().stream()
				.map(m -> String.valueOf(m.get("name")))
				.toList();
		assertThat(names).contains("freed_bytes");
		Map<String, Object> col = auditLogColumns().stream()
				.filter(m -> "freed_bytes".equals(String.valueOf(m.get("name"))))
				.findFirst().orElseThrow();
		assertThat(String.valueOf(col.get("type"))).isEqualToIgnoringCase("INTEGER");
		assertThat(String.valueOf(col.get("notnull"))).isEqualTo("0");
	}

	@Test
	void AT57_queryReturnsFreedBytesFieldOrNullByAction() {
		// 1 条 BACKUP_ORPHAN_CLEANED（freed_bytes=2048）+ 1 条 BACKUP_DELETED（freed_bytes=null）
		insertOrphanAuditRow(UUID.randomUUID().toString(), "Orphan .enc file removed.", 2048L,
				"2026-09-07T13:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record deleted by single delete.", "2026-09-07T12:00:00Z");

		String body = listAuditLogs("?page=1&pageSize=20").getBody();
		assertThat(JsonProbe.arraySize(body, "items")).isEqualTo(2);
		// DESC 排序：首条最新（13:00，孤儿清理），freedBytes=2048
		assertThat(JsonProbe.arrStr(body, "items", 0, "action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(JsonProbe.arrLng(body, "items", 0, "freedBytes")).isEqualTo(2048L);
		assertThat(JsonProbe.arrStr(body, "items", 0, "reason")).doesNotContain("freedBytes=");
		// 次条 BACKUP_DELETED，freedBytes=null（JsonProbe.arrLng 对 JSON null 返回 0L，用文本断言）
		assertThat(JsonProbe.arrStr(body, "items", 1, "action")).isEqualTo("BACKUP_DELETED");
		assertThat(body).contains("\"freedBytes\":null");
	}

	@Test
	void AT57_exportReturnsFreedBytesFieldByFormat() {
		insertOrphanAuditRow(UUID.randomUUID().toString(), "Orphan .enc file removed.", 1024L,
				"2026-09-07T13:00:00Z");
		insertAuditRow("BACKUP_DELETED", "BACKUP_RECORD", UUID.randomUUID().toString(),
				"Backup record deleted by single delete.", "2026-09-07T12:00:00Z");

		// JSON：孤儿清理元素 freedBytes=1024，BACKUP_DELETED 元素 freedBytes=null
		String json = new String(exportAuditLogs("?format=json").getBody(), StandardCharsets.UTF_8);
		assertThat(JsonProbe.arraySize(json, "")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(json, "", 0, "action")).isEqualTo("BACKUP_ORPHAN_CLEANED");
		assertThat(JsonProbe.arrLng(json, "", 0, "freedBytes")).isEqualTo(1024L);
		assertThat(json).contains("\"freedBytes\":null");

		// CSV：表头含 freedBytes 列；孤儿清理行该列=1024，BACKUP_DELETED 行该列为空
		String csv = csvText(exportAuditLogs("?format=csv").getBody());
		assertThat(csv).startsWith("id,resourceType,resourceId,action,reason,freedBytes,occurredAt");
		long orphanRowsWithFreed = java.util.Arrays.stream(csv.replace("\r\n", "\n").split("\n", -1))
				.filter(l -> l.contains("BACKUP_ORPHAN_CLEANED"))
				.filter(l -> l.contains(",1024,"))
				.count();
		assertThat(orphanRowsWithFreed).isEqualTo(1);
		long deletedRowsWithEmptyFreed = java.util.Arrays.stream(csv.replace("\r\n", "\n").split("\n", -1))
				.filter(l -> l.contains("BACKUP_DELETED"))
				.count();
		assertThat(deletedRowsWithEmptyFreed).isEqualTo(1);
	}
}
