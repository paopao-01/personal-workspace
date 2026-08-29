package com.jobhub.interview.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.datamanagement.application.NotificationService;
import com.jobhub.interview.infrastructure.DueReminderRow;
import com.jobhub.interview.infrastructure.ReminderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 到期提醒调度：将 scheduled_at 已到期的 PENDING 提醒标记为 SENT，并生成一条站内通知。
 * 通知只在 PENDING -> SENT 状态转移成功时插入，重复扫描不会产生重复通知。
 * 不做外部投递（P0 无邮件/浏览器推送），失败原因由通知渠道引入时再产生。
 */
@Service
public class ReminderDispatchService {
	private static final Logger log = LoggerFactory.getLogger(ReminderDispatchService.class);

	private final ReminderMapper reminderMapper;
	private final NotificationService notificationService;
	private final UtcTime time;

	public ReminderDispatchService(ReminderMapper reminderMapper, NotificationService notificationService, UtcTime time) {
		this.reminderMapper = reminderMapper;
		this.notificationService = notificationService;
		this.time = time;
	}

	@Transactional
	public int dispatchDue() {
		String now = time.now();
		List<DueReminderRow> due = reminderMapper.selectDue(now);
		int dispatched = 0;
		for (DueReminderRow row : due) {
			// 单次状态转移保证幂等：仅转移成功的提醒生成通知
			if (reminderMapper.markSent(row.getId(), now) == 1) {
				notificationService.createFromReminder(row.getId(), "面试提醒：" + row.getRoundName(),
					reminderLabel(row.getReminderType()) + " · 面试开始时间 " + row.getScheduledAt());
				dispatched++;
			}
		}
		if (dispatched > 0) {
			log.info("Dispatched {} due reminders to notifications", dispatched);
		}
		return dispatched;
	}

	private String reminderLabel(String type) {
		return switch (type) {
			case "ONE_DAY" -> "提前 1 天";
			case "TWO_HOURS" -> "提前 2 小时";
			case "THIRTY_MINUTES" -> "提前 30 分钟";
			default -> "自定义提醒";
		};
	}
}
