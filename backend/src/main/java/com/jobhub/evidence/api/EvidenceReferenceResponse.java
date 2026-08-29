package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.Evidence;

public record EvidenceReferenceResponse(String id, String type, String title, String urlOrPath) {
	public static EvidenceReferenceResponse fromEvidence(Evidence evidence) {
		return new EvidenceReferenceResponse(evidence.getId(),
			evidence.getType() == null ? null : evidence.getType().name(),
			evidence.getTitle(), evidence.getUrlOrPath());
	}
}
