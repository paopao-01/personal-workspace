package com.jobhub.review.infrastructure;

import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.review.domain.KnowledgePoint;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface QuestionMapper {
	@Insert("INSERT INTO interview_question (id, review_id, content, question_type, answer_status, created_at, updated_at, version) VALUES (#{id},#{reviewId},#{content},#{type},#{answerStatus},#{createdAt},#{updatedAt},#{version})")
	int insert(InterviewQuestion question);

	@Select("SELECT id, review_id, content, question_type AS type, my_answer, reference_answer, answer_status, difficulty, error_reason, improvement_plan, created_at, updated_at, version FROM interview_question WHERE id=#{id} AND deleted_at IS NULL")
	InterviewQuestion selectById(@Param("id") String id);

	@Select("SELECT id, review_id, content, question_type AS type, my_answer, reference_answer, answer_status, difficulty, error_reason, improvement_plan, created_at, updated_at, version FROM interview_question WHERE review_id=#{reviewId} AND deleted_at IS NULL ORDER BY created_at, id")
	List<InterviewQuestion> selectByReview(@Param("reviewId") String reviewId);

	@Insert("INSERT INTO question_knowledge (question_id, knowledge_point_id, created_at) VALUES (#{questionId},#{knowledgePointId},#{now})")
	int insertKnowledge(@Param("questionId") String questionId, @Param("knowledgePointId") String knowledgePointId, @Param("now") String now);

	@Select("SELECT k.id, k.name, k.category FROM knowledge_point k JOIN question_knowledge qk ON qk.knowledge_point_id=k.id WHERE qk.question_id=#{questionId} AND k.deleted_at IS NULL ORDER BY k.name")
	List<KnowledgePoint> selectKnowledgePoints(@Param("questionId") String questionId);
}
