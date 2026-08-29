package com.jobhub.job.api;

import com.jobhub.job.application.MatchReportContent;
import com.jobhub.job.application.MatchReportService.MatchReportView;
import java.util.List;
import java.util.Map;

public record MatchReportResponse(
	String id,
	String jobId,
	String ruleVersion,
	Map<String, Integer> weights,
	String generatedAt,
	MatchReportContent.Summary mustSummary,
	MatchReportContent.Summary bonusSummary,
	MatchReportContent.Score mustScore,
	MatchReportContent.Score bonusScore,
	MatchReportContent.Suggestion suggestion,
	List<MatchReportContent.Item> requirements,
	boolean stale
) {
	public static MatchReportResponse from(MatchReportView view) {
		return new MatchReportResponse(
			view.id(),
			view.jobId(),
			view.ruleVersion(),
			view.weights(),
			view.generatedAt(),
			view.mustSummary(),
			view.bonusSummary(),
			view.mustScore(),
			view.bonusScore(),
			view.suggestion(),
			view.requirements(),
			view.stale()
		);
	}
}
