-- P1 简历定制草稿（PRD 9.4）：ai_job.job_type 扩展 RESUME_DRAFT
-- SQLite 无法修改 CHECK 约束，按标准流程重建表并迁移存量行（含索引）。
PRAGMA foreign_keys = OFF;

CREATE TABLE ai_job_v9 (
  id TEXT PRIMARY KEY,
  job_type TEXT NOT NULL CHECK (job_type IN ('JD_EXTRACTION', 'RESUME_DRAFT')),
  object_id TEXT NOT NULL,
  object_version INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
  provider_id TEXT NOT NULL REFERENCES ai_provider(id),
  provider_type TEXT NOT NULL,
  model TEXT NOT NULL,
  prompt_version TEXT NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  failure_reason TEXT,
  input_snapshot TEXT NOT NULL,
  output_json TEXT,
  started_at TEXT,
  finished_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT INTO ai_job_v9 (id, job_type, object_id, object_version, status, provider_id, provider_type, model,
                       prompt_version, attempt_count, failure_reason, input_snapshot, output_json,
                       started_at, finished_at, created_at, updated_at)
SELECT id, job_type, object_id, object_version, status, provider_id, provider_type, model,
       prompt_version, attempt_count, failure_reason, input_snapshot, output_json,
       started_at, finished_at, created_at, updated_at
FROM ai_job;

DROP TABLE ai_job;

ALTER TABLE ai_job_v9 RENAME TO ai_job;

CREATE INDEX idx_ai_job_object ON ai_job(job_type, object_id, created_at);

PRAGMA foreign_keys = ON;
