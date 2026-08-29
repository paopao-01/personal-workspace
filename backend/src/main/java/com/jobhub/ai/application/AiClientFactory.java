package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.ProviderType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 按 provider_type 路由到对应客户端；新增供应商类型时在此注册实现即可。 */
@Component
public class AiClientFactory {
	private final Map<ProviderType, AiChatClient> clients;

	public AiClientFactory(OpenAiCompatibleClient openAiCompatibleClient, AnthropicClient anthropicClient) {
		this.clients = Map.of(
			ProviderType.OPENAI_COMPATIBLE, openAiCompatibleClient,
			ProviderType.ANTHROPIC, anthropicClient);
	}

	public AiChatClient clientFor(ProviderType providerType) {
		AiChatClient client = clients.get(providerType);
		if (client == null) {
			throw new IllegalArgumentException("不支持的供应商类型：" + providerType);
		}
		return client;
	}
}
