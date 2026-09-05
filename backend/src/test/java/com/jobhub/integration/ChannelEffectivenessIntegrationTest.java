package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-35 投递渠道与简历版本效果对比（PRD 10：高级趋势分析最小切片）。
 * 只读原始计数，不输出趋势结论、能力等级、归因或行动建议。
 */
class ChannelEffectivenessIntegrationTest extends AbstractIntegrationTest {

	@Test
	void AT35_channelEffectivenessAggregatesRawCounts() {
		// 渠道 A：3 份投递（1 OFFER、1 INTERVIEWING、1 APPLIED）
		String jobA = createJob();
		String a1 = createApplication(jobA, "2026-09-01", "渠道A", "简历版本X");
		transition(a1, "APPLIED", "0");
		transition(a1, "RESUME_PASSED", "1");
		transition(a1, "INTERVIEWING", "2");
		// a1 进入 OFFER 需要一场 COMPLETED 面试或显式例外
		completeInterviewFor(a1);
		transition(a1, "OFFER", "3", null, true);

		String a2 = createApplication(jobA, "2026-09-02", "渠道A", "简历版本X");
		transition(a2, "APPLIED", "0");
		transition(a2, "RESUME_PASSED", "1");
		transition(a2, "INTERVIEWING", "2");

		String a3 = createApplication(createJob(), "2026-09-03", "渠道A", null);
		transition(a3, "APPLIED", "0");

		// 渠道 B：1 份投递（APPLIED）
		String jobB = createJob();
		String b1 = createApplication(jobB, "2026-09-04", "渠道B", null);
		transition(b1, "APPLIED", "0");

		String report = restTemplate.getForEntity(url("/analytics/channel-effectiveness"), String.class).getBody();
		assertThat(JsonProbe.str(report, "from")).isEqualTo("null");
		assertThat(JsonProbe.str(report, "to")).isEqualTo("null");

		// channelGroups 按 applicationCount DESC, channel ASC 排序；渠道A 在前
		assertThat(JsonProbe.arraySize(report, "channelGroups")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(report, "channelGroups", 0, "channel")).isEqualTo("渠道A");
		assertThat(JsonProbe.arrLng(report, "channelGroups", 0, "applicationCount")).isEqualTo(3L);
		assertThat(JsonProbe.arrLng(report, "channelGroups", 0, "interviewCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(report, "channelGroups", 0, "offerCount")).isEqualTo(1L);
		assertThat(JsonProbe.arrDbl(report, "channelGroups", 0, "offerRate")).isCloseTo(1.0 / 3.0, within(0.001));
		assertThat(JsonProbe.arrStr(report, "channelGroups", 1, "channel")).isEqualTo("渠道B");
		assertThat(JsonProbe.arrLng(report, "channelGroups", 1, "applicationCount")).isEqualTo(1L);
		assertThat(JsonProbe.arrLng(report, "channelGroups", 1, "interviewCount")).isEqualTo(0L);
		assertThat(JsonProbe.arrLng(report, "channelGroups", 1, "offerCount")).isEqualTo(0L);
		assertThat(JsonProbe.arrDbl(report, "channelGroups", 1, "offerRate")).isEqualTo(0.0);

		// resumeVersionGroups：简历版本X（2 份，含 1 offer）与 未指定版本（2 份，0 offer）
		assertThat(JsonProbe.arraySize(report, "resumeVersionGroups")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(report, "resumeVersionGroups", 0, "resumeVersion")).isEqualTo("简历版本X");
		assertThat(JsonProbe.arrLng(report, "resumeVersionGroups", 0, "applicationCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(report, "resumeVersionGroups", 0, "offerCount")).isEqualTo(1L);
		assertThat(JsonProbe.arrDbl(report, "resumeVersionGroups", 0, "offerRate")).isCloseTo(0.5, within(0.001));
		assertThat(JsonProbe.arrStr(report, "resumeVersionGroups", 1, "resumeVersion")).isEqualTo("null");
		assertThat(JsonProbe.arrLng(report, "resumeVersionGroups", 1, "applicationCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(report, "resumeVersionGroups", 1, "offerCount")).isEqualTo(0L);
		assertThat(JsonProbe.arrDbl(report, "resumeVersionGroups", 1, "offerRate")).isEqualTo(0.0);

		// 不输出趋势结论字样
		assertThat(report).doesNotContain("trend").doesNotContain("趋势结论").doesNotContain("归因");

		// 日期过滤：只取 2026-09-03 之后，剩 a3（渠道A）与 b1（渠道B），各 1 份
		String filtered = restTemplate
			.getForEntity(url("/analytics/channel-effectiveness?from=2026-09-03"), String.class).getBody();
		assertThat(JsonProbe.str(filtered, "from")).isEqualTo("2026-09-03");
		assertThat(JsonProbe.arrLng(filtered, "channelGroups", 0, "applicationCount")).isEqualTo(1L);
	}

	@Test
	void AT35_channelEffectivenessEmptyReturnsZeroGroups() {
		String report = restTemplate.getForEntity(url("/analytics/channel-effectiveness"), String.class).getBody();
		assertThat(JsonProbe.arraySize(report, "channelGroups")).isEqualTo(0);
		assertThat(JsonProbe.arraySize(report, "resumeVersionGroups")).isEqualTo(0);
	}

	@Test
	void AT35_channelEffectivenessRejectsInvalidRange() {
		var resp = restTemplate.getForEntity(url("/analytics/channel-effectiveness?from=2026-09-10&to=2026-09-01"),
				String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	private String createJob() {
		return JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("效果对比科技", "Java 后端工程师")), String.class).getBody(), "id");
	}

	private String createApplication(String jobId, String appliedAt, String channel, String resumeVersion) {
		// 直接构造请求体，确保 resumeVersion 注入（TestFixtures.createApplicationBody 不支持 resumeVersion）
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
