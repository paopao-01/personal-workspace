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
 * 投递 CRUD 集成测试 + AT-08 二次投递检测。
 *
 * 覆盖：
 *   创建投递 → 201 DRAFT v0
 *   GET detail → job 非空、statusHistory 含初始项、interviews 为空
 *   AT-08 前半：同岗位已有 INTERVIEWING 投递，不带 allowDuplicate 创建 → 409 DUPLICATE_APPLICATION
 *   AT-08 后半：allowDuplicate=true 本切片搁置（V1 唯一索引限制），返回 409 提示未支持
 */
class ApplicationCrudIntegrationTest extends AbstractIntegrationTest {

	@Test
	void createApplication_returns201_draft_persists() {
		String jobId = createJob();
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.str(resp.getBody(), "status")).isEqualTo("DRAFT");
		assertThat(JsonProbe.lng(resp.getBody(), "version")).isZero();
		assertThat(JsonProbe.str(resp.getBody(), "jobId")).isEqualTo(jobId);
	}

	@Test
	void getDetail_returnsApplicationJobStatusHistory_emptyInterviews() {
		String jobId = createJob();
		String appId = createApplication(jobId);
		// 转换一次产生 status_history
		transition(appId, "0", "APPLIED", TestFixtures.newKey());

		ResponseEntity<String> resp = restTemplate.exchange(
				url("/applications/" + appId), HttpMethod.GET,
				TestFixtures.httpJson(""), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(resp.getBody(), "id")).isEqualTo(appId);
		assertThat(JsonProbe.str(resp.getBody(), "job.title")).isEqualTo("Java 后端工程师");
		assertThat(JsonProbe.arraySize(resp.getBody(), "statusHistory")).isGreaterThanOrEqualTo(1);
		assertThat(JsonProbe.arraySize(resp.getBody(), "interviews")).isZero();
	}

	@Test
	void createSecondApplication_sameJobWithoutAllowDuplicate_returns409() {
		String jobId = createJob();
		createInterviewingApplication(jobId);  // 旧投递 INTERVIEWING

		// 同岗位创建第二条，不带 allowDuplicate
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-25", "拉勾", null, null, null),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("DUPLICATE_APPLICATION");
	}

	@Test
	void createSecondApplication_withAllowDuplicate_returns409_notSupported() {
		String jobId = createJob();
		createInterviewingApplication(jobId);  // 旧投递 INTERVIEWING

		// allowDuplicate=true：V1 唯一索引限制，本切片搁置，仍返回 409
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-25", "拉勾", null, null, true),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("DUPLICATE_APPLICATION");
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
						TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
						"Idempotency-Key", TestFixtures.newKey()),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}

	private void transition(String appId, String version, String target, String key) {
		restTemplate.exchange(url("/applications/" + appId + "/transition"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.transitionBody(target, null, null),
						"Idempotency-Key", key, "If-Match-Version", version),
				String.class);
	}

	/** 构造 INTERVIEWING 投递：DRAFT→APPLIED→RESUME_PASSED→INTERVIEWING（本切片无面试模块，矩阵直接允许）。 */
	private String createInterviewingApplication(String jobId) {
		String appId = createApplication(jobId);
		transition(appId, "0", "APPLIED", TestFixtures.newKey());
		transition(appId, "1", "RESUME_PASSED", TestFixtures.newKey());
		transition(appId, "2", "INTERVIEWING", TestFixtures.newKey());
		return appId;
	}
}
