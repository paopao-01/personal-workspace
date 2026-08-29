package com.jobhub.review.infrastructure;

public class WeakKnowledgePointRow {
	private String knowledgePointId;
	private String name;
	private String category;
	private double weightedWeaknessCount;
	private int questionCount;

	public String getKnowledgePointId() { return knowledgePointId; }
	public String getName() { return name; }
	public String getCategory() { return category; }
	public double getWeightedWeaknessCount() { return weightedWeaknessCount; }
	public int getQuestionCount() { return questionCount; }
}
