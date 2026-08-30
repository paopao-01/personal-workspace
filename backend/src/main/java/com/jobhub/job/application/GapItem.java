package com.jobhub.job.application;

import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.JobRequirement;
import java.util.List;

public record GapItem(
		JobRequirement requirement,
		GapStatus status,
		List<GapEvidence> evidence,
		String manualOverrideReason
) { }
