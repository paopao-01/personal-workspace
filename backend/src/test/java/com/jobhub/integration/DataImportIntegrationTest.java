package com.jobhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 数据导入与完整恢复（PRD 9.5）：冲突预检、恢复预览、恢复结果报告。
 * 恢复语义：只插入缺失行；重复行、冲突行、缺父级行跳过且不覆盖现状。
 */
class DataImportIntegrationTest extends AbstractIntegrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void P1_importRestoresRemappedExportPackageEndToEnd() throws Exception {
		String interviewId = completedInterview();
		String reviewBody = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"导入测试知识点\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"导入测试问题\",\"answerStatus\":\"UNANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class);

		String exportId = JsonProbe.str(restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"JSON\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		String packageJson = restTemplate.getForEntity(url("/data-exports/" + exportId + "/download"), String.class).getBody();
		assertThat(packageJson).contains("job_posting");

		// 整体重映射全部 UUID（保持引用关系一致），并改写业务唯一键 normalized_name，使数据包相对当前库全部为“缺失行”
		String remapped = remapAllUuids(packageJson);
		ObjectNode remappedNode = (ObjectNode) JSON.readTree(remapped);
		for (String businessUniqueTable : new String[] {"knowledge_point", "skill"}) {
			JsonNode rows = remappedNode.path("tables").path(businessUniqueTable);
			if (rows.isArray()) {
				for (JsonNode rowNode : rows) {
					if (rowNode.has("normalized_name")) {
						((ObjectNode) rowNode).put("normalized_name",
							rowNode.path("normalized_name").asText() + "-导入副本-" + rowNode.path("id").asText().substring(0, 8));
					}
				}
			}
		}
		remapped = JSON.writeValueAsString(remappedNode);
		int totalRows = countRows(remappedNode);
		String newJobId = remappedNode.path("tables").path("job_posting").get(0).path("id").asText();

		ResponseEntity<String> validated = restTemplate.exchange(url("/data-imports/validate"), HttpMethod.POST,
			TestFixtures.httpJson(remapped), String.class);
		assertThat(validated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(validated.getBody(), "valid")).isEqualTo("true");
		assertThat(JsonProbe.lng(validated.getBody(), "totalRows")).isEqualTo((long) totalRows);
		assertThat(JsonProbe.lng(validated.getBody(), "insertableRows")).isEqualTo((long) totalRows);

		ResponseEntity<String> restored = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(remapped, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(restored.getBody(), "inserted")).isEqualTo((long) totalRows);
		assertThat(JsonProbe.lng(restored.getBody(), "failed")).isEqualTo(0);
		assertThat(JsonProbe.lng(restored.getBody(), "skippedConflict")).isEqualTo(0);

		// 导入后的岗位可通过业务接口读取（含关联链路）
		ResponseEntity<String> importedJob = restTemplate.getForEntity(url("/jobs/" + newJobId), String.class);
		assertThat(importedJob.getStatusCode()).isEqualTo(HttpStatus.OK);
		String originalTitle = JsonProbe.str(JSON.readTree(packageJson).path("tables").path("job_posting").get(0).toString(), "title");
		assertThat(JsonProbe.str(importedJob.getBody(), "title")).isEqualTo(originalTitle);

		// 幂等：重复恢复同一数据包 → 全部重复跳过，不再插入
		ResponseEntity<String> restoredAgain = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(remapped, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(JsonProbe.lng(restoredAgain.getBody(), "inserted")).isEqualTo(0);
		assertThat(JsonProbe.lng(restoredAgain.getBody(), "skippedIdentical")).isEqualTo((long) totalRows);
	}

	@Test
	void P1_importSkipsConflictsAndKeepsExistingFacts() throws Exception {
		String interviewId = completedInterview();
		String applicationId = JsonProbe.str(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "applicationId");
		String originalJobId = JsonProbe.str(restTemplate.getForEntity(url("/applications/" + applicationId), String.class).getBody(), "jobId");
		String originalJob = restTemplate.getForEntity(url("/jobs/" + originalJobId), String.class).getBody();
		String originalTitle = JsonProbe.str(originalJob, "title");

		String exportId = JsonProbe.str(restTemplate.exchange(url("/data-exports"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"format\":\"JSON\"}", "Idempotency-Key", TestFixtures.newKey()),
			String.class).getBody(), "id");
		String packageJson = restTemplate.getForEntity(url("/data-exports/" + exportId + "/download"), String.class).getBody();

		// 修改数据包中该岗位的标题 → 同 ID 内容不同 → 冲突
		ObjectNode root = (ObjectNode) JSON.readTree(packageJson);
		ObjectNode jobRow = (ObjectNode) root.path("tables").path("job_posting").get(0);
		assertThat(jobRow.path("id").asText()).isEqualTo(originalJobId);
		jobRow.put("title", originalTitle + "（数据包已修改）");
		String modified = JSON.writeValueAsString(root);

		ResponseEntity<String> validated = restTemplate.exchange(url("/data-imports/validate"), HttpMethod.POST,
			TestFixtures.httpJson(modified), String.class);
		assertThat(JsonProbe.lng(validated.getBody(), "insertableRows")).isEqualTo(0);
		assertThat(JsonProbe.arraySize(validated.getBody(), "issues")).isGreaterThanOrEqualTo(1);
		boolean hasConflictIssue = false;
		for (int i = 0; i < JsonProbe.arraySize(validated.getBody(), "issues"); i++) {
			if ("CONFLICT".equals(JsonProbe.arrStr(validated.getBody(), "issues", i, "type"))
				&& originalJobId.equals(JsonProbe.arrStr(validated.getBody(), "issues", i, "rowId"))) {
				hasConflictIssue = true;
			}
		}
		assertThat(hasConflictIssue).isTrue();

		ResponseEntity<String> restored = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(modified, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(restored.getBody(), "inserted")).isEqualTo(0);
		assertThat(JsonProbe.lng(restored.getBody(), "skippedConflict")).isGreaterThanOrEqualTo(1);
		// 用户事实优先：数据库中的标题不被覆盖
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/jobs/" + originalJobId), String.class).getBody(), "title"))
			.isEqualTo(originalTitle);
	}

	@Test
	void P1_importSkipsMissingParentRowsAndRejectsInvalidPackages() throws Exception {
		String jobId = "a1a1a1a1-1111-4111-8111-a1a1a1a1a1a1";
		String orphanRequirementId = "b2b2b2b2-2222-4222-8222-b2b2b2b2b2b2";
		String notificationId = "c3c3c3c3-3333-4333-8333-c3c3c3c3c3c3";
		String pkg = """
			{"format":"JSON","exportedAt":"2026-08-29T00:00:00Z","tables":{
			  "job_posting":[{"id":"%s","company_name":"缺父级测试公司","title":"缺父级测试岗位","jd_raw_text":"要求 Java。","status":"ACTIVE","created_at":"2026-08-29T00:00:00Z","updated_at":"2026-08-29T00:00:00Z","version":0}],
			  "job_requirement":[{"id":"%s","job_id":"d4d4d4d4-4444-4444-8444-d4d4d4d4d4d4","raw_text":"不存在的岗位要求","requirement_type":"MUST","confirmation_status":"CONFIRMED","source_type":"RULE","created_at":"2026-08-29T00:00:00Z","updated_at":"2026-08-29T00:00:00Z","version":0}],
			  "notification":[{"id":"%s","reminder_id":null,"title":"无关联提醒通知","content":"内容","read_at":null,"created_at":"2026-08-29T00:00:00Z"}],
			  "user_setting":[{"time_zone":"Asia/Shanghai"}]
			}}
			""".formatted(jobId, orphanRequirementId, notificationId);

		ResponseEntity<String> validated = restTemplate.exchange(url("/data-imports/validate"), HttpMethod.POST,
			TestFixtures.httpJson(pkg), String.class);
		assertThat(validated.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(validated.getBody(), "insertableRows")).isEqualTo(2);
		assertThat(JsonProbe.str(validated.getBody(), "valid")).isEqualTo("true");
		boolean hasMissingParent = false;
		boolean hasUnknownTable = false;
		for (int i = 0; i < JsonProbe.arraySize(validated.getBody(), "issues"); i++) {
			String type = JsonProbe.arrStr(validated.getBody(), "issues", i, "type");
			if ("MISSING_PARENT".equals(type) && orphanRequirementId.equals(JsonProbe.arrStr(validated.getBody(), "issues", i, "rowId"))) {
				hasMissingParent = true;
			}
			if ("UNKNOWN_TABLE".equals(type) && "user_setting".equals(JsonProbe.arrStr(validated.getBody(), "issues", i, "tableName"))) {
				hasUnknownTable = true;
			}
		}
		assertThat(hasMissingParent).isTrue();
		assertThat(hasUnknownTable).isTrue();

		ResponseEntity<String> restored = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(pkg, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(restored.getBody(), "inserted")).isEqualTo(2);
		assertThat(JsonProbe.lng(restored.getBody(), "skippedMissingParent")).isEqualTo(1);
		// 缺父级行未插入：目标岗位不存在其要求
		assertThat(restTemplate.getForEntity(url("/jobs/d4d4d4d4-4444-4444-8444-d4d4d4d4d4d4"), String.class).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(JsonProbe.str(restTemplate.getForEntity(url("/jobs/" + jobId), String.class).getBody(), "companyName"))
			.isEqualTo("缺父级测试公司");

		// 非法数据包：format 不符 / 缺 tables → 422
		for (String invalid : new String[] {"{\"format\":\"CSV\",\"tables\":{}}", "{\"format\":\"JSON\"}"}) {
			ResponseEntity<String> invalidValidate = restTemplate.exchange(url("/data-imports/validate"), HttpMethod.POST,
				TestFixtures.httpJson(invalid), String.class);
			assertThat(invalidValidate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
			assertThat(JsonProbe.str(invalidValidate.getBody(), "code")).isEqualTo("BUSINESS_RULE_ERROR");
			ResponseEntity<String> invalidRestore = restTemplate.exchange(url("/data-imports/restore"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(invalid, "Idempotency-Key", TestFixtures.newKey()), String.class);
			assertThat(invalidRestore.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	private int countRows(JsonNode root) {
		JsonNode tables = root.path("tables");
		int total = 0;
		for (var field : tables.properties()) {
			if (field.getValue().isArray()) {
				total += field.getValue().size();
			}
		}
		return total;
	}

	/** 把数据包中出现的所有 UUID 一致地重映射为新 UUID，保持引用关系不变且相对当前库全部为缺失行。 */
	private String remapAllUuids(String packageJson) throws Exception {
		java.util.Map<String, String> mapping = new java.util.HashMap<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(packageJson);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String uuid = matcher.group();
			String replacement = mapping.computeIfAbsent(uuid, ignored -> java.util.UUID.randomUUID().toString());
			matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private String completedInterview() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("导入恢复科技", "Java 后端工程师")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "BOSS直聘", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transition(applicationId, "APPLIED", "0");
		transition(applicationId, "RESUME_PASSED", "1");
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"导入测试面试\",\"startsAt\":\"2026-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
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
}
