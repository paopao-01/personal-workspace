package com.jobhub.common.error;

/**
 * 业务规则不满足（如 JD 修改后不允许某些操作、岗位已归档等）。
 * 状态机非法转换请使用 IllegalStateTransitionException。
 */
public class BusinessRuleException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessRuleException(String message) {
		super(message);
		this.errorCode = ErrorCode.BUSINESS_RULE_ERROR;
	}

	public BusinessRuleException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
