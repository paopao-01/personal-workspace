package com.jobhub.ai.domain;

/**
 * AI 供应商配置（PRD 9.2 前置）：可随时新增与切换激活供应商；api_key 仅存本地库，
 * 不导出、不回显（同邮件渠道凭据约定）。
 */
public class AiProvider {
	private String id;
	private ProviderType providerType;
	private String name;
	private String baseUrl;
	private String model;
	private String apiKey;
	private boolean active;
	private String createdAt;
	private String updatedAt;
	private long version;

	public static AiProvider create(String id, ProviderType providerType, String name, String baseUrl, String model,
			String apiKey, String now) {
		AiProvider provider = new AiProvider();
		provider.id = id;
		provider.providerType = providerType;
		provider.name = name;
		provider.baseUrl = baseUrl;
		provider.model = model;
		provider.apiKey = apiKey;
		provider.createdAt = now;
		provider.updatedAt = now;
		return provider;
	}

	public void update(String name, String baseUrl, String model, String apiKey, String now) {
		this.name = name;
		this.baseUrl = baseUrl;
		this.model = model;
		if (apiKey != null) {
			this.apiKey = apiKey;
		}
		this.updatedAt = now;
	}

	public void activate(String now) {
		this.active = true;
		this.updatedAt = now;
	}

	public void deactivate(String now) {
		this.active = false;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public ProviderType getProviderType() { return providerType; }
	public String getName() { return name; }
	public String getBaseUrl() { return baseUrl; }
	public String getModel() { return model; }
	public String getApiKey() { return apiKey; }
	public boolean isActive() { return active; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
