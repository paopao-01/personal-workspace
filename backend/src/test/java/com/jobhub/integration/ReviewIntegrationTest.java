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
