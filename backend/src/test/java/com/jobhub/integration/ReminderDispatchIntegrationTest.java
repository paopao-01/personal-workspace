package com.jobhub.integration;

import com.jobhub.integration.support.*;
import com.jobhub.interview.application.ReminderDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ReminderDispatchIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ReminderDispatchService dispatchService;

	@Test
	void P1_dispatchMarksDueRemindersSentAndCreatesNotificationsOnce() {
		String interviewId = createInterview(Instant.now().plus(Duration.ofMinutes(10)));
		String remindersBefore = restTemplate.getForEntity(url("/interviews/" + interviewId + "/reminders"), String.class).getBody();
		assertThat(JsonProbe.arraySize(remindersBefore, "")).isEqualTo(3);
		assertThat(JsonProbe.arrStr(remindersBefore, "", 0, "status")).isEqualTo("PENDING");

		assertThat(dispatchService.dispatchDue()).isEqualTo(3);

		String remindersAfter = restTemplate.getForEntity(url("/interviews/" + interviewId + "/reminders"), String.class).getBody();
		for (int i = 0; i < 3; i++) {
			assertThat(JsonProbe.arrStr(remindersAfter, "", i, "status")).isEqualTo("SENT");
		}

		String notifications = restTemplate.getForEntity(url("/notifications"), String.class).getBody();
		assertThat(JsonProbe.arraySize(notifications, "")).isEqualTo(3);
		assertThat(JsonProbe.arrStr(notifications, "", 0, "title")).contains("P1 一面");
		assertThat(JsonProbe.arrStr(notifications, "", 0, "reminderId")).isNotNull();
		assertThat(JsonProbe.str(notifications, "0.readAt")).isEqualTo("null");

		// 重复扫描不产生重复通知（单次状态转移去重）
		assertThat(dispatchService.dispatchDue()).isEqualTo(0);
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/notifications"), String.class).getBody(), ""))
			.isEqualTo(3);

		// 标记已读幂等：重复调用返回同一 readAt
		String notificationId = JsonProbe.arrStr(notifications, "", 0, "id");
		String readOnce = restTemplate.exchange(url("/notifications/" + notificationId + "/read"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(readOnce, "readAt")).isNotNull();
		String readTwice = restTemplate.exchange(url("/notifications/" + notificationId + "/read"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(readTwice, "readAt")).isEqualTo(JsonProbe.str(readOnce, "readAt"));

		ResponseEntity<String> missing = restTemplate.exchange(
			url("/notifications/99999999-9999-9999-9999-999999999999/read"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void P1_dispatchSkipsFutureAndCanceledReminders() {
		String interviewId = createInterview(Instant.now().plus(Duration.ofDays(2)));
		// 未到期：无通知
		assertThat(dispatchService.dispatchDue()).isEqualTo(0);
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/notifications"), String.class).getBody(), ""))
			.isEqualTo(0);

		// 取消面试后 PENDING 提醒全部 CANCELED，调度跳过
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/cancel"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
		assertThat(dispatchService.dispatchDue()).isEqualTo(0);
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/notifications"), String.class).getBody(), ""))
			.isEqualTo(0);
	}

	@Test
	void P1_expiredProcessingLeaseCanBeReclaimedWithoutDuplicateNotification() {
		String interviewId = createInterview(Instant.now().plus(Duration.ofMinutes(10)));
		String reminders = restTemplate.getForEntity(url("/interviews/" + interviewId + "/reminders"), String.class).getBody();
		String reminderId = JsonProbe.arrStr(reminders, "", 0, "id");
		jdbc.update("UPDATE interview_reminder SET status='PROCESSING', attempt_count=7, lease_until=?, lease_token=? WHERE id=?",
			"2000-01-01T00:00:00Z", "expired-worker", reminderId);

		assertThat(dispatchService.dispatchDue()).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT status FROM interview_reminder WHERE id=?", String.class, reminderId))
			.isEqualTo("SENT");
		assertThat(jdbc.queryForObject("SELECT attempt_count FROM interview_reminder WHERE id=?", Integer.class, reminderId))
			.isEqualTo(8);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE reminder_id=?", Integer.class, reminderId))
			.isEqualTo(1);
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/notifications"), String.class).getBody(), ""))
			.isEqualTo(3);
	}

	@Test
	void P1_failedReminderCanBeRetriedWithVersionAndStaleVersionHasNoSideEffect() {
		String interviewId = createInterview(Instant.now().plus(Duration.ofMinutes(10)));
		String reminders = restTemplate.getForEntity(url("/interviews/" + interviewId + "/reminders"), String.class).getBody();
		String reminderId = JsonProbe.arrStr(reminders, "", 0, "id");
		long initialVersion = JsonProbe.lng(reminders, "0.version");
		jdbc.update("UPDATE interview_reminder SET status='FAILED', failure_reason=?, attempt_count=4 WHERE id=?",
			"SMTP 暂时不可用", reminderId);

		ResponseEntity<String> retried = restTemplate.exchange(url("/reminders/" + reminderId + "/retry"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(initialVersion)), String.class);
		assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(retried.getBody(), "status")).isEqualTo("PENDING");
		assertThat(JsonProbe.str(retried.getBody(), "failureReason")).isEqualTo("null");
		assertThat(JsonProbe.lng(retried.getBody(), "attemptCount")).isEqualTo(4);

		ResponseEntity<String> stale = restTemplate.exchange(url("/reminders/" + reminderId + "/retry"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(initialVersion)), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(jdbc.queryForObject("SELECT status FROM interview_reminder WHERE id=?", String.class, reminderId))
			.isEqualTo("PENDING");
		assertThat(dispatchService.dispatchDue()).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE reminder_id=?", Integer.class, reminderId))
			.isEqualTo(1);
	}

	private String createInterview(Instant startsAt) {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("提醒科技", "P1 通知岗位")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "P1 渠道", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transitionApplication(applicationId, "APPLIED", "0");
		transitionApplication(applicationId, "RESUME_PASSED", "1");
		return JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"P1 一面\",\"startsAt\":\""
				+ startsAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) + "\",\"eventTimeZone\":\"Asia/Shanghai\"}",
			"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private void transitionApplication(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}
}
