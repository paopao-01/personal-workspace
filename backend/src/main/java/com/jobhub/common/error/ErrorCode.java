package com.jobhub.common.error;

/**
 * 稳定错误码，与 OpenAPI Error.code 对齐。
 * 02-state-machines.md / AGENTS.md 规定的码值必须在此固定，不得运行时拼接。
 */
public enum ErrorCode {

	VALIDATION_ERROR("VALIDATION_ERROR", 400),
	NOT_FOUND("NOT_FOUND", 404),
	VERSION_CONFLICT("VERSION_CONFLICT", 409),
	IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", 409),
	DUPLICATE_APPLICATION("DUPLICATE_APPLICATION", 409),
	ILLEGAL_STATE_TRANSITION("ILLEGAL_STATE_TRANSITION", 422),
	BUSINESS_RULE_ERROR("BUSINESS_RULE_ERROR", 422),
	INTERNAL_ERROR("INTERNAL_ERROR", 500);

	private final String code;
	private final int httpStatus;

	ErrorCode(String code, int httpStatus) {
		this.code = code;
		this.httpStatus = httpStatus;
	}

	public String code() {
		return code;
	}

	public int httpStatus() {
		return httpStatus;
	}
}
