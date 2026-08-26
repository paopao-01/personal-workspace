package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投递状态转换幂等集成测试（AT-07）。
 *
 * 覆盖：
 *   相同 Idempotency-Key + 相同 transition body → 同一成功结果，仅一条 application_status_log
 *   相同 Idempotency-Key + 不同 targetStatus body → 409 IDEMPOTENCY_CONFLICT
 */
class ApplicationIdempotencyIntegrationTest extends AbstractIntegrationTest {

	@Test
	void sameKeySameTransitionBody_replays_oneStatusLog() {
		String jobId = createJob();
		String appId = createApplication(jobId);
		String key = TestFixtures.newKey();
		String body = TestFixtures.transitionBody("APPLIED", null, null);

		// 第一次 DRAFT → APPLIED
		ResponseEntity<String> first = restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key, "If-Match-Version", "0"),
				String.class);

		// 相同 key + 相同 body 第二次 → 回放同一成功结果
		ResponseEntity<String> replay = restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key, "If-Match-Version", "0"),
				String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(replay.getBody(), "id")).isEqualTo(JsonProbe.str(first.getBody(), "id"));
		assertThat(JsonProbe.str(replay.getBody(), "status")).isEqualTo("APPLIED");

		// DB：status_log 仅一条 DRAFT → APPLIED（幂等不重复写历史）
		Integer logCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM application_status_log WHERE application_id = ?", Integer.class, appId);
		assertThat(logCount).isEqualTo(1);
	}

	@Test
	void sameKeyDifferentTargetStatus_returns409IdempotencyConflict() {
		String jobId = createJob();
		String appId = createApplication(jobId);
		String key = TestFixtures.newKey();

		// 第一次 DRAFT → APPLIED 成功
		restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.transitionBody("APPLIED", null, null),
						"Idempotency-Key", key, "If-Match-Version", "0"),
				String.class);

		// 相同 key + 不同 targetStatus=WITHDRAWN → 409 IDEMPOTENCY_CONFLICT
		ResponseEntity<String> resp = restTemplate.exchange(
				url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.transitionBody("WITHDRAWN", null, null),
						"Idempotency-Key", key, "If-Match-Version", "0"),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("IDEMPOTENCY_CONFLICT");

		// DB：无新增 status_log（第二次被拦截，未执行）
		Integer logCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM application_status_log WHERE application_id = ?", Integer.class, appId);
		assertThat(logCount).isEqualTo(1);
	}

	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}

	private String createApplication(String jobId) {
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-20", "演示招聘站", null, null, null),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}
}
