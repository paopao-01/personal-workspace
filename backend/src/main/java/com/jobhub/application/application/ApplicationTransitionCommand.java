package com.jobhub.application.application;

import com.jobhub.application.domain.ApplicationStatus;

/**
 * 投递状态转换命令。targetStatus 驱动（不是命令名），服务端从 (currentState, targetStatus) 推导业务命令。
 */
public record ApplicationTransitionCommand(
		ApplicationStatus targetStatus,
		String reason,
		boolean allowOfferWithoutCompletedInterview
) { }
