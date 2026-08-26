package com.jobhub.application.api;

import com.jobhub.application.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 投递状态转换请求。targetStatus 驱动，服务端推导业务命令。
 */
public class ApplicationTransitionRequest {

	@NotNull
	private ApplicationStatus targetStatus;

	@Size(max = 1000)
	private String reason;

	private boolean allowOfferWithoutCompletedInterview;

	public ApplicationStatus getTargetStatus() { return targetStatus; }
	public void setTargetStatus(ApplicationStatus targetStatus) { this.targetStatus = targetStatus; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public boolean isAllowOfferWithoutCompletedInterview() { return allowOfferWithoutCompletedInterview; }
	public void setAllowOfferWithoutCompletedInterview(boolean allowOfferWithoutCompletedInterview) {
		this.allowOfferWithoutCompletedInterview = allowOfferWithoutCompletedInterview;
	}
}
