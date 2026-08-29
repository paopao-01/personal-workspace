package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.UserSettings;
import java.util.List;

public record UserSettingsResponse(
	String timeZone,
	List<Integer> defaultReminderOffsetsMinutes,
	long version
) {
	public static UserSettingsResponse from(UserSettings settings) {
		return new UserSettingsResponse(
			settings.getTimeZone(),
			settings.offsetsMinutes(),
			settings.getVersion()
		);
	}
}
