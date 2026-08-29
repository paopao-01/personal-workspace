package com.jobhub.interview.api;

import com.jobhub.interview.application.SourceRef;

public record SourceRefResponse(String type, String id, String label) {
	public static SourceRefResponse from(SourceRef ref) {
		return new SourceRefResponse(ref.type(), ref.id(), ref.label());
	}
}
