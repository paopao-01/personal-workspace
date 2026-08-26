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
 * 非法状态转换集成测试（02-state-machines.md JobStatus 状态机）。
 *
 * 覆盖：
 *   - archive 一个已 ARCHIVED 岗位 → 422 ILLEGAL_STATE_TRANSITION（currentState=ARCHIVED, targetState=ARCHIVED）
 *   - restore 一个 ACTIVE 岗位 → 422 ILLEGAL_STATE_TRANSITION（currentState=ACTIVE, targetState=ACTIVE）
 *
 * 断言：返回 422 + 稳定错误码 + currentState/targetState/reason + 无数据副作用（事务回滚）。
 */
class IllegalTransitionIntegrationTest extends AbstractIntegrationTest {

	@Test
	void archive_alreadyArchivedJob_returns422IllegalTransition_noSideEffect() {
		String id = createJob();

		// 先 archive 一次 → ARCHIVED v1
		restTemplate.exchange(url("/jobs/" + id + "/archive"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "0"), String.class);

		// 再次 archive → 422
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + id + "/archive"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "1"), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("ILLEGAL_STATE_TRANSITION");
		assertThat(JsonProbe.str(resp.getBody(), "currentState")).isEqualTo("ARCHIVED");
		assertThat(JsonProbe.str(resp.getBody(), "targetState")).isEqualTo("ARCHIVED");
		assertThat(JsonProbe.str(resp.getBody(), "reason")).contains("only ACTIVE");
		assertThat(JsonProbe.str(resp.getBody(), "traceId")).isNotBlank();

		// DB：无副作用（status/version 不变）
		Map<String, Object> row = jdbc.queryForMap("SELECT status, version FROM job_posting WHERE id = ?", id);
		assertThat(row.get("status")).isEqualTo("ARCHIVED");
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
	}

	@Test
	void restore_activeJob_returns422IllegalTransition_noSideEffect() {
		String id = createJob();

		// restore 一个 ACTIVE 岗位 → 422
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + id + "/restore"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "0"), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("ILLEGAL_STATE_TRANSITION");
		assertThat(JsonProbe.str(resp.getBody(), "currentState")).isEqualTo("ACTIVE");
		assertThat(JsonProbe.str(resp.getBody(), "targetState")).isEqualTo("ACTIVE");
		assertThat(JsonProbe.str(resp.getBody(), "reason")).contains("only ARCHIVED");

		// DB：无副作用（status/version 不变）
		Map<String, Object> row = jdbc.queryForMap("SELECT status, version FROM job_posting WHERE id = ?", id);
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(0L);
	}

	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")), String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}
}
