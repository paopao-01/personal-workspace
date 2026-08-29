package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectEvidenceIntegrationTest extends AbstractIntegrationTest {

	private static final String URL_OR_PATH = "https://github.com/user/cache-refactor";

	@Test
	void P10_createEvidenceAndProject_roundTripAndAssociationSync() {
		String skillId = seedSkill();
		String evidence = createEvidence("缓存改造架构图", skillId, null);
		String evidenceId = JsonProbe.str(evidence, "id");
		assertThat(JsonProbe.str(evidence, "urlOrPath")).isEqualTo(URL_OR_PATH);
		assertThat(JsonProbe.lng(evidence, "version")).isEqualTo(0);
		assertThat(JsonProbe.str(evidence, "skillIds.0")).isEqualTo(skillId);

		String project = createProject("缓存服务改造", "[\"" + evidenceId + "\"]", null);
		String projectId = JsonProbe.str(project, "id");
		assertThat(JsonProbe.lng(project, "version")).isEqualTo(0);
		assertThat(JsonProbe.arraySize(project, "evidenceRefs")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(project, "evidenceRefs", 0, "id")).isEqualTo(evidenceId);
		assertThat(JsonProbe.arrStr(project, "evidenceRefs", 0, "type")).isEqualTo("GIT_REPOSITORY");

		String evidence2 = createEvidence("接口文档", null, "result 应体现量化数据");
		String evidenceId2 = JsonProbe.str(evidence2, "id");
		String updated = restTemplate.exchange(url("/projects/" + projectId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders(projectBody("缓存服务改造项目", "[\"" + evidenceId + "\",\"" + evidenceId2 + "\"]"),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.str(updated, "title")).isEqualTo("缓存服务改造项目");
		assertThat(JsonProbe.str(updated, "result")).isEqualTo("QPS 从 1200 提升到 3500");
		assertThat(JsonProbe.lng(updated, "version")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(updated, "evidenceRefs")).isEqualTo(2);

		String evidenceUpdated = restTemplate.exchange(url("/evidence/" + evidenceId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("""
				{
				  "type":"GIT_REPOSITORY",
				  "title":"缓存改造代码仓库",
				  "urlOrPath":"https://github.com/user/cache-refactor-v2"
				}
				""", "Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.str(evidenceUpdated, "title")).isEqualTo("缓存改造代码仓库");
		assertThat(JsonProbe.str(evidenceUpdated, "urlOrPath")).isEqualTo("https://github.com/user/cache-refactor-v2");
		assertThat(JsonProbe.lng(evidenceUpdated, "version")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(evidenceUpdated, "skillIds")).isEqualTo(0);

		String projectList = restTemplate.getForEntity(url("/projects"), String.class).getBody();
		assertThat(JsonProbe.arraySize(projectList, "")).isEqualTo(1);
		String evidenceList = restTemplate.getForEntity(url("/evidence"), String.class).getBody();
		assertThat(JsonProbe.arraySize(evidenceList, "")).isEqualTo(2);

		Integer refCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM project_evidence WHERE project_id=?", Integer.class, projectId);
		assertThat(refCount).isEqualTo(2);
		Integer version = jdbc.queryForObject("SELECT version FROM project WHERE id=?", Integer.class, projectId);
		assertThat(version).isEqualTo(1);
	}

	@Test
	void P10_projectCrud_rejectsStaleVersionMissingHeaderAndInvalidRefsWithoutSideEffects() {
		String evidence = createEvidence("压测报告", null, null);
		String evidenceId = JsonProbe.str(evidence, "id");
		String project = createProject("订单链路治理", "[\"" + evidenceId + "\"]", null);
		String projectId = JsonProbe.str(project, "id");

		ResponseEntity<String> stale = restTemplate.exchange(url("/projects/" + projectId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders(projectBody("过期版本更新", "[]"),
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "5"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(stale.getBody()).contains("VERSION_CONFLICT");

		ResponseEntity<String> missingVersion = restTemplate.exchange(url("/projects/" + projectId), HttpMethod.PUT,
			TestFixtures.httpWithHeaders(projectBody("缺版本更新", "[]"), "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		String unknownId = "99999999-9999-9999-9999-999999999999";
		ResponseEntity<String> unknownEvidenceRef = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(projectBody("引用不存在证据", "[\"" + unknownId + "\"]"),
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(unknownEvidenceRef.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(unknownEvidenceRef.getBody()).contains("BUSINESS_RULE_ERROR");
		Integer projectCount = jdbc.queryForObject("SELECT COUNT(*) FROM project", Integer.class);
		assertThat(projectCount).isEqualTo(1);
		Integer refCount = jdbc.queryForObject(
			"SELECT COUNT(*) FROM project_evidence WHERE project_id=?", Integer.class, projectId);
		assertThat(refCount).isEqualTo(1);

		ResponseEntity<String> unknownSkillRef = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{
				  "type":"ARTICLE",
				  "title":"技术文章",
				  "skillIds":["%s"]
				}
				""".formatted(unknownId), "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(unknownSkillRef.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		Integer evidenceCount = jdbc.queryForObject("SELECT COUNT(*) FROM evidence", Integer.class);
		assertThat(evidenceCount).isEqualTo(1);

		ResponseEntity<String> missingTitle = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders("""
				{"scenario":"场景","approach":"方案","problemSolved":"问题"}
				""", "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(missingTitle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(missingTitle.getBody()).contains("VALIDATION_ERROR");
	}

	@Test
	void P10_projectCreate_isIdempotentOnReplay() {
		String body = projectBody("幂等项目", "[]");
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> replay = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonProbe.str(replay.getBody(), "id")).isEqualTo(JsonProbe.str(first.getBody(), "id"));

		Integer projectCount = jdbc.queryForObject("SELECT COUNT(*) FROM project", Integer.class);
		assertThat(projectCount).isEqualTo(1);
	}

	private String createEvidence(String title, String skillId, String result) {
		String skillIds = skillId == null ? "" : "\"skillIds\":[\"" + skillId + "\"],";
		String resultField = result == null ? "" : "\"result\":\"" + result + "\",";
		String body = """
			{
			  "type":"GIT_REPOSITORY",
			  "title":"%s",
			  "whereUsed":"订单缓存模块",
			  %s%s
			  "urlOrPath":"%s"
			}
			""".formatted(title, skillIds, resultField, URL_OR_PATH);
		ResponseEntity<String> response = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private String createProject(String title, String evidenceIdsJson, String result) {
		ResponseEntity<String> response = restTemplate.exchange(url("/projects"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(projectBody(title, evidenceIdsJson),
				"Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private String projectBody(String title, String evidenceIdsJson) {
		return """
			{
			  "title":"%s",
			  "scenario":"高峰期缓存与数据库双写不一致，订单读取命中旧值。",
			  "approach":"引入 Cache Aside 与延迟双删，补充监控告警。",
			  "problemSolved":"将缓存不一致窗口从分钟级降到秒级。",
			  "result":"QPS 从 1200 提升到 3500",
			  "evidenceIds":%s
			}
			""".formatted(title, evidenceIdsJson);
	}

	private String seedSkill() {
		String now = "2026-08-29T00:00:00Z";
		String skillId = "20000000-0000-0000-0000-000000000001";
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
			skillId, "Redis", "redis-test", "Redis", 1, now, now);
		return skillId;
	}
}
