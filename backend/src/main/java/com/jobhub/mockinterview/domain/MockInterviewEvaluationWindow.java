package com.jobhub.mockinterview.domain;

import java.util.List;

public record MockInterviewEvaluationWindow(
	String from,
	String to,
	int evaluatedAnswerCount,
	int evaluatedSessionCount,
	Double averageScore,
	List<MockInterviewScoreCount> scoreDistribution
) { }
