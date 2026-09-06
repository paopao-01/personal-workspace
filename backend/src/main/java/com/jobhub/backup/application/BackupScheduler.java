package com.jobhub.backup.application;

import com.jobhub.backup.domain.BackupRecord;
import com.jobhub.backup.domain.BackupSchedule;
import com.jobhub.backup.infrastructure.BackupScheduleMapper;
import com.jobhub.common.time.UtcTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 加密定时备份调度器：固定间隔轮询 backup_schedule 单行配置，
 * enabled 且 armed 且 cron 到点（距上次运行已过最近一个 cron 周期）时复用 BackupService.create(passphrase) 生成加密备份。
 * - armed（内存 passphrase）由 BackupScheduleService 持有，应用重启后自动 false。
 * - 未武装到点记 SKIPPED_DISARMED，不生成备份。
 * - 失败记 last_run_status=FAILED + last_run_error，不产生部分 backup_record 或残留密文文件。
 * last_run_* 由调度器系统写入（不 bump version、不参与乐观锁）。
 * 间隔由 jobhub.backup-scan-delay-ms 控制（默认 60s；e2e 置 1s；test 置 1h 并直调 runOnce 保证确定性）。
 */
@Component
public class BackupScheduler {

	private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

	private final BackupScheduleService scheduleService;
	private final BackupService backupService;
	private final BackupScheduleMapper scheduleMapper;
	private final UtcTime time;

	public BackupScheduler(BackupScheduleService scheduleService, BackupService backupService,
			BackupScheduleMapper scheduleMapper, UtcTime time) {
		this.scheduleService = scheduleService;
		this.backupService = backupService;
		this.scheduleMapper = scheduleMapper;
		this.time = time;
	}

	@Scheduled(fixedDelayString = "${jobhub.backup-scan-delay-ms:60000}",
			initialDelayString = "${jobhub.backup-scan-initial-delay-ms:1000}")
	public void scan() {
		try {
			runOnce();
		} catch (Exception ex) {
			// 调度异常不应中断后续轮询；记录但不抛出
			log.warn("backup schedule scan error: {}", ex.getMessage());
		}
	}

	/**
	 * 单次扫描判定与触发。供集成测试直调，避免依赖真实定时。
	 * 判定逻辑：enabled 且 armed 时，计算 lastRunAt 之后下一个 cron 触发点 nextDue；
	 * 若 nextDue <= now，则已到一个应当执行且尚未执行的 cron 周期 → 执行。
	 * lastRunAt 为 null 时以 epoch 起点，确保启用后首次到点立即执行。
	 */
	public void runOnce() {
		BackupSchedule schedule = scheduleService.get();
		if (!schedule.isEnabled()) {
			return;
		}
		String passphrase = scheduleService.getArmedPassphrase();
		if (passphrase == null) {
			// 未武装：到点记 SKIPPED_DISARMED，不生成备份
			recordRun("SKIPPED_DISARMED", null, null);
			return;
		}
		CronExpression cron = CronExpression.parse(BackupScheduleService.normalizeCron(schedule.getCronExpression()));
		LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
		LocalDateTime lastRun = parseLastRun(schedule.getLastRunAt());
		LocalDateTime nextDue = cron.next(lastRun);
		if (nextDue == null || nextDue.isAfter(now)) {
			// 尚未到下一个 cron 周期
			return;
		}
		try {
			BackupRecord record = backupService.create(passphrase);
			recordRun("SUCCESS", null, record.getId());
		} catch (Exception ex) {
			log.warn("scheduled backup failed: {}", ex.getMessage());
			recordRun("FAILED", truncate(ex.getMessage()), null);
		}
	}

	/** 系统写入上次运行结果，不参与乐观锁、不 bump version。 */
	private void recordRun(String status, String error, String backupId) {
		scheduleMapper.updateLastRun(time.now(), status, error, backupId);
	}

	private LocalDateTime parseLastRun(String lastRunAt) {
		if (lastRunAt == null || lastRunAt.isBlank()) {
			return LocalDateTime.of(1970, 1, 1, 0, 0);
		}
		try {
			Instant instant = Instant.parse(lastRunAt);
			return LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));
		} catch (Exception ex) {
			return LocalDateTime.of(1970, 1, 1, 0, 0);
		}
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() > 500 ? value.substring(0, 500) : value;
	}

	// 仅供测试可见的辅助：判断当前是否武装（透传 service，避免在测试中重复构造）
	boolean isArmed() {
		return scheduleService.isArmed();
	}
}
