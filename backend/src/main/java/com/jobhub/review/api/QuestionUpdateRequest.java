package com.jobhub.review.api;

import com.jobhub.review.domain.AnswerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionUpdateRequest(
	@NotBlank @Size(max = 10000) String content,
	@NotNull AnswerStatus answerStatus,
	@Size(max = 100) String type,
	List<String> knowledgePointIds,
	@Size(max = 20000) String myAnswer,
	@Size(max = 20000) String referenceAnswer,
	@Min(1) @Max(5) Integer difficulty,
	@Size(max = 5000) String errorReason,
	@Size(max = 5000) String improvementPlan
) { }
