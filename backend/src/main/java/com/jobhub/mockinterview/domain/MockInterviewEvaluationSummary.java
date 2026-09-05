package com.jobhub.mockinterview.domain;
import java.util.List;
public record MockInterviewEvaluationSummary(int evaluatedAnswerCount, int evaluatedSessionCount, Double averageScore, List<MockInterviewScoreCount> scoreDistribution, List<MockInterviewRecentScore> recentScores) {}
