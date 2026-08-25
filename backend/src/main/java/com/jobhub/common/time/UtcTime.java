package com.jobhub.common.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
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
}
