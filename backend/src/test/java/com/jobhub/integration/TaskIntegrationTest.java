package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class TaskIntegrationTest extends AbstractIntegrationTest {

	@Test
	void AT18_createTaskFromQuestionOnlyPersistsAfterUserConfirmation() {
		String questionId = weakQuestion();
		String before = restTemplate.getForEntity(url("/tasks"), String.class).getBody();
		assertThat(JsonProbe.intVal(before, "total")).isEqualTo(0);

		ResponseEntity<String> created = restTemplate.exchange(
			url("/interview-questions/" + questionId + "/create-task"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{
				  "mode":"CREATE_NEW",
				  "title":"梳理 Redis 缓存一致性",
				  "acceptanceCriteria":"能说明 Cache Aside 的更新顺序、失效策略和异常补偿。",
				  "verificationMethod":"口述演练并记录验证结果"
				}
				""", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		);

		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.str(created.getBody(), "title")).isEqualTo("梳理 Redis 缓存一致性");
		assertThat(JsonProbe.str(created.getBody(), "status")).isEqualTo("TODO");
		assertThat(JsonProbe.arraySize(created.getBody(), "knowledgePoints")).isEqualTo(1);
		String taskId = JsonProbe.str(created.getBody(), "id");
		Integer questionSourceCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM task_source WHERE task_id=? AND source_type='QUESTION' AND source_id=?",
			Integer.class, taskId, questionId);
		assertThat(questionSourceCount).isEqualTo(1);
		String after = restTemplate.getForEntity(url("/tasks"), String.class).getBody();
		assertThat(JsonProbe.intVal(after, "total")).isEqualTo(1);
	}

	@Test
	void AT19_completingTaskKeepsSkillLevelAndWeakQuestions() {
		String skillId = seedRedisSkill(2);
		String questionId = weakQuestion();
		String task = restTemplate.exchange(
			url("/interview-questions/" + questionId + "/create-task"),
			HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{
				  "mode":"CREATE_NEW",
				  "title":"补齐 Redis 缓存一致性",
				  "acceptanceCriteria":"能完整讲清一致性风险。",
				  "verificationMethod":"自测和口述演练"
				}
				""", "Idempotency-Key", TestFixtures.newKey()),
			String.class
		).getBody();
		String taskId = JsonProbe.str(task, "id");

		String started = transitionTask(taskId, JsonProbe.lng(task, "version"), "IN_PROGRESS", null).getBody();
		ResponseEntity<String> completed = transitionTask(taskId, JsonProbe.lng(started, "version"), "COMPLETED",
			"已完成 3 次口述演练并记录卡点");

		assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(completed.getBody(), "status")).isEqualTo("COMPLETED");
		assertThat(JsonProbe.str(completed.getBody(), "verificationResult")).isEqualTo("已完成 3 次口述演练并记录卡点");
		Integer selfLevel = jdbc.queryForObject("SELECT self_level FROM user_skill WHERE skill_id=?", Integer.class, skillId);
		assertThat(selfLevel).isEqualTo(2);

		String weak = restTemplate.getForEntity(url("/knowledge-points/weak"), String.class).getBody();
		assertThat(JsonProbe.arrStr(weak, "", 0, "questions.0.id")).isEqualTo(questionId);
	}

	@Test
	void p0_directJobTask_canBeListedBySourceAndExposesSourceReference() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("任务关联科技", "Java 后端工程师")), String.class).getBody(), "id");
		ResponseEntity<String> created = restTemplate.exchange(url("/tasks"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{"title":"阅读岗位要求","priority":"HIGH","dueAt":"2099-01-01T00:00:00Z","relatedJobIds":["%s"]}
				""".formatted(jobId), "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.arrStr(created.getBody(), "sourceRefs", 0, "type")).isEqualTo("JOB");
		assertThat(JsonProbe.arrStr(created.getBody(), "sourceRefs", 0, "id")).isEqualTo(jobId);

		String filtered = restTemplate.getForObject(url("/tasks?sourceType=JOB&jobId=" + jobId), String.class);
		assertThat(JsonProbe.intVal(filtered, "total")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(filtered, "items", 0, "sourceRefs.0.label")).isEqualTo("Java 后端工程师");
	}

	private ResponseEntity<String> transitionTask(String taskId, long version, String targetStatus, String verificationResult) {
		String body = verificationResult == null
			? "{\"targetStatus\":\"" + targetStatus + "\"}"
			: "{\"targetStatus\":\"" + targetStatus + "\",\"verificationResult\":\"" + verificationResult + "\"}";
		return restTemplate.exchange(url("/tasks/" + taskId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
	}

	private String weakQuestion() {
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
		return JsonProbe.str(restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存与数据库双写如何保证一致性？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private String completedInterview() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("任务科技", "Java 后端工程师")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transitionApplication(applicationId, "APPLIED", "0");
		transitionApplication(applicationId, "RESUME_PASSED", "1");
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"技术一面\",\"startsAt\":\"2026-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(version)),
			String.class);
		return interviewId;
	}

	private void transitionApplication(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}

	private String seedRedisSkill(int selfLevel) {
		String now = "2026-08-29T00:00:00Z";
		String skillId = "10000000-0000-0000-0000-000000000004";
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
			skillId, "Redis", "redis", "Redis", 1, now, now);
		jdbc.update("INSERT INTO user_skill (id, user_id, skill_id, self_level, evidence_status, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,0)",
			"11000000-0000-0000-0000-000000000004", "00000000-0000-0000-0000-000000000001", skillId, selfLevel,
			"NO_EVIDENCE", now, now);
		return skillId;
	}
}
