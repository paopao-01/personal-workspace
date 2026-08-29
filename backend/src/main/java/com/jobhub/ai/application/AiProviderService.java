package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.ProviderType;
import com.jobhub.ai.infrastructure.AiProviderMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.VersionConflictException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 供应商配置：可新增多个供应商并随时切换激活（同一时刻仅一个激活）。
 * api_key 仅写入不回显；更新时省略/null 表示保留既有 key。
 */
@Service
public class AiProviderService {
	private final AiProviderMapper providerMapper;
	private final AiClientFactory clientFactory;
	private final IdGenerator ids;
	private final UtcTime time;

	public AiProviderService(AiProviderMapper providerMapper, AiClientFactory clientFactory, IdGenerator ids,
			UtcTime time) {
		this.providerMapper = providerMapper;
		this.clientFactory = clientFactory;
		this.ids = ids;
		this.time = time;
	}

	public List<AiProvider> list() {
		return providerMapper.selectAll();
	}

	public AiProvider get(String id) {
		return requireProvider(id);
	}

	@Transactional
	public AiProvider create(ProviderType providerType, String name, String baseUrl, String model, String apiKey) {
		validate(providerType, baseUrl, model);
		AiProvider provider = AiProvider.create(ids.newId(), providerType, name.trim(), baseUrl.trim(), model.trim(),
			apiKey == null || apiKey.isBlank() ? null : apiKey.trim(), time.now());
		providerMapper.insert(provider);
		// 首个供应商自动激活
		if (providerMapper.selectActive() == null) {
			provider.activate(time.now());
			providerMapper.updateActive(provider);
		}
		return requireProvider(provider.getId());
	}

	@Transactional
	public AiProvider update(String id, long expectedVersion, ProviderType providerType, String name, String baseUrl,
			String model, String apiKey) {
		AiProvider provider = requireProvider(id);
		validate(providerType, baseUrl, model);
		provider.update(name.trim(), baseUrl.trim(), model.trim(),
			apiKey == null || apiKey.isBlank() ? null : apiKey.trim(), time.now());
		if (providerMapper.update(provider, expectedVersion) == 0) {
			throw new VersionConflictException(provider.getVersion());
		}
		return requireProvider(id);
	}

	/** 切换激活供应商：新任务一律使用激活供应商（用户要求可随时切换）。 */
	@Transactional
	public AiProvider activate(String id) {
		AiProvider provider = requireProvider(id);
		String now = time.now();
		providerMapper.deactivateOthers(id, now);
		provider.activate(now);
		providerMapper.updateActive(provider);
		return requireProvider(id);
	}

	/** 连通性测试：发送最小补全请求，返回延迟与结果说明。 */
	public AiProviderTestResult test(String id) {
		AiProvider provider = requireProvider(id);
		long start = System.currentTimeMillis();
		try {
			clientFactory.clientFor(provider.getProviderType())
				.complete(provider, "你是连通性测试助手。", "请回复：OK");
			return new AiProviderTestResult(true, (int) (System.currentTimeMillis() - start), null);
		} catch (Exception ex) {
			String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (message.length() > 500) {
				message = message.substring(0, 500);
			}
			return new AiProviderTestResult(false, (int) (System.currentTimeMillis() - start), message);
		}
	}

	private void validate(ProviderType providerType, String baseUrl, String model) {
		if (baseUrl == null || baseUrl.isBlank() || !baseUrl.startsWith("http")) {
			throw new BusinessRuleException("base_url 必须是以 http(s) 开头的完整地址");
		}
		if (model == null || model.isBlank()) {
			throw new BusinessRuleException("model 不能为空");
		}
	}

	private AiProvider requireProvider(String id) {
		AiProvider provider = providerMapper.selectById(id);
		VersionCheck.requireFound(provider, "AiProvider", id);
		return provider;
	}

	public record AiProviderTestResult(boolean ok, Integer latencyMs, String message) { }
}
