package com.jobhub.common.audit.api;

import com.jobhub.common.audit.AuditLogEntry;
import com.jobhub.common.audit.infrastructure.AuditLogMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 全量审计日志只读查询 REST 接口：分页查询 {@code audit_log} 表，可按 action/resourceType/from/to 过滤。
 * 承接 {@code GET /api/backups/orphans/audit}（仅 BACKUP_ORPHAN_CLEANED 的备份域便捷入口，不废弃），
 * 本端点为跨域通用查询入口。只读：不写 audit_log、不需确认头/幂等键、不动任何业务表；响应不含 passphrase。
 */
@RestController
@RequestMapping("/api")
@Validated
public class AuditLogController {

	private final AuditLogMapper auditLogMapper;

	public AuditLogController(AuditLogMapper auditLogMapper) {
		this.auditLogMapper = auditLogMapper;
	}

	/**
	 * 分页查询全量审计日志（只读）。action、resourceType、from、to 均可选，空=不过滤返回全量，可单独或任意组合。
	 * action/resourceType 按字符串精确匹配；from（起始含，{@code occurred_at >= from}）与 to（结束含，
	 * {@code occurred_at <= to}）按 occurred_at 时间范围过滤，均为 ISO-8601 UTC 字符串，非法格式返回 400。
	 * 按 occurred_at DESC 排序；无二级索引走全表扫描（本地单用户量小可接受，不新增迁移/索引）。
	 */
	@GetMapping("/audit-logs")
	public PageAuditLogEntryResponse listAuditLogs(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		long total = auditLogMapper.count(action, resourceType, validatedFrom, validatedTo);
		int offset = (page - 1) * pageSize;
		return PageAuditLogEntryResponse.from(
				auditLogMapper.selectPage(action, resourceType, validatedFrom, validatedTo, pageSize, offset),
				total, page, pageSize);
	}

	/**
	 * 导出全量审计日志为 CSV 或 JSON 文件（只读，即时下载）。过滤参数与 {@link #listAuditLogs} 完全一致
	 * （action/resourceType/from/to，空=不过滤导出全量，可单独或任意组合，按 occurred_at DESC 排序）；
	 * {@code format} 选 {@code csv} 或 {@code json}（默认 {@code json}）。即时在内存生成直接返回响应，
	 * 不落盘、不写文件系统、不创建 data_export 记录、无 afterCommit 清理（与 {@code POST /data-exports}
	 * 的持久化导出不同——审计导出是只读即时下载）。本地单用户审计量小，不设行数上限。响应不含 passphrase，
	 * 省略恒 null 的快照字段。{@code from}/{@code to} 非法格式返回 400；{@code from > to} 返回空结果
	 * （CSV 仅表头、JSON 为 {@code []}，不报 400）；{@code format} 非 {@code csv}/{@code json} 返回 400。
	 */
	@GetMapping("/audit-logs/export")
	public ResponseEntity<byte[]> exportAuditLogs(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(defaultValue = "json") String format) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		List<AuditLogEntry> entries = auditLogMapper.selectAll(action, resourceType, validatedFrom, validatedTo);
		String stamp = Instant.now().toString().replace(":", "");
		if ("json".equalsIgnoreCase(format)) {
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs-" + stamp + ".json")
					.contentType(MediaType.APPLICATION_JSON)
					.body(toJson(entries));
		} else if ("csv".equalsIgnoreCase(format)) {
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs-" + stamp + ".csv")
					.contentType(MediaType.valueOf("text/csv"))
					.body(toCsv(entries));
		}
		throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
				"format 必须为 csv 或 json");
	}

	/**
	 * 校验 from/to 为合法 ISO-8601 UTC（用 {@link Instant#parse} 校验），空返回 null（不过滤）。
	 * <p>occurred_at 以 TEXT 存 UTC ISO（如 2026-09-07T13:00:00Z），Instant.parse 接受同一格式；
	 * 不接受仅日期（2026-09-07）或非 ISO 格式，故返回 400。校验后原样回传字符串供 SQL 字符串比较
	 * （ISO-8601 字典序与时间序一致）。
	 */
	private static String parseIsoUtc(String value, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			Instant.parse(value);
			return value;
		} catch (DateTimeParseException ex) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
					field + " 必须为合法 ISO-8601 UTC 字符串（如 2026-09-07T13:00:00Z）");
		}
	}

	private static final byte[] CRLF = {'\r', '\n'};
	private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
	private static final String[] CSV_COLUMNS = {"id", "resourceType", "resourceId", "action", "reason", "freedBytes", "occurredAt"};

	/** 把审计条目列表序列化为 JSON 数组（与查询端点 items 元素结构对齐，省略恒 null 的快照字段）。 */
	private static byte[] toJson(List<AuditLogEntry> entries) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			AuditLogEntry e = entries.get(i);
			sb.append("{\"id\":").append(jsonString(e.getId()))
					.append(",\"resourceType\":").append(jsonString(e.getResourceType()))
					.append(",\"resourceId\":").append(jsonString(e.getResourceId()))
					.append(",\"action\":").append(jsonString(e.getAction()))
					.append(",\"reason\":").append(jsonString(e.getReason()))
					.append(",\"freedBytes\":").append(e.getFreedBytes() == null ? "null" : e.getFreedBytes())
					.append(",\"occurredAt\":").append(jsonString(e.getOccurredAt()))
					.append('}');
		}
		sb.append(']');
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** 把审计条目列表序列化为 CSV（UTF-8 BOM + RFC 4180 转义 + CRLF，首行表头，无记录仅表头）。 */
	private static byte[] toCsv(List<AuditLogEntry> entries) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try {
			out.write(UTF8_BOM);
			out.write(csvRow(CSV_COLUMNS).getBytes(StandardCharsets.UTF_8));
			out.write(CRLF);
			for (AuditLogEntry e : entries) {
				String[] values = {e.getId(), e.getResourceType(), e.getResourceId(),
						e.getAction(), e.getReason(),
						e.getFreedBytes() == null ? "" : e.getFreedBytes().toString(),
						e.getOccurredAt()};
				out.write(csvRow(values).getBytes(StandardCharsets.UTF_8));
				out.write(CRLF);
			}
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
		return out.toByteArray();
	}

	private static String csvRow(String[] values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escapeCsvField(values[i]));
		}
		return sb.toString();
	}

	private static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
				|| value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
			return '"' + value.replace("\"", "\"\"") + '"';
		}
		return value;
	}

	/** JSON 字符串字面量（含双引号包裹与基本转义）。 */
	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> sb.append(c);
			}
		}
		return sb.append('"').toString();
	}
}
