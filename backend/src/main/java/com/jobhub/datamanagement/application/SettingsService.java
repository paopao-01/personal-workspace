package com.jobhub.datamanagement.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.UserSettings;
import com.jobhub.datamanagement.infrastructure.UserSettingsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class SettingsService {
	private static final String SINGLE_USER_ID = "00000000-0000-0000-0000-000000000001";

	private final UserSettingsMapper settingsMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public SettingsService(UserSettingsMapper settingsMapper, IdGenerator ids, UtcTime time) {
		this.settingsMapper = settingsMapper;
		this.ids = ids;
		this.time = time;
	}

	public UserSettings get() {
		UserSettings settings = settingsMapper.selectFirst();
		if (settings != null) {
			return settings;
		}
		String now = time.now();
		UserSettings created = UserSettings.create(ids.newId(), now);
		settingsMapper.insert(created.getId(), SINGLE_USER_ID, created);
		return settingsMapper.selectFirst();
	}

	@Transactional
	public UserSettings update(long expectedVersion, String timeZone, List<Integer> offsetsMinutes) {
		UserSettings settings = get();
		validateTimeZone(timeZone);
		settings.update(timeZone.trim(), normalizeOffsets(offsetsMinutes), time.now());
		VersionCheck.requireAffected(settingsMapper.update(settings, expectedVersion), settings.getVersion());
		return get();
	}

	/**
	 * 创建或改期面试时的默认提醒节点（分钟，倒序）。配置为空表示不生成默认提醒。
	 */
	public List<Integer> defaultReminderOffsetsMinutes() {
		return get().offsetsMinutes();
	}

	private void validateTimeZone(String timeZone) {
		if (timeZone == null || timeZone.isBlank()) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "时区不能为空");
		}
		try {
			ZoneId.of(timeZone.trim());
		} catch (Exception ex) {
			throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
				"无效的 IANA 时区：" + timeZone.trim() + "，例如 Asia/Shanghai 或 UTC");
		}
	}

	private List<Integer> normalizeOffsets(List<Integer> offsetsMinutes) {
		if (offsetsMinutes == null) return List.of();
		return offsetsMinutes.stream()
			.filter(Objects::nonNull)
			.filter((minutes) -> minutes >= 1)
			.distinct()
			.sorted(Comparator.reverseOrder())
			.toList();
	}
}
