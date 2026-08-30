package com.jobhub.datamanagement.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/**
 * 导出数据读取。仅覆盖业务数据表及其关联表；
 * 排除 user_profile、user_setting、audit_log、idempotency_record、data_export、trash_item，
 * 满足 AT-24：不含访问令牌、密钥、完整应用日志、幂等记录。
 */
@Mapper
public interface ExportDataMapper {
	@Select("SELECT * FROM job_posting ORDER BY created_at")
	List<Map<String, Object>> selectJobPostings();

	@Select("SELECT * FROM job_requirement ORDER BY created_at")
	List<Map<String, Object>> selectJobRequirements();

	@Select("SELECT * FROM requirement_match")
	List<Map<String, Object>> selectRequirementMatches();

	@Select("SELECT * FROM requirement_skill")
	List<Map<String, Object>> selectRequirementSkills();

	@Select("SELECT * FROM application_record ORDER BY created_at")
	List<Map<String, Object>> selectApplicationRecords();

	// 显式排除 idempotency_key 列：AT-24 要求导出不含幂等记录
	@Select("SELECT id, application_id, from_status, to_status, reason, occurred_at FROM application_status_log ORDER BY occurred_at")
	List<Map<String, Object>> selectApplicationStatusLogs();

	@Select("SELECT * FROM interview_schedule ORDER BY starts_at")
	List<Map<String, Object>> selectInterviewSchedules();

	@Select("SELECT * FROM interview_checklist_item ORDER BY created_at")
	List<Map<String, Object>> selectInterviewChecklistItems();

	@Select("SELECT * FROM interview_reminder ORDER BY scheduled_at")
	List<Map<String, Object>> selectInterviewReminders();

	@Select("SELECT * FROM interview_review ORDER BY created_at")
	List<Map<String, Object>> selectInterviewReviews();

	@Select("SELECT * FROM interview_question ORDER BY created_at")
	List<Map<String, Object>> selectInterviewQuestions();

	@Select("SELECT * FROM question_knowledge")
	List<Map<String, Object>> selectQuestionKnowledge();

	@Select("SELECT * FROM knowledge_point ORDER BY created_at")
	List<Map<String, Object>> selectKnowledgePoints();

	@Select("SELECT * FROM learning_task ORDER BY created_at")
	List<Map<String, Object>> selectLearningTasks();

	@Select("SELECT * FROM task_source")
	List<Map<String, Object>> selectTaskSources();

	@Select("SELECT * FROM skill ORDER BY created_at")
	List<Map<String, Object>> selectSkills();

	@Select("SELECT * FROM skill_alias")
	List<Map<String, Object>> selectSkillAliases();

	@Select("SELECT * FROM user_skill")
	List<Map<String, Object>> selectUserSkills();

	@Select("SELECT * FROM skill_evidence")
	List<Map<String, Object>> selectSkillEvidence();

	@Select("SELECT * FROM project ORDER BY created_at")
	List<Map<String, Object>> selectProjects();

	@Select("SELECT * FROM evidence ORDER BY created_at")
	List<Map<String, Object>> selectEvidence();

	@Select("SELECT * FROM project_evidence")
	List<Map<String, Object>> selectProjectEvidence();

	@Select("SELECT * FROM evidence_attachment ORDER BY created_at")
	List<Map<String, Object>> selectEvidenceAttachments();

	@Select("SELECT * FROM notification ORDER BY created_at")
	List<Map<String, Object>> selectNotifications();
}
