package com.jobhub.testsupport.api;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e")
@RestController
@RequestMapping("/api/e2e")
public class E2eReminderFixtureController {

	private final JdbcTemplate jdbc;

	public E2eReminderFixtureController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/reminders/{id}/mark-sent")
	public Map<String, Object> markSent(@PathVariable String id) {
		int updated = jdbc.update(
			"UPDATE interview_reminder SET status='SENT', updated_at=datetime('now'), version=version+1 WHERE id=?",
			id
		);
		return Map.of("updated", updated);
	}
}
