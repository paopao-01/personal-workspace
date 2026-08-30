package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.review.domain.AnswerStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Generates one editable answer-quality candidate without changing the user's answer. */
@Component
public class AnswerQualityAnalysisHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "ANSWER_QUALITY_ANALYSIS_V1";

	@Override
	public AiJobType type() {
		return AiJobType.ANSWER_QUALITY_ANALYSIS;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public String buildSystemPrompt() {
		return """
			你是面试复盘助手。根据输入 JSON 中的面试问题、用户原回答和可选参考答案，生成一个回答质量分析候选。
			只输出 JSON 数组，不要输出其他文字或 Markdown。数组只能有一个元素：
			{"type":"ANSWER_QUALITY","rawText":"总体评价","normalizedName":"回答质量分析","answerStatus":"PARTIALLY_ANSWERED","referenceAnswer":"建议参考答案","errorReason":"主要问题","improvementPlan":"可执行改进方案","rationale":"判断依据"}
			type 必须是 ANSWER_QUALITY；answerStatus 只能是 FULLY_ANSWERED、PARTIALLY_ANSWERED、UNANSWERED。
			不得改写或补充用户原回答，不得虚构用户经历、项目事实或量化结果；信息不足时明确说明。""";
	}

	@Override
	public List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client) {
		List<AiItemPayload> parsed = AiItemPayload.parseList(
			client.complete(provider, buildSystemPrompt(), job.getInputSnapshot()));
		if (parsed.size() != 1 || parsed.get(0) == null) {
			throw new IllegalStateException("模型未返回有效的回答质量分析候选");
		}
		AiItemPayload payload = parsed.get(0);
		if (!"ANSWER_QUALITY".equals(payload.type()) || payload.rawText() == null
				|| payload.rawText().isBlank() || payload.answerStatus() == null) {
			throw new IllegalStateException("模型返回的回答质量分析候选无效");
		}
		return List.of(new AiItemPayload(
			"ANSWER_QUALITY",
			payload.rawText().trim(),
			payload.normalizedName() == null || payload.normalizedName().isBlank()
				? "回答质量分析" : payload.normalizedName().trim(),
			null,
			trimToNull(payload.rationale()),
			payload.answerStatus(),
			trimToNull(payload.referenceAnswer()),
			trimToNull(payload.errorReason()),
			trimToNull(payload.improvementPlan())));
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
