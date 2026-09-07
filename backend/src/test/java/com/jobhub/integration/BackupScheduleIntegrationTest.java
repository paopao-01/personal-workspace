package com.jobhub.integration;

import com.jobhub.backup.application.BackupScheduler;
import com.jobhub.backup.application.BackupScheduleService;
import com.jobhub.backup.infrastructure.BackupScheduleMapper;
import com.jobhub.integration.support.AbstractIntegrationTest;
import com.jobhub.integration.support.JsonProbe;
import com.jobhub.integration.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AT-38 加密定时备份调度：内存武装 + cron 触发 + 重启解除武装 + 乐观锁 + 非法 cron。
 * passphrase 仅存内存、永不落盘；armed 反映内存状态；未武装到点记 SKIPPED_DISARMED。
 * 调度器后台轮询在 test profile 置 1h，本测试直调 BackupScheduler.runOnce() 保证确定性。
 * armed 为单例内存字段，跨方法需在 @BeforeEach 显式解除武装，避免串扰。
 */
class BackupScheduleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private BackupScheduler scheduler;

	@Autowired
	private BackupScheduleService scheduleService;

	@Autowired
	private BackupScheduleMapper scheduleMapper;

	private static final String PASSPHRASE = "TestPass1234!plus";

	@BeforeEach
	void disarmBeforeEach() {
		// armed 是单例 bean 的内存字段，跨方法不随 DatabaseCleaner 重置，需显式解除武装避免串扰
		scheduleService.disarm();
	}

	/** GET /backups/schedule：首次懒初始化默认行（enabled=false、cron 默认、version=0、armed=false）。 */
	@Test
	void getScheduleLazilyInitializesDefaultRow() {
		String body = restTemplate.getForEntity(url("/backups/schedule"), String.class).getBody();
		assertThat(body).isNotNull();
		assertThat(JsonProbe.str(body, "cronExpression")).isNotNull();
		assertThat(JsonProbe.str(body, "enabled")).isEqualTo("false");
		assertThat(JsonProbe.str(body, "armed")).isEqualTo("false");
		assertThat(JsonProbe.lng(body, "version")).isZero();
		// passphrase 永不回显
		assertThat(body).doesNotContain("passphrase");
		// 单行已落库
		Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backup_schedule", Integer.class);
		assertThat(rows).isEqualTo(1);
	}

	/** PUT /backups/schedule：更新 cron + enabled，version 自增。 */
	@Test
	void updateScheduleBumpsVersion() {
		// 懒初始化
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String reqBody = """
				{"cronExpression":"0 4 * * *","enabled":true,"version":0}
				""";
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(reqBody, "If-Match-Version", "0"), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(body).isNotNull();
		assertThat(JsonProbe.str(body, "cronExpression")).isEqualTo("0 0 4 * * *");
		assertThat(JsonProbe.str(body, "enabled")).isEqualTo("true");
		assertThat(JsonProbe.lng(body, "version")).isEqualTo(1L);
	}

	/** PUT /backups/schedule：非法 cron 返回 422，不更新配置。 */
	@Test
	void invalidCronReturns422() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String reqBody = """
				{"cronExpression":"not-a-cron","enabled":true,"version":0}
				""";
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(reqBody, "If-Match-Version", "0"), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		// 配置不变
		String after = restTemplate.getForEntity(url("/backups/schedule"), String.class).getBody();
		assertThat(JsonProbe.str(after, "cronExpression")).isNotEqualTo("not-a-cron");
	}

	/** PUT /backups/schedule：旧 version 返回 409。 */
	@Test
	void staleVersionReturns409() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String reqBody = """
				{"cronExpression":"0 5 * * *","enabled":true,"version":0}
				""";
		// 第一次 version 0 → 1
		restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(reqBody, "If-Match-Version", "0"), String.class);
		// 再次用旧 version 0 → 409
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(reqBody, "If-Match-Version", "0"), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	/** PUT 缺 If-Match-Version 头返回 400。 */
	@Test
	void updateWithoutIfMatchVersionReturns400() {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String reqBody = """
				{"cronExpression":"0 6 * * *","enabled":true,"version":0}
				""";
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpJson(reqBody), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/** 武装端点：armed 变 true，不回显 passphrase。 */
	@Test
	void armSetsArmedTrueWithoutEchoingPassphrase() {
		String reqBody = "{\"passphrase\":\"" + PASSPHRASE + "\"}";
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule/arm"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = res.getBody();
		assertThat(body).isNotNull();
		assertThat(JsonProbe.str(body, "armed")).isEqualTo("true");
		assertThat(body).doesNotContain(PASSPHRASE);
	}

	/** 短 passphrase 武装返回 400。 */
	@Test
	void armShortPassphraseReturns400() {
		String reqBody = "{\"passphrase\":\"short\"}";
		ResponseEntity<String> res = restTemplate.exchange(url("/backups/schedule/arm"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	/** enabled=true + armed + runOnce（last_run_at=null 立即触发）→ 生成 backup_record、last_run_status=SUCCESS。 */
	@Test
	void enabledArmedRunOnceCreatesBackupSuccess() {
		// 造一个岗位保证导出有数据
		restTemplate.postForEntity(url("/jobs"),
				TestFixtures.httpJson(TestFixtures.createJobBody("调度示例", "Java 后端")), String.class);
		// 启用调度
		enableSchedule("0 3 * * *");
		// 武装
		arm(PASSPHRASE);
		// 直调 runOnce：last_run_at=null → cron.next(epoch) <= now → 触发
		scheduler.runOnce();

		String status = lastRunStatusFromDb();
		assertThat(status).isEqualTo("SUCCESS");
		String backupId = jdbc.queryForObject(
				"SELECT last_backup_id FROM backup_schedule", String.class);
		assertThat(backupId).isNotNull();
		Integer backupRows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM backup_record WHERE id = ?", Integer.class, backupId);
		assertThat(backupRows).isEqualTo(1);
		// last_run_at 已写入
		String lastRunAt = jdbc.queryForObject(
				"SELECT last_run_at FROM backup_schedule", String.class);
		assertThat(lastRunAt).isNotNull();
	}

	/** enabled=true 但未武装 → runOnce 记 SKIPPED_DISARMED，不生成 backup_record。 */
	@Test
	void enabledDisarmedRunOnceSkipsWithoutBackup() {
		Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		enableSchedule("0 3 * * *");
		// 不武装
		scheduler.runOnce();
		assertThat(lastRunStatusFromDb()).isEqualTo("SKIPPED_DISARMED");
		Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM backup_record", Integer.class);
		assertThat(after).isEqualTo(before);
	}

	/** enabled=false → runOnce 不触发（不写 last_run_*）。 */
	@Test
	void disabledScheduleDoesNotRun() {
		arm(PASSPHRASE);
		// 先确保单行存在并置 enabled=false（version=0 起步）
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String disable = "{\"cronExpression\":\"0 3 * * *\",\"enabled\":false,\"version\":0}";
		restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(disable, "If-Match-Version", "0"), String.class);
		// armed=true 但 enabled=false
		assertThat(scheduleService.isArmed()).isTrue();
		scheduler.runOnce();
		// 不写 last_run_status
		String lastRun = lastRunStatusFromDb();
		assertThat(lastRun == null || lastRun.isEmpty()).isTrue();
	}

	/** 解除武装后再次 runOnce（enabled=true）→ SKIPPED_DISARMED。 */
	@Test
	void disarmThenRunSkips() {
		enableSchedule("0 3 * * *");
		arm(PASSPHRASE);
		assertThat(scheduleService.isArmed()).isTrue();
		scheduleService.disarm();
		assertThat(scheduleService.isArmed()).isFalse();
		scheduler.runOnce();
		assertThat(lastRunStatusFromDb()).isEqualTo("SKIPPED_DISARMED");
	}

	/** passphrase 永不落库（backup_schedule 无 passphrase 列）。 */
	@Test
	void passphraseNeverPersisted() {
		arm(PASSPHRASE);
		Integer passCol = jdbc.queryForObject(
				"SELECT COUNT(*) FROM pragma_table_info('backup_schedule') WHERE name = 'passphrase'", Integer.class);
		assertThat(passCol).isZero();
	}

	private void enableSchedule(String cron) {
		restTemplate.getForEntity(url("/backups/schedule"), String.class);
		String reqBody = "{\"cronExpression\":\"" + cron + "\",\"enabled\":true,\"version\":0}";
		restTemplate.exchange(url("/backups/schedule"), HttpMethod.PUT,
				TestFixtures.httpWithHeaders(reqBody, "If-Match-Version", "0"), String.class);
	}

	private void arm(String passphrase) {
		String reqBody = "{\"passphrase\":\"" + passphrase + "\"}";
		restTemplate.exchange(url("/backups/schedule/arm"), HttpMethod.POST,
				TestFixtures.httpWithHeaders(reqBody, "Idempotency-Key", TestFixtures.newKey()), String.class);
	}

	private String lastRunStatusFromDb() {
		return jdbc.queryForObject(
				"SELECT last_run_status FROM backup_schedule", String.class);
	}
}
