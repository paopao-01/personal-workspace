package com.jobhub.ai.api;

import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.ProviderType;

/**
 * 供应商响应。api_key 永不回显；是否已设置凭据见 hasCredential。
 */
public record AiProviderResponse(
	String id,
	String providerType,
	String name,
	String baseUrl,
	String model,
	boolean hasCredential,
	boolean isActive,
	long version
) {
	public static AiProviderResponse from(AiProvider provider) {
		return new AiProviderResponse(
			provider.getId(),
			provider.getProviderType().name(),
			provider.getName(),
			provider.getBaseUrl(),
			provider.getModel(),
			provider.getApiKey() != null && !provider.getApiKey().isBlank(),
			provider.isActive(),
			provider.getVersion()
		);
	}
}
