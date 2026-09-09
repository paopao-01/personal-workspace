package com.jobhub.analytics.api;

import com.jobhub.analytics.application.ChannelEffectivenessService;
import com.jobhub.analytics.application.FunnelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 效果对比与投递漏斗转化只读聚合端点（PRD 10：高级趋势分析最小切片）。
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final ChannelEffectivenessService service;
	private final FunnelService funnelService;

	public AnalyticsController(ChannelEffectivenessService service, FunnelService funnelService) {
		this.service = service;
		this.funnelService = funnelService;
	}

	@GetMapping("/channel-effectiveness")
	public ChannelEffectivenessResponse channelEffectiveness(
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to) {
		return ChannelEffectivenessResponse.from(service.report(from, to));
	}

	@GetMapping("/funnel")
	public List<FunnelBucket> funnel(
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to,
			@RequestParam(value = "granularity", defaultValue = "day") String granularity) {
		return funnelService.report(from, to, granularity);
	}
}
