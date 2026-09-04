package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobItem;
import com.jobhub.ai.domain.AiJobItemStatus;
import com.jobhub.ai.domain.AiJobStatus;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.AiQuestionCategory;
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
import com.jobhub.review.application.ReviewService;
import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.task.application.TaskCreateCommand;
import com.jobhub.task.application.TaskService;
import com.jobhub.task.domain.TaskPriority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 异步任务服务（PRD 9.2）：创建入队、重试（上限 3）、取消、候选条目逐项采纳（可编辑）/拒绝。
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
	private final QuestionMapper questionMapper;
	private final ReviewService reviewService;
	private final TaskService taskService;
	private final AiJobExecutor executor;
	private final IdGenerator ids;
	private final UtcTime time;
	private final List<AiTaskHandler> handlers;

	public AiJobService(AiJobMapper aiJobMapper, AiJobItemMapper itemMapper, AiProviderMapper providerMapper,
			JobMapper jobMapper, RequirementService requirementService, AiJobExecutor executor, IdGenerator ids,
			UtcTime time, List<AiTaskHandler> handlers, QuestionMapper questionMapper, ReviewService reviewService,
			TaskService taskService) {
		this.aiJobMapper = aiJobMapper;
		this.itemMapper = itemMapper;
		this.providerMapper = providerMapper;
		this.jobMapper = jobMapper;
		this.requirementService = requirementService;
		this.questionMapper = questionMapper;
		this.reviewService = reviewService;
		this.taskService = taskService;
		this.executor = executor;
		this.ids = ids;
		this.time = time;
		this.handlers = handlers;
	}

	@Transactional
	public AiJob createQuestionClassification(String questionId) {
		InterviewQuestion question = questionMapper.selectById(questionId);
		VersionCheck.requireFound(question, "InterviewQuestion", questionId);
		AiProvider provider = requireActiveProvider();
		String now = time.now();
		AiJob aiJob = new AiJob();
		aiJob.setId(ids.newId());
		aiJob.setJobType(AiJobType.QUESTION_CLASSIFICATION);
		aiJob.setObjectId(questionId);
		aiJob.setObjectVersion(question.getVersion());
		aiJob.setStatus(AiJobStatus.QUEUED);
		aiJob.setProviderId(provider.getId());
		aiJob.setProviderType(provider.getProviderType().name());
		aiJob.setModel(provider.getModel());
		aiJob.setPromptVersion(promptVersion(AiJobType.QUESTION_CLASSIFICATION));
		aiJob.setAttemptCount(0);
		aiJob.setInputSnapshot(question.getContent());
		aiJob.setCreatedAt(now);
		aiJob.setUpdatedAt(now);
		aiJobMapper.insert(aiJob);
		submitAfterCommit(aiJob.getId());
		return requireJob(aiJob.getId());
	}

	@Transactional
	public AiJob createAnswerQualityAnalysis(String questionId) {
		InterviewQuestion question = questionMapper.selectById(questionId);
		VersionCheck.requireFound(question, "InterviewQuestion", questionId);
		if (question.getMyAnswer() == null || question.getMyAnswer().isBlank()) {
			throw new BusinessRuleException("请先填写我的回答，再发起回答质量分析");
		}
		AiProvider provider = requireActiveProvider();
		String now = time.now();
		AiJob aiJob = new AiJob();
		aiJob.setId(ids.newId());
		aiJob.setJobType(AiJobType.ANSWER_QUALITY_ANALYSIS);
		aiJob.setObjectId(questionId);
		aiJob.setObjectVersion(question.getVersion());
		aiJob.setStatus(AiJobStatus.QUEUED);
		aiJob.setProviderId(provider.getId());
		aiJob.setProviderType(provider.getProviderType().name());
		aiJob.setModel(provider.getModel());
		aiJob.setPromptVersion(promptVersion(AiJobType.ANSWER_QUALITY_ANALYSIS));
		aiJob.setAttemptCount(0);
		aiJob.setInputSnapshot(serializeAnswerSnapshot(question));
		aiJob.setCreatedAt(now);
		aiJob.setUpdatedAt(now);
		aiJobMapper.insert(aiJob);
		submitAfterCommit(aiJob.getId());
		return requireJob(aiJob.getId());
	}

	@Transactional
	public AiJob createTaskSuggestion(String questionId) {
		InterviewQuestion question = questionMapper.selectById(questionId);
		VersionCheck.requireFound(question, "InterviewQuestion", questionId);
		question.setKnowledgePoints(questionMapper.selectKnowledgePoints(questionId));
		if (question.getAnswerStatus() == null || question.getAnswerStatus() == com.jobhub.review.domain.AnswerStatus.FULLY_ANSWERED) {
			throw new BusinessRuleException("只有部分答出或未答出的问题可以生成学习任务建议");
		}
		AiProvider provider = requireActiveProvider();
		String now = time.now();
		AiJob aiJob = new AiJob();
		aiJob.setId(ids.newId());
		aiJob.setJobType(AiJobType.TASK_SUGGESTION);
		aiJob.setObjectId(questionId);
		aiJob.setObjectVersion(question.getVersion());
		aiJob.setStatus(AiJobStatus.QUEUED);
		aiJob.setProviderId(provider.getId());
		aiJob.setProviderType(provider.getProviderType().name());
		aiJob.setModel(provider.getModel());
		aiJob.setPromptVersion(promptVersion(AiJobType.TASK_SUGGESTION));
		aiJob.setAttemptCount(0);
		aiJob.setInputSnapshot(serializeTaskSuggestionSnapshot(question));
		aiJob.setCreatedAt(now);
		aiJob.setUpdatedAt(now);
		aiJobMapper.insert(aiJob);
		submitAfterCommit(aiJob.getId());
		return requireJob(aiJob.getId());
	}

	@Transactional
	public AiJob create(AiJobType jobType, String objectId) {
		return create(jobType, objectId, null);
	}

	@Transactional
	public AiJob create(AiJobType jobType, String objectId, String sourceText) {
		if (isQuestionJobType(jobType)) {
			throw new BusinessRuleException("面试问题 AI 任务必须通过问题专用接口创建");
		}
		Job job = jobMapper.selectById(objectId);
		VersionCheck.requireFound(job, "Job", objectId);
		if (jobType == AiJobType.JD_EXTRACTION && (job.getJdRawText() == null || job.getJdRawText().isBlank())) {
			throw new BusinessRuleException("岗位缺少 JD 原文，无法执行 AI 提取");
		}
		if (jobType == AiJobType.RESUME_DRAFT && (sourceText == null || sourceText.isBlank())) {
			throw new BusinessRuleException("请先提供已确认的简历原文");
		}
		AiProvider provider = requireActiveProvider();
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
		aiJob.setPromptVersion(promptVersion(jobType));
		aiJob.setAttemptCount(0);
		aiJob.setInputSnapshot(jobType == AiJobType.RESUME_DRAFT
			? "USER_CONFIRMED_RESUME:\n" + sourceText.trim() + "\n\nJOB_DESCRIPTION:\n" + job.getJdRawText()
			: job.getJdRawText());
		aiJob.setCreatedAt(now);
		aiJob.setUpdatedAt(now);
		aiJobMapper.insert(aiJob);
		submitAfterCommit(aiJob.getId());
		return requireJob(aiJob.getId());
	}

	@Transactional
	public AiJob createMockInterview(String sessionId, String projectSnapshot) {
		AiProvider provider = requireActiveProvider(); String now = time.now(); AiJob job = new AiJob();
		job.setId(ids.newId()); job.setJobType(AiJobType.MOCK_INTERVIEW); job.setObjectId(sessionId); job.setObjectVersion(0L);
		job.setStatus(AiJobStatus.QUEUED); job.setProviderId(provider.getId()); job.setProviderType(provider.getProviderType().name()); job.setModel(provider.getModel());
		job.setPromptVersion(promptVersion(AiJobType.MOCK_INTERVIEW)); job.setAttemptCount(0); job.setInputSnapshot(projectSnapshot); job.setCreatedAt(now); job.setUpdatedAt(now); aiJobMapper.insert(job); submitAfterCommit(job.getId()); return requireJob(job.getId());
	}

	@Transactional
	public AiJob createMockInterviewFollowUp(String sessionId, String projectSnapshot, String question, String answer) {
		AiProvider provider = requireActiveProvider(); String now = time.now(); AiJob job = new AiJob();
		job.setId(ids.newId()); job.setJobType(AiJobType.MOCK_INTERVIEW_FOLLOW_UP); job.setObjectId(sessionId); job.setObjectVersion(0L);
		job.setStatus(AiJobStatus.QUEUED); job.setProviderId(provider.getId()); job.setProviderType(provider.getProviderType().name()); job.setModel(provider.getModel());
		job.setPromptVersion(promptVersion(AiJobType.MOCK_INTERVIEW_FOLLOW_UP)); job.setAttemptCount(0); job.setInputSnapshot(serializeMockFollowUp(projectSnapshot, question, answer)); job.setCreatedAt(now); job.setUpdatedAt(now); aiJobMapper.insert(job); submitAfterCommit(job.getId()); return requireJob(job.getId());
	}

	private String serializeMockFollowUp(String projectSnapshot, String question, String answer) {
		try { return JSON.writeValueAsString(java.util.Map.of("projectSnapshot", projectSnapshot, "question", question, "answer", answer)); }
		catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BusinessRuleException("连续追问输入快照序列化失败"); }
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

	public List<AiJob> listByObject(String objectId) {
		return aiJobMapper.selectByObjectAll(objectId).stream().map(this::hydrate).toList();
	}

	public List<AiJob> listQuestionClassifications(String questionId) {
		return listQuestionJobs(questionId, AiJobType.QUESTION_CLASSIFICATION);
	}

	public List<AiJob> listQuestionJobs(String questionId, AiJobType jobType) {
		VersionCheck.requireFound(questionMapper.selectById(questionId), "InterviewQuestion", questionId);
		if (!isQuestionJobType(jobType)) {
			throw new BusinessRuleException("不支持的问题 AI 任务类型");
		}
		return listByObject(jobType, questionId);
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

	/** 采纳候选（可编辑）：创建 source_type=AI 的 PENDING 岗位要求并回链条目。 */
	@Transactional
	public AiJobItem acceptItem(String itemId, AiItemPayload editedPayload, Long questionVersion) {
		AiJobItem item = requireItem(itemId);
		AiJob itemJob = aiJobMapper.selectById(item.getAiJobId());
		VersionCheck.requireFound(itemJob, "AiJob", item.getAiJobId());
		if (itemJob.getJobType() == AiJobType.RESUME_DRAFT) {
			throw new BusinessRuleException("简历草稿只能人工编辑，不能采纳为岗位要求");
		}
		if (item.getStatus() != AiJobItemStatus.PROPOSED) {
			throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"仅 PROPOSED 条目可采纳");
		}
		AiItemPayload payload = resolvePayload(item, editedPayload);
		String now = time.now();
		if (itemJob.getJobType() == AiJobType.QUESTION_CLASSIFICATION) {
			if (questionVersion == null) {
				throw new BusinessRuleException("采纳问题分类时必须携带问题当前版本");
			}
			validateQuestionCategory(payload.type());
			reviewService.applyAiClassification(itemJob.getObjectId(), questionVersion, payload.type());
			String editedJson = editedPayload == null ? null : serialize(editedPayload);
			if (itemMapper.markAccepted(itemId, editedJson, null, null, now) == 0) {
				throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"条目状态已变化");
			}
			return itemMapper.selectById(itemId);
		}
		if (itemJob.getJobType() == AiJobType.ANSWER_QUALITY_ANALYSIS) {
			if (questionVersion == null) {
				throw new BusinessRuleException("采纳回答质量分析时必须携带问题当前版本");
			}
			validateAnswerAnalysis(payload);
			reviewService.applyAiAnswerAnalysis(itemJob.getObjectId(), questionVersion, payload.answerStatus(),
				payload.referenceAnswer(), payload.errorReason(), payload.improvementPlan());
			String editedJson = editedPayload == null ? null : serialize(editedPayload);
			if (itemMapper.markAccepted(itemId, editedJson, null, null, now) == 0) {
				throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"条目状态已变化");
			}
			return itemMapper.selectById(itemId);
		}
		if (itemJob.getJobType() == AiJobType.TASK_SUGGESTION) {
			InterviewQuestion question = questionMapper.selectById(itemJob.getObjectId());
			VersionCheck.requireFound(question, "InterviewQuestion", itemJob.getObjectId());
			question.setKnowledgePoints(questionMapper.selectKnowledgePoints(itemJob.getObjectId()));
			if (questionVersion == null) {
				throw new BusinessRuleException("采纳学习任务建议时必须携带问题当前版本");
			}
			if (question.getVersion() != questionVersion) {
				throw new com.jobhub.common.error.VersionConflictException(question.getVersion());
			}
			if (question.getAnswerStatus() == null || question.getAnswerStatus() == com.jobhub.review.domain.AnswerStatus.FULLY_ANSWERED) {
				throw new BusinessRuleException("只有部分答出或未答出的问题可以创建学习任务");
			}
			validateTaskSuggestion(payload, question);
			TaskPriority priority = parsePriority(payload.priority());
			var task = taskService.create(new TaskCreateCommand(
				payload.taskTitle().trim(), "AI_SUGGESTED", payload.knowledgePointIds(), List.of(),
				List.of(question.getId()), priority, payload.estimatedMinutes(), null,
				payload.learningGoal().trim(), payload.acceptanceCriteria().trim(),
				payload.verificationMethod().trim(), null));
			String editedJson = editedPayload == null ? null : serialize(editedPayload);
			if (itemMapper.markAccepted(itemId, editedJson, null, task.getId(), now) == 0) {
				throw new IllegalStateTransitionException(item.getStatus().name(), AiJobItemStatus.ACCEPTED.name(),
					"条目状态已变化");
			}
			return itemMapper.selectById(itemId);
		}
		var requirement = requirementService.createAiCandidate(requireJobOfItem(item), payload.type(),
				payload.rawText(), payload.normalizedName(), payload.proficiencyText());
		String editedJson = editedPayload == null ? null : serialize(editedPayload);
		if (itemMapper.markAccepted(itemId, editedJson, requirement.getId(), null, now) == 0) {
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

	private String serialize(AiItemPayload payload) {
		try {
			return JSON.writeValueAsString(payload);
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new BusinessRuleException("候选内容序列化失败：" + ex.getMessage());
		}
	}

	private AiItemPayload resolvePayload(AiJobItem item, AiItemPayload edited) {
		AiItemPayload base = AiItemPayload.parseList("[" + item.getPayloadJson() + "]").get(0);
		if (edited == null) {
			return base;
		}
		return new AiItemPayload(
			edited.type() == null || edited.type().isBlank() ? base.type() : edited.type(),
			edited.rawText() == null || edited.rawText().isBlank() ? base.rawText() : edited.rawText(),
			edited.normalizedName() == null || edited.normalizedName().isBlank() ? base.normalizedName() : edited.normalizedName(),
			edited.proficiencyText() == null ? base.proficiencyText() : edited.proficiencyText(),
			edited.rationale() == null ? base.rationale() : edited.rationale(),
			edited.answerStatus() == null ? base.answerStatus() : edited.answerStatus(),
			edited.referenceAnswer() == null ? base.referenceAnswer() : edited.referenceAnswer(),
			edited.errorReason() == null ? base.errorReason() : edited.errorReason(),
			edited.improvementPlan() == null ? base.improvementPlan() : edited.improvementPlan(),
			edited.taskTitle() == null ? base.taskTitle() : edited.taskTitle(),
			edited.priority() == null ? base.priority() : edited.priority(),
			edited.estimatedMinutes() == null ? base.estimatedMinutes() : edited.estimatedMinutes(),
			edited.learningGoal() == null ? base.learningGoal() : edited.learningGoal(),
			edited.acceptanceCriteria() == null ? base.acceptanceCriteria() : edited.acceptanceCriteria(),
			edited.verificationMethod() == null ? base.verificationMethod() : edited.verificationMethod(),
			edited.knowledgePointIds() == null ? base.knowledgePointIds() : edited.knowledgePointIds());
	}

	private AiProvider requireActiveProvider() {
		AiProvider provider = providerMapper.selectActive();
		if (provider == null) {
			throw new BusinessRuleException("尚未配置激活的 AI 供应商，请先在设置中配置");
		}
		return provider;
	}

	private void validateQuestionCategory(String type) {
		try {
			AiQuestionCategory.valueOf(type);
		} catch (Exception ex) {
			throw new BusinessRuleException("问题分类候选值无效");
		}
	}

	private void validateAnswerAnalysis(AiItemPayload payload) {
		if (!"ANSWER_QUALITY".equals(payload.type()) || payload.answerStatus() == null) {
			throw new BusinessRuleException("回答质量分析候选值无效");
		}
	}

	private boolean isQuestionJobType(AiJobType jobType) {
		return jobType == AiJobType.QUESTION_CLASSIFICATION
			|| jobType == AiJobType.ANSWER_QUALITY_ANALYSIS
			|| jobType == AiJobType.TASK_SUGGESTION;
	}

	private String serializeAnswerSnapshot(InterviewQuestion question) {
		try {
			return JSON.writeValueAsString(new AnswerAnalysisSnapshot(question.getContent(), question.getMyAnswer(),
				question.getReferenceAnswer()));
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new BusinessRuleException("回答分析输入快照序列化失败：" + ex.getMessage());
		}
	}

	private record AnswerAnalysisSnapshot(String question, String myAnswer, String referenceAnswer) { }

	private String serializeTaskSuggestionSnapshot(InterviewQuestion question) {
		try {
			return JSON.writeValueAsString(new TaskSuggestionSnapshot(question.getContent(), question.getAnswerStatus(),
				question.getMyAnswer(), question.getReferenceAnswer(), question.getErrorReason(), question.getImprovementPlan(),
				question.getKnowledgePoints().stream().map(p -> new KnowledgePointSnapshot(p.getId(), p.getName(), p.getCategory())).toList()));
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw new BusinessRuleException("学习任务建议输入快照序列化失败：" + ex.getMessage());
		}
	}

	private record TaskSuggestionSnapshot(String question, com.jobhub.review.domain.AnswerStatus answerStatus,
		String myAnswer, String referenceAnswer, String errorReason, String improvementPlan,
		List<KnowledgePointSnapshot> knowledgePoints) { }
	private record KnowledgePointSnapshot(String id, String name, String category) { }

	private void validateTaskSuggestion(AiItemPayload payload, InterviewQuestion question) {
		if (!"LEARNING_TASK".equals(payload.type()) || isBlank(payload.taskTitle())
				|| isBlank(payload.learningGoal()) || isBlank(payload.acceptanceCriteria())
				|| isBlank(payload.verificationMethod()) || payload.estimatedMinutes() == null
				|| payload.estimatedMinutes() < 1 || isBlank(payload.priority())) {
			throw new BusinessRuleException("学习任务建议候选值无效");
		}
		parsePriority(payload.priority());
		var allowed = question.getKnowledgePoints().stream().map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
		if (payload.knowledgePointIds() != null && payload.knowledgePointIds().stream()
				.anyMatch(id -> id == null || !allowed.contains(id))) {
			throw new BusinessRuleException("学习任务建议只能关联问题已有知识点");
		}
	}

	private TaskPriority parsePriority(String value) {
		try {
			return TaskPriority.valueOf(value);
		} catch (Exception ex) {
			throw new BusinessRuleException("学习任务建议优先级无效");
		}
	}

	private boolean isBlank(String value) { return value == null || value.isBlank(); }

	private String requireJobOfItem(AiJobItem item) {
		AiJob job = aiJobMapper.selectById(item.getAiJobId());
		if (job == null) {
			throw new ResourceNotFoundException("AiJob", item.getAiJobId());
		}
		return job.getObjectId();
	}

	private String promptVersion(AiJobType jobType) {
		return handlers.stream()
			.filter(h -> h.type() == jobType)
			.findFirst()
			.orElseThrow(() -> new BusinessRuleException("暂不支持的任务类型：" + jobType))
			.promptVersion();
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
