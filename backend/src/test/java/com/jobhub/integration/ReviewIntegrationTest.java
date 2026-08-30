package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewIntegrationTest extends AbstractIntegrationTest {

	@Test
	void AT15_quickReviewAllowsMinimalDraftAndQuestion() {
		String interviewId = completedInterview();

		ResponseEntity<String> reviewResponse = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		);

		assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(reviewResponse.getBody(), "status")).isEqualTo("DRAFT");
		assertThat(JsonProbe.str(reviewResponse.getBody(), "interviewResult")).isEqualTo("FAILED");
		assertThat(JsonProbe.str(reviewResponse.getBody(), "noQuestionsRecorded")).isEqualTo("false");
		assertThat(JsonProbe.arraySize(reviewResponse.getBody(), "questions")).isEqualTo(0);

		String reviewId = JsonProbe.str(reviewResponse.getBody(), "id");
		ResponseEntity<String> questionResponse = restTemplate.exchange(
			url("/reviews/" + reviewId + "/questions"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"Redis 缓存一致性如何保证？\",\"answerStatus\":\"UNANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		);

		assertThat(questionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.str(questionResponse.getBody(), "answerStatus")).isEqualTo("UNANSWERED");
		assertThat(questionResponse.getBody()).contains("\"myAnswer\":null");
		assertThat(questionResponse.getBody()).contains("\"referenceAnswer\":null");
		assertThat(questionResponse.getBody()).contains("\"errorReason\":null");

		String saved = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.str(saved, "status")).isEqualTo("DRAFT");
		assertThat(JsonProbe.arraySize(saved, "questions")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(saved, "questions", 0, "answerStatus")).isEqualTo("UNANSWERED");
	}

	@Test
	void AT16_completeReviewRequiresQuestionOrExplicitNoQuestionsRecorded() {
		String interviewId = completedInterview();
		String reviewBody = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		long version = JsonProbe.lng(reviewBody, "version");

		ResponseEntity<String> invalidComplete = restTemplate.exchange(
			url("/reviews/" + reviewId + "/complete"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)),
			String.class
		);

		assertThat(invalidComplete.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(invalidComplete.getBody(), "code")).isEqualTo("BUSINESS_RULE_ERROR");
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody(), "status"))
			.isEqualTo("DRAFT");

		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"Redis 缓存一致性如何保证？\",\"answerStatus\":\"UNANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		String updatedReview = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();

		ResponseEntity<String> completed = restTemplate.exchange(
			url("/reviews/" + reviewId + "/complete"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(updatedReview, "version")),
			String.class
		);

		assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(completed.getBody(), "status")).isEqualTo("COMPLETED");
		assertThat(JsonProbe.arraySize(completed.getBody(), "questions")).isEqualTo(1);
	}

	@Test
	void AT17_updatingAnswerStatusRefreshesWeakKnowledgePointStatsWithDrillDownQuestions() {
		String interviewId = completedInterview();
		String reviewBody = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"Redis 缓存一致性\",\"category\":\"Redis\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");

		String firstQuestion = restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存与数据库双写时如何处理一致性？\",\"answerStatus\":\"UNANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"Cache Aside 删除缓存失败怎么办？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);

		String stats = restTemplate.getForEntity(url("/knowledge-points/weak"), String.class).getBody();
		assertThat(JsonProbe.arraySize(stats, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(stats, "", 0, "knowledgePoint.name")).isEqualTo("Redis 缓存一致性");
		assertThat(JsonProbe.arrDbl(stats, "", 0, "weightedWeaknessCount")).isEqualTo(1.5);
		assertThat(JsonProbe.arrInt(stats, "", 0, "questionCount")).isEqualTo(2);
		assertThat(JsonProbe.arraySize(stats, "0.questions")).isEqualTo(2);

		String firstQuestionId = JsonProbe.str(firstQuestion, "id");
		long firstQuestionVersion = JsonProbe.lng(firstQuestion, "version");
		ResponseEntity<String> updated = restTemplate.exchange(url("/interview-questions/" + firstQuestionId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存与数据库双写时如何处理一致性？\",\"answerStatus\":\"FULLY_ANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(firstQuestionVersion)), String.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(updated.getBody(), "answerStatus")).isEqualTo("FULLY_ANSWERED");

		String refreshedStats = restTemplate.getForEntity(url("/knowledge-points/weak"), String.class).getBody();
		assertThat(JsonProbe.arrDbl(refreshedStats, "", 0, "weightedWeaknessCount")).isEqualTo(0.5);
		assertThat(JsonProbe.arrInt(refreshedStats, "", 0, "questionCount")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(refreshedStats, "0.questions")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(refreshedStats, "0.questions", 0, "answerStatus")).isEqualTo("PARTIALLY_ANSWERED");
	}

	@Test
	void completedReviewCanBeEditedDirectlyWhileCompletionRequirementsRemainSatisfied() {
		String interviewId = completedInterview();
		String draft = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody();
		String reviewId = JsonProbe.str(draft, "id");
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"完成前的问题\",\"answerStatus\":\"UNANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		String ready = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		restTemplate.exchange(url("/reviews/" + reviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(ready, "version")),
			String.class);
		String completed = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();

		ResponseEntity<String> edited = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false,\"overallFeeling\":\"完成后补充感受\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(completed, "version")), String.class);
		assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(edited.getBody(), "status")).isEqualTo("COMPLETED");
		assertThat(JsonProbe.str(edited.getBody(), "overallFeeling")).isEqualTo("完成后补充感受");

		ResponseEntity<String> extraQuestion = restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"完成后补充的问题\",\"answerStatus\":\"PARTIALLY_ANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(extraQuestion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String afterQuestion = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.str(afterQuestion, "status")).isEqualTo("COMPLETED");
		assertThat(JsonProbe.arraySize(afterQuestion, "questions")).isEqualTo(2);
	}

	@Test
	void P1_reopenCompletedReviewKeepsQuestionsAndAllowsEditing() {
		String interviewId = completedInterview();
		String reviewBody = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\" reopen 前的问题\",\"answerStatus\":\"UNANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		String draftReview = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		restTemplate.exchange(url("/reviews/" + reviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(draftReview, "version")),
			String.class);
		// complete 会递增版本，reopen 前重新获取当前版本
		String completedReview = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();

		// 缺 If-Match-Version → 400
		ResponseEntity<String> missingVersion = restTemplate.exchange(
			url("/reviews/" + reviewId + "/reopen"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		// reopen 成功：状态回 DRAFT，问题与版本保留
		String reopened = restTemplate.exchange(
			url("/reviews/" + reviewId + "/reopen"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(completedReview, "version")),
			String.class
		).getBody();
		assertThat(JsonProbe.str(reopened, "status")).isEqualTo("DRAFT");
		assertThat(JsonProbe.arraySize(reopened, "questions")).isEqualTo(1);
		assertThat(JsonProbe.lng(reopened, "version")).isEqualTo(JsonProbe.lng(completedReview, "version") + 1);

		// DRAFT 再次 reopen → 422 ILLEGAL_STATE_TRANSITION
		ResponseEntity<String> illegal = restTemplate.exchange(
			url("/reviews/" + reviewId + "/reopen"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(reopened, "version")),
			String.class
		);
		assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(illegal.getBody(), "code")).isEqualTo("ILLEGAL_STATE_TRANSITION");

		// reopen 后可继续编辑：新增问题并再次完成
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\" reopen 后补充的问题\",\"answerStatus\":\"PARTIALLY_ANSWERED\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		String editedReview = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.arraySize(editedReview, "questions")).isEqualTo(2);
		ResponseEntity<String> completedAgain = restTemplate.exchange(
			url("/reviews/" + reviewId + "/complete"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(editedReview, "version")),
			String.class
		);
		assertThat(completedAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(completedAgain.getBody(), "status")).isEqualTo("COMPLETED");
	}

	@Test
	void P1_fullReviewExtraFieldsRoundtripOnReviewAndQuestion() {
		String interviewId = completedInterview();
		String saved = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"PASSED\",\"noQuestionsRecorded\":false,"
				+ "\"overallFeeling\":\"整体顺畅\",\"interviewerFocus\":\"系统设计\",\"jobInterest\":\"较高\",\"projectExpressRisk\":\"缺少量化结果\"}",
				"Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();
		assertThat(JsonProbe.str(saved, "projectExpressRisk")).isEqualTo("缺少量化结果");
		assertThat(JsonProbe.str(saved, "interviewerFocus")).isEqualTo("系统设计");
		assertThat(JsonProbe.str(saved, "jobInterest")).isEqualTo("较高");

		String question = restTemplate.exchange(
			url("/reviews/" + JsonProbe.str(saved, "id") + "/questions"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"Redis 缓存穿透怎么解决？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"type\":\"技术\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();

		String updated = restTemplate.exchange(
			url("/interview-questions/" + JsonProbe.str(question, "id")),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"content\":\"Redis 缓存穿透怎么解决？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"type\":\"技术\","
				+ "\"myAnswer\":\"提到布隆过滤器\",\"referenceAnswer\":\"布隆过滤器 + 空值缓存\",\"difficulty\":4,"
				+ "\"errorReason\":\"漏了空值缓存\",\"improvementPlan\":\"整理缓存异常场景清单\",\"knowledgePointIds\":[]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(question, "version")),
			String.class
		).getBody();
		assertThat(JsonProbe.str(updated, "myAnswer")).isEqualTo("提到布隆过滤器");
		assertThat(JsonProbe.str(updated, "referenceAnswer")).isEqualTo("布隆过滤器 + 空值缓存");
		assertThat(JsonProbe.intVal(updated, "difficulty")).isEqualTo(4);
		assertThat(JsonProbe.str(updated, "errorReason")).isEqualTo("漏了空值缓存");
		assertThat(JsonProbe.str(updated, "improvementPlan")).isEqualTo("整理缓存异常场景清单");

		String review = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.str(review, "projectExpressRisk")).isEqualTo("缺少量化结果");
		assertThat(JsonProbe.arrStr(review, "questions", 0, "myAnswer")).isEqualTo("提到布隆过滤器");
		assertThat(JsonProbe.arrInt(review, "questions", 0, "difficulty")).isEqualTo(4);

		// 复盘为全字段替换：再次保存草稿但不带 projectExpressRisk 时应清空
		String replaced = restTemplate.exchange(
			url("/interviews/" + interviewId + "/review"),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"PASSED\",\"noQuestionsRecorded\":false,\"overallFeeling\":\"整体顺畅\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(saved, "version")),
			String.class
		).getBody();
		assertThat(JsonProbe.str(replaced, "projectExpressRisk")).isNull();
	}

	private String completedInterview() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("复盘科技", "Java 后端工程师")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transition(applicationId, "APPLIED", "0");
		transition(applicationId, "RESUME_PASSED", "1");
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"技术一面\",\"startsAt\":\"2026-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)),
			String.class);
		return interviewId;
	}

	private void transition(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}
}
