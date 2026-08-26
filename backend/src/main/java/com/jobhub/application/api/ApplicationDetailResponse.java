package com.jobhub.application.api;

import com.jobhub.application.application.ApplicationService;
import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.StatusLogEntry;
import com.jobhub.job.api.JobResponse;
import com.jobhub.job.domain.Job;

import java.util.List;

/**
 * 投递详情响应。与 OpenAPI ApplicationDetail（allOf Application + job + statusHistory + interviews）对齐。
 * interviews 由面试模块按 applicationId 聚合查询。
 */
public record ApplicationDetailResponse(
		String id,
		String jobId,
		String status,
		String previousActiveStatus,
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
		String updatedAt,
		JobResponse job,
		List<StatusLogResponse> statusHistory,
		List<Object> interviews
) {
	public static ApplicationDetailResponse from(ApplicationService.ApplicationDetail detail) {
		Application app = detail.application();
		Job job = detail.job();
		return new ApplicationDetailResponse(
				app.getId(),
				app.getJobId(),
				app.getStatus() != null ? app.getStatus().name() : null,
				app.getPreviousActiveStatus() != null ? app.getPreviousActiveStatus().name() : null,
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
				app.getUpdatedAt(),
				JobResponse.from(job),
				StatusLogResponse.fromList(detail.statusHistory()),
				List.of()
		);
	}

	/** 接受外部 interviews 列表（预留面试模块）。 */
	public static ApplicationDetailResponse from(ApplicationService.ApplicationDetail detail,
												List<?> interviews) {
		ApplicationDetailResponse base = from(detail);
		return new ApplicationDetailResponse(
				base.id, base.jobId, base.status, base.previousActiveStatus,
				base.appliedAt, base.channel, base.resumeVersion, base.expectedSalary,
				base.contact, base.nextAction, base.nextActionDueAt, base.rejectionReason,
				base.notes, base.version, base.createdAt, base.updatedAt,
				base.job, base.statusHistory, List.copyOf(interviews));
	}
}
