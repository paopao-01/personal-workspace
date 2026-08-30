package com.jobhub.ai.domain;

import com.jobhub.review.domain.AnswerStatus;
import java.util.List;

/**
 * AI 候选条目载荷。不同任务类型复用同一可编辑 JSON 结构；所有条目都必须经过人工采纳。
 */
public record AiItemPayload(String type, String rawText, String normalizedName, String proficiencyText,
		String rationale, AnswerStatus answerStatus, String referenceAnswer, String errorReason,
		String improvementPlan, String taskTitle, String priority, Integer estimatedMinutes,
		String learningGoal, String acceptanceCriteria, String verificationMethod, List<String> knowledgePointIds) {
	public static final int MAX_ITEMS = 20;

	/** Compatibility constructor for existing AI handlers. */
	public AiItemPayload(String type, String rawText, String normalizedName, String proficiencyText,
			String rationale, AnswerStatus answerStatus, String referenceAnswer, String errorReason,
			String improvementPlan) {
		this(type, rawText, normalizedName, proficiencyText, rationale, answerStatus, referenceAnswer,
			errorReason, improvementPlan, null, null, null, null, null, null, null);
	}

	public static List<AiItemPayload> parseList(String outputJson) {
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			return mapper.readValue(outputJson,
				mapper.getTypeFactory().constructCollectionType(List.class, AiItemPayload.class));
		} catch (Exception ex) {
			throw new IllegalArgumentException("输出 JSON 解析失败：" + ex.getMessage(), ex);
		}
	}
}
