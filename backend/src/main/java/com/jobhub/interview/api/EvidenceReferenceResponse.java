package com.jobhub.interview.api;

import com.jobhub.interview.application.EvidenceReference;

public record EvidenceReferenceResponse(String id, String type, String title, String urlOrPath) {
	public static EvidenceReferenceResponse from(EvidenceReference evidence) {
		return new EvidenceReferenceResponse(evidence.getId(), evidence.getType(), evidence.getTitle(), evidence.getUrlOrPath());
	}
}
