package com.jobhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class PreparationIntegrationTest extends AbstractIntegrationTest {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void AT20_preparationPackAggregatesTraceableSourcesAndExcludesPendingRequirements() throws Exception {
		String jobId = createJobWithOneConfirmedRequirement();
		String applicationId = createInterviewingApplication(jobId);
		String historicalInterviewId = createInterview(applicationId, "模拟面试", "2026-08-10T10:00:00Z");
		completeInterview(historicalInterviewId);
		String questionId = createWeakQuestion(historicalInterviewId);
		String taskId = createTaskFromQuestion(questionId);
		seedProjectEvidenceForConfirmedRequirement(jobId);
		String futureInterviewId = createInterview(applicationId, "技术一面", "2026-09-10T10:00:00Z");

		ResponseEntity<String> response = restTemplate.getForEntity(url("/interviews/" + futureInterviewId + "/preparation"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(JsonProbe.str(body, "interview.id")).isEqualTo(futureInterviewId);
		assertThat(JsonProbe.arraySize(body, "requirements")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(body, "requirements", 0, "requirement.confirmationStatus")).isEqualTo("CONFIRMED");
		assertThat(JsonProbe.arrStr(body, "projectCases", 0, "title")).isEqualTo("库存服务缓存改造");
		assertThat(JsonProbe.arraySize(body, "projectCases.0.evidenceRefs")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(body, "historicalQuestions", 0, "id")).isEqualTo(questionId);
		assertThat(JsonProbe.arrStr(body, "openTasks", 0, "id")).isEqualTo(taskId);
		assertThat(JsonProbe.arraySize(body, "checklist")).isEqualTo(1);

		JsonNode items = MAPPER.readTree(body).get("prioritizedItems");
		assertThat(items.size()).isGreaterThanOrEqualTo(5);
		for (JsonNode item : items) {
			assertThat(item.get("reasons").size()).isGreaterThanOrEqualTo(1);
			assertThat(item.get("sourceRefs").size()).isGreaterThanOrEqualTo(1);
			if ("REQUIREMENT".equals(item.get("type").asText())) {
				assertThat(item.get("sourceRefs").get(0).get("label").asText()).isEqualTo("Redis");
			}
		}
		assertThat(MAPPER.readTree(body).get("requirements").findValuesAsText("confirmationStatus"))
			.containsOnly("CONFIRMED");
		assertThat(body).contains("\"type\":\"REQUIREMENT\"");
		assertThat(body).contains("\"type\":\"PROJECT_CASE\"");
		assertThat(body).contains("\"type\":\"QUESTION\"");
		assertThat(body).contains("\"type\":\"TASK\"");
		assertThat(body).contains("\"type\":\"CHECKLIST\"");
	}

	@Test
	void AT21_preparationPackUsesPendingPlaceholderWhenNoProjectCaseExists() {
		String jobId = createJobWithOneConfirmedRequirement();
		String applicationId = createInterviewingApplication(jobId);
		String interviewId = createInterview(applicationId, "技术一面", "2026-09-10T10:00:00Z");

		String body = restTemplate.getForEntity(url("/interviews/" + interviewId + "/preparation"), String.class).getBody();

		assertThat(JsonProbe.arrStr(body, "projectCases", 0, "title")).isEqualTo("待补充项目案例");
		assertThat(JsonProbe.arrStr(body, "projectCases", 0, "scenario")).isEqualTo("待补充");
		assertThat(body).doesNotContain("综合能力分数");
		assertThat(body).doesNotContain("能力分");
	}

	private String createJobWithOneConfirmedRequirement() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("准备包科技", "Java 后端工程师")), String.class).getBody(), "id");
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{}", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String requirementId = jdbc.queryForObject(
			"SELECT id FROM job_requirement WHERE job_id=? AND normalized_name='Redis' LIMIT 1",
			String.class, jobId);
		long version = jdbc.queryForObject("SELECT version FROM job_requirement WHERE id=?", Long.class, requirementId);
		restTemplate.exchange(url("/job-requirements/" + requirementId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("""
				{"confirmationStatus":"CONFIRMED","normalizedName":"Redis","type":"MUST","manualMatchStatus":"SELF_REPORTED_NO_EVIDENCE","reason":"需要补充项目证据"}
				""", "If-Match-Version", String.valueOf(version)), String.class);
		return jobId;
	}

	private String createInterviewingApplication(String jobId) {
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘",
				"准备 Redis 项目案例", "2026-09-09T10:00:00Z", null), "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		transitionApplication(applicationId, "APPLIED", "0");
		transitionApplication(applicationId, "RESUME_PASSED", "1");
		return applicationId;
	}

	private String createInterview(String applicationId, String roundName, String startsAt) {
		return JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"" + roundName
				+ "\",\"startsAt\":\"" + startsAt + "\",\"eventTimeZone\":\"Asia/Shanghai\",\"preparationChecklist\":[\"准备库存服务项目讲解\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private void completeInterview(String interviewId) {
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
	}

	private String createWeakQuestion(String interviewId) {
		String reviewId = JsonProbe.str(restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"Redis 缓存一致性\",\"category\":\"Redis\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		return JsonProbe.str(restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存与数据库双写如何保证一致性？\",\"answerStatus\":\"UNANSWERED\",\"knowledgePointIds\":[\""
				+ knowledgePointId + "\"]}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private String createTaskFromQuestion(String questionId) {
		return JsonProbe.str(restTemplate.exchange(url("/interview-questions/" + questionId + "/create-task"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{"mode":"CREATE_NEW","title":"梳理 Redis 缓存一致性方案","acceptanceCriteria":"能说明 Cache Aside 更新顺序和异常补偿。","verificationMethod":"口述演练"}
				""", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private void seedProjectEvidenceForConfirmedRequirement(String jobId) {
		String now = "2026-08-29T00:00:00Z";
		String skillId = "10000000-0000-0000-0000-000000000004";
		String requirementId = jdbc.queryForObject(
			"SELECT id FROM job_requirement WHERE job_id=? AND confirmation_status='CONFIRMED' LIMIT 1",
			String.class, jobId);
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
			skillId, "Redis", "redis", "Redis", 1, now, now);
		jdbc.update("INSERT INTO requirement_skill (requirement_id, skill_id, created_at) VALUES (?,?,?)", requirementId, skillId, now);
		String projectId = "30000000-0000-0000-0000-000000000001";
		String evidenceId = "40000000-0000-0000-0000-000000000001";
		jdbc.update("INSERT INTO project (id, title, scenario, approach, problem_solved, result_text, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,?,0)",
			projectId, "库存服务缓存改造", "库存查询接口压力高。", "使用 Cache Aside 管理热点数据。", "降低重复查询并保持可接受一致性。", null, now, now);
		jdbc.update("INSERT INTO evidence (id, type, title, where_used, problem_solved, approach, result_text, url_or_path, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,?,?,?,0)",
			evidenceId, "ARCHITECTURE_DIAGRAM", "缓存流程图", "库存查询接口", "解释缓存命中、回源和失效路径", "Cache Aside", null, "C:/example/jobhub-demo/cache.png", now, now);
		jdbc.update("INSERT INTO skill_evidence (skill_id, evidence_id, created_at) VALUES (?,?,?)", skillId, evidenceId, now);
		jdbc.update("INSERT INTO project_evidence (project_id, evidence_id, created_at) VALUES (?,?,?)", projectId, evidenceId, now);
	}

	private void transitionApplication(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}
}
