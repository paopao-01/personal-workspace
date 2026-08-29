package com.jobhub.task.infrastructure;

import com.jobhub.review.domain.KnowledgePoint;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskSourceType;
import com.jobhub.task.domain.TaskStatus;
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
	List<LearningTask> selectPage(@Param("status") TaskStatus status, @Param("pageSize") int pageSize,
			@Param("offset") int offset);

	@Select("""
		<script>
		SELECT COUNT(*)
		FROM learning_task
		WHERE deleted_at IS NULL
		<if test='status != null'>AND status = #{status}</if>
		</script>
		""")
	long selectPageCount(@Param("status") TaskStatus status);

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
}
