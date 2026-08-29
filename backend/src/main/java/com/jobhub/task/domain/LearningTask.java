package com.jobhub.task.domain;

import com.jobhub.common.error.IllegalStateTransitionException;
import com.jobhub.review.domain.KnowledgePoint;
import java.util.List;
import java.util.Set;

public class LearningTask {
	private String id;
	private String title;
	private String type;
	private TaskPriority priority;
	private Integer estimatedMinutes;
	private String dueAt;
	private String learningGoal;
	private String acceptanceCriteria;
	private String verificationMethod;
	private String verificationResult;
	private String outputUrl;
	private TaskStatus status;
	private String createdAt;
	private String updatedAt;
	private String completedAt;
	private String abandonedAt;
	private String deletedAt;
	private long version;
	private List<KnowledgePoint> knowledgePoints = List.of();

	public static LearningTask create(String id, String title, String type, TaskPriority priority,
			Integer estimatedMinutes, String dueAt, String learningGoal, String acceptanceCriteria,
			String verificationMethod, String outputUrl, String now) {
		LearningTask task = new LearningTask();
		task.id = id;
		task.title = title;
		task.type = type;
		task.priority = priority == null ? TaskPriority.MEDIUM : priority;
		task.estimatedMinutes = estimatedMinutes;
		task.dueAt = dueAt;
		task.learningGoal = learningGoal;
		task.acceptanceCriteria = acceptanceCriteria;
		task.verificationMethod = verificationMethod;
		task.outputUrl = outputUrl;
		task.status = TaskStatus.TODO;
		task.createdAt = now;
		task.updatedAt = now;
		return task;
	}

	public void updateMeta(String title, String type, TaskPriority priority, Integer estimatedMinutes, String dueAt,
			String learningGoal, String acceptanceCriteria, String verificationMethod, String verificationResult,
			String outputUrl, String now) {
		this.title = title;
		this.type = type;
		this.priority = priority == null ? TaskPriority.MEDIUM : priority;
		this.estimatedMinutes = estimatedMinutes;
		this.dueAt = dueAt;
		this.learningGoal = learningGoal;
		this.acceptanceCriteria = acceptanceCriteria;
		this.verificationMethod = verificationMethod;
		this.verificationResult = verificationResult;
		this.outputUrl = outputUrl;
		this.updatedAt = now;
	}

	public void transition(TaskStatus target, String verificationResult, String now) {
		TaskStatus current = this.status;
		if (!allowedTargets(current).contains(target)) {
			throw new IllegalStateTransitionException(current.name(), target.name(),
				"transition from " + current + " to " + target + " is not allowed");
		}
		this.status = target;
		this.updatedAt = now;
		if (target == TaskStatus.COMPLETED) {
			this.completedAt = now;
			this.abandonedAt = null;
			this.verificationResult = verificationResult == null || verificationResult.isBlank()
				? "未验证完成"
				: verificationResult.trim();
		}
		if (target == TaskStatus.ABANDONED) {
			this.abandonedAt = now;
		}
		if (target == TaskStatus.IN_PROGRESS || target == TaskStatus.TODO) {
			this.completedAt = null;
			this.abandonedAt = null;
		}
	}

	private static Set<TaskStatus> allowedTargets(TaskStatus current) {
		return switch (current) {
			case TODO -> Set.of(TaskStatus.IN_PROGRESS, TaskStatus.ABANDONED);
			case IN_PROGRESS -> Set.of(TaskStatus.COMPLETED, TaskStatus.ABANDONED, TaskStatus.TODO);
			case COMPLETED -> Set.of(TaskStatus.IN_PROGRESS);
			case ABANDONED -> Set.of(TaskStatus.TODO);
		};
	}

	public String getId() { return id; }
	public String getTitle() { return title; }
	public String getType() { return type; }
	public TaskPriority getPriority() { return priority; }
	public Integer getEstimatedMinutes() { return estimatedMinutes; }
	public String getDueAt() { return dueAt; }
	public String getLearningGoal() { return learningGoal; }
	public String getAcceptanceCriteria() { return acceptanceCriteria; }
	public String getVerificationMethod() { return verificationMethod; }
	public String getVerificationResult() { return verificationResult; }
	public String getOutputUrl() { return outputUrl; }
	public TaskStatus getStatus() { return status; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getCompletedAt() { return completedAt; }
	public String getAbandonedAt() { return abandonedAt; }
	public String getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public List<KnowledgePoint> getKnowledgePoints() { return knowledgePoints == null ? List.of() : knowledgePoints; }
	public void setKnowledgePoints(List<KnowledgePoint> knowledgePoints) {
		this.knowledgePoints = knowledgePoints == null ? List.of() : knowledgePoints;
	}
}
