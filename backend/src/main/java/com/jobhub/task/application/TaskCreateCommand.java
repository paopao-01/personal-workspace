package com.jobhub.task.application;

import com.jobhub.task.domain.TaskPriority;
import java.util.List;

public record TaskCreateCommand(
	String title,
	String type,
	List<String> knowledgePointIds,
	List<String> relatedJobIds,
	List<String> relatedQuestionIds,
	TaskPriority priority,
	Integer estimatedMinutes,
	String dueAt,
	String learningGoal,
	String acceptanceCriteria,
	String verificationMethod,
	String outputUrl
) { }
