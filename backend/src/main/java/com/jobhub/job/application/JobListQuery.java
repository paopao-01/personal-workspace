package com.jobhub.job.application;

import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.domain.JobStatus;

public record JobListQuery(
		String query,
		JobDecisionStatus decisionStatus,
		JobStatus jobStatus,
		String location,
		String source,
		Boolean hasPendingRequirements,
		int page,
		int pageSize
) { }
