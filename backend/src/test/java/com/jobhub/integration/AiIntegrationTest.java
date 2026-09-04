package com.jobhub.integration;

import com.jobhub.integration.support.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1 AI 基础设施（PRD 9.2）：可切换供应商配置、异步任务状态机、候选变更逐项确认。
 * 用 JDK HttpServer 充当 OpenAI 兼容假供应商，覆盖真实 HTTP 调用与解析路径。
 */
class AiIntegrationTest extends AbstractIntegrationTest {

	private static final com.fasterxml.jackson.databind.ObjectMapper CONTENT_JSON =
			new com.fasterxml.jackson.databind.ObjectMapper();

	private static final String CANDIDATES_JSON = """
		[
		  {"type":"MUST","rawText":"熟悉 Spring Boot 与 MySQL","normalizedName":"Spring Boot/MySQL","proficiencyText":"熟练"},
		  {"type":"BONUS","rawText":"有 Redis 高并发经验","normalizedName":"Redis 高并发","proficiencyText":""},
		  {"type":"INVALID","rawText":"类型非法应被跳过","normalizedName":"bad","proficiencyText":""}
		]""";
	private static final String QUESTION_CLASSIFICATION_JSON = """
		[
		  {"type":"TECHNICAL","rawText":"Redis 持久化机制如何选择？","normalizedName":"技术基础","rationale":"问题要求解释具体技术机制。"}
		]""";
	private static final String ANSWER_QUALITY_JSON = """
		[
		  {"type":"ANSWER_QUALITY","rawText":"覆盖了核心概念，但缺少故障场景与取舍说明。","normalizedName":"回答质量分析","answerStatus":"PARTIALLY_ANSWERED","referenceAnswer":"先说明机制，再比较适用场景、风险和恢复策略。","errorReason":"缺少方案取舍和边界条件。","improvementPlan":"按机制、场景、取舍、案例四步重新组织回答。","rationale":"原回答只提到了机制名称。"}
		]""";
	private static final String TASK_SUGGESTION_JSON = """
		[
		  {"type":"LEARNING_TASK","rawText":"完成一次可验证的口述演练。","taskTitle":"补齐缓存一致性回答","priority":"HIGH","estimatedMinutes":45,"learningGoal":"能够解释核心机制和边界条件。","acceptanceCriteria":"能在 3 分钟内完整回答原问题并说明一个边界场景。","verificationMethod":"口述演练并记录验证结果","rationale":"原问题回答存在薄弱点。"}
		]""";
	private static final String MOCK_INTERVIEW_JSON = """
		[{"type":"MOCK_INTERVIEW_OPENING","rawText":"我会从场景、方案、解决的问题和结果四部分讲解该项目。","rationale":"请解释方案选择时考虑过哪些取舍？"}]""";

	private static HttpServer fakeProvider;
	private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

	@BeforeAll
	static void startFakeProvider() throws Exception {
		fakeProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakeProvider.createContext("/v1/chat/completions", exchange -> {
			// 把候选 JSON 数组序列化为字符串字面量嵌入 OpenAI 响应，避免手工转义
			String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String candidates = request.contains("Java 项目面试官") ? MOCK_INTERVIEW_JSON : request.contains("LEARNING_TASK")
				? TASK_SUGGESTION_JSON
				: request.contains("ANSWER_QUALITY")
				? ANSWER_QUALITY_JSON
				: request.contains("PROJECT_EXPERIENCE") ? QUESTION_CLASSIFICATION_JSON : CANDIDATES_JSON;
			String contentJson = CONTENT_JSON.writeValueAsString(candidates);
			byte[] body = ("{\"choices\":[{\"message\":{\"content\":" + contentJson + "}}]}")
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
			REQUEST_COUNT.incrementAndGet();
		});
		fakeProvider.start();
	}

	@AfterAll
	static void stopFakeProvider() {
		if (fakeProvider != null) {
			fakeProvider.stop(0);
		}
	}

	private String createActiveProvider(String baseUrl) {
		String body = restTemplate.exchange(url("/ai-providers"), HttpMethod.POST,
			TestFixtures.httpJson("{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"假供应商\",\"baseUrl\":\"" + baseUrl
					+ "\",\"model\":\"fake-model\",\"apiKey\":\"sk-test-key\"}"), String.class).getBody();
		String id = JsonProbe.str(body, "id");
		restTemplate.exchange(url("/ai-providers/" + id + "/activate"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		return id;
	}

	private String createJob() {
		return JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("AI验证科技", "P1 AI 岗位")), String.class).getBody(), "id");
	}

	private String createProject() {
		return JsonProbe.str(restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"title\":\"订单平台\",\"scenario\":\"订单高峰\",\"approach\":\"异步削峰\",\"problemSolved\":\"降低延迟\",\"result\":\"\"}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private String createExtraction(String jobId) {
		return restTemplate.exchange(url("/ai-jobs"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"jobType\":\"JD_EXTRACTION\",\"objectId\":\"" + jobId + "\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
	}

	private String createQuestionClassification(String questionId) {
		return restTemplate.exchange(url("/interview-questions/" + questionId + "/ai-classification"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
	}

	private String createAnswerQualityAnalysis(String questionId) {
		return restTemplate.exchange(url("/interview-questions/" + questionId + "/ai-answer-analysis"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
	}

	private String createTaskSuggestion(String questionId) {
		return restTemplate.exchange(url("/interview-questions/" + questionId + "/ai-task-suggestion"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
	}

	private String createCompletedInterview() {
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
			TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
		return interviewId;
	}

	private void transition(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}

	private String waitForTerminal(String aiJobId) throws InterruptedException {
		for (int i = 0; i < 200; i++) {
			String body = restTemplate.getForEntity(url("/ai-jobs/" + aiJobId), String.class).getBody();
			String status = JsonProbe.str(body, "status");
			if (!"QUEUED".equals(status) && !"RUNNING".equals(status)) {
				return body;
			}
			Thread.sleep(200);
		}
		throw new IllegalStateException("AI job did not finish in time: " + aiJobId);
	}

	@Test
	void V03_mockInterviewPersistsOpeningWithoutChangingProject() throws Exception {
		String provider = createActiveProvider("http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1");
		assertThat(provider).isNotBlank();
		String projectId = createProject();
		String created = restTemplate.exchange(url("/mock-interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"projectId\":\"" + projectId + "\"}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String sessionId = JsonProbe.str(created, "id"); String jobId = JsonProbe.str(created, "aiJobId");
		waitForTerminal(jobId);
		String session = restTemplate.getForEntity(url("/mock-interviews/" + sessionId), String.class).getBody();
		assertThat(JsonProbe.str(session, "status")).isEqualTo("ACTIVE");
		long sessionVersion = JsonProbe.lng(session, "version");
		String answer = restTemplate.exchange(url("/mock-interviews/" + sessionId + "/answers"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"我会先说明流量峰值，再解释异步削峰的取舍。\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(sessionVersion)), String.class).getBody();
		assertThat(JsonProbe.str(answer, "speaker")).isEqualTo("USER");
		ResponseEntity<String> staleAnswer = restTemplate.exchange(url("/mock-interviews/" + sessionId + "/answers"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"不应保存的重复作答\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", String.valueOf(sessionVersion)), String.class);
		assertThat(staleAnswer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		String answeredSession = restTemplate.getForEntity(url("/mock-interviews/" + sessionId), String.class).getBody();
		String completed = restTemplate.exchange(url("/mock-interviews/" + sessionId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"targetStatus\":\"COMPLETED\"}", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", JsonProbe.str(answeredSession, "version")), String.class).getBody();
		assertThat(JsonProbe.str(completed, "status")).isEqualTo("COMPLETED");
		String turns = restTemplate.getForEntity(url("/mock-interviews/" + sessionId + "/turns"), String.class).getBody();
		assertThat(turns).contains("场景、方案", "取舍", "异步削峰的取舍");
		assertThat(restTemplate.getForEntity(url("/projects"), String.class).getBody()).contains("订单平台");
	}

	@Test
	void P1_providerConfigIsSwitchableAndKeyNeverEchoed() {
		ResponseEntity<String> created = restTemplate.exchange(url("/ai-providers"), HttpMethod.POST,
			TestFixtures.httpJson("{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"供应商A\",\"baseUrl\":\"http://127.0.0.1:1/v1\",\"model\":\"m1\",\"apiKey\":\"sk-a\"}"),
			String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String providerA = JsonProbe.str(created.getBody(), "id");
		assertThat(JsonProbe.str(created.getBody(), "hasCredential")).isEqualTo("true");
		assertThat(JsonProbe.str(created.getBody(), "isActive")).isEqualTo("true");
		assertThat(created.getBody()).doesNotContain("sk-a");

		String providerB = createActiveProvider("http://127.0.0.1:1/v1");
		String list = restTemplate.getForEntity(url("/ai-providers"), String.class).getBody();
		int indexA = providerA.equals(JsonProbe.str(list, "0.id")) ? 0 : 1;
		int indexB = 1 - indexA;
		assertThat(JsonProbe.arrStr(list, "", indexA, "isActive")).isEqualTo("false");
		assertThat(JsonProbe.arrStr(list, "", indexB, "isActive")).isEqualTo("true");

		// 更新不带 apiKey → 凭据保留（activate 会递增版本，用当前实际版本）
		String detailA = restTemplate.getForEntity(url("/ai-providers/" + providerA), String.class).getBody();
		String currentVersion = JsonProbe.str(detailA, "version");

		ResponseEntity<String> updated = restTemplate.exchange(url("/ai-providers/" + providerA), HttpMethod.PUT,
			requestWithVersion(currentVersion, "{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"供应商A改名\",\"baseUrl\":\"http://127.0.0.1:1/v1\",\"model\":\"m1\"}"),
			String.class);

		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(updated.getBody(), "hasCredential")).isEqualTo("true");

		// 旧版本 → 409
		ResponseEntity<String> conflict = restTemplate.exchange(url("/ai-providers/" + providerA), HttpMethod.PUT,
			requestWithVersion(currentVersion, "{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"x\",\"baseUrl\":\"http://127.0.0.1:1/v1\",\"model\":\"m\"}"),
			String.class);
		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	private HttpEntity<String> requestWithVersion(String version, String body) {
		return TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", version);
	}

	@Test
	void P1_providerDeleteProtectsActiveReferencesAndVersion() throws Exception {
		ResponseEntity<String> activeResponse = restTemplate.exchange(url("/ai-providers"), HttpMethod.POST,
			TestFixtures.httpJson("{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"激活供应商\",\"baseUrl\":\"http://127.0.0.1:1/v1\",\"model\":\"m1\",\"apiKey\":\"sk-active\"}"),
			String.class);
		String activeId = JsonProbe.str(activeResponse.getBody(), "id");
		String activeVersion = JsonProbe.str(activeResponse.getBody(), "version");

		ResponseEntity<String> removableResponse = restTemplate.exchange(url("/ai-providers"), HttpMethod.POST,
			TestFixtures.httpJson("{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"待删除供应商\",\"baseUrl\":\"http://127.0.0.1:1/v1\",\"model\":\"m2\"}"),
			String.class);
		String removableId = JsonProbe.str(removableResponse.getBody(), "id");
		String removableVersion = JsonProbe.str(removableResponse.getBody(), "version");
		assertThat(JsonProbe.str(removableResponse.getBody(), "isActive")).isEqualTo("false");

		ResponseEntity<String> stale = restTemplate.exchange(url("/ai-providers/" + removableId), HttpMethod.DELETE,
			requestWithVersion(String.valueOf(Long.parseLong(removableVersion) + 1), ""), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		ResponseEntity<String> deleted = restTemplate.exchange(url("/ai-providers/" + removableId), HttpMethod.DELETE,
			requestWithVersion(removableVersion, ""), String.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(restTemplate.getForEntity(url("/ai-providers"), String.class).getBody()).doesNotContain(removableId);

		ResponseEntity<String> referencedResponse = restTemplate.exchange(url("/ai-providers"), HttpMethod.POST,
			TestFixtures.httpJson("{\"providerType\":\"OPENAI_COMPATIBLE\",\"name\":\"已引用供应商\",\"baseUrl\":\"http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1\",\"model\":\"fake-model\"}"),
			String.class);
		String referencedId = JsonProbe.str(referencedResponse.getBody(), "id");
		restTemplate.exchange(url("/ai-providers/" + referencedId + "/activate"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String jobId = createJob();
		String extraction = createExtraction(jobId);
		waitForTerminal(JsonProbe.str(extraction, "id"));
		ResponseEntity<String> reactivate = restTemplate.exchange(url("/ai-providers/" + activeId + "/activate"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(reactivate.getStatusCode()).isEqualTo(HttpStatus.OK);
		String referencedVersion = JsonProbe.str(restTemplate.getForEntity(url("/ai-providers/" + referencedId), String.class).getBody(), "version");
		ResponseEntity<String> referencedDelete = restTemplate.exchange(url("/ai-providers/" + referencedId), HttpMethod.DELETE,
			requestWithVersion(referencedVersion, ""), String.class);
		assertThat(referencedDelete.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		String currentActiveVersion = JsonProbe.str(restTemplate.getForEntity(url("/ai-providers/" + activeId), String.class).getBody(), "version");
		ResponseEntity<String> activeDelete = restTemplate.exchange(url("/ai-providers/" + activeId), HttpMethod.DELETE,
			requestWithVersion(currentActiveVersion, ""), String.class);
		assertThat(activeDelete.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/ai-providers/" + activeId), String.class).getBody(), "isActive")).isEqualTo("true");
	}

	@Test
	void P1_jdExtractionProducesCandidatesAndAcceptRejectCreatesRequirements() throws Exception {
		String baseUrl = "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1";
		createActiveProvider(baseUrl);
		String jobId = createJob();
		String aiJob = createExtraction(jobId);
		assertThat(JsonProbe.str(aiJob, "status")).isEqualTo("QUEUED");
		assertThat(JsonProbe.str(aiJob, "promptVersion")).isEqualTo("JD_EXTRACTION_V1");

		String finished = waitForTerminal(JsonProbe.str(aiJob, "id"));
		assertThat(JsonProbe.str(finished, "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.arraySize(finished, "items")).isEqualTo(2);
		assertThat(JsonProbe.str(finished, "items.0.payload.type")).isEqualTo("MUST");
		assertThat(JsonProbe.str(finished, "items.1.payload.rawText")).contains("Redis");

		// 采纳第一条（带编辑）：创建 source=AI 的 PENDING 岗位要求
		String item0 = JsonProbe.str(finished, "items.0.id");
		String accept = restTemplate.exchange(url("/ai-job-items/" + item0 + "/accept"), HttpMethod.POST,
			TestFixtures.httpJson("{\"payload\":{\"type\":\"MUST\",\"rawText\":\"熟悉 Spring Boot 与 MySQL（编辑后）\",\"normalizedName\":\"Spring Boot/MySQL\",\"proficiencyText\":\"精通\"}}"),
			String.class).getBody();
		assertThat(JsonProbe.str(accept, "status")).isEqualTo("ACCEPTED");
		String requirementId = JsonProbe.str(accept, "requirementId");
		assertThat(requirementId).isNotEqualTo("null");

		String requirements = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		boolean foundEditedAiPending = false;
		for (int i = 0; i < JsonProbe.arraySize(requirements, ""); i++) {
			if (requirementId.equals(JsonProbe.arrStr(requirements, "", i, "id"))) {
				assertThat(JsonProbe.arrStr(requirements, "", i, "confirmationStatus")).isEqualTo("PENDING");
				foundEditedAiPending = JsonProbe.arrStr(requirements, "", i, "rawText").contains("编辑后");
			}
		}
		assertThat(foundEditedAiPending).isTrue();

		// 拒绝第二条
		String item1 = JsonProbe.str(finished, "items.1.id");
		String rejected = restTemplate.exchange(url("/ai-job-items/" + item1 + "/reject"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(rejected, "status")).isEqualTo("REJECTED");

		// 重新生成产生新任务，既有条目不受影响
		String second = createExtraction(jobId);
		String secondFinished = waitForTerminal(JsonProbe.str(second, "id"));
		assertThat(JsonProbe.str(secondFinished, "status")).isEqualTo("SUCCEEDED");
		String history = restTemplate.getForEntity(url("/jobs/" + jobId + "/ai-jobs"), String.class).getBody();
		assertThat(JsonProbe.arraySize(history, "")).isEqualTo(2);
		assertThat(JsonProbe.str(history, "1.items.0.status")).isEqualTo("ACCEPTED");

		// 重复采纳同一条目 → 422
		ResponseEntity<String> duplicate = restTemplate.exchange(url("/ai-job-items/" + item0 + "/accept"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	@Test
	void P1_questionClassificationIsEditableAndRequiresCurrentQuestionVersion() throws Exception {
		String baseUrl = "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1";
		createActiveProvider(baseUrl);
		String interviewId = createCompletedInterview();
		String review = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(review, "id");
		String question = restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"Redis 持久化机制如何选择？\",\"answerStatus\":\"UNANSWERED\",\"type\":\"自定义类型\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String questionId = JsonProbe.str(question, "id");

		String aiJob = createQuestionClassification(questionId);
		assertThat(JsonProbe.str(aiJob, "jobType")).isEqualTo("QUESTION_CLASSIFICATION");
		assertThat(JsonProbe.str(aiJob, "promptVersion")).isEqualTo("QUESTION_CLASSIFICATION_V1");
		String finished = waitForTerminal(JsonProbe.str(aiJob, "id"));
		assertThat(JsonProbe.str(finished, "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.str(finished, "items.0.status")).isEqualTo("PROPOSED");
		assertThat(JsonProbe.str(finished, "items.0.payload.type")).isEqualTo("TECHNICAL");

		String itemId = JsonProbe.str(finished, "items.0.id");
		String accepted = restTemplate.exchange(url("/ai-job-items/" + itemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"payload\":{\"type\":\"SYSTEM_DESIGN\",\"rawText\":\"Redis 持久化机制如何选择？\",\"normalizedName\":\"系统设计\",\"rationale\":\"用户编辑后的分类理由\"}}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.str(accepted, "status")).isEqualTo("ACCEPTED");
		assertThat(JsonProbe.str(accepted, "requirementId")).isEqualTo("null");

		String afterAccept = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.str(afterAccept, "questions.0.type")).isEqualTo("SYSTEM_DESIGN");
		assertThat(JsonProbe.str(afterAccept, "questions.0.answerStatus")).isEqualTo("UNANSWERED");
		assertThat(JsonProbe.intVal(afterAccept, "questions.0.version")).isEqualTo(1);

		String secondJob = createQuestionClassification(questionId);
		String secondFinished = waitForTerminal(JsonProbe.str(secondJob, "id"));
		String secondItemId = JsonProbe.str(secondFinished, "items.0.id");
		ResponseEntity<String> stale = restTemplate.exchange(url("/ai-job-items/" + secondItemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		String rejected = restTemplate.exchange(url("/ai-job-items/" + secondItemId + "/reject"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(rejected, "status")).isEqualTo("REJECTED");
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody(),
			"questions.0.type")).isEqualTo("SYSTEM_DESIGN");
	}

	@Test
	void P1_answerQualityAnalysisIsEditableAndPreservesUserFacts() throws Exception {
		String baseUrl = "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1";
		createActiveProvider(baseUrl);
		String interviewId = createCompletedInterview();
		String review = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(review, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"AI 回答分析知识点\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		String question = restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存一致性如何处理？\",\"answerStatus\":\"UNANSWERED\",\"type\":\"自定义类型\",\"knowledgePointIds\":[\""
				+ knowledgePointId + "\"]}", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String questionId = JsonProbe.str(question, "id");

		ResponseEntity<String> missingAnswer = restTemplate.exchange(
			url("/interview-questions/" + questionId + "/ai-answer-analysis"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missingAnswer.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		String detailedQuestion = restTemplate.exchange(url("/interview-questions/" + questionId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存一致性如何处理？\",\"answerStatus\":\"UNANSWERED\",\"type\":\"自定义类型\","
				+ "\"myAnswer\":\"我会先更新数据库，再删除缓存。\",\"referenceAnswer\":\"旧参考答案\",\"difficulty\":4,"
				+ "\"errorReason\":\"旧错误原因\",\"improvementPlan\":\"旧改进方案\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.intVal(detailedQuestion, "version")).isEqualTo(1);

		String aiJob = createAnswerQualityAnalysis(questionId);
		assertThat(JsonProbe.str(aiJob, "jobType")).isEqualTo("ANSWER_QUALITY_ANALYSIS");
		assertThat(JsonProbe.str(aiJob, "promptVersion")).isEqualTo("ANSWER_QUALITY_ANALYSIS_V1");
		String finished = waitForTerminal(JsonProbe.str(aiJob, "id"));
		assertThat(JsonProbe.str(finished, "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.str(finished, "items.0.payload.type")).isEqualTo("ANSWER_QUALITY");
		assertThat(JsonProbe.str(finished, "items.0.payload.answerStatus")).isEqualTo("PARTIALLY_ANSWERED");

		String classificationHistory = restTemplate.getForEntity(
			url("/interview-questions/" + questionId + "/ai-jobs?jobType=QUESTION_CLASSIFICATION"), String.class).getBody();
		String analysisHistory = restTemplate.getForEntity(
			url("/interview-questions/" + questionId + "/ai-jobs?jobType=ANSWER_QUALITY_ANALYSIS"), String.class).getBody();
		assertThat(JsonProbe.arraySize(classificationHistory, "")).isZero();
		assertThat(JsonProbe.arraySize(analysisHistory, "")).isEqualTo(1);

		String itemId = JsonProbe.str(finished, "items.0.id");
		String accepted = restTemplate.exchange(url("/ai-job-items/" + itemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"payload\":{\"type\":\"ANSWER_QUALITY\",\"rawText\":\"用户编辑后的评价\","
				+ "\"normalizedName\":\"回答质量分析\",\"answerStatus\":\"FULLY_ANSWERED\",\"referenceAnswer\":\"编辑后的参考答案\","
				+ "\"errorReason\":\"编辑后的错误原因\",\"improvementPlan\":\"编辑后的改进方案\"}}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "1"), String.class).getBody();
		assertThat(JsonProbe.str(accepted, "status")).isEqualTo("ACCEPTED");

		String afterAccept = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.str(afterAccept, "questions.0.content")).isEqualTo("缓存一致性如何处理？");
		assertThat(JsonProbe.str(afterAccept, "questions.0.type")).isEqualTo("自定义类型");
		assertThat(JsonProbe.str(afterAccept, "questions.0.myAnswer")).isEqualTo("我会先更新数据库，再删除缓存。");
		assertThat(JsonProbe.str(afterAccept, "questions.0.answerStatus")).isEqualTo("FULLY_ANSWERED");
		assertThat(JsonProbe.str(afterAccept, "questions.0.referenceAnswer")).isEqualTo("编辑后的参考答案");
		assertThat(JsonProbe.str(afterAccept, "questions.0.errorReason")).isEqualTo("编辑后的错误原因");
		assertThat(JsonProbe.str(afterAccept, "questions.0.improvementPlan")).isEqualTo("编辑后的改进方案");
		assertThat(JsonProbe.intVal(afterAccept, "questions.0.difficulty")).isEqualTo(4);
		assertThat(JsonProbe.str(afterAccept, "questions.0.knowledgePoints.0.id")).isEqualTo(knowledgePointId);
		assertThat(JsonProbe.intVal(afterAccept, "questions.0.version")).isEqualTo(2);

		String secondJob = createAnswerQualityAnalysis(questionId);
		String secondFinished = waitForTerminal(JsonProbe.str(secondJob, "id"));
		String secondItemId = JsonProbe.str(secondFinished, "items.0.id");
		ResponseEntity<String> stale = restTemplate.exchange(url("/ai-job-items/" + secondItemId + "/accept"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "1"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		String rejected = restTemplate.exchange(url("/ai-job-items/" + secondItemId + "/reject"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(rejected, "status")).isEqualTo("REJECTED");
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class)
			.getBody(), "questions.0.referenceAnswer")).isEqualTo("编辑后的参考答案");
	}

	@Test
	void P1_taskSuggestionRequiresAcceptanceAndLinksExistingSources() throws Exception {
		String baseUrl = "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1";
		createActiveProvider(baseUrl);
		String interviewId = createCompletedInterview();
		String review = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(review, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"AI 任务建议知识点\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		String question = restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存一致性如何处理？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String questionId = JsonProbe.str(question, "id");

		String before = restTemplate.getForEntity(url("/tasks"), String.class).getBody();
		assertThat(JsonProbe.intVal(before, "total")).isZero();
		String aiJob = createTaskSuggestion(questionId);
		assertThat(JsonProbe.str(aiJob, "jobType")).isEqualTo("TASK_SUGGESTION");
		assertThat(JsonProbe.str(aiJob, "promptVersion")).isEqualTo("TASK_SUGGESTION_V1");
		String finished = waitForTerminal(JsonProbe.str(aiJob, "id"));
		assertThat(JsonProbe.str(finished, "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.str(finished, "items.0.status")).isEqualTo("PROPOSED");
		assertThat(JsonProbe.str(finished, "items.0.payload.type")).isEqualTo("LEARNING_TASK");
		assertThat(JsonProbe.str(finished, "items.0.payload.priority")).isEqualTo("HIGH");
		assertThat(JsonProbe.intVal(restTemplate.getForEntity(url("/tasks"), String.class).getBody(), "total")).isZero();

		String itemId = JsonProbe.str(finished, "items.0.id");
		String accepted = restTemplate.exchange(url("/ai-job-items/" + itemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"payload\":{\"type\":\"LEARNING_TASK\",\"rawText\":\"编辑后的建议\",\"taskTitle\":\"编辑后的学习任务\",\"priority\":\"URGENT\",\"estimatedMinutes\":30,\"learningGoal\":\"编辑后的目标\",\"acceptanceCriteria\":\"编辑后的验收标准\",\"verificationMethod\":\"编辑后的验证方式\"}}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.str(accepted, "status")).isEqualTo("ACCEPTED");
		String taskId = JsonProbe.str(accepted, "taskId");
		assertThat(taskId).isNotEqualTo("null");
		String task = restTemplate.getForEntity(url("/tasks"), String.class).getBody();
		assertThat(JsonProbe.str(task, "items.0.title")).isEqualTo("编辑后的学习任务");
		assertThat(JsonProbe.str(task, "items.0.status")).isEqualTo("TODO");
		assertThat(JsonProbe.str(task, "items.0.knowledgePoints.0.id")).isEqualTo(knowledgePointId);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_source WHERE task_id=? AND source_type='QUESTION' AND source_id=?",
			Integer.class, taskId, questionId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_source WHERE task_id=? AND source_type='KNOWLEDGE_POINT' AND source_id=?",
			Integer.class, taskId, knowledgePointId)).isEqualTo(1);

		String second = createTaskSuggestion(questionId);
		String secondFinished = waitForTerminal(JsonProbe.str(second, "id"));
		String secondItemId = JsonProbe.str(secondFinished, "items.0.id");
		restTemplate.exchange(url("/interview-questions/" + questionId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"content\":\"缓存一致性如何处理？\",\"answerStatus\":\"PARTIALLY_ANSWERED\",\"type\":\"人工补充\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class);
		ResponseEntity<String> stale = restTemplate.exchange(url("/ai-job-items/" + secondItemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(JsonProbe.intVal(restTemplate.getForEntity(url("/tasks"), String.class).getBody(), "total")).isEqualTo(1);
	}

	@Test
	void P1_aiJobFailureRetryAndCancel() throws Exception {
		// 指向不可达端口 → FAILED 带原因
		createActiveProvider("http://127.0.0.1:1/v1");
		String jobId = createJob();
		String aiJob = createExtraction(jobId);
		String failed = waitForTerminal(JsonProbe.str(aiJob, "id"));
		assertThat(JsonProbe.str(failed, "status")).isEqualTo("FAILED");
		assertThat(JsonProbe.str(failed, "failureReason")).isNotEqualTo("null");
		String aiJobId = JsonProbe.str(aiJob, "id");

		// 重试：FAILED → QUEUED（attempt_count 递增）→ 仍不可达 → FAILED
		String retried = restTemplate.exchange(url("/ai-jobs/" + aiJobId + "/retry"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class)
			.getBody();
		assertThat(JsonProbe.str(retried, "status")).isEqualTo("QUEUED");
		assertThat(JsonProbe.intVal(retried, "attemptCount")).isEqualTo(1);
		waitForTerminal(aiJobId);

		// 取消任务（QUEUED/RUNNING → CANCELED；若执行器已置终态则接受 FAILED）
		String cancelJob = createExtraction(jobId);
		String cancelJobId = JsonProbe.str(cancelJob, "id");
		restTemplate.exchange(url("/ai-jobs/" + cancelJobId + "/cancel"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String canceled = restTemplate.getForEntity(url("/ai-jobs/" + cancelJobId), String.class).getBody();
		String status = JsonProbe.str(canceled, "status");
		assertThat(status).isIn("CANCELED", "FAILED");

		// 对终态任务取消 → 422
		ResponseEntity<String> illegal = restTemplate.exchange(url("/ai-jobs/" + aiJobId + "/cancel"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		// 测试端点：不可达供应商返回 ok=false 与原因
		String list = restTemplate.getForEntity(url("/ai-providers"), String.class).getBody();
		String providerId = JsonProbe.str(list, "0.id");
		String test = restTemplate.exchange(url("/ai-providers/" + providerId + "/test"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(test, "ok")).isEqualTo("false");
		assertThat(JsonProbe.str(test, "message")).isNotEqualTo("null");
	}
}
