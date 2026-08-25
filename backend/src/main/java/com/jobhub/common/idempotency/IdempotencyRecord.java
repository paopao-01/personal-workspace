package com.jobhub.common.idempotency;

import java.time.Instant;

/**
 * 幂等记录实体。对应 idempotency_record 表。
 */
public record IdempotencyRecord(
		String id,
		String idempotencyKey,
		String operation,
		String requestFingerprint,
		Integer responseStatus,
		String responseBodyJson,
		String createdAt,
		String expiresAt
) {
	public boolean isExpired(Instant now) {
		return Instant.parse(expiresAt).isBefore(now);
	}
}
