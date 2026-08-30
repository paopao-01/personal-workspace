package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import org.springframework.stereotype.Component;
import java.util.List;

/** Generates one editable candidate draft without adding unconfirmed facts. */
@Component
public class ResumeDraftHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "RESUME_DRAFT_V1";
	@Override public AiJobType type() { return AiJobType.RESUME_DRAFT; }
	@Override public String promptVersion() { return PROMPT_VERSION; }
	@Override public String buildSystemPrompt() {
		return """
			你是简历编辑助手。根据已确认简历和岗位描述生成一份可编辑的定制简历草稿。
			只输出 JSON 数组，不要输出其他文字或 Markdown。数组只能有一个元素：{"type":"DRAFT","rawText":"完整草稿"}。
			只能重写和重排输入中已有的经历、技能、项目和证据；不得新增未确认的项目、职责、指标、技术栈或任职经历。无法确认的内容不要补写。
			""";
	}
	@Override public List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client) {
		List<AiItemPayload> result = AiItemPayload.parseList(client.complete(provider, buildSystemPrompt(), job.getInputSnapshot()));
		if (result.size() != 1 || result.get(0) == null || result.get(0).rawText() == null || result.get(0).rawText().isBlank()) {
			throw new IllegalStateException("模型未返回有效的简历草稿");
		}
		return List.of(new AiItemPayload("DRAFT", result.get(0).rawText().trim(), "简历定制草稿", null, null,
			null, null, null, null));
	}
}
