package com.jobhub.interview.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 本地单用户定时任务开关。当前仅提醒到期扫描；后续通知渠道接入时复用。
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
