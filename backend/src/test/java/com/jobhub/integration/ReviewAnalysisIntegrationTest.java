package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 完整复盘分析（PRD 8.6 / 16.2）：跨面试聚合问题回答、知识点表现、问题类型与结果汇总。
 * 指标以原始计数 + 分子/分母输出，不做趋势推断。
 */
class ReviewAnalysisIntegrationTest extends AbstractIntegrationTest {

	@Test
	void P1_reviewAnalysisAggregatesAcrossInterviews() {
		SeedInterview first = completedInterview("2026-09-10T10:00:00Z", "PASSED");
		String reviewBody1 = restTemplate.exchange(url("/interviews/" + first.interviewId() + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"PASSED\",\"noQuestionsRecorded\":false,"
				+ "\"interviewerFocus\":\"系统设计深度\",\"jobInterest\":\"高\",\"projectExpressRisk\":\"量化结果不足\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(reviewBody1, "projectExpressRisk")).isEqualTo("量化结果不足");

		String redisKpId = createKnowledgePoint("Redis 缓存");
		String projectKpId = createKnowledgePoint("项目表达");
		String reviewId1 = JsonProbe.str(reviewBody1, "id");
		createQuestion(reviewId1, "Redis 缓存穿透怎么解决？", "FULLY_ANSWERED", "技术", redisKpId);
		createQuestion(reviewId1, "讲讲你的项目经历", "PARTIALLY_ANSWERED", "项目", projectKpId);

		SeedInterview second = completedInterview("2026-09-20T14:00:00Z", "FAILED");
		String reviewBody2 = restTemplate.exchange(url("/interviews/" + second.interviewId() + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String mysqlKpId = createKnowledgePoint("MySQL 索引");
		String reviewId2 = JsonProbe.str(reviewBody2, "id");
		createQuestion(reviewId2, "MySQL 索引失效场景有哪些？", "UNANSWERED", "技术", mysqlKpId);
		createQuestion(reviewId2, "Redis 持久化机制对比", "FULLY_ANSWERED", null, redisKpId);

		String analysis = restTemplate.getForEntity(url("/reviews/analysis"), String.class).getBody();
		assertThat(JsonProbe.lng(analysis, "reviewCount")).isEqualTo(2L);
		assertThat(JsonProbe.lng(analysis, "questionStats.totalCount")).isEqualTo(4L);
		assertThat(JsonProbe.lng(analysis, "questionStats.fullyAnsweredCount")).isEqualTo(2L);
		assertThat(JsonProbe.lng(analysis, "questionStats.partiallyAnsweredCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(analysis, "questionStats.unansweredCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(analysis, "questionStats.fullyAnswered.numerator")).isEqualTo(2L);
		assertThat(JsonProbe.lng(analysis, "questionStats.fullyAnswered.denominator")).isEqualTo(4L);

		assertThat(JsonProbe.arraySize(analysis, "knowledgePointStats")).isEqualTo(3);
		assertThat(JsonProbe.arrStr(analysis, "knowledgePointStats", 0, "knowledgePoint.name")).isEqualTo("Redis 缓存");
		assertThat(JsonProbe.arrLng(analysis, "knowledgePointStats", 0, "questionCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(analysis, "knowledgePointStats", 0, "fullyAnsweredCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(analysis, "knowledgePointStats", 0, "notFullyAnsweredCount")).isEqualTo(0L);
		assertThat(JsonProbe.arrStr(analysis, "knowledgePointStats", 1, "knowledgePoint.name")).isEqualTo("MySQL 索引");
		assertThat(JsonProbe.arrLng(analysis, "knowledgePointStats", 1, "notFullyAnsweredCount")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(analysis, "knowledgePointStats", 2, "knowledgePoint.name")).isEqualTo("项目表达");

		assertThat(JsonProbe.arraySize(analysis, "questionTypeStats")).isEqualTo(3);
		// JsonProbe 对 JSON null 输出字符串 "null"（type 未填写）
		assertThat(JsonProbe.collectArrayField(analysis, "questionTypeStats", "type"))
			.containsExactlyInAnyOrder("技术", "项目", "null");
		assertThat(JsonProbe.arrLng(analysis, "questionTypeStats", 0, "questionCount")).isEqualTo(2L);
		assertThat(JsonProbe.arrLng(analysis, "questionTypeStats", 0, "fullyAnsweredCount")).isEqualTo(1L);

		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.reviewCount")).isEqualTo(2L);
		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.withResultCount")).isEqualTo(2L);
		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.passedCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.failedCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.pendingCount")).isEqualTo(0L);

		String filtered = restTemplate.getForEntity(url("/reviews/analysis?from=2026-09-15"), String.class).getBody();
		assertThat(JsonProbe.str(filtered, "timeRange.from")).isEqualTo("2026-09-15");
		assertThat(JsonProbe.str(filtered, "timeRange.to")).isEqualTo("null");
		assertThat(JsonProbe.lng(filtered, "reviewCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(filtered, "questionStats.totalCount")).isEqualTo(2L);
		assertThat(JsonProbe.lng(filtered, "questionStats.fullyAnswered.numerator")).isEqualTo(1L);
		assertThat(JsonProbe.lng(filtered, "questionStats.fullyAnswered.denominator")).isEqualTo(2L);
		assertThat(JsonProbe.arraySize(filtered, "knowledgePointStats")).isEqualTo(2);

		String byJob = restTemplate.getForEntity(url("/reviews/analysis?jobId=" + first.jobId()), String.class).getBody();
		assertThat(JsonProbe.lng(byJob, "reviewCount")).isEqualTo(1L);
		assertThat(JsonProbe.lng(byJob, "questionStats.totalCount")).isEqualTo(2L);
		assertThat(JsonProbe.arraySize(byJob, "knowledgePointStats")).isEqualTo(2);

		String compared = restTemplate.getForEntity(url("/reviews/analysis?from=2026-09-15&compareFrom=2026-09-01&compareTo=2026-09-14"), String.class).getBody();
		assertThat(JsonProbe.str(compared, "weakPointComparison.compareTimeRange.from")).isEqualTo("2026-09-01");
		assertThat(JsonProbe.str(compared, "weakPointComparison.compareTimeRange.to")).isEqualTo("2026-09-14");
		assertThat(compared).contains("项目表达");
		assertThat(JsonProbe.arraySize(compared, "weakPointComparison.items")).isGreaterThanOrEqualTo(1);
	}

	@Test
	void P1_reviewAnalysisEmptyOrUnknownJobReturnsZeros() {
		String analysis = restTemplate.getForEntity(url("/reviews/analysis"), String.class).getBody();
		assertThat(JsonProbe.lng(analysis, "reviewCount")).isEqualTo(0L);
		assertThat(JsonProbe.lng(analysis, "questionStats.totalCount")).isEqualTo(0L);
		assertThat(JsonProbe.lng(analysis, "questionStats.fullyAnswered.numerator")).isEqualTo(0L);
		assertThat(JsonProbe.lng(analysis, "questionStats.fullyAnswered.denominator")).isEqualTo(0L);
		assertThat(JsonProbe.arraySize(analysis, "knowledgePointStats")).isEqualTo(0);
		assertThat(JsonProbe.arraySize(analysis, "questionTypeStats")).isEqualTo(0);
		assertThat(JsonProbe.lng(analysis, "interviewResultSummary.withResultCount")).isEqualTo(0L);
		// JsonProbe 对 JSON null 输出字符串 "null"
		assertThat(JsonProbe.str(analysis, "timeRange.from")).isEqualTo("null");
		assertThat(JsonProbe.str(analysis, "timeRange.to")).isEqualTo("null");

		String unknownJob = restTemplate
			.getForEntity(url("/reviews/analysis?jobId=11111111-1111-1111-1111-111111111111"), String.class).getBody();
		assertThat(JsonProbe.lng(unknownJob, "reviewCount")).isEqualTo(0L);
		assertThat(JsonProbe.lng(unknownJob, "questionStats.totalCount")).isEqualTo(0L);
	}

	private record SeedInterview(String interviewId, String jobId) { }

	private SeedInterview completedInterview(String startsAt, String result) {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("复盘分析科技", "Java 后端工程师")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transition(applicationId, "APPLIED", "0");
		transition(applicationId, "RESUME_PASSED", "1");
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"技术一面\",\"startsAt\":\"" + startsAt + "\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"" + result + "\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
		return new SeedInterview(interviewId, jobId);
	}

	private void transition(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}

	private String createKnowledgePoint(String name) {
		return JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"" + name + "\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
	}

	private void createQuestion(String reviewId, String content, String answerStatus, String type, String knowledgePointId) {
		String typePart = type == null ? "" : ",\"type\":\"" + type + "\"";
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"" + content + "\",\"answerStatus\":\"" + answerStatus + "\"" + typePart
				+ ",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
	}
}
