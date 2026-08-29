package com.jobhub.task.application;

public record CreateTaskFromQuestionCommand(
	String mode,
	String existingTaskId,
	String title,
	String dueAt,
	String acceptanceCriteria,
	String verificationMethod
) { }
