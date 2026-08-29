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
 * dashboard 行动识别集成测试（AT-09）。
 *
 * 覆盖：
 *   APPLIED 投递无 nextAction → actionItems 含补充行动提示（priority=2）
 *   INTERVIEWING 投递 nextActionDueAt 早于当前 → 逾期天数显示 + 优先级 1，排在补充行动之前
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

	@Test
	void appliedApplicationWithoutNextAction_showsActionDuePrompt() {
		String jobId = createJob();
		String appId = createApplication(jobId, null, null);  // 无 nextAction
		transition(appId, "0", "APPLIED", TestFixtures.newKey());

		ResponseEntity<String> resp = restTemplate.exchange(
				url("/dashboard"), HttpMethod.GET, TestFixtures.httpJson(""), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		// 至少一条 actionItem，标题含"补充"
		assertThat(JsonProbe.arraySize(resp.getBody(), "actionItems")).isGreaterThanOrEqualTo(1);
		String missingTitle = findActionItemTitle(resp.getBody(), appId);
		assertThat(missingTitle).contains("补充");
	}

	@Test
	void interviewingApplicationOverdueNextAction_showsOverdueDays_sortsFirst() {
		String jobId = createJob();
		// 创建带 nextAction 且 nextActionDueAt 为过去时间的投递
		String appId = createApplication(jobId,
				"准备一面", "2020-01-01T00:00:00Z");  // 过去时间
		transition(appId, "0", "APPLIED", TestFixtures.newKey());
		transition(appId, "1", "RESUME_PASSED", TestFixtures.newKey());
		transition(appId, "2", "INTERVIEWING", TestFixtures.newKey());

		// 再造一条缺失行动的 APPLIED 投递（应排在逾期项之后）
		String jobId2 = createJob();
		String missingId = createApplication(jobId2, null, null);
		transition(missingId, "0", "APPLIED", TestFixtures.newKey());

		ResponseEntity<String> resp = restTemplate.exchange(
				url("/dashboard"), HttpMethod.GET, TestFixtures.httpJson(""), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		String overdueTitle = findActionItemTitle(resp.getBody(), appId);
		assertThat(overdueTitle).contains("逾期");

		// 逾期项 priority=1，排在 priority=2 的补充行动之前
		Integer overduePriority = findActionItemPriority(resp.getBody(), appId);
		Integer missingPriority = findActionItemPriority(resp.getBody(), missingId);
		assertThat(overduePriority).isEqualTo(1);
		assertThat(missingPriority).isEqualTo(2);
		assertThat(overduePriority).isLessThan(missingPriority);
	}

	@Test
	void scheduledFutureInterview_appearsInUpcomingInterviews() {
		String jobId = createJob();
		String appId = createApplication(jobId, null, null);
		transition(appId, "0", "APPLIED", TestFixtures.newKey());
		transition(appId, "1", "RESUME_PASSED", TestFixtures.newKey());
		ResponseEntity<String> created = restTemplate.exchange(
				url("/interviews"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						"{\"applicationId\":\"" + appId + "\",\"roundName\":\"技术一面\",\"startsAt\":\"2999-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
						"Idempotency-Key", TestFixtures.newKey()), String.class);
		String interviewId = JsonProbe.str(created.getBody(), "id");

		ResponseEntity<String> overview = restTemplate.exchange(
				url("/dashboard"), HttpMethod.GET, TestFixtures.httpJson(""), String.class);

		assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.arraySize(overview.getBody(), "upcomingInterviews")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(overview.getBody(), "upcomingInterviews", 0, "id"))
				.isEqualTo(interviewId);
	}

	@Test
	void weakKnowledgePoints_aggregatedFromReviewQuestions() {
		String jobId = createJob();
		String appId = createApplication(jobId, null, null);
		transition(appId, "0", "APPLIED", TestFixtures.newKey());
		transition(appId, "1", "RESUME_PASSED", TestFixtures.newKey());
		ResponseEntity<String> created = restTemplate.exchange(
				url("/interviews"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						"{\"applicationId\":\"" + appId + "\",\"roundName\":\"技术一面\",\"startsAt\":\"2999-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
						"Idempotency-Key", TestFixtures.newKey()), String.class);
		String interviewId = JsonProbe.str(created.getBody(), "id");
		long interviewVersion = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(),
						"If-Match-Version", String.valueOf(interviewVersion)), String.class);

		String reviewBody = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
						"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("{\"name\":\"Dashboard 弱点\",\"category\":\"聚合\"}",
						"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("{\"content\":\"聚合断言问题\",\"answerStatus\":\"UNANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
						"Idempotency-Key", TestFixtures.newKey()), String.class);

		ResponseEntity<String> overview = restTemplate.exchange(
				url("/dashboard"), HttpMethod.GET, TestFixtures.httpJson(""), String.class);

		assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.arraySize(overview.getBody(), "weakKnowledgePoints")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(overview.getBody(), "weakKnowledgePoints", 0, "knowledgePoint.name"))
				.isEqualTo("Dashboard 弱点");
		assertThat(JsonProbe.arrInt(overview.getBody(), "weakKnowledgePoints", 0, "questionCount")).isEqualTo(1);
		assertThat(JsonProbe.arrDbl(overview.getBody(), "weakKnowledgePoints", 0, "weightedWeaknessCount")).isEqualTo(1.0);
	}

	/** 在 actionItems 数组中找到 sourceRef.id == appId 的项，返回其 title。 */
	private String findActionItemTitle(String body, String appId) {
		int size = JsonProbe.arraySize(body, "actionItems");
		for (int i = 0; i < size; i++) {
			String refId = JsonProbe.arrStr(body, "actionItems", i, "sourceRef.id");
			if (appId.equals(refId)) {
				return JsonProbe.arrStr(body, "actionItems", i, "title");
			}
		}
		return null;
	}

	private Integer findActionItemPriority(String body, String appId) {
		int size = JsonProbe.arraySize(body, "actionItems");
		for (int i = 0; i < size; i++) {
			String refId = JsonProbe.arrStr(body, "actionItems", i, "sourceRef.id");
			if (appId.equals(refId)) {
				return JsonProbe.arrLng(body, "actionItems", i, "priority").intValue();
			}
		}
		return null;
	}

	private String createJob() {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")),
				String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}

	private String createApplication(String jobId, String nextAction, String nextActionDueAt) {
		ResponseEntity<String> resp = restTemplate.exchange(url("/applications"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(
						TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘",
								nextAction, nextActionDueAt, null),
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
}
