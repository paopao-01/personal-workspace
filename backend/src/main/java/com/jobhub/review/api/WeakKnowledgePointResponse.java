package com.jobhub.review.api;

import com.jobhub.review.domain.WeakKnowledgePoint;
import java.util.List;

public record WeakKnowledgePointResponse(
	KnowledgePointResponse knowledgePoint,
	double weightedWeaknessCount,
	int questionCount,
	List<InterviewQuestionResponse> questions
) {
	public static WeakKnowledgePointResponse from(WeakKnowledgePoint weak) {
		return new WeakKnowledgePointResponse(
			KnowledgePointResponse.from(weak.getKnowledgePoint()),
			weak.getWeightedWeaknessCount(),
			weak.getQuestionCount(),
			weak.getQuestions().stream().map(InterviewQuestionResponse::from).toList()
		);
	}
}
