package com.jobhub.job.application;

import java.util.List;

public record JobListResult(List<JobListItem> items, long total, int page, int pageSize) {
	public int totalPages() {
		return (int) Math.ceil((double) total / pageSize);
	}
}
