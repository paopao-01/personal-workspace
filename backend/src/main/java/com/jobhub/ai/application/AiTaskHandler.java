package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;

import java.util.List;

/**
 * AI 任务处理器扩展点：每种 job_type 提供提示词与输出解析。
 * 后续简历定制（PRD 9.4）等任务在此注册新实现。
 */
public interface AiTaskHandler {
	AiJobType type();

	String promptVersion();

	String buildSystemPrompt();

	List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client);
}
