package com.jobhub.common.error;

/**
 * 同一 Idempotency-Key 提交了不同的请求体。响应 409 IDEMPOTENCY_CONFLICT。
 */
public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String message) {
		super(message);
	}
}
