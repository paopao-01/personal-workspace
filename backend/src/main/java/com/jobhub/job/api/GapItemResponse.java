package com.jobhub.job.api;

import com.jobhub.job.application.GapItem;
import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.JobRequirement;

import java.util.List;

public record GapItemResponse(
		JobRequirementResponse requirement,
		GapStatus status,
		List<Object> evidence,
		String manualOverrideReason
) {
	public static GapItemResponse from(GapItem item) {
		JobRequirement req = item.requirement();
		return new GapItemResponse(
				JobRequirementResponse.from(req),
				item.status(),
				List.of(),
				item.manualOverrideReason()
		);
	}
}
