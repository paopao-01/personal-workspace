-- 加密定时备份调度配置（单行可变元数据，singleton）。
-- cron_expression + enabled + version 经 If-Match-Version 乐观锁更新；
-- last_run_* 由调度器系统写入（不 bump version、不参与乐观锁）；
-- armed 为进程内存状态，不落盘；passphrase 与派生密钥永不持久化（与手动备份同一硬规则）。
-- 主键固定为 'singleton'，保证全局唯一行；应用启动时由 BackupScheduleService.get() 懒插入。
CREATE TABLE backup_schedule (
  id               VARCHAR(32) PRIMARY KEY,
  cron_expression  VARCHAR(64) NOT NULL,
  enabled          BOOLEAN NOT NULL DEFAULT FALSE,
  version          INTEGER NOT NULL DEFAULT 0,
  last_run_at      VARCHAR(32),
  last_run_status  VARCHAR(24),
  last_run_error   VARCHAR(512),
  last_backup_id   VARCHAR(36)
);
