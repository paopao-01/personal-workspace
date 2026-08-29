package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.application.SettingsService;
import com.jobhub.datamanagement.domain.UserSettings;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SettingsController {
	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	@GetMapping("/settings")
	public UserSettingsResponse get() {
		return UserSettingsResponse.from(service.get());
	}

	@PutMapping("/settings")
	public ResponseEntity<UserSettingsResponse> update(
			@RequestHeader(value = "If-Match-Version", required = false) Long version,
			@Valid @RequestBody UserSettingsUpdateRequest request) {
		if (version == null) {
			return ResponseEntity.badRequest().build();
		}
		UserSettings settings = service.update(version, request.timeZone(), request.defaultReminderOffsetsMinutes());
		return ResponseEntity.ok(UserSettingsResponse.from(settings));
	}
}
