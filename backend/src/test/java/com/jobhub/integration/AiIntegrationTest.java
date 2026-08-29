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

	private static HttpServer fakeProvider;
	private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();

	@BeforeAll
	static void startFakeProvider() throws Exception {
		fakeProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakeProvider.createContext("/v1/chat/completions", exchange -> {
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String contentJson;
			if (requestBody.contains("sourceType")) {
				// 简历草稿模式：从事实清单提示词中回显第一个 sourceId（保证溯源校验通过）
				String flat = requestBody.replace("\\", "");
				java.util.regex.Matcher matcher = java.util.regex.Pattern
					.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
					.matcher(flat.substring(flat.indexOf("sourceId")));
				String sourceId = matcher.find() ? matcher.group() : "unknown";
				String arrayJson = CONTENT_JSON.writeValueAsString(java.util.List.of(java.util.Map.of(
					"sourceType", "PROJECT", "sourceId", sourceId,
					"sourceTitle", "集成测试项目", "suggestedText", "面向目标岗位重写的项目表达（集成测试）")));
				contentJson = CONTENT_JSON.writeValueAsString(arrayJson);
			} else {
				// JD 提取模式：把候选 JSON 数组序列化为字符串字面量嵌入响应
				contentJson = CONTENT_JSON.writeValueAsString(CANDIDATES_JSON);
			}
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

	@Test
	void P1_resumeDraftUsesConfirmedFactsAndTypeAwareAccept() throws Exception {
		String baseUrl = "http://127.0.0.1:" + fakeProvider.getAddress().getPort() + "/v1";
		createActiveProvider(baseUrl);
		String jobId = createJob();

		// 确认一条岗位要求（规则提取 → 确认），作为定制目标
		restTemplate.exchange(url("/jobs/" + jobId + "/requirements/extract"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String requirements = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		int requirementCount = JsonProbe.arraySize(requirements, "");
		assertThat(requirementCount).isGreaterThanOrEqualTo(1);
		String firstRequirementId = JsonProbe.arrStr(requirements, "", 0, "id");
		String confirmedRequirement = restTemplate.exchange(url("/job-requirements/" + firstRequirementId),
			HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"confirmationStatus\":\"CONFIRMED\",\"type\":\"MUST\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version",
				JsonProbe.arrStr(requirements, "", 0, "version")), String.class).getBody();
		assertThat(JsonProbe.str(confirmedRequirement, "confirmationStatus")).isEqualTo("CONFIRMED");

		// 创建项目事实
		String project = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"title\":\"集成测试项目\",\"scenario\":\"高并发订单系统\",\"approach\":\"Spring Boot + Redis 缓存\",\"problemSolved\":\"订单峰值性能瓶颈\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String projectId = JsonProbe.str(project, "id");

		// 创建简历草稿任务
		String aiJob = restTemplate.exchange(url("/ai-jobs"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"jobType\":\"RESUME_DRAFT\",\"objectId\":\"" + jobId + "\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String finished = waitForTerminal(JsonProbe.str(aiJob, "id"));
		System.out.println("[DBG] " + finished);

		assertThat(JsonProbe.str(finished, "status")).isEqualTo("SUCCEEDED");
		assertThat(JsonProbe.str(finished, "items.0.payload.sourceId")).isEqualTo(projectId);
		assertThat(JsonProbe.str(finished, "promptVersion")).isEqualTo("RESUME_DRAFT_V1");

		// 采纳（带编辑）：仅确认建议文本，不创建岗位要求，溯源字段锁定
		String itemId = JsonProbe.str(finished, "items.0.id");
		String accepted = restTemplate.exchange(url("/ai-job-items/" + itemId + "/accept"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"payload\":{\"sourceType\":\"PROJECT\",\"sourceId\":\"tampered-id\",\"suggestedText\":\"编辑后的定制表达\"}}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		assertThat(JsonProbe.str(accepted, "status")).isEqualTo("ACCEPTED");
		assertThat(JsonProbe.str(accepted, "editedPayload.suggestedText")).isEqualTo("编辑后的定制表达");
		assertThat(JsonProbe.str(accepted, "editedPayload.sourceId")).isEqualTo(projectId);
		assertThat(JsonProbe.str(accepted, "requirementId")).isEqualTo("null");

		// AI 采纳未创建岗位要求（确认要求数量不变）
		String requirementsAfter = restTemplate.getForEntity(url("/jobs/" + jobId + "/requirements"), String.class).getBody();
		assertThat(JsonProbe.arraySize(requirementsAfter, "")).isEqualTo(requirementCount);
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

	private String createExtraction(String jobId) {
		return restTemplate.exchange(url("/ai-jobs"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"jobType\":\"JD_EXTRACTION\",\"objectId\":\"" + jobId + "\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
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
