package com.jobhub.skill.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillCreateRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 100) String category
) { }
