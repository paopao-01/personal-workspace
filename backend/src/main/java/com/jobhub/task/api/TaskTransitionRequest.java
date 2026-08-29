package com.jobhub.task.api;

import com.jobhub.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskTransitionRequest(
	@NotNull TaskStatus targetStatus,
	@Size(max = 5000) String verificationResult,
	@Size(max = 1000) String note
) { }
