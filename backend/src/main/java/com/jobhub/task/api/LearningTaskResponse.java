package com.jobhub.task.api;

import com.jobhub.review.api.KnowledgePointResponse;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskPriority;
import com.jobhub.task.domain.TaskStatus;
import java.util.List;

public record LearningTaskResponse(
	String id,
	String title,
	TaskStatus status,
	TaskPriority priority,
	String dueAt,
	List<KnowledgePointResponse> knowledgePoints,
	String learningGoal,
	String acceptanceCriteria,
	String verificationMethod,
	String verificationResult,
	String outputUrl,
	long version
) {
	public static LearningTaskResponse from(LearningTask task) {
		return new LearningTaskResponse(
			task.getId(),
			task.getTitle(),
			task.getStatus(),
			task.getPriority(),
			task.getDueAt(),
			task.getKnowledgePoints().stream().map(KnowledgePointResponse::from).toList(),
			task.getLearningGoal(),
			task.getAcceptanceCriteria(),
			task.getVerificationMethod(),
			task.getVerificationResult(),
			task.getOutputUrl(),
			task.getVersion()
		);
	}
}
