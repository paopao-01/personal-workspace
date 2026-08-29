-- P1 CSV 导出（PRD 18 / V0.2）：放宽 data_export.format 约束以支持 CSV
-- SQLite 无法修改 CHECK 约束，按标准流程重建表并迁移存量行。
CREATE TABLE data_export_v6 (
  id TEXT PRIMARY KEY,
  format TEXT NOT NULL CHECK (format IN ('JSON', 'CSV')),
  status TEXT NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  download_path TEXT,
  failure_reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT INTO data_export_v6 (id, format, status, download_path, failure_reason, created_at, updated_at)
SELECT id, format, status, download_path, failure_reason, created_at, updated_at
FROM data_export;

DROP TABLE data_export;

ALTER TABLE data_export_v6 RENAME TO data_export;
