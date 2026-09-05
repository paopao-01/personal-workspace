package com.jobhub.analytics.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 投递渠道与简历版本效果对比聚合 Mapper（注解 SQL，无 XML）。
 *
 * 单表 application_record 聚合，软删过滤 deleted_at IS NULL；可选日期过滤按 applied_at 日期前缀；
 * 计数用 SUM(CASE WHEN status ... THEN 1 ELSE 0 END) 分桶（仿 QuestionMapper.selectAnalysisKnowledgePointStats）。
 */
@Mapper
public interface ChannelEffectivenessMapper {

	@Select("""
			SELECT channel AS dimension,
				COUNT(*) AS applicationCount,
				SUM(CASE WHEN status IN ('INTERVIEWING','OFFER') THEN 1 ELSE 0 END) AS interviewCount,
				SUM(CASE WHEN status = 'OFFER' THEN 1 ELSE 0 END) AS offerCount
			FROM application_record
			WHERE deleted_at IS NULL
			  AND status != 'DRAFT'
			  AND (#{from} IS NULL OR substr(applied_at, 1, 10) >= #{from})
			  AND (#{to} IS NULL OR substr(applied_at, 1, 10) <= #{to})
			GROUP BY channel
			ORDER BY applicationCount DESC, channel ASC
			""")
	List<EffectivenessRow> selectByChannel(@Param("from") String from, @Param("to") String to);

	@Select("""
			SELECT resume_version AS dimension,
				COUNT(*) AS applicationCount,
				SUM(CASE WHEN status IN ('INTERVIEWING','OFFER') THEN 1 ELSE 0 END) AS interviewCount,
				SUM(CASE WHEN status = 'OFFER' THEN 1 ELSE 0 END) AS offerCount
			FROM application_record
			WHERE deleted_at IS NULL
			  AND status != 'DRAFT'
			  AND (#{from} IS NULL OR substr(applied_at, 1, 10) >= #{from})
			  AND (#{to} IS NULL OR substr(applied_at, 1, 10) <= #{to})
			GROUP BY resume_version
			ORDER BY applicationCount DESC, resume_version IS NULL, resume_version ASC
			""")
	List<EffectivenessRow> selectByResumeVersion(@Param("from") String from, @Param("to") String to);
}
