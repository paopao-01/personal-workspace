package com.jobhub.job.application;

import com.jobhub.job.domain.JobDecisionStatus;

public record JobCreateCommand(
		String companyName,
		String title,
		String jdRawText,
		String source,
		String sourceUrl,
		String location,
		String salaryRange,
		String notes
) { }
