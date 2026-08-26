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
 * AT-22 版本冲突集成测试。
 *
 * 覆盖：
 *   - 旧 If-Match-Version → 409 VERSION_CONFLICT，reason 含真实当前版本，DB 不变（A 的更新保持）
 *   - 当前 If-Match-Version → 200，version +1
 *   - 缺失 If-Match-Version → 400（控制器 required=false + 手动 badRequest，无 body）
 *
 * 依赖 VersionCheck 修复：调用方传 selectById 读出的 job.getVersion()（真实当前版本），
 * 而非客户端提交的旧 expectedVersion。本测试按"返回真实当前版本"断言，验证修复生效。
 */
class VersionConflictIntegrationTest extends AbstractIntegrationTest {

	@Test
	void staleIfMatchVersion_returns409VersionConflict_withCurrentVersion_dbUnchanged() {
		String id = createJob();

		// 页面 A：If-Match-Version:0 更新 decisionStatus=TO_APPLY → v1
		restTemplate.exchange(url("/jobs/" + id), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateDecisionBody("TO_APPLY", null),
						"If-Match-Version", "0"), String.class);

		// 页面 B：用旧版本 0 更新 decisionStatus=IGNORE → 409
		ResponseEntity<String> conflict = restTemplate.exchange(url("/jobs/" + id), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateDecisionBody("IGNORE", null),
						"If-Match-Version", "0"), String.class);

		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.str(conflict.getBody(), "code")).isEqualTo("VERSION_CONFLICT");
		// reason 含真实当前版本（VersionCheck 修复后 = 1；修复前会是 0）
		assertThat(JsonProbe.str(conflict.getBody(), "reason")).contains("currentVersion=1");

		// DB：A 的更新保持不变，B 无副作用
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT version, decision_status FROM job_posting WHERE id = ?", id);
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
		assertThat(row.get("decision_status")).isEqualTo("TO_APPLY");
	}

	@Test
	void currentIfMatchVersion_succeeds_andIncrementsVersion() {
		String id = createJob();

		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + id), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
						TestFixtures.updateJdBody("示例科技", "Java 后端工程师", TestFixtures.SAMPLE_JD),
						"If-Match-Version", "0"), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(resp.getBody(), "version")).isEqualTo(1L);

		Long version = jdbc.queryForObject(
				"SELECT version FROM job_posting WHERE id = ?", Long.class, id);
		assertThat(version).isEqualTo(1L);
	}

	@Test
	void missingIfMatchVersion_onWriteOps_returns400() {
		String id = createJob();

		// PUT 不带 If-Match-Version → 400 空 body
		ResponseEntity<String> put = restTemplate.exchange(url("/jobs/" + id), HttpMethod.PUT,
				TestFixtures.httpJson(TestFixtures.updateDecisionBody("TO_APPLY", null)), String.class);
		assertThat(put.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(put.getBody()).isNullOrEmpty();

		// archive 不带 If-Match-Version → 400 空 body
		ResponseEntity<String> archive = restTemplate.exchange(url("/jobs/" + id + "/archive"),
				HttpMethod.POST, TestFixtures.httpJson(""), String.class);
		assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")), String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}
}
