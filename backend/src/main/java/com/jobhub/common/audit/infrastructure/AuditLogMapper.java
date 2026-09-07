package com.jobhub.common.audit.infrastructure;

import com.jobhub.common.audit.AuditLogEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审计日志仅允许追加，不提供更新或删除接口。只读查询方法（selectPageByAction/countByAction）为纯 SELECT，
 * 不违背仅追加语义；按 action 过滤 + occurred_at DESC 排序，复用 V1 既有 audit_log 表无新增迁移。
 */
@Mapper
public interface AuditLogMapper {

	@Insert("INSERT INTO audit_log (id, resource_type, resource_id, action, before_snapshot_json, " +
			"after_snapshot_json, reason, occurred_at) VALUES (" +
			"#{entry.id}, #{entry.resourceType}, #{entry.resourceId}, #{entry.action}, " +
			"#{entry.beforeSnapshotJson, jdbcType=VARCHAR}, #{entry.afterSnapshotJson, jdbcType=VARCHAR}, " +
			"#{entry.reason, jdbcType=VARCHAR}, #{entry.occurredAt})")
	int insert(@Param("entry") AuditLogEntry entry);

	@Select("SELECT id, resource_type AS resourceType, resource_id AS resourceId, action, " +
			"before_snapshot_json AS beforeSnapshotJson, after_snapshot_json AS afterSnapshotJson, " +
			"reason, occurred_at AS occurredAt " +
			"FROM audit_log WHERE action = #{action} " +
			"ORDER BY occurred_at DESC LIMIT #{pageSize} OFFSET #{offset}")
	List<AuditLogEntry> selectPageByAction(@Param("action") String action,
			@Param("pageSize") int pageSize, @Param("offset") int offset);

	@Select("SELECT COUNT(*) FROM audit_log WHERE action = #{action}")
	long countByAction(@Param("action") String action);
}

