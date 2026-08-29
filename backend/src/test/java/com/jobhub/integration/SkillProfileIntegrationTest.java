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

	private void seedSkill(String skillId, String name) {
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,1,?,?)",
			skillId, name, name.toLowerCase() + "-profile", "后端", NOW, NOW);
	}
}
