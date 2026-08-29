package com.jobhub.review.infrastructure;

public class AnalysisKnowledgePointStatRow {
	private String knowledgePointId;
	private String name;
	private String category;
	private long questionCount;
	private long fullyAnsweredCount;

	public String getKnowledgePointId() { return knowledgePointId; }
	public String getName() { return name; }
	public String getCategory() { return category; }
	public long getQuestionCount() { return questionCount; }
	public long getFullyAnsweredCount() { return fullyAnsweredCount; }
}
