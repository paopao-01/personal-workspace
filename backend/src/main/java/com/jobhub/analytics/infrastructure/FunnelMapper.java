package com.jobhub.analytics.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 投递漏斗转化时间序列聚合 Mapper（注解 SQL，无 XML）。
 *
 * 单表 application_record 按 applied_at 与 granularity 分组，软删过滤 deleted_at IS NULL；
 * 计数用 SUM(CASE WHEN status ... THEN 1 ELSE 0 END) 分桶（口径同 ChannelEffectivenessMapper 的 §3.1 口径）。
 * bucket 表达式按 granularity 用 <choose> 切 date(applied_at)/strftime(...)，与 AuditLogMapper.selectFreedBytesTimeseries 同模式。
 * applied_at 以 TEXT 存 UTC ISO，date()/strftime() 按字典序分组（与时间序一致）。
 */
@Mapper
public interface FunnelMapper {

	@Select("<script>" +
			"SELECT " +
			"<choose>" +
			"<when test='granularity == \"hour\"'>strftime('%Y-%m-%dT%H:00:00Z', applied_at)</when>" +
			"<otherwise>date(applied_at)</otherwise>" +
			"</choose>" +
			" AS date, COUNT(*) AS total, " +
			"SUM(CASE WHEN status != 'DRAFT' THEN 1 ELSE 0 END) AS applied, " +
			"SUM(CASE WHEN status IN ('INTERVIEWING','OFFER') THEN 1 ELSE 0 END) AS interviewed, " +
			"SUM(CASE WHEN status = 'OFFER' THEN 1 ELSE 0 END) AS offered " +
			"FROM application_record " +
			"WHERE deleted_at IS NULL " +
			"<if test='from != null and from != \"\"'>AND applied_at &gt;= #{from}</if>" +
			"<if test='to != null and to != \"\"'>AND applied_at &lt;= #{to}</if>" +
			"GROUP BY " +
			"<choose>" +
			"<when test='granularity == \"hour\"'>strftime('%Y-%m-%dT%H:00:00Z', applied_at)</when>" +
			"<otherwise>date(applied_at)</otherwise>" +
			"</choose>" +
			" ORDER BY 1 ASC" +
			"</script>")
	List<Map<String, Object>> selectFunnel(@Param("from") String from, @Param("to") String to,
			@Param("granularity") String granularity);
}
