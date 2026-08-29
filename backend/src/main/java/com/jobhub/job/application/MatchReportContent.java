package com.jobhub.job.application;

import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.RequirementType;

import java.util.List;

/**
 * 匹配报告内容（序列化为 match_report.report_json 数据快照）。
 * 计分规则 MATCH_RULE_V1：满足=1、自报无证据=0.5、未满足=0；缺少资料的要求不计入分母（不按零分处理）。
 */
public record MatchReportContent(
	List<Item> requirements,
	Summary mustSummary,
	Summary bonusSummary,
	Score mustScore,
	Score bonusScore,
	Suggestion suggestion
) {

	public record Item(
		String requirementId,
		String normalizedName,
		String rawText,
		RequirementType type,
		GapStatus status,
		List<String> reasons
	) { }

	public record Summary(
		int total,
		int satisfiedWithEvidence,
		int selfReportedNoEvidence,
		int notMet,
		int insufficientInfo
	) { }

	public record Score(double numerator, double denominator, int weight) { }

	public record Suggestion(String type, List<String> reasons) { }
}
