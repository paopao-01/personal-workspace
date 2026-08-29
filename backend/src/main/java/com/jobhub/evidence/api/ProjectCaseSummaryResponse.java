package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.ProjectCase;
import java.util.List;

public record ProjectCaseSummaryResponse(
	String id,
	String title,
	String scenario,
	String approach,
	String problemSolved,
	String result,
	List<EvidenceReferenceResponse> evidenceRefs,
	long version
) {
	public static ProjectCaseSummaryResponse from(ProjectCase project) {
		return new ProjectCaseSummaryResponse(
			project.getId(),
			project.getTitle(),
			project.getScenario(),
			project.getApproach(),
			project.getProblemSolved(),
			project.getResultText(),
			project.getEvidenceRefs().stream().map(EvidenceReferenceResponse::fromEvidence).toList(),
			project.getVersion()
		);
	}
}
