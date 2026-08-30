package com.jobhub.job.api;

import com.jobhub.job.application.JobListItem;

import java.util.List;

public record PageJobResponse(List<JobListItemResponse> items, long total, int page, int pageSize, int totalPages) {
	public static PageJobResponse from(List<JobListItem> items, long total, int page, int pageSize) {
		List<JobListItemResponse> dtos = items.stream().map(JobListItemResponse::from).toList();
		return new PageJobResponse(dtos, total, page, pageSize, (int) Math.ceil((double) total / pageSize));
	}

	public record JobListItemResponse(JobResponse job, long confirmedRequirementCount, long pendingRequirementCount,
			String gapOverview, boolean hasActiveApplication) {
		static JobListItemResponse from(JobListItem item) {
			return new JobListItemResponse(JobResponse.from(item.job()), item.confirmedRequirementCount(),
					item.pendingRequirementCount(), item.gapOverview(), item.hasActiveApplication());
		}
	}
}
