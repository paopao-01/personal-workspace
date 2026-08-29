package com.jobhub.datamanagement.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UserSettingsUpdateRequest(
	@NotBlank String timeZone,
	List<@Min(1) Integer> defaultReminderOffsetsMinutes
) { }
