package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiProvider;

import java.util.List;

/**
 * AI 任务处理器扩展点：每种 job_type 提供输入快照构建、提示词与输出解析。
 * 已注册：JD_EXTRACTION（岗位要求候选）、RESUME_DRAFT（简历定制建议，PRD 9.4）。
 */
public interface AiTaskHandler {
	AiJobType type();

	String promptVersion();

	/** 任务创建时构建输入快照（审计 + 执行输入）。校验失败抛 BusinessRuleException。 */
	String buildInputSnapshot(String objectId);

	String buildSystemPrompt();

	/** 执行任务：调用供应商并解析为条目载荷列表（按任务类型区分结构）。 */
	List<?> execute(AiJob job, AiProvider provider, AiChatClient client);
}
