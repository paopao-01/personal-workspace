package com.jobhub.task.application;

import com.jobhub.task.domain.TaskStatus;
import com.jobhub.task.domain.TaskSourceType;

public record TaskListQuery(TaskStatus status, String knowledgePointId, TaskSourceType sourceType,
		String dueAfter, String dueBefore, String jobId, String interviewId, int page, int pageSize) { }
