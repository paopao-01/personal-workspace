-- V1.0 通知渠道扩展：放宽 notification_channel 与 channel_delivery 的 channel_type CHECK 纳入 WEBHOOK。
-- SQLite 不支持直接修改 CHECK，按既有重建范式（V20）重建两表并复制既有数据，保留 UNIQUE、外键、索引与枚举一致性。
PRAGMA foreign_keys = OFF;
ALTER TABLE channel_delivery RENAME TO channel_delivery_v23_old;
ALTER TABLE notification_channel RENAME TO notification_channel_v23_old;
CREATE TABLE notification_channel (
  id TEXT PRIMARY KEY,
  channel_type TEXT NOT NULL UNIQUE CHECK (channel_type IN ('BROWSER', 'EMAIL', 'WEBHOOK')),
  enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
  config_json TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);
INSERT INTO notification_channel SELECT * FROM notification_channel_v23_old;
DROP TABLE notification_channel_v23_old;
CREATE TABLE channel_delivery (
  id TEXT PRIMARY KEY,
  notification_id TEXT NOT NULL REFERENCES notification(id),
  channel_type TEXT NOT NULL CHECK (channel_type IN ('BROWSER', 'EMAIL', 'WEBHOOK')),
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
  failure_reason TEXT,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  sent_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (notification_id, channel_type)
);
INSERT INTO channel_delivery
  SELECT id, notification_id, channel_type, status, failure_reason, attempt_count, sent_at, created_at, updated_at
  FROM channel_delivery_v23_old;
DROP TABLE channel_delivery_v23_old;
CREATE INDEX idx_channel_delivery_pending ON channel_delivery(channel_type, status);
PRAGMA foreign_keys = ON;
