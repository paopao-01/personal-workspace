package com.jobhub.task.application;

import com.jobhub.task.domain.LearningTask;
import java.util.List;

public record TaskListResult(List<LearningTask> items, long total, int page, int pageSize) { }
