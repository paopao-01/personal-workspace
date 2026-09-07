package com.jobhub.common.audit.api;

import com.jobhub.common.audit.infrastructure.AuditLogMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全量审计日志只读查询 REST 接口：分页查询 {@code audit_log} 表，可按 action/resourceType 过滤。
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
	 * 分页查询全量审计日志（只读）。action 与 resourceType 均可选，空=不过滤返回全量，可单独或组合。
	 * 按 occurred_at DESC 排序；无二级索引走全表扫描（本地单用户量小可接受，不新增迁移/索引）。
	 */
	@GetMapping("/audit-logs")
	public PageAuditLogEntryResponse listAuditLogs(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String resourceType) {
		long total = auditLogMapper.count(action, resourceType);
		int offset = (page - 1) * pageSize;
		return PageAuditLogEntryResponse.from(
				auditLogMapper.selectPage(action, resourceType, pageSize, offset), total, page, pageSize);
	}
}
