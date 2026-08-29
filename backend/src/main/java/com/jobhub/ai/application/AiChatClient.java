package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiProvider;

/**
 * AI 供应商抽象（PRD 9.2 前置，用户要求可随时切换供应商）。
 * 实现按 provider_type 路由：OPENAI_COMPATIBLE（OpenAI/DeepSeek/Kimi/GLQ/Ollama 等兼容端点）、ANTHROPIC。
 */
public interface AiChatClient {
	String complete(AiProvider provider, String systemPrompt, String userPrompt);
}
