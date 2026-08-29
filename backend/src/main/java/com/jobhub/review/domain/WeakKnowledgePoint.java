package com.jobhub.review.domain;

import java.util.List;

public class WeakKnowledgePoint {
	private final KnowledgePoint knowledgePoint;
	private final double weightedWeaknessCount;
	private final int questionCount;
	private final List<InterviewQuestion> questions;

	public WeakKnowledgePoint(KnowledgePoint knowledgePoint, double weightedWeaknessCount, int questionCount,
			List<InterviewQuestion> questions) {
		this.knowledgePoint = knowledgePoint;
		this.weightedWeaknessCount = weightedWeaknessCount;
		this.questionCount = questionCount;
		this.questions = questions == null ? List.of() : questions;
	}

	public KnowledgePoint getKnowledgePoint() { return knowledgePoint; }
	public double getWeightedWeaknessCount() { return weightedWeaknessCount; }
	public int getQuestionCount() { return questionCount; }
	public List<InterviewQuestion> getQuestions() { return questions; }
}
