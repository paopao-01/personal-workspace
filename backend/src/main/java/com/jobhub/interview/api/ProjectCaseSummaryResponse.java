package com.jobhub.interview.api;

import com.jobhub.interview.application.ProjectCaseSummary;
import java.util.List;

public record ProjectCaseSummaryResponse(
	String id,
	String title,
	String scenario,
	String approach,
	String problemSolved,
	List<EvidenceReferenceResponse> evidenceRefs,
	long version
) {
	public static ProjectCaseSummaryResponse from(ProjectCaseSummary project) {
		return new ProjectCaseSummaryResponse(
			project.getId(),
			project.getTitle(),
			project.getScenario(),
			project.getApproach(),
			project.getProblemSolved(),
			project.getEvidenceRefs().stream().map(EvidenceReferenceResponse::from).toList(),
			project.getVersion()
		);
	}
}
