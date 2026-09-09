package com.jobhub.integration;

import com.jobhub.integration.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

class SkillProfileIntegrationTest extends AbstractIntegrationTest {

	private static final String NOW = "2026-08-29T00:00:00Z";

	@Test
	void P1_skillProfileListsAllSkillsWithUnratedAsNull() {
		assertThat(JsonProbe.arraySize(restTemplate.getForEntity(url("/skills/profile"), String.class).getBody(), ""))
			.isEqualTo(0);

		seedSkill("30000000-0000-0000-0000-000000000001", "Kafka");
		seedSkill("30000000-0000-0000-0000-000000000002", "MySQL");
		jdbc.update("INSERT INTO user_skill (id, user_id, skill_id, self_level, evidence_status, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,0)",
			"31000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000001",
			"30000000-0000-0000-0000-000000000002", 4, "WEAK", NOW, NOW);

		String profiles = restTemplate.getForEntity(url("/skills/profile"), String.class).getBody();
		assertThat(JsonProbe.arraySize(profiles, "")).isEqualTo(2);
		// 按名称排序：Kafka 在前且未评估
		assertThat(JsonProbe.arrStr(profiles, "", 0, "skillName")).isEqualTo("Kafka");
		assertThat(JsonProbe.str(profiles, "0.selfLevel")).isEqualTo("null");
		assertThat(JsonProbe.str(profiles, "0.evidenceStatus")).isEqualTo("null");
		assertThat(JsonProbe.lng(profiles, "0.version")).isEqualTo(0);
		// MySQL 已有自评记录：三维度独立呈现
		assertThat(JsonProbe.arrStr(profiles, "", 1, "skillName")).isEqualTo("MySQL");
		assertThat(JsonProbe.lng(profiles, "1.selfLevel")).isEqualTo(4);
		assertThat(JsonProbe.arrStr(profiles, "", 1, "evidenceStatus")).isEqualTo("WEAK");
	}

	@Test
	void P1_updateSelfLevelCreatesRecordOnFirstSetAndGuardsVersion() {
		String skillId = "30000000-0000-0000-0000-000000000003";
		seedSkill(skillId, "Redis");

		// 首次自评：无 user_skill 记录，以 profile version 0 创建
		ResponseEntity<String> missingVersion = restTemplate.exchange(
			url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":3}", "Idempotency-Key", TestFixtures.newKey()),
			String.class);
		assertThat(missingVersion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		String created = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":3,\"reason\":\"能讲清持久化与主从\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.lng(created, "selfLevel")).isEqualTo(3);
		assertThat(JsonProbe.str(created, "evidenceStatus")).isEqualTo("NO_EVIDENCE");
		// 首次创建为初始版本 0，后续更新以其作为乐观锁基线
		assertThat(JsonProbe.lng(created, "version")).isEqualTo(0);

		// 再次自评走乐观锁更新
		String updated = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":5}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.lng(updated, "selfLevel")).isEqualTo(5);
		assertThat(JsonProbe.lng(updated, "version")).isEqualTo(1);

		// 旧版本 → 409；非法等级 → 400；未知技能 → 404
		ResponseEntity<String> stale = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":2}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
		assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		ResponseEntity<String> outOfRange = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":6}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "1"), String.class);
		assertThat(outOfRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(outOfRange.getBody()).contains("VALIDATION_ERROR");

		ResponseEntity<String> unknown = restTemplate.exchange(
			url("/skills/99999999-9999-9999-9999-999999999999/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":3}", "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", "0"), String.class);
		assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// 三维度独立：自评更新不得改动 evidence_status
		String profile = restTemplate.getForEntity(url("/skills/profile"), String.class).getBody();
		assertThat(JsonProbe.lng(profile, "0.selfLevel")).isEqualTo(5);
		assertThat(JsonProbe.arrStr(profile, "", 0, "evidenceStatus")).isEqualTo("NO_EVIDENCE");
	}

	@Test
	void AT66_selfLevelHistoryWritesFromNullOnFirstAndFromOldOnSubsequent() {
		String skillId = "30000000-0000-0000-0000-000000000010";
		seedSkill(skillId, "SpringBoot");

		// 首次自评：fromLevel=null（无前值），reason 持久化
		String first = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":3,\"reason\":\"能讲清自动装配\"}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.lng(first, "selfLevel")).isEqualTo(3);

		String historyAfterFirst = restTemplate.getForEntity(
			url("/skills/" + skillId + "/self-level/history"), String.class).getBody();
		assertThat(JsonProbe.arraySize(historyAfterFirst, "")).isEqualTo(1);
		assertThat(JsonProbe.str(historyAfterFirst, "0.fromLevel")).isEqualTo("null");
		assertThat(JsonProbe.intVal(historyAfterFirst, "0.toLevel")).isEqualTo(3);
		assertThat(JsonProbe.arrStr(historyAfterFirst, "", 0, "reason")).isEqualTo("能讲清自动装配");
		assertThat(JsonProbe.arrStr(historyAfterFirst, "", 0, "occurredAt")).isNotNull();
		assertThat(JsonProbe.arrStr(historyAfterFirst, "", 0, "id")).isNotNull();
	}

	@Test
	void AT66_subsequentUpdateWritesFromOldLevel() {
		String skillId = "30000000-0000-0000-0000-000000000011";
		seedSkill(skillId, "Docker");
		putSelfLevel(skillId, 3, 0, "first");
		// 再次自评 fromLevel=旧值 3 → toLevel=5
		String updated = restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":5}",
				"Idempotency-Key", TestFixtures.newKey(), "If-Match-Version", "0"), String.class).getBody();
		assertThat(JsonProbe.lng(updated, "selfLevel")).isEqualTo(5);

		String history = restTemplate.getForEntity(
			url("/skills/" + skillId + "/self-level/history"), String.class).getBody();
		assertThat(JsonProbe.arraySize(history, "")).isEqualTo(2);
		// 升序：首条 from=null→3，次条 from=3→5
		assertThat(JsonProbe.str(history, "0.fromLevel")).isEqualTo("null");
		assertThat(JsonProbe.intVal(history, "0.toLevel")).isEqualTo(3);
		assertThat(JsonProbe.intVal(history, "1.fromLevel")).isEqualTo(3);
		assertThat(JsonProbe.intVal(history, "1.toLevel")).isEqualTo(5);
		// 第二次 PUT 未带 reason，历史行 reason 为 null
		assertThat(JsonProbe.str(history, "1.reason")).isEqualTo("null");
	}

	@Test
	void AT66_idempotencyReplayDoesNotDuplicateHistory() {
		String skillId = "30000000-0000-0000-0000-000000000012";
		seedSkill(skillId, "Kubernetes");
		String key = TestFixtures.newKey();
		restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":4}", "Idempotency-Key", key, "If-Match-Version", "0"),
			String.class);
		// 用同一 Idempotency-Key 重放
		restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders("{\"selfLevel\":4}", "Idempotency-Key", key, "If-Match-Version", "0"),
			String.class);
		String history = restTemplate.getForEntity(
			url("/skills/" + skillId + "/self-level/history"), String.class).getBody();
		assertThat(JsonProbe.arraySize(history, "")).isEqualTo(1);
	}

	@Test
	void AT66_unknownSkillReturns404() {
		ResponseEntity<String> resp = restTemplate.getForEntity(
			url("/skills/99999999-9999-9999-9999-999999999999/self-level/history"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void AT66_skillWithNoHistoryReturnsEmptyArray() {
		String skillId = "30000000-0000-0000-0000-000000000013";
		seedSkill(skillId, "RabbitMQ");
		ResponseEntity<String> resp = restTemplate.getForEntity(
			url("/skills/" + skillId + "/self-level/history"), String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonProbe.arraySize(resp.getBody(), "")).isEqualTo(0);
	}

	@Test
	void AT66_historyDoesNotAffectThreeDimensionIndependenceAndVersionLogic() {
		String skillId = "30000000-0000-0000-0000-000000000014";
		seedSkill(skillId, "Redis");
		// 首次自评创建记录 version=0、selfLevel=2
		putSelfLevel(skillId, 2, 0, null);
		// 二次更新仍以 version=0 作乐观锁基线（首次创建为初始版本 0），更新后 version=1、selfLevel=4
		putSelfLevel(skillId, 4, 0, null);
		// 三维度独立：evidence_status 仍 NO_EVIDENCE；version 由乐观锁管理递增
		String profile = restTemplate.getForEntity(url("/skills/profile"), String.class).getBody();
		assertThat(JsonProbe.lng(profile, "0.selfLevel")).isEqualTo(4);
		assertThat(JsonProbe.arrStr(profile, "", 0, "evidenceStatus")).isEqualTo("NO_EVIDENCE");
		assertThat(JsonProbe.lng(profile, "0.version")).isEqualTo(1);
		// 历史两条，version 递增逻辑不受历史写入影响
		String history = restTemplate.getForEntity(
			url("/skills/" + skillId + "/self-level/history"), String.class).getBody();
		assertThat(JsonProbe.arraySize(history, "")).isEqualTo(2);
	}

	private void putSelfLevel(String skillId, int level, int version, String reason) {
		String body = reason != null
				? "{\"selfLevel\":" + level + ",\"reason\":\"" + reason + "\"}"
				: "{\"selfLevel\":" + level + "}";
		restTemplate.exchange(url("/skills/" + skillId + "/self-level"), HttpMethod.PUT,
			TestFixtures.httpWithHeaders(body, "Idempotency-Key", TestFixtures.newKey(),
				"If-Match-Version", String.valueOf(version)), String.class);
	}

	private void seedSkill(String skillId, String name) {
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,1,?,?)",
			skillId, name, name.toLowerCase() + "-profile", "后端", NOW, NOW);
	}
}
