package com.jobhub.interview.application;

import com.jobhub.common.time.UtcTime;
import com.jobhub.interview.infrastructure.DueReminderRow;
import com.jobhub.interview.infrastructure.ReminderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 到期提醒调度：扫描候选后逐条交给独立事务领取租约并生成站内通知。
 * 租约令牌保证多实例不会同时完成同一条提醒，通知写入失败则保留 FAILED 原因。
 */
@Service
public class ReminderDispatchService {
	private static final Logger log = LoggerFactory.getLogger(ReminderDispatchService.class);

	private final ReminderMapper reminderMapper;
	private final UtcTime time;
	private final ReminderDispatchAttemptService attemptService;

	public ReminderDispatchService(ReminderMapper reminderMapper, UtcTime time, ReminderDispatchAttemptService attemptService) {
		this.reminderMapper = reminderMapper;
		this.time = time;
		this.attemptService = attemptService;
	}

	public int dispatchDue() {
		String now = time.now();
		List<DueReminderRow> due = reminderMapper.selectDue(now);
		int dispatched = 0;
		for (DueReminderRow row : due) {
			if (attemptService.dispatch(row)) {
				dispatched++;
			}
		}
		if (dispatched > 0) {
			log.info("Dispatched {} due reminders to notifications", dispatched);
		}
		return dispatched;
	}

}
