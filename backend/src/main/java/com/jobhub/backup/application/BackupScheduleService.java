package com.jobhub.backup.application;

import com.jobhub.backup.domain.BackupSchedule;
import com.jobhub.backup.infrastructure.BackupScheduleMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;
import com.jobhub.common.version.VersionCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时备份调度配置服务：单行配置（singleton）的懒初始化、乐观锁更新与内存武装。
 * passphrase 仅存本类 volatile 字段（armed=true 时持有），不落盘、不回显、不进日志；
 * 应用重启后本实例重新构造，armed 自然为 false，需用户重新武装。
 */
@Service
public class BackupScheduleService {

	private final BackupScheduleMapper mapper;

	/** 内存武装 passphrase（volatile 保证跨线程可见，应用重启后清空）。永不持久化。 */
	private volatile String armedPassphrase;

	public BackupScheduleService(BackupScheduleMapper mapper) {
		this.mapper = mapper;
	}

	/**
	 * 读取单行配置；不存在时懒插入默认行（enabled=false、cron=默认、version=0）。
	 */
	@Transactional
	public BackupSchedule get() {
		BackupSchedule schedule = mapper.selectSingleton();
		if (schedule != null) {
			return schedule;
		}
		BackupSchedule created = new BackupSchedule();
		created.setId(BackupSchedule.SINGLETON_ID);
		created.setCronExpression(BackupSchedule.DEFAULT_CRON);
		created.setEnabled(false);
		created.setVersion(0);
		mapper.insert(created);
		return mapper.selectSingleton();
	}

	/**
	 * 乐观锁更新调度配置（cron + enabled）。本端点不接收 passphrase——
	 * passphrase 经 arm 端点单独武装，仅存内存。非法 cron 返回 422。
	 */
	@Transactional
	public BackupSchedule update(long expectedVersion, String cronExpression, boolean enabled) {
		validateCron(cronExpression);
		BackupSchedule current = get();
		current.setCronExpression(normalizeCron(cronExpression));
		current.setEnabled(enabled);
		int affected = mapper.updateConfig(current, expectedVersion);
		VersionCheck.requireAffected(affected, current.getVersion(), "BackupSchedule", BackupSchedule.SINGLETON_ID);
		return get();
	}

	/**
	 * 武装调度器：将 passphrase 写入进程内存。先校验长度（>=8），再强制强度门槛
	 * （要求 strong：score<70 返回 400，弱与中均拒绝），武装后 armed=true；重复武装覆盖旧 passphrase。
	 * passphrase 永不落盘、不回显。
	 */
	@Transactional
	public BackupSchedule arm(String passphrase) {
		if (passphrase == null || passphrase.length() < 8 || passphrase.length() > 256) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "passphrase 长度需在 8–256 之间");
		}
		// 强度门槛（强制，要求 strong）：score<70（弱或中）不写入内存武装，返回 400。
		PassphraseStrengthValidator.requireAcceptable(passphrase);
		this.armedPassphrase = passphrase;
		return get();
	}

	/**
	 * 解除武装：清空内存 passphrase（用户主动或应用重启后调用，亦可由 restart 重置）。
	 */
	@Transactional
	public BackupSchedule disarm() {
		this.armedPassphrase = null;
		return get();
	}

	/**
	 * 调度器读取内存 passphrase；未武装返回 null。
	 * 仅 BackupScheduler 在轮询触发时调用，读取后不持有引用副本（按需取用）。
	 */
	public String getArmedPassphrase() {
		return armedPassphrase;
	}

	public boolean isArmed() {
		return armedPassphrase != null;
	}

	/**
	 * cron 表达式校验：使用 Spring CronExpression 解析，非法返回 422。
	 * 接受 5 字段（分 时 日 月 周）或 6 字段（秒 分 时 日 月 周）；5 字段自动前补 "0" 秒。
	 */
	private void validateCron(String cronExpression) {
		if (cronExpression == null || cronExpression.isBlank()) {
			throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_ERROR, "cron 表达式不能为空");
		}
		try {
			org.springframework.scheduling.support.CronExpression.parse(normalizeCron(cronExpression));
		} catch (IllegalArgumentException ex) {
			throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_ERROR,
				"无效的 cron 表达式：" + cronExpression.trim() + "，例如 0 3 * * * 表示每天 3 点");
		}
	}

	/**
	 * 将 5 字段 cron 归一化为 Spring CronExpression 所需的 6 字段（前补 "0" 秒）。
	 * 6 字段原样返回。多处调用统一，保证校验、存储、调度解析一致。
	 */
	public static String normalizeCron(String cronExpression) {
		String trimmed = cronExpression == null ? "" : cronExpression.trim();
		long fieldCount = trimmed.chars().filter(c -> c == ' ').count() + 1;
		if (fieldCount == 5) {
			return "0 " + trimmed;
		}
		return trimmed;
	}
}
