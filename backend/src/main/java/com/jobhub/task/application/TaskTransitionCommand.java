package com.jobhub.task.application;

import com.jobhub.task.domain.TaskStatus;

public record TaskTransitionCommand(TaskStatus targetStatus, String verificationResult, String note) { }
