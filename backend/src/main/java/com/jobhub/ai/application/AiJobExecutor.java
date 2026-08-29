package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobItem;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.ProviderType;
import com.jobhub.ai.infrastructure.AiJobItemMapper;
import com.jobhub.ai.infrastructure.AiJobMapper;
import com.jobhub.ai.infrastructure.AiProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 任务异步执行器：单线程顺序执行，避免本地单用户场景的并发开销。
 * 状态转移以单次 WHERE 守卫（QUEUED -> RUNNING、RUNNING -> SUCCEEDED/FAILED），
 * 用户取消（RUNNING -> CANCELED）后完成转移不生效，结果丢弃。
 */
@Component
public class AiJobExecutor {
	private static final Logger log = LoggerFactory.getLogger(AiJobExecutor.class);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final int MAX_REASON_LENGTH = 500;

	private final AiJobMapper jobMapper;
	private final AiJobItemMapper itemMapper;
	private final AiProviderMapper providerMapper;
	private final AiClientFactory clientFactory;
	private final List<AiTaskHandler> handlers;
	private final ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ai-job-executor");
		thread.setDaemon(true);
		return thread;
	});

	public AiJobExecutor(AiJobMapper jobMapper, AiJobItemMapper itemMapper, AiProviderMapper providerMapper,
			AiClientFactory clientFactory, List<AiTaskHandler> handlers) {
		this.jobMapper = jobMapper;
		this.itemMapper = itemMapper;
		this.providerMapper = providerMapper;
		this.clientFactory = clientFactory;
		this.handlers = handlers;
	}

	public void submit(String aiJobId) {
		pool.submit(() -> run(aiJobId));
	}

	void run(String aiJobId) {
		AiJob job = jobMapper.selectById(aiJobId);
		if (job == null) {
			return;
		}
		String now = Instant.now().toString();
		if (jobMapper.markRunning(aiJobId, now) == 0) {
			return; // 已被取消或并发处理
		}
		try {
			AiTaskHandler handler = handlers.stream()
				.filter(h -> h.type() == job.getJobType())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("无任务处理器：" + job.getJobType()));
			AiProvider provider = requireProvider(job.getProviderId());
			List<?> payloads = handler.execute(job, provider,
				clientFactory.clientFor(provider.getProviderType()));

			StringBuilder outputBuilder = new StringBuilder("[");
			for (int i = 0; i < payloads.size(); i++) {
				if (i > 0) {
					outputBuilder.append(',');
				}
				outputBuilder.append(JSON.writeValueAsString(payloads.get(i)));
			}
			outputBuilder.append(']');
			String outputJson = outputBuilder.toString();

			String itemNow = Instant.now().toString();
			for (int i = 0; i < payloads.size(); i++) {
				AiJobItem item = AiJobItem.create(UUID.randomUUID().toString(), aiJobId,
					JSON.writeValueAsString(payloads.get(i)), itemNow);
				item.setSortOrder(i);
				itemMapper.insert(item);
			}
			if (jobMapper.markSucceeded(aiJobId, outputJson, Instant.now().toString()) == 0) {
				log.info("AI job {} finished after cancellation, result discarded", aiJobId);
			}
		} catch (Exception ex) {
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > MAX_REASON_LENGTH) {
				reason = reason.substring(0, MAX_REASON_LENGTH);
			}
			log.info("AI job {} failed: {}", aiJobId, reason);
			jobMapper.markFailed(aiJobId, reason, Instant.now().toString());
		}
	}

	/** 执行时按 provider_id 取真实 base_url 与 api_key（key 不落任务表，保持审计与凭据分离）。 */
	private AiProvider requireProvider(String providerId) {
		AiProvider provider = providerMapper.selectById(providerId);
		if (provider == null) {
			throw new IllegalStateException("任务引用的供应商不存在：" + providerId);
		}
		return provider;
	}
}
