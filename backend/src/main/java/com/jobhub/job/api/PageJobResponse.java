package com.jobhub.job.api;

import com.jobhub.job.domain.Job;

import java.util.List;

public record PageJobResponse(List<JobResponse> items, long total, int page, int pageSize, int totalPages) {
	public static PageJobResponse from(List<Job> items, long total, int page, int pageSize) {
		List<JobResponse> dtos = items.stream().map(JobResponse::from).toList();
		return new PageJobResponse(dtos, total, page, pageSize, (int) Math.ceil((double) total / pageSize));
	}
}
