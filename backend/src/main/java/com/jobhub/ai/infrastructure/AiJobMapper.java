package com.jobhub.ai.infrastructure;

import com.jobhub.ai.domain.AiJob;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiJobMapper {
	@Insert("""
		INSERT INTO ai_job (id, job_type, object_id, object_version, status, provider_id, provider_type, model,
			prompt_version, attempt_count, input_snapshot, created_at, updated_at)
		VALUES (#{j.id}, #{j.jobType}, #{j.objectId}, #{j.objectVersion}, #{j.status}, #{j.providerId},
			#{j.providerType}, #{j.model}, #{j.promptVersion}, #{j.attemptCount}, #{j.inputSnapshot},
			#{j.createdAt}, #{j.updatedAt})
		""")
	int insert(@Param("j") AiJob job);

	@Select("""
		SELECT id, job_type AS jobType, object_id AS objectId, object_version AS objectVersion, status,
		       provider_id AS providerId, provider_type AS providerType, model, prompt_version AS promptVersion,
		       attempt_count AS attemptCount, failure_reason AS failureReason, input_snapshot AS inputSnapshot,
		       output_json AS outputJson, started_at AS startedAt, finished_at AS finishedAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM ai_job
		WHERE id=#{id}
		""")
	AiJob selectById(@Param("id") String id);

	@Select("""
		SELECT id, job_type AS jobType, object_id AS objectId, object_version AS objectVersion, status,
		       provider_id AS providerId, provider_type AS providerType, model, prompt_version AS promptVersion,
		       attempt_count AS attemptCount, failure_reason AS failureReason, input_snapshot AS inputSnapshot,
		       output_json AS outputJson, started_at AS startedAt, finished_at AS finishedAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM ai_job
		WHERE job_type=#{jobType} AND object_id=#{objectId}
		ORDER BY created_at DESC, id
		LIMIT 50
		""")
	List<AiJob> selectByObject(@Param("jobType") String jobType, @Param("objectId") String objectId);

	/** 单次转移：QUEUED -> RUNNING，返回受控行数用于并发守卫。 */
	@Update("""
		UPDATE ai_job SET status='RUNNING', started_at=COALESCE(started_at, #{now}), updated_at=#{now}
		WHERE id=#{id} AND status='QUEUED'
		""")
	int markRunning(@Param("id") String id, @Param("now") String now);

	@Update("""
		UPDATE ai_job SET status='SUCCEEDED', output_json=#{outputJson}, finished_at=#{now}, updated_at=#{now}
		WHERE id=#{id} AND status='RUNNING'
		""")
	int markSucceeded(@Param("id") String id, @Param("outputJson") String outputJson, @Param("now") String now);

	@Update("""
		UPDATE ai_job SET status='FAILED', failure_reason=#{reason}, finished_at=#{now}, updated_at=#{now}
		WHERE id=#{id} AND status='RUNNING'
		""")
	int markFailed(@Param("id") String id, @Param("reason") String reason, @Param("now") String now);

	@Update("""
		UPDATE ai_job SET status='CANCELED', finished_at=#{now}, updated_at=#{now}
		WHERE id=#{id} AND status IN ('QUEUED', 'RUNNING')
		""")
	int markCanceled(@Param("id") String id, @Param("now") String now);

	/** 重试：FAILED -> QUEUED（attempt_count + 1），受 attempt 上限约束由服务层校验。 */
	@Update("""
		UPDATE ai_job SET status='QUEUED', attempt_count=attempt_count+1, failure_reason=NULL,
			finished_at=NULL, updated_at=#{now}
		WHERE id=#{id} AND status='FAILED'
		""")
	int markQueuedForRetry(@Param("id") String id, @Param("now") String now);
}
