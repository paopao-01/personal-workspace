package com.jobhub.ai.api;

import com.jobhub.ai.domain.AiJobType;
import jakarta.validation.constraints.NotNull;

public record AiJobCreateRequest(
	@NotNull AiJobType jobType,
	@NotNull String objectId,
	String sourceText
) { }
