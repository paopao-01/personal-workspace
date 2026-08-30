package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class TrashIntegrationTest extends AbstractIntegrationTest {

	@Test
	void p0_interviewAndApplicationDelete_enterTrashAndCanBeRestored() {
		String interviewId = completedInterview();
		String interview = restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody();
		String applicationId = JsonProbe.str(interview, "applicationId");
		ResponseEntity<String> deletedInterview = restTemplate.exchange(url("/interviews/" + interviewId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "If-Match-Version", String.valueOf(JsonProbe.lng(interview, "version"))), String.class);
		assertThat(deletedInterview.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String trash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceType")).isEqualTo("INTERVIEW");
		String interviewTrashId = JsonProbe.arrStr(trash, "", 0, "id");
		restTemplate.exchange(url("/trash/" + interviewTrashId + "/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String restoredInterview = restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody();
		assertThat(restoredInterview).isNotNull();
		// 投递删除要求先移除关联面试；恢复后再次删除以验证该约束及完整生命周期。
		restTemplate.exchange(url("/interviews/" + interviewId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "If-Match-Version", String.valueOf(JsonProbe.lng(restoredInterview, "version"))), String.class);

		String application = restTemplate.getForEntity(url("/applications/" + applicationId), String.class).getBody();
		ResponseEntity<String> deletedApplication = restTemplate.exchange(url("/applications/" + applicationId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "If-Match-Version", String.valueOf(JsonProbe.lng(application, "version"))), String.class);
		assertThat(deletedApplication.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String applicationTrash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		assertThat(JsonProbe.arrStr(applicationTrash, "", 0, "resourceType")).isEqualTo("APPLICATION");
		restTemplate.exchange(url("/trash/" + JsonProbe.arrStr(applicationTrash, "", 0, "id") + "/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(restTemplate.getForEntity(url("/applications/" + applicationId), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void AT23_deleteReferencedEvidence_showsTrashedRefsAndRestoreKeepsId() {
		String evidence = createEvidence("被引用证据");
		String evidenceId = JsonProbe.str(evidence, "id");
		String project = createProject("关联项目", "[\"" + evidenceId + "\"]");
		String projectId = JsonProbe.str(project, "id");

		ResponseEntity<String> deleted = restTemplate.exchange(url("/evidence/" + evidenceId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"),
			String.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		String trash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		assertThat(JsonProbe.arraySize(trash, "")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceType")).isEqualTo("EVIDENCE");
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceId")).isEqualTo(evidenceId);
		assertThat(JsonProbe.arrStr(trash, "", 0, "displayName")).isEqualTo("被引用证据");
		assertThat(JsonProbe.arrStr(trash, "", 0, "impactSummary.0")).isEqualTo("1 个项目案例引用");
		String trashId = JsonProbe.arrStr(trash, "", 0, "id");
		assertThat(JsonProbe.arrStr(trash, "", 0, "expiresAt")).isNotNull();

		String projectList = restTemplate.getForEntity(url("/projects"), String.class).getBody();
		assertThat(JsonProbe.str(projectList, "0.evidenceRefs.0.trashed")).isEqualTo("true");

		ResponseEntity<String> restored = restTemplate.exchange(url("/trash/" + trashId + "/restore"),
			HttpMethod.POST, TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);

		String projectAfter = restTemplate.getForEntity(url("/projects"), String.class).getBody();
		assertThat(JsonProbe.str(projectAfter, "0.id")).isEqualTo(projectId);
		assertThat(JsonProbe.str(projectAfter, "0.evidenceRefs.0.id")).isEqualTo(evidenceId);
		assertThat(JsonProbe.str(projectAfter, "0.evidenceRefs.0.trashed")).isEqualTo("false");

		// 再次删除后尝试永久删除：证据仍被项目引用，必须拒绝
		restTemplate.exchange(url("/evidence/" + evidenceId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "1"),
			String.class);
		String trashAgain = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		String trashIdAgain = JsonProbe.arrStr(trashAgain, "", 0, "id");
		ResponseEntity<String> purgeBlocked = restTemplate.exchange(url("/trash/" + trashIdAgain + "/permanent"),
			HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"X-Confirm-Permanent-Delete", "true"),
			String.class);
		assertThat(purgeBlocked.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(purgeBlocked.getBody()).contains("BUSINESS_RULE_ERROR");

		// 未被引用的证据可以永久删除
		String standalone = createEvidence("独立证据");
		String standaloneId = JsonProbe.str(standalone, "id");
		restTemplate.exchange(url("/evidence/" + standaloneId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"),
			String.class);
		String trashForStandalone = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		String standaloneTrashId = null;
		for (int i = 0; i < JsonProbe.arraySize(trashForStandalone, ""); i++) {
			if (standaloneId.equals(JsonProbe.arrStr(trashForStandalone, "", i, "resourceId"))) {
				standaloneTrashId = JsonProbe.arrStr(trashForStandalone, "", i, "id");
			}
		}
		ResponseEntity<String> purged = restTemplate.exchange(url("/trash/" + standaloneTrashId + "/permanent"),
			HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"X-Confirm-Permanent-Delete", "true"),
			String.class);
		assertThat(purged.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		Integer evidenceCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM evidence WHERE id=?", Integer.class, standaloneId);
		assertThat(evidenceCount).isEqualTo(0);
	}

	@Test
	void AT23_projectDeleteRestorePurge_keepsIdAndCleansJoins() {
		String evidence = createEvidence("项目证据");
		String evidenceId = JsonProbe.str(evidence, "id");
		String project = createProject("待删除项目", "[\"" + evidenceId + "\"]");
		String projectId = JsonProbe.str(project, "id");

		ResponseEntity<String> missingVersion = restTemplate.exchange(url("/projects/" + projectId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		restTemplate.exchange(url("/projects/" + projectId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"),
			String.class);
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/projects"), String.class).getBody(), ""))
			.isEqualTo(0);
		String trash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceType")).isEqualTo("PROJECT_CASE");
		assertThat(JsonProbe.arrStr(trash, "", 0, "impactSummary.0")).isEqualTo("1 条证据引用");
		String trashId = JsonProbe.arrStr(trash, "", 0, "id");

		restTemplate.exchange(url("/trash/" + trashId + "/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String restoredList = restTemplate.getForEntity(url("/projects"), String.class).getBody();
		assertThat(JsonProbe.str(restoredList, "0.id")).isEqualTo(projectId);
		assertThat(JsonProbe.str(restoredList, "0.evidenceRefs.0.id")).isEqualTo(evidenceId);

		// 恢复后再次删除，先验证缺确认头返回 400，再带确认头永久删除
		restTemplate.exchange(url("/projects/" + projectId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "1"),
			String.class);
		String trashAgain = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		String trashIdAgain = JsonProbe.arrStr(trashAgain, "", 0, "id");
		ResponseEntity<String> noConfirm = restTemplate.exchange(url("/trash/" + trashIdAgain + "/permanent"),
			HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(noConfirm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		restTemplate.exchange(url("/trash/" + trashIdAgain + "/permanent"), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"X-Confirm-Permanent-Delete", "true"), String.class);
		Integer projectCount = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=?", Integer.class, projectId);
		assertThat(projectCount).isEqualTo(0);
		Integer joinCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM project_evidence WHERE project_id=?", Integer.class, projectId);
		assertThat(joinCount).isEqualTo(0);
		Integer evidenceCount = jdbc.queryForObject("SELECT COUNT(*) FROM evidence WHERE id=?", Integer.class, evidenceId);
		assertThat(evidenceCount).isEqualTo(1);
	}

	@Test
	void AT23_questionDelete_entersTrashAndPurgeKeepsTask() {
		String interviewId = completedInterview();
		String questionId = weakQuestion(interviewId);

		ResponseEntity<String> missingVersion = restTemplate.exchange(
			url("/interview-questions/" + questionId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> deleted = restTemplate.exchange(url("/interview-questions/" + questionId),
			HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		String reviewAfterDelete = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.arraySize(reviewAfterDelete, "questions")).isEqualTo(0);

		String trash = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceType")).isEqualTo("INTERVIEW_QUESTION");
		assertThat(JsonProbe.arrStr(trash, "", 0, "resourceId")).isEqualTo(questionId);
		assertThat(JsonProbe.arrStr(trash, "", 0, "impactSummary.0")).isEqualTo("1 个知识点关联");
		String trashId = JsonProbe.arrStr(trash, "", 0, "id");

		restTemplate.exchange(url("/trash/" + trashId + "/restore"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
		String reviewAfterRestore = restTemplate.getForEntity(url("/interviews/" + interviewId + "/review"), String.class).getBody();
		assertThat(JsonProbe.arraySize(reviewAfterRestore, "questions")).isEqualTo(1);
		assertThat(JsonProbe.str(reviewAfterRestore, "questions.0.id")).isEqualTo(questionId);

		// 从恢复后的问题创建学习任务，再删除并永久删除问题：任务保留、任务来源清理
		String taskId = JsonProbe.str(restTemplate.exchange(
			url("/interview-questions/" + questionId + "/create-task"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{
				  "mode":"CREATE_NEW",
				  "title":"AT23 补齐弱问题",
				  "acceptanceCriteria":"能讲清知识点。",
				  "verificationMethod":"口述演练"
				}
				""", "Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");

		restTemplate.exchange(url("/interview-questions/" + questionId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "1"), String.class);
		String trashAgain = restTemplate.getForEntity(url("/trash"), String.class).getBody();
		String trashIdAgain = JsonProbe.arrStr(trashAgain, "", 0, "id");
		restTemplate.exchange(url("/trash/" + trashIdAgain + "/permanent"), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"X-Confirm-Permanent-Delete", "true"), String.class);

		Integer questionCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM interview_question WHERE id=?", Integer.class, questionId);
		assertThat(questionCount).isEqualTo(0);
		Integer knowledgeCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM question_knowledge WHERE question_id=?", Integer.class, questionId);
		assertThat(knowledgeCount).isEqualTo(0);
		Integer sourceCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM task_source WHERE source_type='QUESTION' AND source_id=?", Integer.class, questionId);
		assertThat(sourceCount).isEqualTo(0);
		Integer taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM learning_task WHERE id=?", Integer.class, taskId);
		assertThat(taskCount).isEqualTo(1);
	}

	private String weakQuestion(String interviewId) {
		String reviewBody = restTemplate.exchange(url("/interviews/" + interviewId + "/review"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"interviewResult\":\"FAILED\",\"noQuestionsRecorded\":false}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody();
		String reviewId = JsonProbe.str(reviewBody, "id");
		String knowledgePointId = JsonProbe.str(restTemplate.exchange(url("/knowledge-points"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"name\":\"AT23 知识点\",\"category\":\"AT23\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		return JsonProbe.str(restTemplate.exchange(url("/reviews/" + reviewId + "/questions"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"content\":\"AT23 弱问题\",\"answerStatus\":\"UNANSWERED\",\"knowledgePointIds\":[\"" + knowledgePointId + "\"]}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
	}

	private String completedInterview() {
		String jobId = JsonProbe.str(restTemplate.postForEntity(url("/jobs"),
			TestFixtures.httpJson(TestFixtures.createJobBody("删除科技", "AT23 岗位")), String.class).getBody(), "id");
		String applicationId = JsonProbe.str(restTemplate.exchange(url("/applications"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.createApplicationBody(jobId, "2026-08-20", "AT23 渠道", null, null, null),
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		transitionApplication(applicationId, "APPLIED", "0");
		transitionApplication(applicationId, "RESUME_PASSED", "1");
		String interviewId = JsonProbe.str(restTemplate.exchange(url("/interviews"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"applicationId\":\"" + applicationId + "\",\"roundName\":\"AT23 一面\",\"startsAt\":\"2026-09-10T10:00:00Z\",\"eventTimeZone\":\"Asia/Shanghai\"}",
				"Idempotency-Key", TestFixtures.newKey()), String.class).getBody(), "id");
		long version = JsonProbe.lng(restTemplate.getForEntity(url("/interviews/" + interviewId), String.class).getBody(), "version");
		restTemplate.exchange(url("/interviews/" + interviewId + "/complete"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("{\"result\":\"FAILED\"}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
		return interviewId;
	}

	private void transitionApplication(String applicationId, String targetStatus, String version) {
		restTemplate.exchange(url("/applications/" + applicationId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(TestFixtures.transitionBody(targetStatus, null, null),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", version), String.class);
	}

	private String createEvidence(String title) {
		String body = """
			{
			  "type":"GIT_REPOSITORY",
			  "title":"%s",
			  "urlOrPath":"https://github.com/user/at23"
			}
			""".formatted(title);
		ResponseEntity<String> response = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private String createProject(String title, String evidenceIdsJson) {
		String body = """
			{
			  "title":"%s",
			  "scenario":"AT23 场景",
			  "approach":"AT23 方案",
			  "problemSolved":"AT23 问题",
			  "evidenceIds":%s
			}
			""".formatted(title, evidenceIdsJson);
		ResponseEntity<String> response = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}
}
