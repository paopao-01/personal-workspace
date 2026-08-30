package com.jobhub.ai.infrastructure;

import com.jobhub.ai.domain.AiJobItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiJobItemMapper {
	@Insert("""
		INSERT INTO ai_job_item (id, ai_job_id, payload_json, status, sort_order, created_at, updated_at)
		VALUES (#{i.id}, #{i.aiJobId}, #{i.payloadJson}, 'PROPOSED', #{i.sortOrder}, #{i.createdAt}, #{i.updatedAt})
		""")
	int insert(@Param("i") AiJobItem item);

	@Select("""
		SELECT id, ai_job_id AS aiJobId, payload_json AS payloadJson, edited_payload_json AS editedPayloadJson,
		       status, requirement_id AS requirementId, task_id AS taskId, sort_order AS sortOrder, created_at AS createdAt, updated_at AS updatedAt
		FROM ai_job_item
		WHERE ai_job_id=#{aiJobId}
		ORDER BY sort_order, created_at, id
		""")
	List<AiJobItem> selectByJob(@Param("aiJobId") String aiJobId);

	@Select("""
		SELECT id, ai_job_id AS aiJobId, payload_json AS payloadJson, edited_payload_json AS editedPayloadJson,
		       status, requirement_id AS requirementId, task_id AS taskId, sort_order AS sortOrder, created_at AS createdAt, updated_at AS updatedAt
		FROM ai_job_item
		WHERE id=#{id}
		""")
	AiJobItem selectById(@Param("id") String id);

	/** 采纳（可带编辑内容）：PROPOSED -> ACCEPTED，单次转移守卫。 */
	@Update("""
		UPDATE ai_job_item
		SET status='ACCEPTED', edited_payload_json=#{editedPayloadJson}, requirement_id=#{requirementId}, task_id=#{taskId},
		    updated_at=#{now}
		WHERE id=#{id} AND status='PROPOSED'
		""")
	int markAccepted(@Param("id") String id, @Param("editedPayloadJson") String editedPayloadJson,
			@Param("requirementId") String requirementId, @Param("taskId") String taskId, @Param("now") String now);

	@Update("""
		UPDATE ai_job_item SET status='REJECTED', updated_at=#{now}
		WHERE id=#{id} AND status='PROPOSED'
		""")
	int markRejected(@Param("id") String id, @Param("now") String now);
}
