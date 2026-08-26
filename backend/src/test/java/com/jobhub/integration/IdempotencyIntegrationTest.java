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
 * 幂等集成测试（必须 RANDOM_PORT + TestRestTemplate，走真实 Servlet Filter 链）。
 *
 * 覆盖：
 *   - 相同 key + 相同 body → 回放首次成功结果（同 id/version/createdAt），无重复写入
 *   - 相同 key + 不同 body → 409 IDEMPOTENCY_CONFLICT，第二次不落库
 *   - 不同 key + 相同 body → 各自独立创建
 *
 * 幂等链路：IdempotencyBodyCachingFilter（包装请求体/响应）+ IdempotencyInterceptor
 * （preHandle 查重放/写 pending，postHandle 写 idempotency_record）。MockMvc 不经过 Filter 链，故用 TestRestTemplate。
 */
class IdempotencyIntegrationTest extends AbstractIntegrationTest {

	@Test
	void sameKeySameBody_replaysFirstResponse_noDuplicateWrite() {
		String key = TestFixtures.newKey();
		String body = TestFixtures.createJobBody("幂等公司", "Java 后端");

		ResponseEntity<String> first = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id1 = JsonProbe.str(first.getBody(), "id");
		String createdAt1 = JsonProbe.str(first.getBody(), "createdAt");
		assertThat(id1).isNotBlank();

		// 相同 key + 相同 body → 回放首次响应（逐字段相等）
		ResponseEntity<String> second = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.str(second.getBody(), "id")).isEqualTo(id1);
		assertThat(JsonProbe.lng(second.getBody(), "version")).isZero();
		assertThat(JsonProbe.str(second.getBody(), "createdAt")).isEqualTo(createdAt1);

		// DB：无重复写入，仅 1 条幂等记录
		Integer jobCount = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobCount).isEqualTo(1);
		Integer recordCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?", Integer.class, key);
		assertThat(recordCount).isEqualTo(1);
	}

	@Test
	void sameKeyDifferentBody_returns409IdempotencyConflict() {
		String key = TestFixtures.newKey();
		String body1 = TestFixtures.createJobBody("公司A", "Java 后端");
		String body2 = TestFixtures.createJobBody("公司B", "Java 后端");

		ResponseEntity<String> first = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body1, "Idempotency-Key", key), String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 相同 key + 不同 body → 409
		ResponseEntity<String> second = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body2, "Idempotency-Key", key), String.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.str(second.getBody(), "code")).isEqualTo("IDEMPOTENCY_CONFLICT");
		assertThat(JsonProbe.str(second.getBody(), "traceId")).isNotBlank();

		// DB：第二次未落库，仅首次 1 条记录
		Integer jobCount = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobCount).isEqualTo(1);
		Integer recordCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?", Integer.class, key);
		assertThat(recordCount).isEqualTo(1);
	}

	@Test
	void differentKey_sameBody_createsIndependentRequest() {
		String key1 = TestFixtures.newKey();
		String key2 = TestFixtures.newKey();
		String body = TestFixtures.createJobBody("独立公司", "Java 后端");

		ResponseEntity<String> first = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key1), String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id1 = JsonProbe.str(first.getBody(), "id");

		// 不同 key + 相同 body → 独立创建，id 不同
		ResponseEntity<String> second = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(body, "Idempotency-Key", key2), String.class);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id2 = JsonProbe.str(second.getBody(), "id");
		assertThat(id2).isNotEqualTo(id1);

		// DB：2 个岗位，2 条幂等记录
		Integer jobCount = jdbc.queryForObject("SELECT COUNT(*) FROM job_posting", Integer.class);
		assertThat(jobCount).isEqualTo(2);
		Integer recordCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key IN (?, ?)", Integer.class, key1, key2);
		assertThat(recordCount).isEqualTo(2);
	}
}
