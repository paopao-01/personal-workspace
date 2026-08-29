package com.jobhub.task.api;

import com.jobhub.task.application.TaskListResult;
import java.util.List;

public record PageTaskResponse(
	List<LearningTaskResponse> items,
	int page,
	int pageSize,
	long total
) {
	public static PageTaskResponse from(TaskListResult result) {
		return new PageTaskResponse(
			result.items().stream().map(LearningTaskResponse::from).toList(),
			result.page(),
			result.pageSize(),
			result.total()
		);
	}
}
