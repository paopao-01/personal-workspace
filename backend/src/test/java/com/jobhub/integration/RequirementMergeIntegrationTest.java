package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class RequirementMergeIntegrationTest extends AbstractIntegrationTest {

	@Test
	void P1_mergeSoftDeletesSourceIntoTargetWithAudit() {
		String jobId = createJobWithCandidates();
		String requirements = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		int before = JsonProbe.arraySize(requirements, "");
		assertThat(before).isGreaterThanOrEqualTo(2);
		String targetId = JsonProbe.arrStr(requirements, "", 0, "id");
		String sourceId = JsonProbe.arrStr(requirements, "", 1, "id");

		String merged = restTemplate.exchange(url("/job-requirements/merge"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(
				"{\"targetRequirementId\":\"" + targetId + "\",\"sourceRequirementIds\":[\"" + sourceId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(merged, "id")).isEqualTo(targetId);

		// 来源软删除并指向目标（保留原始记录）
		Integer deleted = jdbc.queryForObject(
			"SELECT COUNT(*) FROM job_requirement WHERE id=? AND deleted_at IS NOT NULL AND merged_into_requirement_id=?",
			Integer.class, sourceId, targetId);
		assertThat(deleted).isEqualTo(1);

		// 审计记录
		Integer audits = jdbc.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE resource_type='JOB_REQUIREMENT' AND resource_id=? AND action='REQUIREMENT_MERGED'",
			Integer.class, sourceId);
		assertThat(audits).isEqualTo(1);

		// 列表不再包含来源
		String after = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		assertThat(JsonProbe.arraySize(after, "")).isEqualTo(before - 1);
	}

	@Test
	void P1_mergeRejectsCrossJobConfirmedSelfAndEmptySources() {
		String jobA = createJobWithCandidates();
		String jobB = createJobWithCandidates();
		String reqA = restTemplate.getForEntity(url("/jobs/" + jobA + "/requirements"), String.class).getBody();
		String reqB = restTemplate.getForEntity(url("/jobs/" + jobB + "/requirements"), String.class).getBody();
		String targetA = JsonProbe.arrStr(reqA, "", 0, "id");
		String otherA = JsonProbe.arrStr(reqA, "", 1, "id");
		String sourceB = JsonProbe.arrStr(reqB, "", 0, "id");

		// 跨岗位来源 → 422 且来源不被删除
		ResponseEntity<String> crossJob = restTemplate.exchange(url("/job-requirements/merge"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(
				"{\"targetRequirementId\":\"" + targetA + "\",\"sourceRequirementIds\":[\"" + sourceB + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(crossJob.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(crossJob.getBody()).contains("BUSINESS_RULE_ERROR");
		Integer notDeleted = jdbc.queryForObject(
			"SELECT COUNT(*) FROM job_requirement WHERE id=? AND deleted_at IS NULL", Integer.class, sourceB);
		assertThat(notDeleted).isEqualTo(1);

		// 目标不能同时作为来源
		ResponseEntity<String> selfMerge = restTemplate.exchange(url("/job-requirements/merge"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(
				"{\"targetRequirementId\":\"" + targetA + "\",\"sourceRequirementIds\":[\"" + targetA + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(selfMerge.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		// 已确认要求不可作为来源
		String firstVersion = JsonProbe.arrStr(reqA, "", 1, "version");
		restTemplate.exchange(url("/job-requirements/" + otherA), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"confirmationStatus\":\"CONFIRMED\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", firstVersion), String.class);
		ResponseEntity<String> confirmedSource = restTemplate.exchange(url("/job-requirements/merge"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(
				"{\"targetRequirementId\":\"" + targetA + "\",\"sourceRequirementIds\":[\"" + otherA + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(confirmedSource.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(confirmedSource.getBody()).contains("Only PENDING requirements can be merged");

		// 空来源列表 → 400
		ResponseEntity<String> emptySources = restTemplate.exchange(url("/job-requirements/merge"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(
				"{\"targetRequirementId\":\"" + targetA + "\",\"sourceRequirementIds\":[]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(emptySources.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(emptySources.getBody()).contains("VALIDATION_ERROR");
	}

	private String createJobWithCandidates() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("合并科技", "P1 合并岗位")), String.class).getBody(), "id");
		ResponseEntity<String> extracted = restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(extracted.getStatusCode()).isEqualTo(HttpStatus.OK);
		return jobId;
	}
}
