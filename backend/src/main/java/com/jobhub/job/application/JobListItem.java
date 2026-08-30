package com.jobhub.job.application;

import com.jobhub.job.domain.Job;

public record JobListItem(Job job, long confirmedRequirementCount, long pendingRequirementCount,
		String gapOverview, boolean hasActiveApplication) { }
