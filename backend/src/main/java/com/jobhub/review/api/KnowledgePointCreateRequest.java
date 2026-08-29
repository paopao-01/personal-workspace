package com.jobhub.review.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgePointCreateRequest(
	@NotBlank @Size(max = 100) String name,
	@Size(max = 100) String category
) { }
