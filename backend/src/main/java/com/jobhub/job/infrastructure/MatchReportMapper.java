package com.jobhub.job.infrastructure;

import com.jobhub.job.domain.MatchReport;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MatchReportMapper {
	@Insert("""
		INSERT INTO match_report (id, job_id, rule_version, weights_json, report_json, input_fingerprint, generated_at, created_at, updated_at)
		VALUES (#{report.id}, #{report.jobId}, #{report.ruleVersion}, #{report.weightsJson}, #{report.reportJson}, #{report.inputFingerprint}, #{report.generatedAt}, #{report.generatedAt}, #{report.generatedAt})
		""")
	int insert(@Param("report") MatchReport report);

	@Select("""
		SELECT id, job_id, rule_version, weights_json, report_json, input_fingerprint, generated_at
		FROM match_report
		WHERE job_id=#{jobId}
		ORDER BY generated_at DESC, created_at DESC
		LIMIT 1
		""")
	MatchReport selectLatestByJob(@Param("jobId") String jobId);

	@Select("""
		SELECT id, job_id, rule_version, weights_json, report_json, input_fingerprint, generated_at
		FROM match_report
		WHERE job_id=#{jobId}
		ORDER BY generated_at DESC, created_at DESC
		LIMIT 50
		""")
	List<MatchReport> selectHistoryByJob(@Param("jobId") String jobId);

	@Select("""
		SELECT id, job_id, rule_version, weights_json, report_json, input_fingerprint, generated_at
		FROM match_report
		WHERE id=#{id}
		""")
	MatchReport selectById(@Param("id") String id);
}
