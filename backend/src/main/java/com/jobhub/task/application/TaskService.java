package com.jobhub.task.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.review.domain.AnswerStatus;
import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.review.domain.KnowledgePoint;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskPriority;
import com.jobhub.task.domain.TaskSourceType;
import com.jobhub.task.domain.TaskStatus;
import com.jobhub.task.infrastructure.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TaskService {
	private final TaskMapper taskMapper;
	private final QuestionMapper questionMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public TaskService(TaskMapper taskMapper, QuestionMapper questionMapper, IdGenerator ids, UtcTime time) {
		this.taskMapper = taskMapper;
		this.questionMapper = questionMapper;
		this.ids = ids;
		this.time = time;
	}

	public TaskListResult list(TaskListQuery query) {
		int offset = (query.page() - 1) * query.pageSize();
		List<LearningTask> items = taskMapper.selectPage(query.status(), query.pageSize(), offset).stream()
			.map(this::hydrate)
			.toList();
		return new TaskListResult(items, taskMapper.selectPageCount(query.status()), query.page(), query.pageSize());
	}

	public LearningTask get(String id) {
		return hydrate(requireTask(id));
	}

	@Transactional
	public LearningTask create(TaskCreateCommand cmd) {
		String now = time.now();
		LearningTask task = LearningTask.create(ids.newId(), requiredText(cmd.title(), "Task title is required"),
			blankToNull(cmd.type()), cmd.priority(), cmd.estimatedMinutes(), blankToNull(cmd.dueAt()),
			blankToNull(cmd.learningGoal()), blankToNull(cmd.acceptanceCriteria()),
			blankToNull(cmd.verificationMethod()), blankToNull(cmd.outputUrl()), now);
		taskMapper.insert(task);
		replaceSources(task.getId(), cmd.knowledgePointIds(), cmd.relatedJobIds(), cmd.relatedQuestionIds(), now);
		return get(task.getId());
	}

	@Transactional
	public LearningTask update(String id, long expectedVersion, TaskUpdateCommand cmd) {
		LearningTask task = requireTask(id);
		String now = time.now();
		task.updateMeta(requiredText(cmd.title(), "Task title is required"), blankToNull(cmd.type()), cmd.priority(),
			cmd.estimatedMinutes(), blankToNull(cmd.dueAt()), blankToNull(cmd.learningGoal()),
			blankToNull(cmd.acceptanceCriteria()), blankToNull(cmd.verificationMethod()),
			blankToNull(cmd.verificationResult()), blankToNull(cmd.outputUrl()), now);
		VersionCheck.requireAffected(taskMapper.updateMeta(task, expectedVersion), task.getVersion());
		VersionCheck.requireAffected(taskMapper.bumpVersion(id, expectedVersion), task.getVersion());
		replaceSources(id, cmd.knowledgePointIds(), cmd.relatedJobIds(), cmd.relatedQuestionIds(), now);
		return get(id);
	}

	@Transactional
	public LearningTask transition(String id, long expectedVersion, TaskTransitionCommand cmd) {
		LearningTask task = requireTask(id);
		task.transition(cmd.targetStatus(), cmd.verificationResult(), time.now());
		VersionCheck.requireAffected(taskMapper.updateStatus(task, expectedVersion), task.getVersion());
		VersionCheck.requireAffected(taskMapper.bumpVersion(id, expectedVersion), task.getVersion());
		return get(id);
	}

	@Transactional
	public LearningTask createFromQuestion(String questionId, CreateTaskFromQuestionCommand cmd) {
		InterviewQuestion question = requireQuestion(questionId);
		if (question.getAnswerStatus() == AnswerStatus.FULLY_ANSWERED) {
			throw new BusinessRuleException("Only partially answered or unanswered questions can create learning tasks");
		}
		List<String> knowledgePointIds = questionMapper.selectKnowledgePoints(questionId).stream()
			.map(KnowledgePoint::getId)
			.toList();
		if ("LINK_EXISTING".equals(cmd.mode())) {
			String taskId = requiredText(cmd.existingTaskId(), "Existing task id is required");
			LearningTask task = requireTask(taskId);
			String now = time.now();
			insertSource(taskId, TaskSourceType.QUESTION, questionId, now);
			for (String knowledgePointId : knowledgePointIds) {
				insertSource(taskId, TaskSourceType.KNOWLEDGE_POINT, knowledgePointId, now);
			}
			return hydrate(task);
		}
		if (!"CREATE_NEW".equals(cmd.mode())) {
			throw new BusinessRuleException("Unsupported task creation mode");
		}
		String title = requiredText(cmd.title(), "Task title is required");
		String acceptanceCriteria = requiredText(cmd.acceptanceCriteria(), "Acceptance criteria is required");
		String verificationMethod = requiredText(cmd.verificationMethod(), "Verification method is required");
		return create(new TaskCreateCommand(title, "INTERVIEW_QUESTION", knowledgePointIds, List.of(),
			List.of(questionId), TaskPriority.MEDIUM, null, blankToNull(cmd.dueAt()),
			"补齐面试问题：" + question.getContent(), acceptanceCriteria, verificationMethod, null));
	}

	private void replaceSources(String taskId, List<String> knowledgePointIds, List<String> jobIds,
			List<String> questionIds, String now) {
		if (!isEmpty(jobIds)) {
			throw new BusinessRuleException("Related jobs are not supported by the current task source schema");
		}
		taskMapper.deleteSources(taskId);
		for (String knowledgePointId : distinct(knowledgePointIds)) {
			requireKnowledgePoint(knowledgePointId);
			insertSource(taskId, TaskSourceType.KNOWLEDGE_POINT, knowledgePointId, now);
		}
		for (String questionId : distinct(questionIds)) {
			requireQuestion(questionId);
			insertSource(taskId, TaskSourceType.QUESTION, questionId, now);
		}
		if (isEmpty(knowledgePointIds) && isEmpty(jobIds) && isEmpty(questionIds)) {
			insertSource(taskId, TaskSourceType.MANUAL, null, now);
		}
	}

	private void insertSource(String taskId, TaskSourceType sourceType, String sourceId, String now) {
		taskMapper.insertSource(ids.newId(), taskId, sourceType, sourceId, now);
	}

	private LearningTask hydrate(LearningTask task) {
		task.setKnowledgePoints(taskMapper.selectKnowledgePoints(task.getId()));
		return task;
	}

	private LearningTask requireTask(String id) {
		LearningTask task = taskMapper.selectById(id);
		VersionCheck.requireFound(task, "LearningTask", id);
		return task;
	}

	private InterviewQuestion requireQuestion(String id) {
		InterviewQuestion question = questionMapper.selectById(id);
		VersionCheck.requireFound(question, "InterviewQuestion", id);
		return question;
	}

	private void requireKnowledgePoint(String id) {
		VersionCheck.requireFound(questionMapper.selectKnowledgePointById(id), "KnowledgePoint", id);
	}

	private Set<String> distinct(List<String> values) {
		if (values == null) return Set.of();
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = blankToNull(value);
			if (normalized != null) result.add(normalized);
		}
		return result;
	}

	private boolean isEmpty(List<String> values) {
		return values == null || values.stream().allMatch(v -> v == null || v.isBlank());
	}

	private String requiredText(String value, String message) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			throw new BusinessRuleException(message);
		}
		return normalized;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
