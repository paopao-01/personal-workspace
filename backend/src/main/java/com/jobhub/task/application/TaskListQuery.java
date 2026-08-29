package com.jobhub.task.application;

import com.jobhub.task.domain.TaskStatus;

public record TaskListQuery(TaskStatus status, int page, int pageSize) { }
