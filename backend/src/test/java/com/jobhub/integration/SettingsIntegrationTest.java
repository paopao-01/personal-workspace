package com.jobhub.integration;

import com.jobhub.integration.support.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsIntegrationTest extends AbstractIntegrationTest {

	private static final String SEED_SETTINGS_ID = "00000000-0000-0000-0000-000000000002";

	@Test
	void P1_settingsRoundTrip_updatesRemindersAndGuardsVersions() {
		String seed = restTemplate.getForEntity(url("/settings"), String.class).getBody();
		assertThat(JsonProbe.str(seed, "timeZone")).isEqualTo("Asia/Shanghai");
		assertThat(JsonProbe.str(seed, "defaultReminderOffsetsMinutes.0")).isEqualTo("1440");
		assertThat(JsonProbe.lng(seed, "version")).isEqualTo(0);

		// 更新：重复与乱序偏移会被去重并倒序
		String updated = restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"UTC\",\"defaultReminderOffsetsMinutes\":[60,1440,60,90]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.str(updated, "timeZone")).isEqualTo("UTC");
		assertThat(JsonProbe.str(updated, "defaultReminderOffsetsMinutes.0")).isEqualTo("1440");
		assertThat(JsonProbe.str(updated, "defaultReminderOffsetsMinutes.1")).isEqualTo("90");
		assertThat(JsonProbe.str(updated, "defaultReminderOffsetsMinutes.2")).isEqualTo("60");
		assertThat(JsonProbe.lng(updated, "version")).isEqualTo(1);

		ResponseEntity<String> stale = restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"UTC\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		ResponseEntity<String> missingVersion = restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"UTC\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> invalidZone = restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"Mars/Phobos\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "1"), String.class);
		assertThat(invalidZone.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalidZone.getBody()).contains("VALIDATION_ERROR");

		ResponseEntity<String> invalidOffset = restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"UTC\",\"defaultReminderOffsetsMinutes\":[0]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "1"), String.class);
		assertThat(invalidOffset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void P1_interviewDefaultRemindersFollowConfiguredOffsets() {
		String interviewA = createInterviewWithOffsets("[90]", "2026-09-10T10:00:00Z");
		String remindersA = restTemplate.getForEntity(url("/interviews/" + interviewA + "/reminders"), String.class).getBody();
		assertThat(JsonProbe.arraySize(remindersA, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(remindersA, "", 0, "reminderType")).isEqualTo("CUSTOM");
		assertThat(JsonProbe.arrStr(remindersA, "", 0, "scheduledAt"))
			.isEqualTo(Instant.parse("2026-09-10T10:00:00Z").minusSeconds(90 * 60).toString());

		// 空配置表示关闭默认提醒节点：新面试不生成提醒
		String interviewB = createInterviewWithOffsets("[]", "2026-09-11T10:00:00Z");
		String remindersB = restTemplate.getForEntity(url("/interviews/" + interviewB + "/reminders"), String.class).getBody();
		assertThat(JsonProbe.arraySize(remindersB, "")).isEqualTo(0);

		// 改期按"当前配置"重新生成：先改为 [45] 再改期
		updateOffsets("[45]");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewB), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewB + "/reschedule"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"startsAt\":\"2026-09-12T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)), String.class);
		String remindersAfter = restTemplate.getForEntity(url("/interviews/" + interviewB + "/reminders"), String.class).getBody();
		assertThat(JsonProbe.arraySize(remindersAfter, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(remindersAfter, "", 0, "scheduledAt"))
			.isEqualTo(Instant.parse("2026-09-12T10:00:00Z").minusSeconds(45 * 60).toString());
	}

	private void updateOffsets(String offsetsJson) {
		restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"Asia/Shanghai\",\"defaultReminderOffsetsMinutes\":" + offsetsJson + "}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", currentSettingsVersion()), String.class);
	}

	private String createInterviewWithOffsets(String offsetsJson, String startsAt) {
		restTemplate.exchange(url("/settings"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"timeZone\":\"Asia/Shanghai\",\"defaultReminderOffsetsMinutes\":" + offsetsJson + "}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", currentSettingsVersion()), String.class);
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("提醒科技", "P1 岗位")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "P1 渠道", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transitionApplication(applicationId, "APPLIED", "0");
		transitionApplication(applicationId, "RESUME_PASSED", "1");
		return JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"P1 一面\",\"startsAt\":\"" + startsAt + "\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private String currentSettingsVersion() {
		return String.valueOf(JsonProbe.lng(restTemplate.getForEntity(url("/settings"), String.class).getBody(), "version"));
	}

	private void transitionApplication(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}
}
