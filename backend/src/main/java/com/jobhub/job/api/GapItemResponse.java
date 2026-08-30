package com.jobhub.job.api;

import com.jobhub.job.application.GapItem;
import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.JobRequirement;

import java.util.List;

public record GapItemResponse(
		JobRequirementResponse requirement,
		GapStatus status,
		List<GapEvidenceResponse> evidence,
		String manualOverrideReason
) {
	public static GapItemResponse from(GapItem item) {
		JobRequirement req = item.requirement();
		return new GapItemResponse(
				JobRequirementResponse.from(req),
				item.status(),
				item.evidence().stream().map(GapEvidenceResponse::from).toList(),
				item.manualOverrideReason()
		);
	}

	public record GapEvidenceResponse(String id, String type, String title, String urlOrPath, boolean trashed) {
		static GapEvidenceResponse from(com.jobhub.job.application.GapEvidence evidence) {
			return new GapEvidenceResponse(evidence.getId(), evidence.getType(), evidence.getTitle(), evidence.getUrlOrPath(), false);
		}
	}
}
