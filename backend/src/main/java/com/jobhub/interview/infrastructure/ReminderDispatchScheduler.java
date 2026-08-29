package com.jobhub.interview.infrastructure;

import com.jobhub.interview.application.ReminderDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 站内提醒调度器：按可配置间隔扫描到期提醒并生成通知。
 * 间隔由 jobhub.reminder-scan-delay-ms 控制（默认 60s；e2e 置 1s；test 置 1h 并直调服务）。
 * 启动后 1s 执行首次扫描，覆盖应用重启前累积的到期提醒（进入应用后可见）。
 */
@Component
public class ReminderDispatchScheduler {
	private final ReminderDispatchService dispatchService;

	public ReminderDispatchScheduler(ReminderDispatchService dispatchService) {
		this.dispatchService = dispatchService;
	}

	@Scheduled(fixedDelayString = "${jobhub.reminder-scan-delay-ms:60000}",
			initialDelayString = "${jobhub.reminder-scan-initial-delay-ms:1000}")
	public void dispatch() {
		dispatchService.dispatchDue();
	}
}
