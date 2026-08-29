package com.jobhub.ai.domain;

import java.util.List;

/**
 * AI 候选条目载荷。type 仅 MUST/BONUS（与岗位要求类型对应），由解析阶段校验。
 */
public record AiItemPayload(String type, String rawText, String normalizedName, String proficiencyText) {
	public static final int MAX_ITEMS = 20;

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
