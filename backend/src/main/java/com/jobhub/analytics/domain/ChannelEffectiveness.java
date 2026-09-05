package com.jobhub.analytics.domain;

import java.util.List;

/**
 * 投递渠道与简历版本效果对比只读聚合（PRD 10：高级趋势分析）。
 *
 * 按 application_record.channel 与 resume_version 原始填写文本分组返回投递/面试/Offer 原始计数；
 * 计数采用状态近似口径（见状态机 §3.1）：interviewCount = status IN (INTERVIEWING, OFFER)，offerCount = status = OFFER。
 * offerRate 在 applicationCount < 2 时为 null（信息不足）。
 * 不输出趋势结论、能力等级、归因或行动建议。
 */
public record ChannelEffectiveness(
		String from,
		String to,
		List<Group> channelGroups,
		List<Group> resumeVersionGroups
) {
	/** 一组原始计数。dimension 为渠道名或简历版本文本（null 表示未指定版本）。 */
	public record Group(
			String dimension,
			long applicationCount,
			long interviewCount,
			long offerCount,
			Double offerRate
	) { }
}
