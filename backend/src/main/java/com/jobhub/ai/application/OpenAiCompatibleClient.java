package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 端点（覆盖 OpenAI / DeepSeek / Kimi / GLM / Qwen / Ollama 等）。
 * baseUrl 示例：https://api.deepseek.com/v1；本地 Ollama：http://127.0.0.1:11434/v1。
 */
@Component
public class OpenAiCompatibleClient implements AiChatClient {
	private static final ObjectMapper JSON = new ObjectMapper();

	@Override
	public String complete(AiProvider provider, String systemPrompt, String userPrompt) {
		var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(15));
		requestFactory.setReadTimeout(Duration.ofSeconds(180));
		RestClient client = RestClient.builder()
			.baseUrl(trimTrailingSlash(provider.getBaseUrl()))
			.requestFactory(requestFactory)
			.build();
		Map<String, Object> body = Map.of(
			"model", provider.getModel(),
			"messages", new Object[] {
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userPrompt)
			},
			"temperature", 0.2
		);
		String response = client.post()
			.uri("/chat/completions")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(String.class);
		return extractContent(response);
	}

	private String extractContent(String response) {
		try {
			JsonNode root = JSON.readTree(response);
			String content = root.path("choices").path(0).path("message").path("content").asText(null);
			if (content == null || content.isBlank()) {
				throw new IllegalStateException("供应商响应缺少 choices[0].message.content");
			}
			return content;
		} catch (IllegalStateException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException("供应商响应解析失败：" + ex.getMessage(), ex);
		}
	}

	private String trimTrailingSlash(String url) {
		return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
