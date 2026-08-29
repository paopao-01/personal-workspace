package com.jobhub.review.infrastructure;

import com.jobhub.review.domain.InterviewReview;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ReviewMapper {
	@Insert("INSERT INTO interview_review (id, interview_id, review_status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, project_expression_risk, created_at, updated_at, version) VALUES (#{id},#{interviewId},#{status},#{interviewResult},#{noQuestionsRecorded},#{overallFeeling},#{interviewerFocus},#{jobInterest},#{projectExpressionRisk},#{createdAt},#{updatedAt},#{version})")
	int insert(InterviewReview review);

	@Select("SELECT id, interview_id, review_status AS status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, project_expression_risk AS projectExpressionRisk, created_at, updated_at, version FROM interview_review WHERE id=#{id} AND deleted_at IS NULL")
	InterviewReview selectById(@Param("id") String id);

	@Select("SELECT id, interview_id, review_status AS status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, project_expression_risk AS projectExpressionRisk, created_at, updated_at, version FROM interview_review WHERE interview_id=#{interviewId} AND deleted_at IS NULL")
	InterviewReview selectByInterview(@Param("interviewId") String interviewId);

	@Update("UPDATE interview_review SET review_status=#{review.status}, interview_result=#{review.interviewResult}, no_questions_recorded=#{review.noQuestionsRecorded}, overall_feeling=#{review.overallFeeling}, interviewer_focus=#{review.interviewerFocus}, job_interest=#{review.jobInterest}, project_expression_risk=#{review.projectExpressionRisk}, updated_at=#{review.updatedAt}, version=version+1 WHERE id=#{review.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateDraft(@Param("review") InterviewReview review, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE interview_review SET updated_at=#{now}, version=version+1 WHERE id=#{id} AND deleted_at IS NULL")
	int bumpVersion(@Param("id") String id, @Param("now") String now);

	@Update("UPDATE interview_review SET review_status='COMPLETED', updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int complete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Update("UPDATE interview_review SET review_status='DRAFT', updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND review_status='COMPLETED' AND deleted_at IS NULL")
	int reopen(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Select("""
		SELECT r.interview_result AS interviewResult, COUNT(*) AS cnt
		FROM interview_review r
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE r.deleted_at IS NULL
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		GROUP BY r.interview_result
		""")
	List<AnalysisResultCountRow> selectAnalysisResultCounts(@Param("from") String from,
			@Param("to") String to, @Param("jobId") String jobId);
}
