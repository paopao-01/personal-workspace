package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-64 投递漏斗转化时间序列（GET /api/analytics/funnel 只读分组聚合，按 applied_at 与 granularity 分组）。
 * 复用 §3.1 状态近似口径（applied=status != DRAFT、interviewed=status IN (INTERVIEWING,OFFER)、offered=status=OFFER）。
 * 只读：不写任何表、不需确认头/幂等键、不动 application_record/任何业务表；
 * 不输出趋势结论、能力等级、归因或行动建议。
 */
class FunnelIntegrationTest extends AbstractIntegrationTest {

	/** GET /api/analytics/funnel 只读分组聚合（query 形如 "?granularity=hour&from=ISO"）。 */
	private String fetchFunnel(String query) {
		return restTemplate.getForEntity(url("/analytics/funnel" + query), String.class).getBody();
	}

	@Test
	void AT64_dayGranularityGroupsByDateAscending() {
		// 渠道 A：3 份投递（1 OFFER、1 INTERVIEWING、1 APPLIED），applied_at 跨 09-07/08/09 各一份
		String jobA = createJob();
		String a1 = createApplication(jobA, "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		transition(a1, "RESUME_PASSED", "1");
		transition(a1, "INTERVIEWING", "2");
		completeInterviewFor(a1);
		transition(a1, "OFFER", "3", null, true);

		String a2 = createApplication(jobA, "2026-09-08T10:00:00Z", "渠道A", null);
		transition(a2, "APPLIED", "0");
		transition(a2, "RESUME_PASSED", "1");
		transition(a2, "INTERVIEWING", "2");

		String a3 = createApplication(createJob(), "2026-09-09T10:00:00Z", "渠道A", null);
		transition(a3, "APPLIED", "0");

		String body = fetchFunnel("");
		// 3 桶按 date 升序 09-07/08/09
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(3);
		assertThat(JsonProbe.arrStr(body, "", 0, "date")).isEqualTo("2026-09-07");
		// 09-07 桶：1 份 OFFER → total=1/applied=1/interviewed=1/offered=1
		assertThat(JsonProbe.arrLng(body, "", 0, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(body, "", 0, "applied")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(body, "", 0, "interviewed")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(body, "", 0, "offered")).isEqualTo(1L);
		assertThat(JsonProbe.arrDbl(body, "", 0, "interviewRate")).isCloseTo(1.0, within(0.01));
		assertThat(JsonProbe.arrDbl(body, "", 0, "offerRate")).isCloseTo(1.0, within(0.01));
		// 09-08 桶：1 份 INTERVIEWING → interviewed=1/offered=0
		assertThat(JsonProbe.arrStr(body, "", 1, "date")).isEqualTo("2026-09-08");
		assertThat(JsonProbe.arrLng(body, "", 1, "interviewed")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(body, "", 1, "offered")).isZero();
		assertThat(JsonProbe.arrDbl(body, "", 1, "offerRate")).isZero();
		// 09-09 桶：1 份 APPLIED → interviewed=0
		assertThat(JsonProbe.arrStr(body, "", 2, "date")).isEqualTo("2026-09-09");
		assertThat(JsonProbe.arrLng(body, "", 2, "interviewed")).isZero();
		assertThat(body).doesNotContain("passphrase").doesNotContain("归因");
	}

	@Test
	void AT64_hourGranularityGroupsByHour() {
		// 独立岗位避免同岗位二次投递检测拦截
		String a1 = createApplication(createJob(), "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		String a2 = createApplication(createJob(), "2026-09-07T11:00:00Z", "渠道A", null);
		transition(a2, "APPLIED", "0");
		// hour 粒度：同日不同小时 → 2 桶
		String body = fetchFunnel("?granularity=hour");
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(body, "", 0, "date")).isEqualTo("2026-09-07T10:00:00Z");
		assertThat(JsonProbe.arrStr(body, "", 1, "date")).isEqualTo("2026-09-07T11:00:00Z");
	}

	@Test
	void AT64_filtersByTimeRange() {
		String a1 = createApplication(createJob(), "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		String a2 = createApplication(createJob(), "2026-09-08T10:00:00Z", "渠道A", null);
		transition(a2, "APPLIED", "0");
		// from=09-08 → 只一桶 09-08
		String body = fetchFunnel("?from=2026-09-08T00:00:00Z&to=2026-09-08T23:59:59Z");
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(body, "", 0, "date")).isEqualTo("2026-09-08");
	}

	@Test
	void AT64_doesNotFillZeroBuckets() {
		String job = createJob();
		String a1 = createApplication(job, "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		// from=09-01 to=09-30 但只 09-07 有投递 → 1 桶，无 09-01..09-06
		String body = fetchFunnel("?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z");
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(body, "", 0, "date")).isEqualTo("2026-09-07");
	}

	@Test
	void AT64_emptyTableReturnsEmptyArray() {
		// 空表 → []（@BeforeEach 已清表）
		String body = fetchFunnel("");
		assertThat(JsonProbe.arraySize(body, "")).isZero();
		assertThat(body).isEqualTo("[]");
	}

	@Test
	void AT64_draftRowsCountedInTotalNotInApplied() {
		// DRAFT 行：计入 total 不计入 applied（漏斗顶部 status != DRAFT）
		String job = createJob();
		createApplication(job, "2026-09-07T10:00:00Z", "渠道A", null); // 不 transition，保持 DRAFT
		String body = fetchFunnel("");
		assertThat(JsonProbe.arraySize(body, "")).isEqualTo(1);
		assertThat(JsonProbe.arrLng(body, "", 0, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(body, "", 0, "applied")).isZero();
		// applied=0 → 转化率兜底 0 不抛除零异常
		assertThat(JsonProbe.arrDbl(body, "", 0, "interviewRate")).isZero();
		assertThat(JsonProbe.arrDbl(body, "", 0, "offerRate")).isZero();
	}

	@Test
	void AT64_softDeletedRowsExcluded() {
		String job = createJob();
		String a1 = createApplication(job, "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		// 软删 a1
		restTemplate.exchange(url("/applications/" + a1), HttpMethod.DELETE,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "1"), String.class);
		// 软删行不计入 → 空数组
		String body = fetchFunnel("");
		assertThat(JsonProbe.arraySize(body, "")).isZero();
	}

	@Test
	void AT64_rejectsInvalidGranularity() {
		assertThat(restTemplate.getForEntity(url("/analytics/funnel?granularity=invalid"), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT64_rejectsInvalidFrom() {
		assertThat(restTemplate.getForEntity(url("/analytics/funnel?from=not-a-date"), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void AT64_fromGreaterThanToReturnsEmptyArrayNot400() {
		// from > to → 空数组不报 400（与 timeseries 先例一致）
		var resp = restTemplate.getForEntity(
				url("/analytics/funnel?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(resp.getBody()).isEqualTo("[]");
	}

	@Test
	void AT64_doesNotMutateAnyBusinessTable() {
		String job = createJob();
		String a1 = createApplication(job, "2026-09-07T10:00:00Z", "渠道A", null);
		transition(a1, "APPLIED", "0");
		fetchFunnel("");
		fetchFunnel("?granularity=hour");
		// 只读：application_record 行数不变（仍 1 条），不写其他表
		Long appCount = jdbc.queryForObject("SELECT COUNT(*) FROM application_record", Long.class);
		assertThat(appCount).isEqualTo(1L);
	}

	private String createJob() {
		return JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("漏斗分析科技", "Java 后端工程师")), String.class).getBody(), "id");
	}

	private String createApplication(String jobId, String appliedAt, String channel, String resumeVersion) {
		String body = "{\"jobId\":\"" + jobId + "\",\"appliedAt\":\"" + appliedAt + "\",\"channel\":\"" + channel
			+ "\"" + (resumeVersion == null ? "" : ",\"resumeVersion\":\"" + resumeVersion + "\"") + "}";
		return JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private void transition(String applicationId, String targetStatus, String version) {
		transition(applicationId, targetStatus, version, null, null);
	}

	private void transition(String applicationId, String targetStatus, String version, String reason,
			Boolean allowOfferWithoutInterview) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, reason, allowOfferWithoutInterview),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}

	private void completeInterviewFor(String applicationId) {
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId
				+ "\",\"roundName\":\"技术一面\",\"startsAt\":\"2026-09-05T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(),
			"version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"PASSED\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
	}

	private static org.assertj.core.data.Offset<Double> within(double tolerance) {
		return org.assertj.core.data.Offset.offset(tolerance);
	}
}
