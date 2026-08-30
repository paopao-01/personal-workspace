package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.review.domain.AnswerStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Generates one editable learning-task candidate from a weak interview question. */
@Component
public class TaskSuggestionHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "TASK_SUGGESTION_V1";
	private static final ObjectMapper JSON = new ObjectMapper();

	@Override
	public AiJobType type() {
		return AiJobType.TASK_SUGGESTION;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public String buildSystemPrompt() {
		return """
			你是面试复盘学习助手。根据输入 JSON 中的问题、回答状态、已有改进信息和知识点，生成一个可执行的学习任务候选。
			只输出 JSON 数组，不要输出其他文字或 Markdown。数组只能有一个元素：
			{"type":"LEARNING_TASK","rawText":"任务建议摘要","taskTitle":"任务标题","priority":"MEDIUM","estimatedMinutes":60,"learningGoal":"学习目标","acceptanceCriteria":"可检查的验收标准","verificationMethod":"验证方式","knowledgePointIds":["输入中已有的知识点 ID"],"rationale":"建议依据"}
			只能使用输入中已有的知识点 ID，不得新增知识点、虚构项目事实或量化结果；任务建议必须对应原问题的薄弱点。
			priority 只能是 LOW、MEDIUM、HIGH、URGENT；estimatedMinutes 必须是正整数；只输出一个候选。
			""";
	}

	@Override
	public List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client) {
		TaskSuggestionInput input = parseInput(job.getInputSnapshot());
		List<AiItemPayload> parsed = AiItemPayload.parseList(
			client.complete(provider, buildSystemPrompt(), job.getInputSnapshot()));
		if (parsed.size() != 1 || parsed.get(0) == null) {
			throw new IllegalStateException("模型未返回有效的学习任务建议");
		}
		AiItemPayload payload = parsed.get(0);
		if (!"LEARNING_TASK".equals(payload.type()) || isBlank(payload.rawText())
				|| isBlank(payload.taskTitle()) || isBlank(payload.learningGoal())
				|| isBlank(payload.acceptanceCriteria()) || isBlank(payload.verificationMethod())
				|| isBlank(payload.priority()) || payload.estimatedMinutes() == null
				|| payload.estimatedMinutes() < 1 || !isPriority(payload.priority())) {
			throw new IllegalStateException("模型返回的学习任务建议候选无效");
		}
		Set<String> allowedIds = input.knowledgePoints().stream().map(KnowledgePointInput::id).collect(Collectors.toSet());
		List<String> knowledgePointIds = payload.knowledgePointIds() == null || payload.knowledgePointIds().isEmpty()
			? input.knowledgePoints().stream().map(KnowledgePointInput::id).toList()
			: List.copyOf(payload.knowledgePointIds());
		if (knowledgePointIds.stream().anyMatch(id -> id == null || !allowedIds.contains(id))) {
			throw new IllegalStateException("模型返回了问题未关联的知识点");
		}
		return List.of(new AiItemPayload(
			"LEARNING_TASK", payload.rawText().trim(), null, null, trimToNull(payload.rationale()),
			null, null, null, null, payload.taskTitle().trim(), payload.priority().trim(),
			payload.estimatedMinutes(), payload.learningGoal().trim(), payload.acceptanceCriteria().trim(),
			payload.verificationMethod().trim(), knowledgePointIds));
	}

	private TaskSuggestionInput parseInput(String snapshot) {
		try {
			return JSON.readValue(snapshot, TaskSuggestionInput.class);
		} catch (Exception ex) {
			throw new IllegalStateException("学习任务建议输入快照无效", ex);
		}
	}

	private boolean isPriority(String value) {
		return Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(value);
	}

	private String trimToNull(String value) { return isBlank(value) ? null : value.trim(); }
	private boolean isBlank(String value) { return value == null || value.isBlank(); }

	private record TaskSuggestionInput(String question, AnswerStatus answerStatus, String myAnswer,
		String referenceAnswer, String errorReason, String improvementPlan,
		List<KnowledgePointInput> knowledgePoints) {
		TaskSuggestionInput {
			knowledgePoints = knowledgePoints == null ? List.of() : knowledgePoints;
		}
	}
	private record KnowledgePointInput(String id, String name, String category) { }
}
