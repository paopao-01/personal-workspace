package com.jobhub.ai.api;

import com.jobhub.ai.domain.AiJob;

public record AiJobResponse(
	String id,
	String jobType,
	String objectId,
	String status,
	String providerType,
	String model,
	String promptVersion,
	int attemptCount,
	String failureReason,
	java.util.List<AiJobItemResponse> items,
	String startedAt,
	String finishedAt,
	String createdAt,
	String updatedAt
) {
	public static AiJobResponse from(AiJob job) {
		return new AiJobResponse(
			job.getId(),
			job.getJobType().name(),
			job.getObjectId(),
			job.getStatus().name(),
			job.getProviderType(),
			job.getModel(),
			job.getPromptVersion(),
			job.getAttemptCount(),
			job.getFailureReason(),
			job.getItems().stream().map(AiJobItemResponse::from).toList(),
			job.getStartedAt(),
			job.getFinishedAt(),
			job.getCreatedAt(),
			job.getUpdatedAt()
		);
	}
}
