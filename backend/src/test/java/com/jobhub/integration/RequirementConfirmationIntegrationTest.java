package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-02/AT-03/AT-04 集成测试：候选要求确认、JD 修改回退、人工修正匹配状态。
 *
 * AT-02：PENDING 要求不进 gap-list，只含 CONFIRMED。
 * AT-03：修改 jdRawText 后要求全量回退 PENDING、requirement_match 清空、gap-list 失效。
 *        （本切片不实现"人工修正记录保留"——deleteByJobId 硬删除，known gap，留后续切片）
 * AT-04：manualMatchStatus=NOT_MET + reason 写入 requirement_match，原始 evidence_snapshot_json 跨 override 保留。
 */
class RequirementConfirmationIntegrationTest extends AbstractIntegrationTest {

	@Test
	void at02_gapList_excludesPendingRequirements() {
		String jobId = createJob();
		// 提取候选（全 PENDING）
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
				TestFixtures.httpJson(""), String.class);
		// 读取候选 id 列表
		String reqs = restTemplate.getForObject(url("/jobs/" + jobId + "/requirements"), String.class);
		int total = JsonProbe.arraySize(reqs, "");
		assertThat(total).isGreaterThanOrEqualTo(3);
		String reqId0 = JsonProbe.arrStr(reqs, "", 0, "id");
		String reqId1 = JsonProbe.arrStr(reqs, "", 1, "id");

		// 确认 2 项为 CONFIRMED
		confirm(reqId0, "0");
		confirm(reqId1, "0");

		// gap-list 只含 2 项 CONFIRMED，每项 INSUFFICIENT_INFO（无 user_skill 资料）
		String gap = restTemplate.getForObject(url("/jobs/" + jobId + "/gap-list"), String.class);
		assertThat(JsonProbe.arraySize(gap, "")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(gap, "", 0, "status")).isEqualTo("INSUFFICIENT_INFO");
		assertThat(JsonProbe.arrStr(gap, "", 1, "status")).isEqualTo("INSUFFICIENT_INFO");
		// 每项 requirement.confirmationStatus = CONFIRMED
		assertThat(JsonProbe.arrStr(gap, "", 0, "requirement.confirmationStatus")).isEqualTo("CONFIRMED");

		// DB：2 CONFIRMED + 至少 1 PENDING
		Integer confirmed = jdbc.queryForObject(
				"SELECT COUNT(*) FROM job_requirement WHERE job_id = ? AND confirmation_status = 'CONFIRMED'", Integer.class, jobId);
		Integer pending = jdbc.queryForObject(
				"SELECT COUNT(*) FROM job_requirement WHERE job_id = ? AND confirmation_status = 'PENDING'", Integer.class, jobId);
		assertThat(confirmed).isEqualTo(2);
		assertThat(pending).isGreaterThanOrEqualTo(1);
	}

	@Test
	void at03_jdModified_requirementsRevertToPending_gapConclusionsInvalidated() {
		String jobId = createJob();
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
				TestFixtures.httpJson(""), String.class);
		String reqs = restTemplate.getForObject(url("/jobs/" + jobId + "/requirements"), String.class);
		String reqId0 = JsonProbe.arrStr(reqs, "", 0, "id");

		// 确认 1 项 + 人工修正为 NOT_MET（建立 requirement_match 行）
		restTemplate.exchange(url("/job-requirements/" + reqId0), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateRequirementBody("CONFIRMED", "NOT_MET", "无证据"),
						"If-Match-Version", "0"), String.class);
		// gap-list 应含 1 项 NOT_MET
		String gap1 = restTemplate.getForObject(url("/jobs/" + jobId + "/gap-list"), String.class);
		assertThat(JsonProbe.arraySize(gap1, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(gap1, "", 0, "status")).isEqualTo("NOT_MET");

		// 修改 JD 原文（不同文本，触发回退），回填基础字段
		String newJd = TestFixtures.SAMPLE_JD + " 额外补充：团队使用 Git 协作，持续交付。";
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + jobId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
						TestFixtures.updateJdBody("示例科技", "Java 后端工程师", newJd),
						"If-Match-Version", "0"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(resp.getBody(), "version")).isEqualTo(1L);

		// 要求全部回退 PENDING
		String reqsAfter = restTemplate.getForObject(url("/jobs/" + jobId + "/requirements"), String.class);
		List<String> statuses = JsonProbe.collectArrayField(reqsAfter, "", "confirmationStatus");
		assertThat(statuses).allMatch("PENDING"::equals);

		// gap-list 失效（无 CONFIRMED）→ 空数组
		String gap2 = restTemplate.getForObject(url("/jobs/" + jobId + "/gap-list"), String.class);
		assertThat(JsonProbe.arraySize(gap2, "")).isZero();

		// DB：job_requirement 非 PENDING=0；requirement_match 行数=0（deleteByJobId 生效）
		Integer nonPending = jdbc.queryForObject(
				"SELECT COUNT(*) FROM job_requirement WHERE job_id = ? AND confirmation_status <> 'PENDING'", Integer.class, jobId);
		assertThat(nonPending).isZero();
		Integer matchCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM requirement_match WHERE requirement_id IN "
						+ "(SELECT id FROM job_requirement WHERE job_id = ?)", Integer.class, jobId);
		assertThat(matchCount).isZero();
	}

	@Test
	void at04_manualMatchStatusNotMet_withReason_evidenceSnapshotPreserved() {
		String jobId = createJob();
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
				TestFixtures.httpJson(""), String.class);
		String reqs = restTemplate.getForObject(url("/jobs/" + jobId + "/requirements"), String.class);
		String reqId = JsonProbe.arrStr(reqs, "", 0, "id");

		// 第一次：CONFIRMED + SELF_REPORTED_NO_EVIDENCE（version 0 → 1）
		restTemplate.exchange(url("/job-requirements/" + reqId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
						TestFixtures.updateRequirementBody("CONFIRMED", "SELF_REPORTED_NO_EVIDENCE", null),
						"If-Match-Version", "0"), String.class);
		String gap1 = restTemplate.getForObject(url("/jobs/" + jobId + "/gap-list"), String.class);
		assertThat(JsonProbe.arrStr(gap1, "", 0, "status")).isEqualTo("SELF_REPORTED_NO_EVIDENCE");

		// 第二次：manualMatchStatus=NOT_MET + reason（version 1 → 2）
		restTemplate.exchange(url("/job-requirements/" + reqId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
						TestFixtures.updateRequirementBody("CONFIRMED", "NOT_MET", "无相关项目证据"),
						"If-Match-Version", "1"), String.class);
		String gap2 = restTemplate.getForObject(url("/jobs/" + jobId + "/gap-list"), String.class);
		assertThat(JsonProbe.arraySize(gap2, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(gap2, "", 0, "status")).isEqualTo("NOT_MET");
		assertThat(JsonProbe.arrStr(gap2, "", 0, "manualOverrideReason")).isEqualTo("无相关项目证据");

		// DB：match_status=NOT_MET, manual_override_reason=无相关项目证据, evidence_snapshot_json 跨 override 保留 '[]'
		Map<String, Object> match = jdbc.queryForMap(
				"SELECT match_status, manual_override_reason, evidence_snapshot_json FROM requirement_match WHERE requirement_id = ?",
				reqId);
		assertThat(match.get("match_status")).isEqualTo("NOT_MET");
		assertThat(match.get("manual_override_reason")).isEqualTo("无相关项目证据");
		assertThat((String) match.get("evidence_snapshot_json")).isEqualTo("[]");
	}

	// --- 辅助 ---
	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")), String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}

	private void confirm(String reqId, String version) {
		restTemplate.exchange(url("/job-requirements/" + reqId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateRequirementBody("CONFIRMED", null, null),
						"If-Match-Version", version), String.class);
	}
}
