package com.jobhub.common.audit.infrastructure;

import com.jobhub.common.audit.AuditLogEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审计日志仅允许追加，不提供更新或删除接口。只读查询方法（selectPageByAction/countByAction 与
 * selectPage/count）为纯 SELECT，不违背仅追加语义；按 action 过滤 + 可选 resourceType/from/to 过滤
 * + occurred_at DESC 排序，复用 V1 既有 audit_log 表无新增迁移。
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

	/**
	 * 全量审计日志分页查询（只读）：action、resourceType、from、to 均可选，null 或空时不加条件返回全量；
	 * action/resourceType 按字符串精确匹配，from/to 按 occurred_at 时间范围过滤（ISO-8601 UTC 字符串比较，
	 * 字典序与时间序一致）。四者可单独或任意组合使用。按 occurred_at DESC 排序。
	 */
	@Select("<script>" +
			"SELECT id, resource_type AS resourceType, resource_id AS resourceId, action, " +
			"before_snapshot_json AS beforeSnapshotJson, after_snapshot_json AS afterSnapshotJson, " +
			"reason, occurred_at AS occurredAt FROM audit_log " +
			"<where>" +
			"<if test='action != null and action != \"\"'>AND action = #{action}</if>" +
			"<if test='resourceType != null and resourceType != \"\"'>AND resource_type = #{resourceType}</if>" +
			"<if test='from != null and from != \"\"'>AND occurred_at &gt;= #{from}</if>" +
			"<if test='to != null and to != \"\"'>AND occurred_at &lt;= #{to}</if>" +
			"</where>" +
			"ORDER BY occurred_at DESC LIMIT #{pageSize} OFFSET #{offset}" +
			"</script>")
	List<AuditLogEntry> selectPage(@Param("action") String action, @Param("resourceType") String resourceType,
			@Param("from") String from, @Param("to") String to,
			@Param("pageSize") int pageSize, @Param("offset") int offset);

	/** 全量审计日志计数（只读）：action、resourceType、from、to 均可选，null 或空时不加条件计数全量。 */
	@Select("<script>" +
			"SELECT COUNT(*) FROM audit_log " +
			"<where>" +
			"<if test='action != null and action != \"\"'>AND action = #{action}</if>" +
			"<if test='resourceType != null and resourceType != \"\"'>AND resource_type = #{resourceType}</if>" +
			"<if test='from != null and from != \"\"'>AND occurred_at &gt;= #{from}</if>" +
			"<if test='to != null and to != \"\"'>AND occurred_at &lt;= #{to}</if>" +
			"</where>" +
			"</script>")
	long count(@Param("action") String action, @Param("resourceType") String resourceType,
			@Param("from") String from, @Param("to") String to);
}

