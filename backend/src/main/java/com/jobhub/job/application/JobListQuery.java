package com.jobhub.job.application;

import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.domain.JobStatus;

public record JobListQuery(
		String query,
		JobDecisionStatus decisionStatus,
		JobStatus jobStatus,
		int page,
		int pageSize
) { }
