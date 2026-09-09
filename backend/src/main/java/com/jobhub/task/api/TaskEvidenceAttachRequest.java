package com.jobhub.task.api;

import jakarta.validation.constraints.NotBlank;

public record TaskEvidenceAttachRequest(
		@NotBlank String evidenceId
) { }
