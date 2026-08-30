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

	@Delete("DELETE FROM question_knowledge WHERE question_id=#{questionId}")
	int deleteKnowledgeForQuestion(@Param("questionId") String questionId);

	@Select("SELECT k.id, k.name, k.category FROM knowledge_point k JOIN question_knowledge qk ON qk.knowledge_point_id=k.id WHERE qk.question_id=#{questionId} AND k.deleted_at IS NULL ORDER BY k.name")
	List<KnowledgePoint> selectKnowledgePoints(@Param("questionId") String questionId);

	@Select("""
		SELECT id, name, category
		FROM knowledge_point
		WHERE deleted_at IS NULL
		  AND (#{query} IS NULL OR name LIKE '%' || #{query} || '%' OR category LIKE '%' || #{query} || '%')
		ORDER BY is_system DESC, name
		LIMIT 50
		""")
	List<KnowledgePoint> listKnowledgePoints(@Param("query") String query);

	@Select("SELECT id, name, category FROM knowledge_point WHERE id=#{id} AND deleted_at IS NULL")
	KnowledgePoint selectKnowledgePointById(@Param("id") String id);

	@Select("SELECT id, name, category FROM knowledge_point WHERE normalized_name=#{normalizedName} AND deleted_at IS NULL")
	KnowledgePoint selectKnowledgePointByNormalizedName(@Param("normalizedName") String normalizedName);

	@Insert("INSERT INTO knowledge_point (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (#{id},#{name},#{normalizedName},#{category},0,#{now},#{now})")
	int insertKnowledgePoint(@Param("id") String id, @Param("name") String name,
			@Param("normalizedName") String normalizedName, @Param("category") String category, @Param("now") String now);

	@Update("""
		UPDATE interview_question
		SET content=#{question.content},
		    question_type=#{question.type},
		    my_answer=#{question.myAnswer},
		    reference_answer=#{question.referenceAnswer},
		    answer_status=#{question.answerStatus},
		    difficulty=#{question.difficulty},
		    error_reason=#{question.errorReason},
		    improvement_plan=#{question.improvementPlan},
		    updated_at=#{question.updatedAt},
		    version=version+1
		WHERE id=#{question.id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int updateQuestion(@Param("question") InterviewQuestion question, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE interview_question SET question_type=#{type}, updated_at=#{now}, version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int updateType(@Param("id") String id, @Param("type") String type, @Param("expectedVersion") long expectedVersion,
			@Param("now") String now);

	@Select("""
		SELECT k.id AS knowledge_point_id,
		       k.name,
		       k.category,
		       SUM(CASE q.answer_status
		           WHEN 'UNANSWERED' THEN 1.0
		           WHEN 'PARTIALLY_ANSWERED' THEN 0.5
		           ELSE 0.0
		       END) AS weighted_weakness_count,
		       COUNT(DISTINCT q.id) AS question_count
		FROM knowledge_point k
		JOIN question_knowledge qk ON qk.knowledge_point_id = k.id
		JOIN interview_question q ON q.id = qk.question_id AND q.deleted_at IS NULL
		JOIN interview_review r ON r.id = q.review_id
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE k.deleted_at IS NULL
		  AND q.answer_status IN ('UNANSWERED', 'PARTIALLY_ANSWERED')
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		GROUP BY k.id, k.name, k.category
		HAVING weighted_weakness_count > 0
		ORDER BY weighted_weakness_count DESC, question_count DESC, k.name
		""")
	List<WeakKnowledgePointRow> selectWeakKnowledgePoints(@Param("from") String from, @Param("to") String to,
			@Param("jobId") String jobId);

	@Select("""
		SELECT DISTINCT q.id, q.review_id, q.content, q.question_type AS type, q.my_answer, q.reference_answer,
		       q.answer_status, q.difficulty, q.error_reason, q.improvement_plan, q.created_at, q.updated_at, q.version
		FROM interview_question q
		JOIN question_knowledge qk ON qk.question_id = q.id
		JOIN interview_review r ON r.id = q.review_id
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE q.deleted_at IS NULL
		  AND qk.knowledge_point_id = #{knowledgePointId}
		  AND q.answer_status IN ('UNANSWERED', 'PARTIALLY_ANSWERED')
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		ORDER BY i.starts_at DESC, q.created_at DESC, q.id
		""")
	List<InterviewQuestion> selectWeakQuestions(@Param("knowledgePointId") String knowledgePointId,
			@Param("from") String from, @Param("to") String to, @Param("jobId") String jobId);

	@Update("""
		UPDATE interview_question
		SET deleted_at=#{now}, updated_at=#{now}, version=version+1
		WHERE id=#{id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int softDelete(@Param("id") String id, @Param("expectedVersion") long expectedVersion, @Param("now") String now);	@Update("UPDATE interview_question SET deleted_at=NULL, updated_at=#{now} WHERE id=#{id} AND deleted_at IS NOT NULL")
	int restoreById(@Param("id") String id, @Param("now") String now);

	@Delete("DELETE FROM interview_question WHERE id=#{id}")
	int hardDelete(@Param("id") String id);

	@Delete("DELETE FROM task_source WHERE source_type='QUESTION' AND source_id=#{questionId}")
	int deleteTaskSourceForQuestion(@Param("questionId") String questionId);

	@Select("""
		SELECT q.answer_status AS answerStatus, COUNT(*) AS cnt
		FROM interview_question q
		JOIN interview_review r ON r.id = q.review_id AND r.deleted_at IS NULL
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE q.deleted_at IS NULL
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		GROUP BY q.answer_status
		""")
	List<AnalysisStatusCountRow> selectAnalysisQuestionStatusCounts(@Param("from") String from, @Param("to") String to,
			@Param("jobId") String jobId);

	@Select("""
		SELECT k.id AS knowledgePointId,
		       k.name,
		       k.category,
		       COUNT(*) AS questionCount,
		       SUM(CASE WHEN q.answer_status = 'FULLY_ANSWERED' THEN 1 ELSE 0 END) AS fullyAnsweredCount
		FROM knowledge_point k
		JOIN question_knowledge qk ON qk.knowledge_point_id = k.id
		JOIN interview_question q ON q.id = qk.question_id AND q.deleted_at IS NULL
		JOIN interview_review r ON r.id = q.review_id AND r.deleted_at IS NULL
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE k.deleted_at IS NULL
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		GROUP BY k.id, k.name, k.category
		ORDER BY questionCount DESC, k.name
		""")
	List<AnalysisKnowledgePointStatRow> selectAnalysisKnowledgePointStats(@Param("from") String from,
			@Param("to") String to, @Param("jobId") String jobId);

	@Select("""
		SELECT q.question_type AS type,
		       COUNT(*) AS questionCount,
		       SUM(CASE WHEN q.answer_status = 'FULLY_ANSWERED' THEN 1 ELSE 0 END) AS fullyAnsweredCount
		FROM interview_question q
		JOIN interview_review r ON r.id = q.review_id AND r.deleted_at IS NULL
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE q.deleted_at IS NULL
		  AND (#{from} IS NULL OR substr(i.starts_at, 1, 10) >= #{from})
		  AND (#{to} IS NULL OR substr(i.starts_at, 1, 10) <= #{to})
		  AND (#{jobId} IS NULL OR a.job_id = #{jobId})
		GROUP BY q.question_type
		ORDER BY questionCount DESC
		""")
	List<AnalysisQuestionTypeStatRow> selectAnalysisQuestionTypeStats(@Param("from") String from,
			@Param("to") String to, @Param("jobId") String jobId);
}
