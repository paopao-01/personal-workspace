package com.jobhub.ai.api;

import com.jobhub.ai.domain.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 供应商创建/更新请求。apiKey 仅写入；更新时省略/null 表示保留既有 key。
 */
public record AiProviderUpsertRequest(
	@NotNull ProviderType providerType,
	@NotBlank String name,
	@NotBlank String baseUrl,
	@NotBlank String model,
	String apiKey
) { }
