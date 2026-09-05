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
		InterviewResultSummary interviewResultSummary,
		WeakPointComparison weakPointComparison,
		AnswerStatusComparison answerStatusComparison
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

	public record WeakPointComparison(
		TimeRange compareTimeRange,
		List<WeakPointComparisonItem> items
	) { }

	public record WeakPointComparisonItem(
		KnowledgePoint knowledgePoint,
		double currentWeightedWeaknessCount,
		double compareWeightedWeaknessCount,
		double delta,
		int currentQuestionCount,
		int compareQuestionCount
	) { }
	public record AnswerStatusComparison(
			TimeRange compareTimeRange,
			long currentTotalCount,
			long compareTotalCount,
			long currentFullyAnsweredCount,
			long compareFullyAnsweredCount,
			long currentPartiallyAnsweredCount,
			long comparePartiallyAnsweredCount,
			long currentUnansweredCount,
			long compareUnansweredCount
	) { }

	public static ReviewAnalysisResponse from(ReviewAnalysis analysis) {
		var knowledgePointStats = analysis.knowledgePointStats().stream()
			.map(stat -> new KnowledgePointStat(stat.knowledgePoint(), stat.questionCount(),
				stat.fullyAnsweredCount(), stat.questionCount() - stat.fullyAnsweredCount()))
			.toList();
		var questionTypeStats = analysis.questionTypeStats().stream()
			.map(stat -> new QuestionTypeStat(stat.type(), stat.questionCount(), stat.fullyAnsweredCount()))
			.toList();
		WeakPointComparison weakPointComparison = analysis.weakPointComparison() == null ? null : new WeakPointComparison(
			new TimeRange(analysis.weakPointComparison().compareFrom(), analysis.weakPointComparison().compareTo()),
			analysis.weakPointComparison().items().stream().map(item -> new WeakPointComparisonItem(item.knowledgePoint(),
				item.currentWeightedWeaknessCount(), item.compareWeightedWeaknessCount(), item.delta(),
				item.currentQuestionCount(), item.compareQuestionCount())).toList());
		AnswerStatusComparison answerStatusComparison = analysis.answerStatusComparison() == null ? null
			: new AnswerStatusComparison(
				new TimeRange(analysis.answerStatusComparison().compareFrom(), analysis.answerStatusComparison().compareTo()),
				analysis.answerStatusComparison().currentTotalCount(), analysis.answerStatusComparison().compareTotalCount(),
				analysis.answerStatusComparison().currentFullyAnsweredCount(), analysis.answerStatusComparison().compareFullyAnsweredCount(),
				analysis.answerStatusComparison().currentPartiallyAnsweredCount(), analysis.answerStatusComparison().comparePartiallyAnsweredCount(),
				analysis.answerStatusComparison().currentUnansweredCount(), analysis.answerStatusComparison().compareUnansweredCount());
		return new ReviewAnalysisResponse(
			new TimeRange(analysis.from(), analysis.to()),
			analysis.reviewCount(),
			new QuestionStats(analysis.questionTotalCount(), analysis.fullyAnsweredCount(),
				analysis.partiallyAnsweredCount(), analysis.unansweredCount(),
				new RateFraction(analysis.fullyAnsweredCount(), analysis.questionTotalCount())),
			knowledgePointStats,
			questionTypeStats,
			new InterviewResultSummary(analysis.reviewCount(), analysis.withResultCount(), analysis.passedCount(),
				analysis.failedCount(), analysis.pendingCount()),
			weakPointComparison,
			answerStatusComparison
		);
	}
}
