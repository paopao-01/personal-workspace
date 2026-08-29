package com.jobhub.review.infrastructure;

import com.jobhub.review.domain.InterviewReview;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper {
	@Insert("INSERT INTO interview_review (id, interview_id, review_status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, created_at, updated_at, version) VALUES (#{id},#{interviewId},#{status},#{interviewResult},#{noQuestionsRecorded},#{overallFeeling},#{interviewerFocus},#{jobInterest},#{createdAt},#{updatedAt},#{version})")
	int insert(InterviewReview review);

	@Select("SELECT id, interview_id, review_status AS status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, created_at, updated_at, version FROM interview_review WHERE id=#{id} AND deleted_at IS NULL")
	InterviewReview selectById(@Param("id") String id);

	@Select("SELECT id, interview_id, review_status AS status, interview_result, no_questions_recorded, overall_feeling, interviewer_focus, job_interest, created_at, updated_at, version FROM interview_review WHERE interview_id=#{interviewId} AND deleted_at IS NULL")
	InterviewReview selectByInterview(@Param("interviewId") String interviewId);

	@Update("UPDATE interview_review SET review_status=#{review.status}, interview_result=#{review.interviewResult}, no_questions_recorded=#{review.noQuestionsRecorded}, overall_feeling=#{review.overallFeeling}, interviewer_focus=#{review.interviewerFocus}, job_interest=#{review.jobInterest}, updated_at=#{review.updatedAt}, version=version+1 WHERE id=#{review.id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateDraft(@Param("review") InterviewReview review, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE interview_review SET updated_at=#{now}, version=version+1 WHERE id=#{id} AND deleted_at IS NULL")
	int bumpVersion(@Param("id") String id, @Param("now") String now);

	@Update("UPDATE interview_review SET review_status='COMPLETED', updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int complete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);

	@Update("UPDATE interview_review SET review_status='DRAFT', updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND review_status='COMPLETED' AND deleted_at IS NULL")
	int reopen(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);
}
