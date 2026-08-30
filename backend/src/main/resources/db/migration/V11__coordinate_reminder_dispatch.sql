-- 提醒调度租约与尝试审计：支持多实例领取，过期租约可被接管。
ALTER TABLE interview_reminder ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE interview_reminder ADD COLUMN lease_until TEXT;
ALTER TABLE interview_reminder ADD COLUMN lease_token TEXT;

-- 一个提醒最多对应一条站内通知，避免租约过期重试造成重复通知。
CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_reminder_unique
  ON notification(reminder_id) WHERE reminder_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_interview_reminder_due
  ON interview_reminder(status, scheduled_at, lease_until);
