package com.jobhub.job.application;

import com.jobhub.job.domain.Job;

import java.util.List;

public record JobListResult(List<Job> items, long total, int page, int pageSize) {
	public int totalPages() {
		return (int) Math.ceil((double) total / pageSize);
	}
}
