package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EvidenceCreateRequest(
	@NotNull EvidenceType type,
	@NotBlank @Size(max = 200) String title,
	@Size(max = 2000) String whereUsed,
	@Size(max = 2000) String problemSolved,
	@Size(max = 3000) String approach,
	@Size(max = 2000) String result,
	@Size(max = 2000) String urlOrPath,
	List<String> skillIds
) { }
