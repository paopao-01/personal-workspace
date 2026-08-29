package com.jobhub.testsupport.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e")
@RestController
@RequestMapping("/api/e2e")
public class E2eReminderFixtureController {

	private final JdbcTemplate jdbc;

	public E2eReminderFixtureController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/reminders/{id}/mark-sent")
	public Map<String, Object> markSent(@PathVariable String id) {
		int updated = jdbc.update(
			"UPDATE interview_reminder SET status='SENT', updated_at=datetime('now'), version=version+1 WHERE id=?",
			id
		);
		return Map.of("updated", updated);
	}

	@PostMapping("/jobs/{jobId}/seed-project-evidence")
	public Map<String, Object> seedProjectEvidence(@PathVariable String jobId) {
		String requirementId = jdbc.queryForObject(
			"SELECT id FROM job_requirement WHERE job_id=? AND confirmation_status='CONFIRMED' AND deleted_at IS NULL LIMIT 1",
			String.class,
			jobId
		);
		String suffix = UUID.randomUUID().toString();
		String now = "2026-08-29T00:00:00Z";
		String skillId = UUID.randomUUID().toString();
		String projectId = UUID.randomUUID().toString();
		String evidenceId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO skill (id, name, normalized_name, category, is_system, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
			skillId, "Redis", "redis-" + suffix, "Redis", 1, now, now);
		jdbc.update("INSERT INTO requirement_skill (requirement_id, skill_id, created_at) VALUES (?,?,?)", requirementId, skillId, now);
		jdbc.update("INSERT INTO project (id, title, scenario, approach, problem_solved, result_text, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,?,0)",
			projectId, "库存服务缓存改造", "库存查询接口压力高。", "使用 Cache Aside 管理热点数据。", "降低重复查询并保持可接受一致性。", null, now, now);
		jdbc.update("INSERT INTO evidence (id, type, title, where_used, problem_solved, approach, result_text, url_or_path, created_at, updated_at, version) VALUES (?,?,?,?,?,?,?,?,?,?,0)",
			evidenceId, "ARCHITECTURE_DIAGRAM", "缓存流程图", "库存查询接口", "解释缓存命中、回源和失效路径", "Cache Aside", null, "C:/example/jobhub-demo/cache.png", now, now);
		jdbc.update("INSERT INTO skill_evidence (skill_id, evidence_id, created_at) VALUES (?,?,?)", skillId, evidenceId, now);
		jdbc.update("INSERT INTO project_evidence (project_id, evidence_id, created_at) VALUES (?,?,?)", projectId, evidenceId, now);
		return Map.of("projectId", projectId, "evidenceId", evidenceId);
	}
}
