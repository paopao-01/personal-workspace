package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API。baseUrl 示例：https://api.anthropic.com。
 */
@Component
public class AnthropicClient implements AiChatClient {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String ANTHROPIC_VERSION = "2023-06-01";

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
			"max_tokens", 4096,
			"system", systemPrompt,
			"messages", List.of(Map.of("role", "user", "content", userPrompt))
		);
		String response = client.post()
			.uri("/v1/messages")
			.header("x-api-key", provider.getApiKey())
			.header("anthropic-version", ANTHROPIC_VERSION)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(String.class);
		return extractContent(response);
	}

	private String extractContent(String response) {
		try {
			JsonNode root = JSON.readTree(response);
			String content = root.path("content").path(0).path("text").asText(null);
			if (content == null || content.isBlank()) {
				throw new IllegalStateException("供应商响应缺少 content[0].text");
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
