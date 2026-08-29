package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class MatchReportIntegrationTest extends AbstractIntegrationTest {

	@Test
	void P1_generateMatchReportWithExplainableScores() {
		String jobId = createJobWithConfirmedRequirements();

		// 生成报告：三个 MUST 要求 → SATISFIED_WITH_EVIDENCE / SELF_REPORTED_NO_EVIDENCE / NOT_MET
		ResponseEntity<String> created = restTemplate.exchange(url("/jobs/" + jobId + "/match-reports"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String report = created.getBody();
		assertThat(JsonProbe.str(report, "ruleVersion")).isEqualTo("MATCH_RULE_V1");
		assertThat(JsonProbe.lng(report, "weights.MUST")).isEqualTo(3);
		assertThat(JsonProbe.lng(report, "weights.BONUS")).isEqualTo(1);

		// 汇总：每类状态各 1
		assertThat(JsonProbe.lng(report, "mustSummary.total")).isEqualTo(3);
		assertThat(JsonProbe.lng(report, "mustSummary.satisfiedWithEvidence")).isEqualTo(1);
		assertThat(JsonProbe.lng(report, "mustSummary.selfReportedNoEvidence")).isEqualTo(1);
		assertThat(JsonProbe.lng(report, "mustSummary.notMet")).isEqualTo(1);
		// 计分：MUST=3 → 满足 3 + 自报 1.5 + 未满足 0 = 4.5 / 9（lng 截断为 4）；缺少资料不计入分母
		assertThat(JsonProbe.lng(report, "mustScore.numerator")).isEqualTo(4);
		assertThat(JsonProbe.lng(report, "mustScore.denominator")).isEqualTo(9);
		// 有未满足的必须要求 → LOW_MATCH 且附理由
		assertThat(JsonProbe.str(report, "suggestion.type")).isEqualTo("LOW_MATCH");
		assertThat(JsonProbe.arraySize(report, "suggestion.reasons")).isGreaterThanOrEqualTo(1);
		assertThat(JsonProbe.arraySize(report, "requirements")).isEqualTo(3);
		assertThat(JsonProbe.str(report, "stale")).isEqualTo("false");

		// latest 返回同一报告且未过期
		String latest = restTemplate.getForEntity(url("/jobs/" + jobId + "/match-reports/latest"), String.class).getBody();
		assertThat(JsonProbe.str(latest, "id")).isEqualTo(JsonProbe.str(report, "id"));
		assertThat(JsonProbe.str(latest, "stale")).isEqualTo("false");
	}

	@Test
	void P1_matchReportStaleAfterInputChanges_andRegenerateClearsIt() {
		String jobId = createJobWithConfirmedRequirements();
		String created = restTemplate.exchange(url("/jobs/" + jobId + "/match-reports"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reportId = JsonProbe.str(created, "id");
		// 找到自报无证据的要求（第 2 项），将其人工修正为有证据
		String requirementId = JsonProbe.str(created, "requirements.1.requirementId");
		long requirementVersion = requirementVersion(jobId, requirementId);

		restTemplate.exchange(url("/job-requirements/" + requirementId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders(
				"{\"confirmationStatus\":\"CONFIRMED\",\"manualMatchStatus\":\"SATISFIED_WITH_EVIDENCE\",\"reason\":\"补充了项目证据\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(requirementVersion)),
			String.class);

		// 输入变化 → latest 报告 stale=true（仍返回旧快照）
		String latestStale = restTemplate.getForEntity(url("/jobs/" + jobId + "/match-reports/latest"), String.class).getBody();
		assertThat(JsonProbe.str(latestStale, "stale")).isEqualTo("true");
		assertThat(JsonProbe.str(latestStale, "id")).isEqualTo(reportId);

		// 重新生成 → 新报告 stale=false，汇总反映新状态（2 项有证据）
		String regenerated = restTemplate.exchange(url("/jobs/" + jobId + "/match-reports"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(regenerated, "stale")).isEqualTo("false");
		assertThat(JsonProbe.lng(regenerated, "mustSummary.satisfiedWithEvidence")).isEqualTo(2);
		String latestFresh = restTemplate.getForEntity(url("/jobs/" + jobId + "/match-reports/latest"), String.class).getBody();
		assertThat(JsonProbe.str(latestFresh, "stale")).isEqualTo("false");
		assertThat(JsonProbe.str(latestFresh, "id")).isNotEqualTo(reportId);
	}

	@Test
	void P1_matchReportWithoutConfirmedRequirementsNeedsMoreInfo() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("匹配科技", "无确认要求岗位")), String.class).getBody(), "id");
		String report = restTemplate.exchange(url("/jobs/" + jobId + "/match-reports"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.lng(report, "mustSummary.total")).isEqualTo(0);
		assertThat(JsonProbe.lng(report, "mustScore.denominator")).isEqualTo(0);
		assertThat(JsonProbe.str(report, "suggestion.type")).isEqualTo("NEED_MORE_INFO");

		ResponseEntity<String> missing = restTemplate.getForEntity(
			url("/jobs/99999999-9999-9999-9999-999999999999/match-reports/latest"), String.class);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private long requirementVersion(String jobId, String requirementId) {
		String requirements = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		int count = JsonProbe.arraySize(requirements, "");
		for (int i = 0; i < count; i++) {
			if (requirementId.equals(JsonProbe.arrStr(requirements, "", i, "id"))) {
				return JsonProbe.arrLng(requirements, "", i, "version");
			}
		}
		throw new IllegalStateException("requirement not found: " + requirementId);
	}

	private String createJobWithConfirmedRequirements() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("匹配科技", "P1 匹配报告岗位")), String.class).getBody(), "id");
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String requirements = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		int count = JsonProbe.arraySize(requirements, "");
		String[] statuses = {"SATISFIED_WITH_EVIDENCE", "SELF_REPORTED_NO_EVIDENCE", "NOT_MET"};
		for (int i = 0; i < Math.min(3, count); i++) {
			String requirementId = JsonProbe.arrStr(requirements, "", i, "id");
			long version = JsonProbe.arrLng(requirements, "", i, "version");
			restTemplate.exchange(url("/job-requirements/" + requirementId), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(
					"{\"confirmationStatus\":\"CONFIRMED\",\"type\":\"MUST\",\"manualMatchStatus\":\"" + statuses[i] + "\"}",
					"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)),
				String.class);
		}
		return jobId;
	}
}
