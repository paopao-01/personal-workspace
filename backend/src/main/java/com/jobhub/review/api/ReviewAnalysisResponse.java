package com.jobhub.review.api;

import com.jobhub.review.domain.KnowledgePoint;
import com.jobhub.review.domain.ReviewAnalysis;
import java.util.List;

public record ReviewAnalysisResponse(
	TimeRange timeRange,
	long reviewCount,
	QuestionStats questionStats,
	List<KnowledgePointStat> knowledgePointStats,
	List<QuestionTypeStat> questionTypeStats,
	InterviewResultSummary interviewResultSummary
) {
	public record TimeRange(String from, String to) { }

	public record RateFraction(long numerator, long denominator) { }

	public record QuestionStats(
		long totalCount,
		long fullyAnsweredCount,
		long partiallyAnsweredCount,
		long unansweredCount,
		RateFraction fullyAnswered
	) { }

	public record KnowledgePointStat(
		KnowledgePoint knowledgePoint,
		long questionCount,
		long fullyAnsweredCount,
		long notFullyAnsweredCount
	) { }


	public record QuestionTypeStat(String type, long questionCount, long fullyAnsweredCount) { }

	public record InterviewResultSummary(
		long reviewCount,
		long withResultCount,
		long passedCount,
		long failedCount,
		long pendingCount
	) { }

	public static ReviewAnalysisResponse from(ReviewAnalysis analysis) {
		var knowledgePointStats = analysis.knowledgePointStats().stream()
			.map(stat -> new KnowledgePointStat(stat.knowledgePoint(), stat.questionCount(),
				stat.fullyAnsweredCount(), stat.questionCount() - stat.fullyAnsweredCount()))
			.toList();
		var questionTypeStats = analysis.questionTypeStats().stream()
			.map(stat -> new QuestionTypeStat(stat.type(), stat.questionCount(), stat.fullyAnsweredCount()))
			.toList();
		return new ReviewAnalysisResponse(
			new TimeRange(analysis.from(), analysis.to()),
			analysis.reviewCount(),
			new QuestionStats(analysis.questionTotalCount(), analysis.fullyAnsweredCount(),
				analysis.partiallyAnsweredCount(), analysis.unansweredCount(),
				new RateFraction(analysis.fullyAnsweredCount(), analysis.questionTotalCount())),
			knowledgePointStats,
			questionTypeStats,
			new InterviewResultSummary(analysis.reviewCount(), analysis.withResultCount(), analysis.passedCount(),
				analysis.failedCount(), analysis.pendingCount())
		);
	}
}
