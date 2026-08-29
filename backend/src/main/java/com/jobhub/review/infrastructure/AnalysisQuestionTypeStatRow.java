package com.jobhub.review.infrastructure;

public class AnalysisQuestionTypeStatRow {
	private String type;
	private long questionCount;
	private long fullyAnsweredCount;

	public String getType() { return type; }
	public long getQuestionCount() { return questionCount; }
	public long getFullyAnsweredCount() { return fullyAnsweredCount; }
}
