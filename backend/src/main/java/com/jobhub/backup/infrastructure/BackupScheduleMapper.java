package com.jobhub.backup.infrastructure;

import com.jobhub.backup.domain.BackupSchedule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * backup_schedule 单行配置 Mapper。
 * - selectSingleton：读取单行配置（可能为空，由 Service 懒插入）。
 * - insert：初始化单行（enabled=false、cron 默认、version=0）。
 * - updateConfig：乐观锁更新 cron + enabled + version+1（仅用户配置更新走此路径）。
 * - updateLastRun：调度器系统写入 last_run_*，不校验版本、不 bump version（单实例无并发）。
 */
@Mapper
public interface BackupScheduleMapper {

	@Select("""
		SELECT id, cron_expression, enabled, version, last_run_at, last_run_status,
			last_run_error, last_backup_id
		FROM backup_schedule WHERE id = 'singleton'
		""")
	BackupSchedule selectSingleton();

	@Insert("""
		INSERT INTO backup_schedule (id, cron_expression, enabled, version, last_run_at, last_run_status,
			last_run_error, last_backup_id)
		VALUES (#{schedule.id}, #{schedule.cronExpression}, #{schedule.enabled}, 0, NULL, NULL, NULL, NULL)
		""")
	int insert(@Param("schedule") BackupSchedule schedule);

	/** 乐观锁更新用户配置（cron + enabled），成功 version+1。返回受影响行数，0 表示版本不匹配。 */
	@Update("""
		UPDATE backup_schedule
		SET cron_expression = #{schedule.cronExpression},
		    enabled = #{schedule.enabled},
		    version = version + 1
		WHERE id = 'singleton' AND version = #{expectedVersion}
		""")
	int updateConfig(@Param("schedule") BackupSchedule schedule, @Param("expectedVersion") long expectedVersion);

	/** 调度器系统写入上次运行结果：不校验版本、不 bump version（单实例运行，无并发）。 */
	@Update("""
		UPDATE backup_schedule
		SET last_run_at = #{lastRunAt},
		    last_run_status = #{lastRunStatus},
		    last_run_error = #{lastRunError},
		    last_backup_id = #{lastBackupId}
		WHERE id = 'singleton'
		""")
	int updateLastRun(@Param("lastRunAt") String lastRunAt, @Param("lastRunStatus") String lastRunStatus,
			@Param("lastRunError") String lastRunError, @Param("lastBackupId") String lastBackupId);

	/** 删除备份联动：若 last_backup_id 指向被删 id 则置空，不 bump version（系统写）。返回受影响行数。 */
	@Update("UPDATE backup_schedule SET last_backup_id = NULL WHERE id = 'singleton' AND last_backup_id = #{id}")
	int clearLastBackupIdIfMatch(@Param("id") String id);
}
