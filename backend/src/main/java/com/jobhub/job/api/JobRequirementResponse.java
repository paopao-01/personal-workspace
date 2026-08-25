package com.jobhub.job.api;

import com.jobhub.job.domain.ConfirmationStatus;
import com.jobhub.job.domain.JobRequirement;
import com.jobhub.job.domain.RequirementSource;
import com.jobhub.job.domain.RequirementType;

public record JobRequirementResponse(
		String id,
		String jobId,
		String rawText,
		String normalizedName,
		RequirementType type,
		String proficiencyText,
		ConfirmationStatus confirmationStatus,
		RequirementSource source,
		int sortOrder,
		long version
) {
	public static JobRequirementResponse from(JobRequirement r) {
		if (r == null) return null;
		return new JobRequirementResponse(
				r.getId(),
				r.getJobId(),
				r.getRawText(),
				r.getNormalizedName(),
				r.getType(),
				r.getProficiencyText(),
				r.getConfirmationStatus(),
				r.getSource(),
				r.getSortOrder(),
				r.getVersion()
		);
	}
}
