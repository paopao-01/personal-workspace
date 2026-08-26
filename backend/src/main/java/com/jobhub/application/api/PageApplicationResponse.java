package com.jobhub.application.api;

import com.jobhub.application.domain.Application;

import java.util.List;

/**
 * 投递分页响应。含 totalPages（与 PageJob 一致，OpenAPI 已补此字段为细化非破坏性变更）。
 */
public record PageApplicationResponse(
		List<ApplicationResponse> items,
		long total,
		int page,
		int pageSize,
		int totalPages
) {
	public static PageApplicationResponse from(List<Application> items, long total, int page, int pageSize) {
		List<ApplicationResponse> dtos = items.stream().map(ApplicationResponse::from).toList();
		return new PageApplicationResponse(dtos, total, page, pageSize,
				(int) Math.ceil((double) total / pageSize));
	}
}
