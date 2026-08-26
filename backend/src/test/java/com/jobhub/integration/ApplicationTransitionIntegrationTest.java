package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投递状态转换集成测试（02-state-machines.md §3 投递状态机）。
 *
 * 覆盖：
 *   AT-05 DRAFT→APPLIED 合法转换写不可覆盖历史；普通 PUT 不改历史
 *   AT-06 DRAFT→OFFER 非法转换 422 ILLEGAL_STATE_TRANSITION + currentState/targetState/reason + 零副作用
 */
class ApplicationTransitionIntegrationTest extends AbstractIntegrationTest {

	@Test
	void draftToApplied_writesStatusLog_statusBecomesApplied_putCannotModifyHistory() {
		String jobId = createJob();
		String appId = createApplication(jobId, null, null);  // DRAFT

		// DRAFT → APPLIED
		ResponseEntity<String> resp = restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.transitionBody("APPLIED", null, null),
						"Idempotency-Key", TestFixtures.newKey(),
						"If-Match-Version", "0"),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(resp.getBody(), "status")).isEqualTo("APPLIED");
		assertThat(JsonProbe.lng(resp.getBody(), "version")).isEqualTo(1L);

		// DB：status_log 新增一条 DRAFT → APPLIED
		Map<String, Object> logRow = jdbc.queryForMap(
				"SELECT from_status, to_status FROM application_status_log WHERE application_id = ?", appId);
		assertThat(logRow.get("from_status")).isEqualTo("DRAFT");
		assertThat(logRow.get("to_status")).isEqualTo("APPLIED");

		// 普通 PUT 更新（改 channel）不改 status_log 历史（行数仍为 1）
		restTemplate.exchange(url("/applications/" + appId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
						TestFixtures.updateApplicationBody("BOSS直聘", null, null),
						"If-Match-Version", "1"),
				String.class);
		Integer logCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM application_status_log WHERE application_id = ?", Integer.class, appId);
		assertThat(logCount).isEqualTo(1);
	}

	@Test
	void draftToOffer_illegal_returns422_noSideEffect() {
		String jobId = createJob();
		String appId = createApplication(jobId, null, null);  // DRAFT

		ResponseEntity<String> resp = restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.transitionBody("OFFER", null, null),
						"Idempotency-Key", TestFixtures.newKey(),
						"If-Match-Version", "0"),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("ILLEGAL_STATE_TRANSITION");
		assertThat(JsonProbe.str(resp.getBody(), "currentState")).isEqualTo("DRAFT");
		assertThat(JsonProbe.str(resp.getBody(), "targetState")).isEqualTo("OFFER");
		assertThat(JsonProbe.str(resp.getBody(), "reason")).isNotBlank();

		// DB：状态/版本/历史均不变（零副作用）
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT status, version FROM application_record WHERE id = ?", appId);
		assertThat(row.get("status")).isEqualTo("DRAFT");
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(0L);
		Integer logCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM application_status_log WHERE application_id = ?", Integer.class, appId);
		assertThat(logCount).isEqualTo(0);
	}

	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}

	private String createApplication(String jobId, String nextAction, String nextActionDueAt) {
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-20", "演示招聘站",
								nextAction, nextActionDueAt, null),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}
}
