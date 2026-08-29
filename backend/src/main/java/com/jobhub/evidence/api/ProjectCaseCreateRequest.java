package com.jobhub.evidence.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProjectCaseCreateRequest(
	@NotBlank @Size(max = 200) String title,
	@NotBlank @Size(max = 3000) String scenario,
	@NotBlank @Size(max = 3000) String approach,
	@NotBlank @Size(max = 3000) String problemSolved,
	@Size(max = 2000) String result,
	List<String> evidenceIds
) { }
