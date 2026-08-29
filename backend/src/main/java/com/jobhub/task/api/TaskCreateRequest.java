package com.jobhub.task.api;

import com.jobhub.task.domain.TaskPriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TaskCreateRequest(
	@NotBlank @Size(max = 200) String title,
	@Size(max = 100) String type,
	List<String> knowledgePointIds,
	List<String> relatedJobIds,
	List<String> relatedQuestionIds,
	TaskPriority priority,
	@Min(1) Integer estimatedMinutes,
	String dueAt,
	@Size(max = 5000) String learningGoal,
	@Size(max = 5000) String acceptanceCriteria,
	@Size(max = 1000) String verificationMethod,
	@Size(max = 2000) String outputUrl
) { }
