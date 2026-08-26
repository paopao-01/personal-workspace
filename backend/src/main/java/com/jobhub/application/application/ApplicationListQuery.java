package com.jobhub.application.application;

import com.jobhub.application.domain.ApplicationStatus;

/**
 * 投递列表查询参数。overdueActionOnly=true 仅返回下一步行动逾期的投递（dashboard 与筛选共用）。
 */
public record ApplicationListQuery(
		ApplicationStatus status,
		Boolean overdueActionOnly,
		int page,
		int pageSize
) { }
