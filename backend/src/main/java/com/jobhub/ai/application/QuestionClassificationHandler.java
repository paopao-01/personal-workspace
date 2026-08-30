package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.AiQuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Generates one editable interview-question category candidate. */
@Component
public class QuestionClassificationHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "QUESTION_CLASSIFICATION_V1";

	@Override
	public AiJobType type() {
		return AiJobType.QUESTION_CLASSIFICATION;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public String buildSystemPrompt() {
		return """
			你是面试复盘助手。根据给定的面试问题，为它推荐一个最合适的分类。
			只输出 JSON 数组，不要输出其他文字或 Markdown。数组只能有一个元素：
			{"type":"TECHNICAL","rawText":"原问题","normalizedName":"分类名称","rationale":"分类理由"}
			type 只能是 TECHNICAL、PROJECT_EXPERIENCE、SYSTEM_DESIGN、BEHAVIORAL、DOMAIN、OTHER。
			不得改变或补充问题内容；无法判断时使用 OTHER。""";
	}

	@Override
	public List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client) {
		List<AiItemPayload> parsed = AiItemPayload.parseList(
			client.complete(provider, buildSystemPrompt(), job.getInputSnapshot()));
		if (parsed.size() != 1 || parsed.get(0) == null) {
			throw new IllegalStateException("模型未返回有效的问题分类候选");
		}
		AiItemPayload payload = parsed.get(0);
		if (payload.type() == null || !isCategory(payload.type())
				|| payload.rawText() == null || payload.rawText().isBlank()) {
			throw new IllegalStateException("模型返回的问题分类候选无效");
		}
		return List.of(new AiItemPayload(
			payload.type(),
			payload.rawText().trim(),
			payload.normalizedName() == null || payload.normalizedName().isBlank()
				? payload.type() : payload.normalizedName().trim(),
			null,
			payload.rationale() == null ? null : payload.rationale().trim()));
	}

	private boolean isCategory(String value) {
		try {
			AiQuestionCategory.valueOf(value);
			return true;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}
}
