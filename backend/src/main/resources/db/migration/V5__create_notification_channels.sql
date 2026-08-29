-- P1 浏览器与邮件提醒（PRD 9.3）：通知渠道配置 + 渠道投递状态
-- 渠道由用户主动授权开启；各渠道独立记录发送状态与失败原因；站内通知始终保留。
CREATE TABLE notification_channel (
  id TEXT PRIMARY KEY,
  channel_type TEXT NOT NULL UNIQUE CHECK (channel_type IN ('BROWSER', 'EMAIL')),
  enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
  config_json TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE channel_delivery (
  id TEXT PRIMARY KEY,
  notification_id TEXT NOT NULL REFERENCES notification(id),
  channel_type TEXT NOT NULL CHECK (channel_type IN ('BROWSER', 'EMAIL')),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  failure_reason TEXT,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  sent_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (notification_id, channel_type)
);

CREATE INDEX idx_channel_delivery_pending ON channel_delivery(channel_type, status);
