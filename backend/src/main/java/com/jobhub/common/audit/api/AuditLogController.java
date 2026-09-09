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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

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
	 * 分页查询全量审计日志（只读）。action、resourceType、from、to、hasFreedBytes、freedBytesMin、freedBytesMax 均可选，
	 * 空/hasFreedBytes=false/缺省时不过滤返回全量，可单独或任意组合。action/resourceType 按字符串精确匹配；from（起始含，
	 * {@code occurred_at >= from}）与 to（结束含，{@code occurred_at <= to}）按 occurred_at 时间范围过滤，
	 * 均为 ISO-8601 UTC 字符串，非法格式返回 400；hasFreedBytes=true 只返回 freed_bytes IS NOT NULL 的记录
	 * （孤儿清理行释放字节数有值的，排除 V27 前历史 NULL 行）；freedBytesMin（含边界 {@code freed_bytes >= freedBytesMin}）
	 * 与 freedBytesMax（含边界 {@code freed_bytes <= freedBytesMax}）按释放字节数范围过滤（非负整数闭区间，SQL 中
	 * freed_bytes 与数值比较对 NULL 行求值为 NULL→false 自动排除非孤儿清理行，隐式含 NOT NULL 语义，无需另加
	 * hasFreedBytes；hasFreedBytes 保持独立不与范围参数耦合）；freedBytesMin>freedBytesMax 返回空结果不报 400（与
	 * from>to 先例一致）；freedBytesMin/freedBytesMax 为负数或非整数返回 400（{@code @Min(0)} + Long 类型绑定校验）。
	 * 按 occurred_at DESC 排序；V26 补 occurred_at 单列索引、V28 补 action+occurred_at 复合索引（action 非空走复合索引、
	 * action 为空退回单列索引）；freed_bytes 非索引列，带范围过滤时走索引定位后回表过滤或全表扫描（本地量小可接受）。
	 */
	@GetMapping("/audit-logs")
	public PageAuditLogEntryResponse listAuditLogs(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(defaultValue = "false") boolean hasFreedBytes,
			@RequestParam(required = false) @Min(0) Long freedBytesMin,
			@RequestParam(required = false) @Min(0) Long freedBytesMax) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		long total = auditLogMapper.count(action, resourceType, validatedFrom, validatedTo, hasFreedBytes,
				freedBytesMin, freedBytesMax);
		int offset = (page - 1) * pageSize;
		return PageAuditLogEntryResponse.from(
				auditLogMapper.selectPage(action, resourceType, validatedFrom, validatedTo, hasFreedBytes,
						freedBytesMin, freedBytesMax, pageSize, offset),
				total, page, pageSize);
	}

	/**
	 * 导出全量审计日志为 CSV 或 JSON 文件（只读，流式下载）。过滤参数与 {@link #listAuditLogs} 完全一致
	 * （action/resourceType/from/to/hasFreedBytes/freedBytesMin/freedBytesMax，空/hasFreedBytes=false/缺省=不过滤导出
	 * 全量，可单独或任意组合，按 occurred_at DESC 排序）；{@code format} 选 {@code csv} 或 {@code json}（默认 {@code json}）。
	 *
	 * <p>流式分批生成、内存有界：用 {@link StreamingResponseBody} 按 {@link #EXPORT_BATCH_SIZE}（500）分批调
	 * {@link AuditLogMapper#selectPage selectPage}（offset 递增）逐批写入响应输出流，用户仍一次性下载完整 CSV/JSON
	 * 文件（语义不变），后端内存只持有一批记录而非全量；不预设 {@code Content-Length}、以分块传输编码逐批发送。
	 * 不落盘、不写文件系统、不创建 data_export 记录、无 afterCommit 清理（与 {@code POST /data-exports}
	 * 的持久化导出不同——审计导出是只读即时下载）。本地单用户审计量小，不设行数上限。响应不含 passphrase，
	 * 省略恒 null 的快照字段。
	 *
	 * <p>{@code from}/{@code to}/{@code format}/{@code freedBytesMin}/{@code freedBytesMax} 校验在写流前 fail fast
	 * （返回 400 前不开始写响应体）；{@code from}/{@code to} 非法格式返回 400；{@code from > to} 返回空结果
	 * （CSV 仅表头、JSON 为 {@code []}，不报 400）；{@code freedBytesMin > freedBytesMax} 返回空结果（不报 400，
	 * 与 {@code from > to} 一致）；{@code freedBytesMin}/{@code freedBytesMax} 为负数或非整数返回 400；
	 * {@code format} 非 {@code csv}/{@code json} 返回 400。
	 */
	@GetMapping("/audit-logs/export")
	public ResponseEntity<StreamingResponseBody> exportAuditLogs(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(defaultValue = "false") boolean hasFreedBytes,
			@RequestParam(required = false) @Min(0) Long freedBytesMin,
			@RequestParam(required = false) @Min(0) Long freedBytesMax,
			@RequestParam(defaultValue = "json") String format) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		boolean csv;
		if ("json".equalsIgnoreCase(format)) {
			csv = false;
		} else if ("csv".equalsIgnoreCase(format)) {
			csv = true;
		} else {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "format 必须为 csv 或 json");
		}
		String stamp = Instant.now().toString().replace(":", "");
		String filename = "audit-logs-" + stamp + (csv ? ".csv" : ".json");
		StreamingResponseBody body = csv
				? out -> streamCsv(out, action, resourceType, validatedFrom, validatedTo, hasFreedBytes, freedBytesMin, freedBytesMax)
				: out -> streamJson(out, action, resourceType, validatedFrom, validatedTo, hasFreedBytes, freedBytesMin, freedBytesMax);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
				.contentType(csv ? MediaType.valueOf("text/csv") : MediaType.APPLICATION_JSON)
				.body(body);
	}

	/** 流式导出每批最多 fetch 500 条，offset 递增直到某批不足 500 条停止（内存只持有一批）。 */
	static final int EXPORT_BATCH_SIZE = 500;

	/**
	 * 释放字节数聚合统计（只读）：对 {@code GET /audit-logs} 的全部匹配记录做 {@code COUNT(*)}/
	 * {@code SUM(freed_bytes)}/{@code COUNT(freed_bytes)} 一条聚合 SQL，返回 {@link FreedBytesSummary}。
	 * 过滤参数与 {@link #listAuditLogs} 完全一致（action/resourceType/from/to/hasFreedBytes/freedBytesMin/
	 * freedBytesMax，语义/校验全同）；只读不写、不需确认头/幂等键、不动任何业务表；响应不含 passphrase。
	 * <p>{@code totalCount}={@code COUNT(*)}（含 NULL 行）；{@code totalFreedBytes}={@code SUM(freed_bytes)}
	 * （NULL 不计入，COALESCE 兜底 0）；{@code avgFreedBytes}={@code totalFreedBytes/COUNT(freed_bytes)}
	 * （分母为非空行数，避免 NULL 行稀释均值；分母 0 兜底 0 不抛除零异常）。空匹配/全 NULL/空表均 0。
	 */
	@GetMapping("/audit-logs/freed-bytes-summary")
	public FreedBytesSummary freedBytesSummary(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(defaultValue = "false") boolean hasFreedBytes,
			@RequestParam(required = false) @Min(0) Long freedBytesMin,
			@RequestParam(required = false) @Min(0) Long freedBytesMax) {
		String validatedFrom = parseIsoUtc(from, "from");
		String validatedTo = parseIsoUtc(to, "to");
		Map<String, Object> row = auditLogMapper.selectFreedBytesSummary(action, resourceType,
				validatedFrom, validatedTo, hasFreedBytes, freedBytesMin, freedBytesMax);
		long totalCount = ((Number) row.get("totalCount")).longValue();
		long totalFreedBytes = ((Number) row.get("totalFreedBytes")).longValue();
		long nonNullCount = ((Number) row.get("nonNullCount")).longValue();
		double avgFreedBytes = nonNullCount == 0 ? 0.0 : (double) totalFreedBytes / nonNullCount;
		return new FreedBytesSummary(totalCount, totalFreedBytes, avgFreedBytes);
	}

	/** 释放字节数聚合统计响应（只读）。 */
	public record FreedBytesSummary(long totalCount, long totalFreedBytes, double avgFreedBytes) {
	}

	private void streamJson(OutputStream out, String action, String resourceType, String from, String to,
			boolean hasFreedBytes, Long freedBytesMin, Long freedBytesMax) throws IOException {
		out.write('[');
		boolean first = true;
		int offset = 0;
		while (true) {
			List<AuditLogEntry> batch = auditLogMapper.selectPage(action, resourceType, from, to, hasFreedBytes,
					freedBytesMin, freedBytesMax, EXPORT_BATCH_SIZE, offset);
			if (batch.isEmpty()) {
				break;
			}
			StringBuilder sb = new StringBuilder();
			for (AuditLogEntry e : batch) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				sb.append("{\"id\":").append(jsonString(e.getId()))
						.append(",\"resourceType\":").append(jsonString(e.getResourceType()))
						.append(",\"resourceId\":").append(jsonString(e.getResourceId()))
						.append(",\"action\":").append(jsonString(e.getAction()))
						.append(",\"reason\":").append(jsonString(e.getReason()))
						.append(",\"freedBytes\":").append(e.getFreedBytes() == null ? "null" : e.getFreedBytes())
						.append(",\"occurredAt\":").append(jsonString(e.getOccurredAt()))
						.append('}');
			}
			out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
			if (batch.size() < EXPORT_BATCH_SIZE) {
				break;
			}
			offset += EXPORT_BATCH_SIZE;
		}
		out.write(']');
	}

	private void streamCsv(OutputStream out, String action, String resourceType, String from, String to,
			boolean hasFreedBytes, Long freedBytesMin, Long freedBytesMax) throws IOException {
		out.write(UTF8_BOM);
		out.write(csvRow(CSV_COLUMNS).getBytes(StandardCharsets.UTF_8));
		out.write(CRLF);
		int offset = 0;
		while (true) {
			List<AuditLogEntry> batch = auditLogMapper.selectPage(action, resourceType, from, to, hasFreedBytes,
					freedBytesMin, freedBytesMax, EXPORT_BATCH_SIZE, offset);
			if (batch.isEmpty()) {
				break;
			}
			StringBuilder sb = new StringBuilder();
			for (AuditLogEntry e : batch) {
				String[] values = {e.getId(), e.getResourceType(), e.getResourceId(),
						e.getAction(), e.getReason(),
						e.getFreedBytes() == null ? "" : e.getFreedBytes().toString(),
						e.getOccurredAt()};
				sb.append(csvRow(values)).append("\r\n");
			}
			out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
			if (batch.size() < EXPORT_BATCH_SIZE) {
				break;
			}
			offset += EXPORT_BATCH_SIZE;
		}
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
