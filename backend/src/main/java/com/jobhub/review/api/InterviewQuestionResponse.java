package com.jobhub.review.api;

import com.jobhub.review.domain.AnswerStatus;
import com.jobhub.review.domain.InterviewQuestion;
import java.util.List;

public record InterviewQuestionResponse(
	String id,
	String reviewId,
	String content,
	AnswerStatus answerStatus,
	String type,
	String myAnswer,
	String referenceAnswer,
	Integer difficulty,
	String errorReason,
	String improvementPlan,
	List<KnowledgePointResponse> knowledgePoints,
	long version
) {
	public static InterviewQuestionResponse from(InterviewQuestion q) {
		return new InterviewQuestionResponse(
			q.getId(),
			q.getReviewId(),
			q.getContent(),
			q.getAnswerStatus(),
			q.getType(),
			q.getMyAnswer(),
			q.getReferenceAnswer(),
			q.getDifficulty(),
			q.getErrorReason(),
			q.getImprovementPlan(),
			q.getKnowledgePoints().stream().map(KnowledgePointResponse::from).toList(),
			q.getVersion()
		);
	}
}
