package com.jobhub.ai.domain;

import java.util.List;

/**
 * AI 候选条目载荷。不同任务类型复用同一可编辑 JSON 结构；所有条目都必须经过人工采纳。
 */
public record AiItemPayload(String type, String rawText, String normalizedName, String proficiencyText,
		String rationale) {
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
