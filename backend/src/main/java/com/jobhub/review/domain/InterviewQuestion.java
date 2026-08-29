package com.jobhub.review.domain;

import java.util.List;

public class InterviewQuestion {
	private String id;
	private String reviewId;
	private String content;
	private AnswerStatus answerStatus;
	private String type;
	private String myAnswer;
	private String referenceAnswer;
	private Integer difficulty;
	private String errorReason;
	private String improvementPlan;
	private String createdAt;
	private String updatedAt;
	private long version;
	private List<KnowledgePoint> knowledgePoints = List.of();

	public static InterviewQuestion create(String id, String reviewId, String content, AnswerStatus answerStatus, String type, String now) {
		InterviewQuestion q = new InterviewQuestion();
		q.id = id;
		q.reviewId = reviewId;
		q.content = content;
		q.answerStatus = answerStatus;
		q.type = type;
		q.createdAt = now;
		q.updatedAt = now;
		return q;
	}

	public void update(String content, AnswerStatus answerStatus, String type, String myAnswer, String referenceAnswer,
			Integer difficulty, String errorReason, String improvementPlan, String now) {
		this.content = content;
		this.answerStatus = answerStatus;
		this.type = type;
		this.myAnswer = myAnswer;
		this.referenceAnswer = referenceAnswer;
		this.difficulty = difficulty;
		this.errorReason = errorReason;
		this.improvementPlan = improvementPlan;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public String getReviewId() { return reviewId; }
	public String getContent() { return content; }
	public AnswerStatus getAnswerStatus() { return answerStatus; }
	public String getType() { return type; }
	public String getMyAnswer() { return myAnswer; }
	public String getReferenceAnswer() { return referenceAnswer; }
	public Integer getDifficulty() { return difficulty; }
	public String getErrorReason() { return errorReason; }
	public String getImprovementPlan() { return improvementPlan; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
	public List<KnowledgePoint> getKnowledgePoints() { return knowledgePoints == null ? List.of() : knowledgePoints; }
	public void setKnowledgePoints(List<KnowledgePoint> knowledgePoints) { this.knowledgePoints = knowledgePoints == null ? List.of() : knowledgePoints; }
}
