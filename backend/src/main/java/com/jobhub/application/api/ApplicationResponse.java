package com.jobhub.application.api;

import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.ApplicationStatus;

/**
 * 投递响应。与 OpenAPI Application schema 对齐。
 */
public record ApplicationResponse(
		String id,
		String jobId,
		ApplicationStatus status,
		ApplicationStatus previousActiveStatus,
		String appliedAt,
		String channel,
		String resumeVersion,
		String expectedSalary,
		String contact,
		String nextAction,
		String nextActionDueAt,
		String rejectionReason,
		String notes,
		long version,
		String createdAt,
		String updatedAt
) {
	public static ApplicationResponse from(Application app) {
		if (app == null) return null;
		return new ApplicationResponse(
				app.getId(),
				app.getJobId(),
				app.getStatus(),
				app.getPreviousActiveStatus(),
				app.getAppliedAt(),
				app.getChannel(),
				app.getResumeVersion(),
				app.getExpectedSalary(),
				app.getContact(),
				app.getNextAction(),
				app.getNextActionDueAt(),
				app.getRejectionReason(),
				app.getNotes(),
				app.getVersion(),
				app.getCreatedAt(),
				app.getUpdatedAt()
		);
	}
}
