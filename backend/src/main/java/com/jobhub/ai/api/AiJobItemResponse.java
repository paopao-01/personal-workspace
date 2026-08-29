package com.jobhub.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhub.ai.domain.AiJobItem;

/**
 * 条目载荷按 jobType 区分（JD_EXTRACTION：候选要求；RESUME_DRAFT：简历建议），
 * 以原始 JSON 透传，由前端按任务类型解读。
 */
public record AiJobItemResponse(
	String id,
	String aiJobId,
	JsonNode payload,
	JsonNode editedPayload,
	String status,
	String requirementId,
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
			item.getCreatedAt(),
			item.getUpdatedAt()
		);
	}

	private static JsonNode parse(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return null;
		}
		try {
			return JSON.readTree(payloadJson);
		} catch (Exception ex) {
			return null;
		}
	}
}
