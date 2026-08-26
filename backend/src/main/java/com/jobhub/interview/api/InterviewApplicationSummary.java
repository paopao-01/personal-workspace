package com.jobhub.interview.api;

import com.jobhub.application.domain.ApplicationStatus;
import com.jobhub.interview.domain.InterviewListItem;

/**
 * 面试中心列表中关联投递和岗位的精简上下文，不替代投递详情。
 */
public record InterviewApplicationSummary(
		String id,
		String jobId,
		ApplicationStatus status,
		String companyName,
		String jobTitle
) {
	public static InterviewApplicationSummary from(InterviewListItem item) {
		return new InterviewApplicationSummary(
				item.getApplicationId(), item.getJobId(), item.getApplicationStatus(),
				item.getCompanyName(), item.getJobTitle());
	}
}
