package com.jobhub.task.infrastructure;

import com.jobhub.review.domain.KnowledgePoint;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskSourceType;
import com.jobhub.task.domain.TaskStatus;
import com.jobhub.task.application.TaskSourceRef;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TaskMapper {
	@Insert("""
		INSERT INTO learning_task (
		  id, title, task_type, priority, estimated_minutes, due_at, learning_goal,
		  acceptance_criteria, verification_method, output_url, status, created_at, updated_at, version
		) VALUES (
		  #{id}, #{title}, #{type}, #{priority}, #{estimatedMinutes}, #{dueAt}, #{learningGoal},
		  #{acceptanceCriteria}, #{verificationMethod}, #{outputUrl}, #{status}, #{createdAt}, #{updatedAt}, #{version}
		)
		""")
	int insert(LearningTask task);

	@Select("""
		SELECT id, title, task_type AS type, priority, estimated_minutes, due_at, learning_goal,
		       acceptance_criteria, verification_method, verification_result, output_url, status,
		       created_at, updated_at, completed_at, abandoned_at, deleted_at, version
		FROM learning_task
		WHERE id=#{id} AND deleted_at IS NULL
		""")
	LearningTask selectById(@Param("id") String id);

	@Select("""
		<script>
		SELECT id, title, task_type AS type, priority, estimated_minutes, due_at, learning_goal,
		       acceptance_criteria, verification_method, verification_result, output_url, status,
		       created_at, updated_at, completed_at, abandoned_at, deleted_at, version
		FROM learning_task
		WHERE deleted_at IS NULL
		<if test='status != null'>AND status = #{status}</if>
		<if test='knowledgePointId != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type='KNOWLEDGE_POINT' AND ts.source_id=#{knowledgePointId})</if>
		<if test='sourceType != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type=#{sourceType})</if>
		<if test='dueAfter != null'>AND due_at IS NOT NULL AND due_at &gt;= #{dueAfter}</if>
		<if test='dueBefore != null'>AND due_at IS NOT NULL AND due_at &lt;= #{dueBefore}</if>
		<if test='jobId != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type='JOB' AND ts.source_id=#{jobId})</if>
		<if test='interviewId != null'>AND EXISTS (SELECT 1 FROM task_source ts JOIN interview_question q ON q.id=ts.source_id JOIN interview_review r ON r.id=q.review_id WHERE ts.task_id=learning_task.id AND ts.source_type='QUESTION' AND r.interview_id=#{interviewId})</if>
		ORDER BY
		  CASE
		    WHEN due_at IS NOT NULL AND due_at != '' AND status IN ('TODO','IN_PROGRESS') THEN 0
		    WHEN status IN ('TODO','IN_PROGRESS') THEN 1
		    ELSE 2
		  END,
		  due_at ASC,
		  CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
		  created_at DESC
		LIMIT #{pageSize} OFFSET #{offset}
		</script>
		""")
	List<LearningTask> selectPage(@Param("status") TaskStatus status, @Param("knowledgePointId") String knowledgePointId,
			@Param("sourceType") TaskSourceType sourceType, @Param("dueAfter") String dueAfter, @Param("dueBefore") String dueBefore,
			@Param("jobId") String jobId, @Param("interviewId") String interviewId, @Param("pageSize") int pageSize, @Param("offset") int offset);

	@Select("""
		<script>
		SELECT COUNT(*)
		FROM learning_task
		WHERE deleted_at IS NULL
		<if test='status != null'>AND status = #{status}</if>
		<if test='knowledgePointId != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type='KNOWLEDGE_POINT' AND ts.source_id=#{knowledgePointId})</if>
		<if test='sourceType != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type=#{sourceType})</if>
		<if test='dueAfter != null'>AND due_at IS NOT NULL AND due_at &gt;= #{dueAfter}</if>
		<if test='dueBefore != null'>AND due_at IS NOT NULL AND due_at &lt;= #{dueBefore}</if>
		<if test='jobId != null'>AND EXISTS (SELECT 1 FROM task_source ts WHERE ts.task_id=learning_task.id AND ts.source_type='JOB' AND ts.source_id=#{jobId})</if>
		<if test='interviewId != null'>AND EXISTS (SELECT 1 FROM task_source ts JOIN interview_question q ON q.id=ts.source_id JOIN interview_review r ON r.id=q.review_id WHERE ts.task_id=learning_task.id AND ts.source_type='QUESTION' AND r.interview_id=#{interviewId})</if>
		</script>
		""")
	long selectPageCount(@Param("status") TaskStatus status, @Param("knowledgePointId") String knowledgePointId,
			@Param("sourceType") TaskSourceType sourceType, @Param("dueAfter") String dueAfter, @Param("dueBefore") String dueBefore,
			@Param("jobId") String jobId, @Param("interviewId") String interviewId);

	@Update("""
		UPDATE learning_task
		SET title=#{task.title},
		    task_type=#{task.type},
		    priority=#{task.priority},
		    estimated_minutes=#{task.estimatedMinutes},
		    due_at=#{task.dueAt},
		    learning_goal=#{task.learningGoal},
		    acceptance_criteria=#{task.acceptanceCriteria},
		    verification_method=#{task.verificationMethod},
		    verification_result=#{task.verificationResult},
		    output_url=#{task.outputUrl},
		    updated_at=#{task.updatedAt}
		WHERE id=#{task.id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int updateMeta(@Param("task") LearningTask task, @Param("expectedVersion") long expectedVersion);

	@Update("""
		UPDATE learning_task
		SET status=#{task.status},
		    verification_result=#{task.verificationResult},
		    completed_at=#{task.completedAt},
		    abandoned_at=#{task.abandonedAt},
		    updated_at=#{task.updatedAt}
		WHERE id=#{task.id}
		  AND version=#{expectedVersion}
		  AND deleted_at IS NULL
		""")
	int updateStatus(@Param("task") LearningTask task, @Param("expectedVersion") long expectedVersion);

	@Update("UPDATE learning_task SET version=version+1 WHERE id=#{id} AND version=#{expectedVersion} AND deleted_at IS NULL")
	int bumpVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

	@Insert("""
		INSERT OR IGNORE INTO task_source (id, task_id, source_type, source_id, created_at)
		VALUES (#{id}, #{taskId}, #{sourceType}, #{sourceId}, #{now})
		""")
	int insertSource(@Param("id") String id, @Param("taskId") String taskId,
			@Param("sourceType") TaskSourceType sourceType, @Param("sourceId") String sourceId,
			@Param("now") String now);

	@Delete("DELETE FROM task_source WHERE task_id=#{taskId}")
	int deleteSources(@Param("taskId") String taskId);

	@Select("""
		SELECT k.id, k.name, k.category
		FROM knowledge_point k
		JOIN task_source ts ON ts.source_type='KNOWLEDGE_POINT' AND ts.source_id=k.id
		WHERE ts.task_id=#{taskId} AND k.deleted_at IS NULL
		ORDER BY k.name
		""")
	List<KnowledgePoint> selectKnowledgePoints(@Param("taskId") String taskId);

	@Select("SELECT ts.source_type AS type, COALESCE(ts.source_id, ts.task_id) AS id, COALESCE(j.title, r.raw_text, s.name, k.name, q.content, '手工创建') AS label " +
			"FROM task_source ts LEFT JOIN job_posting j ON ts.source_type='JOB' AND j.id=ts.source_id " +
			"LEFT JOIN job_requirement r ON ts.source_type='JOB_REQUIREMENT' AND r.id=ts.source_id " +
			"LEFT JOIN skill s ON ts.source_type='SKILL' AND s.id=ts.source_id " +
			"LEFT JOIN knowledge_point k ON ts.source_type='KNOWLEDGE_POINT' AND k.id=ts.source_id " +
			"LEFT JOIN interview_question q ON ts.source_type='QUESTION' AND q.id=ts.source_id " +
			"WHERE ts.task_id=#{taskId} ORDER BY ts.created_at")
	List<TaskSourceRef> selectSourceRefs(@Param("taskId") String taskId);

	@Select("SELECT id, title, task_type AS type, priority, estimated_minutes, due_at, learning_goal, acceptance_criteria, verification_method, verification_result, output_url, status, created_at, updated_at, completed_at, abandoned_at, deleted_at, version FROM learning_task WHERE deleted_at IS NULL AND status IN ('TODO','IN_PROGRESS') AND due_at IS NOT NULL AND due_at <= #{until} ORDER BY due_at")
	List<LearningTask> selectDueForDashboard(@Param("until") String until);
}
