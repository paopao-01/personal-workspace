package com.jobhub.interview.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.datamanagement.application.NotificationService;
import com.jobhub.interview.infrastructure.DueReminderRow;
import com.jobhub.interview.infrastructure.ReminderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 单条提醒的领取与完成事务，避免批量扫描持有长事务。 */
@Service
public class ReminderDispatchAttemptService {
	private static final Logger log = LoggerFactory.getLogger(ReminderDispatchAttemptService.class);
	private static final Duration LEASE_DURATION = Duration.ofSeconds(60);

	private final ReminderMapper reminderMapper;
	private final NotificationService notificationService;
	private final IdGenerator ids;
	private final UtcTime time;

	public ReminderDispatchAttemptService(ReminderMapper reminderMapper, NotificationService notificationService,
			IdGenerator ids, UtcTime time) {
		this.reminderMapper = reminderMapper;
		this.notificationService = notificationService;
		this.ids = ids;
		this.time = time;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean dispatch(DueReminderRow row) {
		String now = time.now();
		String leaseToken = ids.newId();
		String leaseUntil = Instant.parse(now).plus(LEASE_DURATION).toString();
		if (reminderMapper.claim(row.getId(), now, leaseUntil, leaseToken) != 1) {
			return false;
		}
		try {
			notificationService.createFromReminder(row.getId(), "面试提醒：" + row.getRoundName(),
				reminderLabel(row.getReminderType()) + " · 面试开始时间 " + row.getScheduledAt());
			return reminderMapper.markSent(row.getId(), time.now(), leaseToken) == 1;
		} catch (Exception ex) {
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > 500) {
				reason = reason.substring(0, 500);
			}
			reminderMapper.markFailed(row.getId(), reason, time.now(), leaseToken);
			log.warn("Reminder {} dispatch failed: {}", row.getId(), reason);
			return false;
		}
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
