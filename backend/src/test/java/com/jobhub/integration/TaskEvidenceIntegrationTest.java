package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-65 任务完成证据关联。
 *
 * 覆盖：挂载/列表/卸载/重复幂等/跨任务隔离/不存在 404/不依赖状态/不影响 transition/
 * 不动 learning_task.version·status·verification_*·output_url / 不动 evidence 表 /
 * 软删证据保留关联显示 trashed=true / 详情回显 evidenceRefs。
 *
 * 仿 ProjectEvidenceIntegrationTest 风格，复用 AbstractIntegrationTest / TestFixtures / JsonProbe / jdbc。
 */
class TaskEvidenceIntegrationTest extends AbstractIntegrationTest {

	private static final String UNKNOWN_ID = "99999999-9999-9999-9999-999999999999";

	@Test
	void AT65_attachAndListEvidence_roundTrip() {
		String taskId = createTask("梳理 Redis 缓存一致性");
		String evidenceA = createEvidence("缓存改造架构图");
		String evidenceB = createEvidence("接口文档");

		ResponseEntity<String> attached = attachEvidence(taskId, evidenceA);
		assertThat(attached.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(attached.getBody(), "id")).isEqualTo(evidenceA);
		assertThat(JsonProbe.bool(attached.getBody(), "trashed")).isFalse();
		assertThat(refCount(taskId, evidenceA)).isEqualTo(1);

		attachEvidence(taskId, evidenceB);
		String list = listEvidence(taskId);
		assertThat(JsonProbe.arraySize(list, "")).isEqualTo(2);
		assertThat(JsonProbe.arrStr(list, "", 0, "id")).isEqualTo(evidenceA);
		assertThat(JsonProbe.arrStr(list, "", 1, "id")).isEqualTo(evidenceB);
	}

	@Test
	void AT65_attachEvidence_isIdempotentOnReplay() {
		String taskId = createTask("幂等挂载任务");
		String evidenceId = createEvidence("压测报告");
		String key = TestFixtures.newKey();

		ResponseEntity<String> first = attachEvidenceWithKey(taskId, evidenceId, key);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		String firstId = JsonProbe.str(first.getBody(), "id");

		ResponseEntity<String> replay = attachEvidenceWithKey(taskId, evidenceId, key);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.str(replay.getBody(), "id")).isEqualTo(firstId);
		assertThat(refCount(taskId, evidenceId)).isEqualTo(1);
	}

	@Test
	void AT65_attachEvidence_isCrossTaskIsolated() {
		String evidenceId = createEvidence("共享证据");
		String task1 = createTask("任务一");
		String task2 = createTask("任务二");

		attachEvidence(task1, evidenceId);
		attachEvidence(task2, evidenceId);

		assertThat(JsonProbe.arraySize(listEvidence(task1), "")).isEqualTo(1);
		assertThat(JsonProbe.arraySize(listEvidence(task2), "")).isEqualTo(1);
		assertThat(refCount(task1, evidenceId)).isEqualTo(1);
		assertThat(refCount(task2, evidenceId)).isEqualTo(1);
	}

	@Test
	void AT65_detachEvidence_removesRefAnd404WhenAbsent() {
		String taskId = createTask("卸载任务");
		String evidenceId = createEvidence("待卸载证据");
		attachEvidence(taskId, evidenceId);

		ResponseEntity<String> detached = detachEvidence(taskId, evidenceId);
		assertThat(detached.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(refCount(taskId, evidenceId)).isEqualTo(0);
		assertThat(JsonProbe.arraySize(listEvidence(taskId), "")).isEqualTo(0);

		ResponseEntity<String> again = detachEvidence(taskId, evidenceId);
		assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void AT65_attachEvidence_unknownEvidence_returns404() {
		String taskId = createTask("未知证据任务");
		ResponseEntity<String> response = attachEvidence(taskId, UNKNOWN_ID);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(refCount(taskId, UNKNOWN_ID)).isEqualTo(0);
	}

	@Test
	void AT65_attachEvidence_unknownTask_returns404() {
		String evidenceId = createEvidence("无任务证据");
		ResponseEntity<String> response = attachEvidence(UNKNOWN_ID, evidenceId);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void AT65_attachEvidence_doesNotDependOnTaskStatus() {
		String taskId = createTask("状态无关任务");
		String evidenceId = createEvidence("任意状态可挂载");
		long version = JsonProbe.lng(getTask(taskId), "version");

		transitionTask(taskId, version, "IN_PROGRESS");
		ResponseEntity<String> attached = attachEvidence(taskId, evidenceId);
		assertThat(attached.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refCount(taskId, evidenceId)).isEqualTo(1);
	}

	@Test
	void AT65_attachEvidence_doesNotAffectTransitionOrVersion() {
		String taskId = createTask("不影响 transition 任务");
		String evidenceId = createEvidence("挂载不 bump version");
		long versionBefore = JsonProbe.lng(getTask(taskId), "version");

		attachEvidence(taskId, evidenceId);
		long versionAfterAttach = JsonProbe.lng(getTask(taskId), "version");
		assertThat(versionAfterAttach).isEqualTo(versionBefore);
		assertThat(JsonProbe.str(getTask(taskId), "status")).isEqualTo("TODO");

		transitionTask(taskId, versionAfterAttach, "IN_PROGRESS");
		transitionTask(taskId, JsonProbe.lng(getTask(taskId), "version"), "COMPLETED");
		assertThat(JsonProbe.str(getTask(taskId), "status")).isEqualTo("COMPLETED");
		assertThat(JsonProbe.str(getTask(taskId), "verificationResult")).isEqualTo("未验证完成");
	}

	@Test
	void AT65_taskDetail_includesEvidenceRefs() {
		String taskId = createTask("详情回显任务");
		String evidenceId = createEvidence("详情证据");
		attachEvidence(taskId, evidenceId);

		String detail = getTask(taskId);
		assertThat(JsonProbe.arraySize(detail, "evidenceRefs")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(detail, "evidenceRefs", 0, "id")).isEqualTo(evidenceId);
	}

	@Test
	void AT65_softDeletedEvidence_showsTrashedFlag() {
		String taskId = createTask("软删证据任务");
		String evidenceId = createEvidence("将被软删");
		attachEvidence(taskId, evidenceId);
		softDeleteEvidence(evidenceId);

		// 软删 evidence 不联动删 task_evidence 关联行（无 ON DELETE CASCADE），引用保留并显示 trashed=true
		assertThat(refCount(taskId, evidenceId)).isEqualTo(1);
		String detail = getTask(taskId);
		assertThat(JsonProbe.arraySize(detail, "evidenceRefs")).isEqualTo(1);
		assertThat(JsonProbe.arrStr(detail, "evidenceRefs", 0, "id")).isEqualTo(evidenceId);
		assertThat(JsonProbe.bool(detail, "evidenceRefs.0.trashed")).isTrue();
	}

	@Test
	void AT65_attachEvidence_doesNotMutateEvidenceTable() {
		String evidenceId = createEvidence("不可变证据");
		long evidenceVersionBefore = jdbc.queryForObject(
			"SELECT version FROM evidence WHERE id=?", Long.class, evidenceId);
		String evidenceUpdatedBefore = jdbc.queryForObject(
			"SELECT updated_at FROM evidence WHERE id=?", String.class, evidenceId);

		String taskId = createTask("不动 evidence 表任务");
		attachEvidence(taskId, evidenceId);
		detachEvidence(taskId, evidenceId);

		long evidenceVersionAfter = jdbc.queryForObject(
			"SELECT version FROM evidence WHERE id=?", Long.class, evidenceId);
		String evidenceUpdatedAfter = jdbc.queryForObject(
			"SELECT updated_at FROM evidence WHERE id=?", String.class, evidenceId);
		assertThat(evidenceVersionAfter).isEqualTo(evidenceVersionBefore);
		assertThat(evidenceUpdatedAfter).isEqualTo(evidenceUpdatedBefore);
	}

	// ---- helpers ----

	private String createTask(String title) {
		String body = "{\"title\":\"" + title + "\"}";
		ResponseEntity<String> response = restTemplate.exchange(url("/tasks"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return JsonProbe.str(response.getBody(), "id");
	}

	private String createEvidence(String title) {
		String body = """
			{"type":"GIT_REPOSITORY","title":"%s","urlOrPath":"https://github.com/user/example"}
			""".formatted(title);
		ResponseEntity<String> response = restTemplate.exchange(url("/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return JsonProbe.str(response.getBody(), "id");
	}

	private ResponseEntity<String> attachEvidence(String taskId, String evidenceId) {
		return attachEvidenceWithKey(taskId, evidenceId, TestFixtures.newKey());
	}

	private ResponseEntity<String> attachEvidenceWithKey(String taskId, String evidenceId, String key) {
		String body = "{\"evidenceId\":\"" + evidenceId + "\"}";
		return restTemplate.exchange(url("/tasks/" + taskId + "/evidence"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", key), String.class);
	}

	private ResponseEntity<String> detachEvidence(String taskId, String evidenceId) {
		return restTemplate.exchange(url("/tasks/" + taskId + "/evidence/" + evidenceId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey()), String.class);
	}

	private String listEvidence(String taskId) {
		return restTemplate.getForEntity(url("/tasks/" + taskId + "/evidence"), String.class).getBody();
	}

	private String getTask(String taskId) {
		return restTemplate.getForEntity(url("/tasks/" + taskId), String.class).getBody();
	}

	private void transitionTask(String taskId, long version, String targetStatus) {
		String body = "{\"targetStatus\":\"" + targetStatus + "\"}";
		restTemplate.exchange(url("/tasks/" + taskId + "/transition"), HttpMethod.POST,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
	}

	private void softDeleteEvidence(String evidenceId) {
		restTemplate.exchange(url("/evidence/" + evidenceId), HttpMethod.DELETE,
			TestFixtures.httpWithHeaders("", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
	}

	private int refCount(String taskId, String evidenceId) {
		Integer count = jdbc.queryForObject(
			"SELECT COUNT(*) FROM task_evidence WHERE task_id=? AND evidence_id=?", Integer.class, taskId, evidenceId);
		return count == null ? 0 : count;
	}
}
