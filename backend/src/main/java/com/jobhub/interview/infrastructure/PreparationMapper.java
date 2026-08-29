package com.jobhub.interview.infrastructure;

import com.jobhub.interview.application.EvidenceReference;
import com.jobhub.interview.application.ProjectCaseSummary;
import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.task.domain.LearningTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface PreparationMapper {
	@Select("""
		SELECT DISTINCT p.id, p.title, p.scenario, p.approach, p.problem_solved, p.version
		FROM project p
		JOIN project_evidence pe ON pe.project_id = p.id
		JOIN skill_evidence se ON se.evidence_id = pe.evidence_id
		JOIN requirement_skill rs ON rs.skill_id = se.skill_id
		JOIN job_requirement jr ON jr.id = rs.requirement_id
		WHERE p.deleted_at IS NULL
		  AND jr.job_id = #{jobId}
		  AND jr.confirmation_status = 'CONFIRMED'
		  AND jr.deleted_at IS NULL
		ORDER BY p.updated_at DESC, p.title
		""")
	List<ProjectCaseSummary> selectProjectCasesForJob(@Param("jobId") String jobId);

	@Select("""
		SELECT e.id, e.type, e.title, e.url_or_path, e.deleted_at IS NOT NULL AS trashed
		FROM evidence e
		JOIN project_evidence pe ON pe.evidence_id = e.id
		WHERE pe.project_id = #{projectId}
		ORDER BY e.title
		""")
	List<EvidenceReference> selectEvidenceForProject(@Param("projectId") String projectId);

	@Select("""
		SELECT DISTINCT q.id, q.review_id, q.content, q.question_type AS type, q.my_answer, q.reference_answer,
		       q.answer_status, q.difficulty, q.error_reason, q.improvement_plan, q.created_at, q.updated_at, q.version
		FROM interview_question q
		JOIN interview_review r ON r.id = q.review_id AND r.deleted_at IS NULL
		JOIN interview_schedule i ON i.id = r.interview_id AND i.deleted_at IS NULL
		JOIN application_record a ON a.id = i.application_id AND a.deleted_at IS NULL
		WHERE q.deleted_at IS NULL
		  AND a.job_id = #{jobId}
		ORDER BY i.starts_at DESC, q.created_at DESC, q.id
		""")
	List<InterviewQuestion> selectHistoricalQuestionsForJob(@Param("jobId") String jobId);

	@Select("""
		SELECT DISTINCT lt.id, lt.title, lt.task_type AS type, lt.priority, lt.estimated_minutes, lt.due_at,
		       lt.learning_goal, lt.acceptance_criteria, lt.verification_method, lt.verification_result,
		       lt.output_url, lt.status, lt.created_at, lt.updated_at, lt.completed_at, lt.abandoned_at,
		       lt.deleted_at, lt.version
		FROM learning_task lt
		JOIN task_source ts ON ts.task_id = lt.id
		LEFT JOIN interview_question q ON ts.source_type = 'QUESTION' AND q.id = ts.source_id AND q.deleted_at IS NULL
		LEFT JOIN interview_review qr ON qr.id = q.review_id AND qr.deleted_at IS NULL
		LEFT JOIN interview_schedule qi ON qi.id = qr.interview_id AND qi.deleted_at IS NULL
		LEFT JOIN application_record qa ON qa.id = qi.application_id AND qa.deleted_at IS NULL
		LEFT JOIN question_knowledge qk ON ts.source_type = 'KNOWLEDGE_POINT' AND qk.knowledge_point_id = ts.source_id
		LEFT JOIN interview_question kq ON kq.id = qk.question_id AND kq.deleted_at IS NULL
		LEFT JOIN interview_review kr ON kr.id = kq.review_id AND kr.deleted_at IS NULL
		LEFT JOIN interview_schedule ki ON ki.id = kr.interview_id AND ki.deleted_at IS NULL
		LEFT JOIN application_record ka ON ka.id = ki.application_id AND ka.deleted_at IS NULL
		LEFT JOIN job_requirement jr ON ts.source_type = 'JOB_REQUIREMENT' AND jr.id = ts.source_id
		WHERE lt.deleted_at IS NULL
		  AND lt.status IN ('TODO', 'IN_PROGRESS')
		  AND (
		    qa.job_id = #{jobId}
		    OR ka.job_id = #{jobId}
		    OR jr.job_id = #{jobId}
		  )
		ORDER BY
		  CASE lt.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
		  lt.due_at ASC,
		  lt.created_at DESC
		""")
	List<LearningTask> selectOpenTasksForJob(@Param("jobId") String jobId);
}
