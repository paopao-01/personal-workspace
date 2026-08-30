package com.jobhub.ai.api;

import com.jobhub.ai.domain.AiJobItem;
import com.jobhub.ai.domain.AiItemPayload;

public record AiJobItemResponse(
	String id,
	String aiJobId,
	AiItemPayload payload,
	AiItemPayload editedPayload,
	String status,
	String requirementId,
	String taskId,
	String createdAt,
	String updatedAt
) {
	private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
			new com.fasterxml.jackson.databind.ObjectMapper();

	public static AiJobItemResponse from(AiJobItem item) {
		return new AiJobItemResponse(
			item.getId(),
			item.getAiJobId(),
			parse(item.getPayloadJson()),
			parse(item.getEditedPayloadJson()),
			item.getStatus().name(),
			item.getRequirementId(),
			item.getTaskId(),
			item.getCreatedAt(),
			item.getUpdatedAt()
		);
	}

	private static AiItemPayload parse(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return null;
		}
		try {
			return JSON.readValue(payloadJson, AiItemPayload.class);
		} catch (Exception ex) {
			return null;
		}
	}
}
