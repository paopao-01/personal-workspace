package com.jobhub.task.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskFromQuestionRequest(
	@NotBlank String mode,
	String existingTaskId,
	@Size(max = 200) String title,
	String dueAt,
	@Size(max = 5000) String acceptanceCriteria,
	@Size(max = 1000) String verificationMethod
) { }
