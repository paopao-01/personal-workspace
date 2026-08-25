package com.jobhub.common.error;

import java.util.List;

/**
 * 统一错误响应，与 OpenAPI components.schemas.Error 对齐。
 * 必填：code、message、traceId；可选：fieldErrors、currentState、targetState、reason。
 */
public record ErrorResponse(
		String code,
		String message,
		String traceId,
		List<FieldError> fieldErrors,
		String currentState,
		String targetState,
		String reason
) {

	public static ErrorResponse of(ErrorCode ec, String message, String traceId) {
		return new ErrorResponse(ec.code(), message, traceId, null, null, null, null);
	}

	public static ErrorResponse of(ErrorCode ec, String message, String traceId,
									String currentState, String targetState, String reason) {
		return new ErrorResponse(ec.code(), message, traceId, null, currentState, targetState, reason);
	}

	public static ErrorResponse of(ErrorCode ec, String message, String traceId, List<FieldError> fieldErrors) {
		return new ErrorResponse(ec.code(), message, traceId, fieldErrors, null, null, null);
	}
}
