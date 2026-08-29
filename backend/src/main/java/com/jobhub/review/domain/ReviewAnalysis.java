package com.jobhub.review.domain;

import java.util.List;

/**
 * 跨面试复盘聚合分析（PRD 16.2：指标必须显示分母和时间范围，样本量不足时展示原始数量，不输出趋势性结论）。
 * 时间范围按面试开始日期过滤。
 */
public record ReviewAnalysis(
	String from,
	String to,
	long reviewCount,
	long questionTotalCount,
	long fullyAnsweredCount,
	long partiallyAnsweredCount,
	long unansweredCount,
	List<KnowledgePointStat> knowledgePointStats,
	List<QuestionTypeStat> questionTypeStats,
	long withResultCount,
	long passedCount,
	long failedCount,
	long pendingCount
) {
	public record KnowledgePointStat(KnowledgePoint knowledgePoint, long questionCount, long fullyAnsweredCount) { }

	public record QuestionTypeStat(String type, long questionCount, long fullyAnsweredCount) { }
}
