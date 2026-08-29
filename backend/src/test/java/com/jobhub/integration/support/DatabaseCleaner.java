package com.jobhub.integration.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 测试间数据库清理。按 FK 顺序 DELETE 业务表，保留 V1 种子 user_profile/user_setting。
 * RANDOM_PORT + TestRestTemplate 下服务端在独立线程提交，无法用 @Transactional 回滚，
 * 故用每方法 DELETE 重置起点。执行于 @BeforeEach。
 *
 * 用 @Component 而非 @TestComponent：@TestComponent 会被 TypeExcludeFilter 从主应用上下文排除，
 * 导致 @SpringBootTest 默认上下文找不到 bean。本类仅存在于 test classpath，生产运行时不会被扫描。
 */
@Component
public class DatabaseCleaner {

	@Autowired
	private JdbcTemplate jdbc;

	/**
	 * 清空所有业务表。FK 安全顺序：先子后父。保留 V1 种子 user_profile/user_setting。
	 * 新增 application/status_log/interview_schedule：M2 投递切片相关，即便本切片不写面试也清表防残留。
	 */
	public void clearAll() {
		jdbc.execute("DELETE FROM idempotency_record");
		jdbc.execute("DELETE FROM audit_log");
		jdbc.execute("DELETE FROM trash_item");
		jdbc.execute("DELETE FROM data_export");
		jdbc.execute("DELETE FROM match_report");
		jdbc.execute("DELETE FROM notification");
		// user_setting 为 V1 种子行，不删除；重置为种子值，保证依赖默认提醒节点的用例互不串扰
		jdbc.update("UPDATE user_setting SET time_zone='Asia/Shanghai', default_reminder_offsets_json='[1440,120,30]', version=0");
		jdbc.execute("DELETE FROM task_source");
		jdbc.execute("DELETE FROM learning_task");
		jdbc.execute("DELETE FROM question_knowledge");
		jdbc.execute("DELETE FROM interview_question");
		jdbc.execute("DELETE FROM interview_review");
		jdbc.execute("DELETE FROM application_status_log");
		jdbc.execute("DELETE FROM interview_reminder");
		jdbc.execute("DELETE FROM interview_checklist_item");
		jdbc.execute("DELETE FROM interview_schedule");
		jdbc.execute("DELETE FROM application_record");
		jdbc.execute("DELETE FROM requirement_match");
		jdbc.execute("DELETE FROM requirement_skill");
		jdbc.execute("DELETE FROM job_requirement");
		jdbc.execute("DELETE FROM job_posting");
		jdbc.execute("DELETE FROM project_evidence");
		jdbc.execute("DELETE FROM skill_evidence");
		jdbc.execute("DELETE FROM evidence");
		jdbc.execute("DELETE FROM project");
		jdbc.execute("DELETE FROM user_skill");
		jdbc.execute("DELETE FROM skill_alias");
		jdbc.execute("DELETE FROM skill");
	}
}
