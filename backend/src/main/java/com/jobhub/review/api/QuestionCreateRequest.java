package com.jobhub.review.api;

import com.jobhub.review.domain.AnswerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionCreateRequest(
	@NotBlank @Size(max = 10000) String content,
	@NotNull AnswerStatus answerStatus,
	@Size(max = 100) String type,
	List<String> knowledgePointIds
) { }
