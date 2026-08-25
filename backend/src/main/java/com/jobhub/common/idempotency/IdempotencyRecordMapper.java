package com.jobhub.common.idempotency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

/**
 * 幂等记录表 mapper。表结构与 V1__initial_schema.sql 的 idempotency_record 对齐。
 * 唯一键：(idempotency_key, operation)。
 */
@Mapper
public interface IdempotencyRecordMapper {

	@Select("SELECT * FROM idempotency_record WHERE idempotency_key = #{key} AND operation = #{operation}")
	IdempotencyRecord selectByKey(@Param("key") String key, @Param("operation") String operation);

	@Insert("INSERT INTO idempotency_record (id, idempotency_key, operation, request_fingerprint, " +
			"response_status, response_body_json, created_at, expires_at) VALUES (" +
			"#{id}, #{idempotencyKey}, #{operation}, #{requestFingerprint}, " +
			"#{responseStatus}, #{responseBodyJson}, #{createdAt}, #{expiresAt})")
	int insert(IdempotencyRecord record);
}
