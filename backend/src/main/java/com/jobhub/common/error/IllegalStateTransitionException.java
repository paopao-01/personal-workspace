package com.jobhub.common.error;

/**
 * 状态机非法转换。响应 422 ILLEGAL_STATE_TRANSITION，且不得产生数据副作用。
 */
public class IllegalStateTransitionException extends RuntimeException {

	private final String currentState;
	private final String targetState;
	private final String reason;

	public IllegalStateTransitionException(String currentState, String targetState, String reason) {
		super("Illegal state transition from " + currentState + " to " + targetState + ": " + reason);
		this.currentState = currentState;
		this.targetState = targetState;
		this.reason = reason;
	}

	public String currentState() {
		return currentState;
	}

	public String targetState() {
		return targetState;
	}

	public String reason() {
		return reason;
	}
}
