package com.jobhub.analytics.api;

import com.jobhub.analytics.domain.ChannelEffectiveness;
import com.jobhub.analytics.domain.ChannelEffectiveness.Group;

import java.util.List;

/**
 * 投递渠道与简历版本效果对比响应。只读原始计数，不输出趋势结论、能力等级、归因或行动建议。
 */
public record ChannelEffectivenessResponse(
		String from,
		String to,
		List<GroupView> channelGroups,
		List<GroupView> resumeVersionGroups
) {
	public record GroupView(
			String channel,
			String resumeVersion,
			long applicationCount,
			long interviewCount,
			long offerCount,
			Double offerRate
	) { }

	public static ChannelEffectivenessResponse from(ChannelEffectiveness report) {
		List<GroupView> channelGroups = report.channelGroups().stream()
			.map(g -> new GroupView(g.dimension(), null, g.applicationCount(), g.interviewCount(), g.offerCount(),
					g.offerRate()))
			.toList();
		List<GroupView> resumeVersionGroups = report.resumeVersionGroups().stream()
			.map(g -> new GroupView(null, g.dimension(), g.applicationCount(), g.interviewCount(), g.offerCount(),
					g.offerRate()))
			.toList();
		return new ChannelEffectivenessResponse(report.from(), report.to(), channelGroups, resumeVersionGroups);
	}
}
