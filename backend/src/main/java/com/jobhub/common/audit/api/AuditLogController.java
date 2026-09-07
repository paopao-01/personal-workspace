package com.jobhub.common.audit.api;

import com.jobhub.common.audit.infrastructure.AuditLogMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

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
}
