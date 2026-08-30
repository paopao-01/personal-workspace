package com.jobhub.task.api;

import com.jobhub.review.api.KnowledgePointResponse;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.domain.TaskPriority;
import com.jobhub.task.domain.TaskStatus;
import com.jobhub.task.application.TaskSourceRef;
import java.util.List;

public record LearningTaskResponse(
	String id,
	String title,
	TaskStatus status,
	String type,
	TaskPriority priority,
	Integer estimatedMinutes,
	String dueAt,
	List<KnowledgePointResponse> knowledgePoints,
	List<SourceRefResponse> sourceRefs,
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
			task.getType(),
			task.getPriority(),
			task.getEstimatedMinutes(),
			task.getDueAt(),
			task.getKnowledgePoints().stream().map(KnowledgePointResponse::from).toList(),
			task.getSourceRefs().stream().map(SourceRefResponse::from).toList(),
			task.getLearningGoal(),
			task.getAcceptanceCriteria(),
			task.getVerificationMethod(),
			task.getVerificationResult(),
			task.getOutputUrl(),
			task.getVersion()
		);
	}

	public record SourceRefResponse(String type, String id, String label) {
		static SourceRefResponse from(TaskSourceRef source) {
			return new SourceRefResponse(source.getType(), source.getId(), source.getLabel());
		}
	}
}
