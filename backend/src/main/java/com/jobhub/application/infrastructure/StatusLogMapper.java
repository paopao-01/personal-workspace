package com.jobhub.application.infrastructure;

import com.jobhub.application.domain.StatusLogEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 投递状态历史 Mapper。application_status_log 不可覆盖历史：仅 INSERT/SELECT，禁止 UPDATE/DELETE。
 */
@Mapper
public interface StatusLogMapper {

	@Insert("INSERT INTO application_status_log (id, application_id, from_status, to_status, reason, " +
			"idempotency_key, occurred_at) VALUES (" +
			"#{log.id}, #{log.applicationId}, #{log.fromStatus, jdbcType=VARCHAR}, " +
			"#{log.toStatus}, #{log.reason, jdbcType=VARCHAR}, " +
			"#{log.idempotencyKey, jdbcType=VARCHAR}, #{log.occurredAt})")
	int insert(@Param("log") StatusLogEntry log);

	@Select("SELECT id, application_id, from_status, to_status, reason, idempotency_key, occurred_at " +
			"FROM application_status_log WHERE application_id = #{applicationId} " +
			"ORDER BY occurred_at ASC")
	List<StatusLogEntry> selectByApplication(@Param("applicationId") String applicationId);
}
