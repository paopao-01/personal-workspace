package com.jobhub.mockinterview.domain;
public record MockInterviewTurn(String id, String sessionId, int turnNumber, String speaker, String content,
	String evaluationAiJobId, Integer evaluationScore, String evaluationFeedback, String evaluationRationale,
	String createdAt) {
	public MockInterviewTurn(String id, String sessionId, int turnNumber, String speaker, String content, String createdAt) {
		this(id, sessionId, turnNumber, speaker, content, null, null, null, null, createdAt);
	}
}
