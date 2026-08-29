package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobItem;
import com.jobhub.ai.domain.AiJobItemStatus;
import com.jobhub.ai.domain.AiJobStatus;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.infrastructure.AiJobItemMapper;
import com.jobhub.ai.infrastructure.AiJobMapper;
import com.jobhub.ai.infrastructure.AiProviderMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.IllegalStateTransitionException;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.application.RequirementService;
import com.jobhub.job.domain.Job;
import com.jobhub.job.infrastructure.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 异步任务服务（PRD 9.2/9.4）：创建入队、重试（上限 3）、取消、候选条目逐项采纳（可编辑）/拒绝。
 * 采纳按任务类型分派：JD_EXTRACTION 创建 source_type=AI 的 PENDING 岗位要求；
 * RESUME_DRAFT 仅确认建议文本（溯源字段不可篡改），不创建业务数据。
 * 重新生成即再创建新任务，既有条目与确认状态不受影响。
 */
@Service
public class AiJobService {
	public static final int MAX_ATTEMPTS = 3;
	private static final ObjectMapper JSON = new ObjectMapper();

	private final AiJobMapper aiJobMapper;
	private final AiJobItemMapper itemMapper;
	private final AiProviderMapper providerMapper;
	private final JobMapper jobMapper;
	private final RequirementService requirementService;
	private final AiJobExecutor executor;
	private final IdGenerator ids;
	private final UtcTime time;
	private final List<AiTaskHandler> handlers;

	public AiJobService(AiJobMapper aiJobMapper, AiJobItemMapper itemMapper, AiProviderMapper providerMapper,
			JobMapper jobMapper, RequirementService requirementService, AiJobExecutor executor, IdGenerator ids,
			UtcTime time, List<AiTaskHandler> handlers) {
		this.aiJobMapper = aiJobMapper;
		this.itemMapper = itemMapper;
		this.providerMapper = providerMapper;
		this.jobMapper = jobMapper;
		this.requirementService = requirementService;
		this.executor = executor;
		this.ids = ids;
		this.time = time;
		this.handlers = handlers;
	}

	@Transactional
	public AiJob create(AiJobType jobType, String objectId) {
		AiTaskHandler handler = handlers.stream()
			.filter(h -> h.type() == jobType)
			.findFirst()
			.orElseThrow(() -> new BusinessRuleException("暂不支持的任务类型：" + jobType));
		Job job = jobMapper.selectById(objectId);
		VersionCheck.requireFound(job, "Job", objectId);
		AiProvider provider = providerMapper.selectActive();
		if (provider == null) {
			throw new BusinessRuleException("尚未配置激活的 AI 供应商，请先在设置中配置");
		}
		String now = time.now();
		AiJob aiJob = new AiJob();
		aiJob.setId(ids.newId());
		aiJob.setJobType(jobType);
		aiJob.setObjectId(objectId);
		aiJob.setObjectVersion(job.getVersion());
		aiJob.setStatus(AiJobStatus.QUEUED);
		aiJob.setProviderId(provider.getId());
		aiJob.setProviderType(provider.getProviderType().name());
		aiJob.setModel(provider.getModel());
		aiJob.setPromptVersion(handler.promptVersion());
		aiJob.setAttemptCount(0);
		aiJob.setInputSnapshot(handler.buildInputSnapshot(objectId));
		aiJob.setCreatedAt(now);
		aiJob.setUpdatedAt(now);
		aiJobMapper.insert(aiJob);
		submitAfterCommit(aiJob.getId());
		return requireJob(aiJob.getId());
	}

	/** 事务提交后再投递执行器，避免执行器线程读不到未提交的任务行。 */
	private void submitAfterCommit(String aiJobId) {
		if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
			org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
				new org.springframework.transaction.support.TransactionSynchronization() {
					@Override
					public void afterCommit() {
						executor.submit(aiJobId);
					}
				});
		} else {
			executor.submit(aiJobId);
		}
	}

	public AiJob get(String id) {
		return hydrate(requireJob(id));
	}

	public List<AiJob> listByObject(AiJobType jobType, String objectId) {
		return aiJobMapper.selectByObject(jobType.name(), objectId).stream().map(this::hydrate).toList();
	}

	@Transactional
	public AiJob retry(String id) {
		AiJob job = requireJob(id);
		if (job.getStatus() != AiJobStatus.FAILED) {
			throw new IllegalStateTransitionException(job.getStatus().name(), AiJobStatus.QUEUED.name(),
					"仅 FAILED 任务可重试");
		}
		if (job.getAttemptCount() >= MAX_ATTEMPTS) {
			throw new BusinessRuleException("已达最大重试次数（" + MAX_ATTEMPTS + "），请检查供应商配置后新建任务");
		}
		if (aiJobMapper.markQueuedForRetry(id, time.now()) == 0) {
			throw new IllegalStateTransitionException(job.getStatus().name(), AiJobStatus.QUEUED.name(),
					"任务状态已变化");
		}
		submitAfterCommit(id);
		return requireJob(id);
	}

	@Transactional
	public AiJob cancel(String id) {
		AiJob job = requireJob(id);
		if (aiJobMapper.markCanceled(id, time.now()) == 0) {
			throw new IllegalStateTransitionException(job.getStatus().name(), AiJobStatus.CANCELED.name(),
					"仅 QUEUED/RUNNING 任务可取消");
		}
		return requireJob(id);
	}

	/**
	 * 采纳候选（可编辑），按任务类型分派：
	 * JD_EXTRACTION → 创建 source_type=AI 的 PENDING 岗位要求并回链条目；
	 * RESUME_DRAFT → 仅确认建议文本，不创建业务数据（溯源字段 sourceId/sourceType/sourceTitle 锁定为原文）。
	 */
	@Transactional
	public AiJobItem acceptItem(String itemId, JsonNode editedPayload) {
		AiJobItem item = requireItem(itemId);
		if (item.getStatus() != AiJobItemStatus.PROPOSED) {
			throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"仅 PROPOSED 条目可采纳");
		}
		AiJob job = aiJobMapper.selectById(item.getAiJobId());
		if (job == null) {
			throw new ResourceNotFoundException("AiJob", item.getAiJobId());
		}
		String now = time.now();
		if (job.getJobType() == AiJobType.JD_EXTRACTION) {
			AiItemPayload base = AiItemPayload.parseList("[" + item.getPayloadJson() + "]").get(0);
			AiItemPayload payload = mergeJdPayload(base, editedPayload);
			var requirement = requirementService.createAiCandidate(job.getObjectId(), payload.type(),
					payload.rawText(), payload.normalizedName(), payload.proficiencyText());
			String editedJson = editedPayload == null ? null : editedPayload.toString();
			if (itemMapper.markAccepted(itemId, editedJson, requirement.getId(), now) == 0) {
				throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
						"条目状态已变化");
			}
			return itemMapper.selectById(itemId);
		}
		// RESUME_DRAFT：确认建议文本；溯源字段强制取原文
		String suggestedText = resolveSuggestedText(item.getPayloadJson(), editedPayload);
		String editedJson = editedPayload == null ? null : lockSuggestionSource(item.getPayloadJson(), editedPayload);
		if (itemMapper.markAccepted(itemId, editedJson, null, now) == 0) {
			throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"条目状态已变化");
		}
		return itemMapper.selectById(itemId);
	}

	@Transactional
	public AiJobItem rejectItem(String itemId) {
		AiJobItem item = requireItem(itemId);
		if (itemMapper.markRejected(itemId, time.now()) == 0) {
			throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.REJECTED.name(),
					"仅 PROPOSED 条目可拒绝");
		}
		return itemMapper.selectById(itemId);
	}

	private AiItemPayload mergeJdPayload(AiItemPayload base, JsonNode edited) {
		if (edited == null) {
			return base;
		}
		AiItemPayload editedPayload;
		try {
			editedPayload = JSON.treeToValue(edited, AiItemPayload.class);
		} catch (Exception ex) {
			throw new BusinessRuleException("采纳内容解析失败：" + ex.getMessage());
		}
		return new AiItemPayload(
			editedPayload.type() == null || editedPayload.type().isBlank() ? base.type() : editedPayload.type(),
			editedPayload.rawText() == null || editedPayload.rawText().isBlank() ? base.rawText() : editedPayload.rawText(),
			editedPayload.normalizedName() == null || editedPayload.normalizedName().isBlank()
					? base.normalizedName() : editedPayload.normalizedName(),
			editedPayload.proficiencyText() == null ? base.proficiencyText() : editedPayload.proficiencyText());
	}

	private String resolveSuggestedText(String payloadJson, JsonNode edited) {
		String base = readTextField(payloadJson, "suggestedText");
		if (edited != null && edited.has("suggestedText") && !edited.get("suggestedText").asText("").isBlank()) {
			return edited.get("suggestedText").asText().trim();
		}
		if (base == null || base.isBlank()) {
			throw new BusinessRuleException("建议文本为空，无法采纳");
		}
		return base;
	}

	/** 采纳时锁定溯源字段：sourceType/sourceId/sourceTitle 一律取原文，用户仅可编辑建议文本。 */
	private String lockSuggestionSource(String payloadJson, JsonNode edited) {
		try {
			com.fasterxml.jackson.databind.node.ObjectNode node =
					(com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(payloadJson);
			if (edited != null && edited.has("suggestedText") && !edited.get("suggestedText").asText("").isBlank()) {
				node.put("suggestedText", edited.get("suggestedText").asText().trim());
			}
			return JSON.writeValueAsString(node);
		} catch (BusinessRuleException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BusinessRuleException("采纳内容处理失败：" + ex.getMessage());
		}
	}

	private String readTextField(String payloadJson, String field) {
		try {
			JsonNode node = JSON.readTree(payloadJson);
			JsonNode value = node.get(field);
			return value == null || value.isNull() ? null : value.asText();
		} catch (Exception ex) {
			return null;
		}
	}

	private AiJob requireJob(String id) {
		AiJob job = aiJobMapper.selectById(id);
		VersionCheck.requireFound(job, "AiJob", id);
		return job;
	}

	private AiJob hydrate(AiJob job) {
		job.setItems(itemMapper.selectByJob(job.getId()));
		return job;
	}

	private AiJobItem requireItem(String id) {
		AiJobItem item = itemMapper.selectById(id);
		VersionCheck.requireFound(item, "AiJobItem", id);
		return item;
	}
}
