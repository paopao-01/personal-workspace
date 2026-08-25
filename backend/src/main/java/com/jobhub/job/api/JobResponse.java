package com.jobhub.job.api;

import com.jobhub.job.domain.Job;
import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.domain.JobStatus;

public record JobResponse(
		String id,
		String companyName,
		String title,
		String jdRawText,
		String source,
		String sourceUrl,
		String location,
		String salaryRange,
		JobDecisionStatus decisionStatus,
		String decisionReason,
		JobStatus status,
		String notes,
		long version,
		String createdAt,
		String updatedAt
) {
	public static JobResponse from(Job job) {
		if (job == null) return null;
		return new JobResponse(
				job.getId(),
				job.getCompanyName(),
				job.getTitle(),
				job.getJdRawText(),
				job.getSource(),
				job.getSourceUrl(),
				job.getLocation(),
				job.getSalaryRange(),
				job.getDecisionStatus(),
				job.getDecisionReason(),
				job.getStatus(),
				job.getNotes(),
				job.getVersion(),
				job.getCreatedAt(),
				job.getUpdatedAt()
		);
	}
}
