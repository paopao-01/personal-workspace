package com.jobhub.job.application;

import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.JobRequirement;

public record GapItem(
		JobRequirement requirement,
		GapStatus status,
		String manualOverrideReason
) { }
