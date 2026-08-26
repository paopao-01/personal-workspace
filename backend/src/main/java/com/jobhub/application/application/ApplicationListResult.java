package com.jobhub.application.application;

import com.jobhub.application.domain.Application;

import java.util.List;

public record ApplicationListResult(List<Application> items, long total, int page, int pageSize) {
	public int totalPages() {
		return (int) Math.ceil((double) total / pageSize);
	}
}
