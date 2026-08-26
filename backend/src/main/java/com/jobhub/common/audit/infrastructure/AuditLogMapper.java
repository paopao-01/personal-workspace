package com.jobhub.common.audit.infrastructure;

import com.jobhub.common.audit.AuditLogEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志仅允许追加，不提供更新或删除接口。
 */
@Mapper
public interface AuditLogMapper {

	@Insert("INSERT INTO audit_log (id, resource_type, resource_id, action, before_snapshot_json, " +
			"after_snapshot_json, reason, occurred_at) VALUES (" +
			"#{entry.id}, #{entry.resourceType}, #{entry.resourceId}, #{entry.action}, " +
			"#{entry.beforeSnapshotJson, jdbcType=VARCHAR}, #{entry.afterSnapshotJson, jdbcType=VARCHAR}, " +
			"#{entry.reason, jdbcType=VARCHAR}, #{entry.occurredAt})")
	int insert(@Param("entry") AuditLogEntry entry);
}
