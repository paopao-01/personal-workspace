package com.jobhub.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * UTC ISO-8601 时间戳生成器。所有持久化时间字段使用本工具产出。
 * 面试日程额外保存事件时区（不在本工具职责内）。
 */
@Component
public class UtcTime {

	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

	private final Clock clock;

	public UtcTime(Clock clock) {
		this.clock = clock;
	}

	public String now() {
		return ISO.format(Instant.now(clock));
	}

	/**
	 * 返回「当前 UTC − N 天」的 ISO-8601 时间戳，作为按龄清理的阈值（created_at < 此值即删）。
	 * 受 Clock 控制，测试可注入固定时钟使阈值可预测。
	 */
	public String nowMinusDays(long days) {
		return ISO.format(Instant.now(clock).minus(Duration.ofDays(days)));
	}
}
