package com.jobhub.integration;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jobhub.integration.support.*;
import com.jobhub.interview.application.ReminderDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 通知渠道（PRD 9.3）：渠道配置/测试通知/渠道投递状态；站内通知始终保留。
 */
class NotificationChannelIntegrationTest extends AbstractIntegrationTest {

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired
	private ReminderDispatchService dispatchService;

	@Autowired
	private com.jobhub.datamanagement.application.EmailDeliveryService emailDeliveryService;

	@BeforeEach
	void resetMail() {
		greenMail.reset();
	}

	@Test
	void P1_emailChannelDeliversViaConfiguredSmtpAndRecordsSent() throws Exception {
		String channelBody = putChannel("EMAIL", 0, true,
			"{\"smtpHost\":\"127.0.0.1\",\"smtpPort\":3025,\"fromAddress\":\"jobhub@test.local\",\"toAddress\":\"user@test.local\"}");
		assertThat(JsonProbe.str(channelBody, "enabled")).isEqualTo("true");
		assertThat(JsonProbe.str(channelBody, "hasCredential")).isEqualTo("false");
		assertThat(channelBody).doesNotContain("\"password\"");

		createDueInterviewAndDispatch();
		assertThat(emailDeliveryService.attemptPending()).isEqualTo(3);

		String notifications = restTemplate.getForEntity(url("/notifications"), String.class).getBody();
		for (int i = 0; i < 3; i++) {
			assertThat(JsonProbe.arrStr(notifications, "", i, "deliveries.0.channelType")).isEqualTo("EMAIL");
			assertThat(JsonProbe.arrStr(notifications, "", i, "deliveries.0.status")).isEqualTo("SENT");
			assertThat(JsonProbe.arrStr(notifications, "", i, "deliveries.0.sentAt")).isNotEqualTo("null");
		}
		assertThat(greenMail.getReceivedMessages()).hasSize(3);
		assertThat(greenMail.getReceivedMessages()[0].getSubject()).contains("面试提醒");
		// 站内通知内容完整保留
		assertThat(JsonProbe.arrStr(notifications, "", 0, "content")).isNotNull();
	}

	@Test
	void P1_emailChannelFailureRecordedAndInSiteNotificationKept() {
		putChannel("EMAIL", 0, true,
			"{\"smtpHost\":\"127.0.0.1\",\"smtpPort\":1,\"toAddress\":\"user@test.local\"}");

		createDueInterviewAndDispatch();
		// 第一次失败：状态保持 PENDING 以便重试，失败原因已记录
		emailDeliveryService.attemptPending();

		String notifications = restTemplate.getForEntity(url("/notifications"), String.class).getBody();
		assertThat(JsonProbe.arraySize(notifications, "")).isEqualTo(3);
		for (int i = 0; i < 3; i++) {
			assertThat(JsonProbe.arrStr(notifications, "", i, "deliveries.0.status")).isEqualTo("PENDING");
			assertThat(JsonProbe.arrStr(notifications, "", i, "deliveries.0.failureReason")).isNotEqualTo("null");
		}
		// 站内提醒始终保留（PRD 9.3）
		assertThat(JsonProbe.arrStr(notifications, "", 0, "title")).contains("面试提醒");

		// 重试达到上限（3 次）后置 FAILED，不再尝试
		emailDeliveryService.attemptPending();
		emailDeliveryService.attemptPending();
		emailDeliveryService.attemptPending();
		String afterRetries = restTemplate.getForEntity(url("/notifications"), String.class).getBody();
		for (int i = 0; i < 3; i++) {
			assertThat(JsonProbe.arrStr(afterRetries, "", i, "deliveries.0.status")).isEqualTo("FAILED");
			assertThat(JsonProbe.arrInt(afterRetries, "", i, "deliveries.0.attemptCount")).isEqualTo(3);
		}
	}

	@Test
	void P1_channelConfigValidationAndCredentialRetention() {
		// 启用 EMAIL 但缺少主机/收件地址 → 422
		ResponseEntity<String> invalid = restTemplate.exchange(url("/notification-channels/EMAIL"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"enabled\":true,\"config\":{\"toAddress\":\"user@test.local\"}}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class);
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		// 缺少 If-Match-Version → 400
		ResponseEntity<String> missingVersion = restTemplate.exchange(url("/notification-channels/EMAIL"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"enabled\":true}", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		// 首次保存（version 0）：禁用状态写入含密码的配置
		String created = putChannel("EMAIL", 0, false,
			"{\"smtpHost\":\"127.0.0.1\",\"smtpPort\":3025,\"password\":\"secret\",\"toAddress\":\"user@test.local\"}");
		assertThat(JsonProbe.lng(created, "version")).isEqualTo(1);
		assertThat(JsonProbe.str(created, "hasCredential")).isEqualTo("true");
		assertThat(created).doesNotContain("secret");

		// 旧版本号 → 409
		ResponseEntity<String> conflict = restTemplate.exchange(url("/notification-channels/EMAIL"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"enabled\":true}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// 更新不带 password → 凭据保留，启用成功
		String enabled = putChannel("EMAIL", 1, true,
			"{\"smtpHost\":\"127.0.0.1\",\"smtpPort\":3025,\"toAddress\":\"user@test.local\"}");
		assertThat(JsonProbe.str(enabled, "enabled")).isEqualTo("true");
		assertThat(JsonProbe.str(enabled, "hasCredential")).isEqualTo("true");

		// BROWSER 渠道默认投影：禁用、version 0、config null
		String browser = restTemplate.getForEntity(url("/notification-channels/BROWSER"), String.class).getBody();
		assertThat(JsonProbe.str(browser, "enabled")).isEqualTo("false");
		assertThat(JsonProbe.lng(browser, "version")).isEqualTo(0);
		assertThat(JsonProbe.str(browser, "config")).isEqualTo("null");
	}

	@Test
	void P1_browserDeliveryAckIdempotentAndTestNotification() {
		putChannel("BROWSER", 0, true, null);

		String notificationId = createDueInterviewAndDispatch();
		String before = restTemplate.getForEntity(url("/notifications/" + notificationId), String.class).getBody();
		assertThat(JsonProbe.arrStr(before, "deliveries", 0, "channelType")).isEqualTo("BROWSER");
		assertThat(JsonProbe.arrStr(before, "deliveries", 0, "status")).isEqualTo("PENDING");

		// 回执 → SENT；重复回执幂等
		ResponseEntity<String> ack = ackDelivery(notificationId, "BROWSER");
		assertThat(ack.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		ackDelivery(notificationId, "BROWSER");
		String after = restTemplate.getForEntity(url("/notifications/" + notificationId), String.class).getBody();
		assertThat(JsonProbe.arrStr(after, "deliveries", 0, "status")).isEqualTo("SENT");
		assertThat(JsonProbe.arrStr(after, "deliveries", 0, "sentAt")).isNotEqualTo("null");

		// EMAIL 渠道回执拒绝
		ResponseEntity<String> emailAck = ackDelivery(notificationId, "EMAIL");
		assertThat(emailAck.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		// 未知通知 → 404
		ResponseEntity<String> unknown = ackDelivery("99999999-9999-9999-9999-999999999999", "BROWSER");
		assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// 测试通知：BROWSER 生成 PENDING 投递；EMAIL 未配置时 422
		ResponseEntity<String> browserTest = restTemplate.exchange(url("/notification-channels/BROWSER/test"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(browserTest.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.arrStr(browserTest.getBody(), "deliveries", 0, "status")).isEqualTo("PENDING");

		ResponseEntity<String> unconfiguredEmailTest = restTemplate.exchange(url("/notification-channels/EMAIL/test"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(unconfiguredEmailTest.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	private String putChannel(String channelType, long version, boolean enabled, String configJson) {
		String configPart = configJson == null ? "" : ",\"config\":" + configJson;
		return restTemplate.exchange(url("/notification-channels/" + channelType), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"enabled\":" + enabled + configPart + "}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)),
			String.class).getBody();
	}

	private ResponseEntity<String> ackDelivery(String notificationId, String channelType) {
		return restTemplate.exchange(
			url("/notifications/" + notificationId + "/channel-deliveries/" + channelType + "/ack"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
	}

	/** 创建一场 10 分钟后开始的面试（三条默认提醒全部到期）并调度生成通知，返回首条通知 id。 */
	private String createDueInterviewAndDispatch() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("渠道科技", "P1 渠道岗位")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "渠道", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transition(applicationId, "APPLIED", "0");
		transition(applicationId, "RESUME_PASSED", "1");
		String startsAt = Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
			.toString();
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"渠道一面\",\"startsAt\":\"" + startsAt + "\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		assertThat(dispatchService.dispatchDue()).isEqualTo(3);
		String notifications = restTemplate.getForEntity(url("/notifications"), String.class).getBody();
		return JsonProbe.arrStr(notifications, "", 0, "id");
	}

	private void transition(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}
}
