package com.jobhub.analytics.api;

import com.jobhub.analytics.application.ChannelEffectivenessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 效果对比只读聚合端点（PRD 10：高级趋势分析最小切片）。
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final ChannelEffectivenessService service;

	public AnalyticsController(ChannelEffectivenessService service) {
		this.service = service;
	}

	@GetMapping("/channel-effectiveness")
	public ChannelEffectivenessResponse channelEffectiveness(
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to) {
		return ChannelEffectivenessResponse.from(service.report(from, to));
	}
}
