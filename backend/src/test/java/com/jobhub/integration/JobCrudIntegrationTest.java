package com.jobhub.integration;

import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-01/AT-03 的 CRUD 路径集成测试：创建/列表分页过滤/详情404/更新+版本自增/
 * archive+restore 状态机/extract 全 PENDING/gap-list 基线。
 *
 * 非幂等路径：省略 Idempotency-Key 头（控制器未强制），不产生 idempotency_record 副作用。
 */
class JobCrudIntegrationTest extends AbstractIntegrationTest {

	@Test
	void createJob_returns201AndVersion0_persistsRow() {
		ResponseEntity<String> resp = restTemplate.exchange(
				url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody("示例科技", "Java 后端工程师")),
				String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String body = resp.getBody();
		assertThat(JsonProbe.str(body, "id")).isNotBlank();
		assertThat(JsonProbe.lng(body, "version")).isZero();
		assertThat(JsonProbe.str(body, "status")).isEqualTo("ACTIVE");
		assertThat(body).contains("\"decisionStatus\":null");

		// DB 持久化
		Map<String, Object> row = jdbc.queryForMap("SELECT version, status FROM job_posting WHERE deleted_at IS NULL");
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(0L);
		assertThat(row.get("status")).isEqualTo("ACTIVE");
	}

	@Test
	void listJobs_paginatesAndFilters() {
		// 预置 3 个岗位：2 ACTIVE / 1 ARCHIVED，1 个 decisionStatus=TO_APPLY
		String id1 = createJob("云仓科技", "Java 后端");
		String id2 = createJob("星河支付", "Go 工程师");
		String id3 = createJob("天河互联", "Java 高级");
		// id3 决策为 TO_APPLY
		restTemplate.exchange(url("/jobs/" + id3), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateDecisionBody("TO_APPLY", null),
						"If-Match-Version", "0"), String.class);
		// id2 归档
		restTemplate.exchange(url("/jobs/" + id2 + "/archive"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "0"), String.class);

		// 分页
		String page1 = restTemplate.getForObject(url("/jobs?page=1&pageSize=2"), String.class);
		assertThat(JsonProbe.lng(page1, "total")).isEqualTo(3L);
		assertThat(JsonProbe.arraySize(page1, "items")).isEqualTo(2);
		assertThat(JsonProbe.intVal(page1, "page")).isEqualTo(1);
		assertThat(JsonProbe.intVal(page1, "totalPages")).isEqualTo(2);

		// 过滤 jobStatus=ARCHIVED
		String archived = restTemplate.getForObject(url("/jobs?jobStatus=ARCHIVED"), String.class);
		assertThat(JsonProbe.lng(archived, "total")).isEqualTo(1L);
		assertThat(JsonProbe.arrStr(archived, "items", 0, "status")).isEqualTo("ARCHIVED");

		// 过滤 decisionStatus=TO_APPLY
		String toApply = restTemplate.getForObject(url("/jobs?decisionStatus=TO_APPLY"), String.class);
		assertThat(JsonProbe.lng(toApply, "total")).isEqualTo(1L);

		// 关键词搜索公司名
		String query = restTemplate.getForObject(url("/jobs?query=云仓"), String.class);
		assertThat(JsonProbe.lng(query, "total")).isEqualTo(1L);
	}

	@Test
	void getJob_unknownId_returns404NotFound() {
		ResponseEntity<String> resp = restTemplate.getForEntity(url("/jobs/no-such-id"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(JsonProbe.str(resp.getBody(), "code")).isEqualTo("NOT_FOUND");
		assertThat(JsonProbe.str(resp.getBody(), "traceId")).isNotBlank();
	}

	@Test
	void updateJob_incrementsVersionAndPersistsDecision() {
		String id = createJob("示例科技", "Java 后端");

		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + id), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(TestFixtures.updateDecisionBody("TO_APPLY", "想投"),
						"If-Match-Version", "0"), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.lng(resp.getBody(), "version")).isEqualTo(1L);
		assertThat(JsonProbe.str(resp.getBody(), "decisionStatus")).isEqualTo("TO_APPLY");

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT version, decision_status, decision_reason FROM job_posting WHERE id = ?", id);
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
		assertThat(row.get("decision_status")).isEqualTo("TO_APPLY");
		assertThat(row.get("decision_reason")).isEqualTo("想投");
	}

	@Test
	void archiveThenRestore_roundTrip_stateMachine() {
		String id = createJob("示例科技", "Java 后端");

		ResponseEntity<String> archived = restTemplate.exchange(url("/jobs/" + id + "/archive"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "0"), String.class);
		assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(archived.getBody(), "status")).isEqualTo("ARCHIVED");
		assertThat(JsonProbe.lng(archived.getBody(), "version")).isEqualTo(1L);

		ResponseEntity<String> restored = restTemplate.exchange(url("/jobs/" + id + "/restore"), HttpMethod.POST,
				TestFixtures.httpWithHeaders("", "If-Match-Version", "1"), String.class);
		assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(restored.getBody(), "status")).isEqualTo("ACTIVE");
		assertThat(JsonProbe.lng(restored.getBody(), "version")).isEqualTo(2L);

		Map<String, Object> row = jdbc.queryForMap("SELECT status, version FROM job_posting WHERE id = ?", id);
		assertThat(row.get("status")).isEqualTo("ACTIVE");
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(2L);
	}

	@Test
	void extractRequirements_returnsAllPendingCandidates() {
		String id = createJob("示例科技", "Java 后端");

		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs/" + id + "/requirements/extract"),
				HttpMethod.POST, TestFixtures.httpJson(""), String.class);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.intVal(resp.getBody(), "newCount")).isGreaterThanOrEqualTo(3);
		List<String> statuses = JsonProbe.collectArrayField(resp.getBody(), "candidates", "confirmationStatus");
		assertThat(statuses).isNotEmpty();
		assertThat(statuses).allMatch("PENDING"::equals);
		List<String> sources = JsonProbe.collectArrayField(resp.getBody(), "candidates", "source");
		assertThat(sources).allMatch("RULE"::equals);

		Integer dbCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM job_requirement WHERE job_id = ? AND deleted_at IS NULL", Integer.class, id);
		assertThat(dbCount).isGreaterThanOrEqualTo(3);
		Integer pendingCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM job_requirement WHERE job_id = ? AND confirmation_status = 'PENDING'", Integer.class, id);
		assertThat(pendingCount).isEqualTo(dbCount);
	}

	@Test
	void gapList_emptyWhenNoConfirmedRequirements() {
		String id = createJob("示例科技", "Java 后端");
		// extract 产生全 PENDING，无 CONFIRMED
		restTemplate.exchange(url("/jobs/" + id + "/requirements/extract"), HttpMethod.POST,
				TestFixtures.httpJson(""), String.class);

		ResponseEntity<String> resp = restTemplate.getForEntity(url("/jobs/" + id + "/gap-list"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.arraySize(resp.getBody(), "")).isZero();
	}

	// --- 辅助：创建岗位并返回 id（不带幂等键） ---
	private String createJob(String company, String title) {
		ResponseEntity<String> resp = restTemplate.exchange(url("/jobs"), HttpMethod.POST,
				TestFixtures.httpJson(TestFixtures.createJobBody(company, title)), String.class);
		return JsonProbe.str(resp.getBody(), "id");
	}
}
