package com.jobhub.job.api;

import com.jobhub.job.application.ExtractionResult;
import com.jobhub.job.domain.JobRequirement;

import java.util.List;

public record RequirementExtractionResultResponse(
		String jobId,
		String extractedAt,
		List<JobRequirementResponse> candidates,
		int newCount
) {
	public static RequirementExtractionResultResponse from(ExtractionResult result, String jobId, String extractedAt) {
		List<JobRequirementResponse> dtos = result.candidates().stream()
				.map(JobRequirementResponse::from).toList();
		return new RequirementExtractionResultResponse(jobId, extractedAt, dtos, result.newCount());
	}
}
