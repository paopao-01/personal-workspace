package com.jobhub.mockinterview.domain;

public record MockInterviewEvaluationTrend(
	MockInterviewEvaluationWindow currentWindow,
	MockInterviewEvaluationWindow compareWindow,
	Double averageScoreDelta
) { }
