-- P1 AI 学习任务建议：扩展 AI 任务类型，保留已有任务、候选和任务回链。
PRAGMA foreign_keys = OFF;
ALTER TABLE ai_job_item RENAME TO ai_job_item_old;
ALTER TABLE ai_job RENAME TO ai_job_old;
CREATE TABLE ai_job (
  id TEXT PRIMARY KEY,
  job_type TEXT NOT NULL CHECK (job_type IN ('JD_EXTRACTION', 'RESUME_DRAFT', 'QUESTION_CLASSIFICATION', 'ANSWER_QUALITY_ANALYSIS', 'TASK_SUGGESTION')),
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
INSERT INTO ai_job SELECT * FROM ai_job_old;
DROP TABLE ai_job_old;
CREATE INDEX idx_ai_job_object ON ai_job(job_type, object_id, created_at);
CREATE TABLE ai_job_item (
  id TEXT PRIMARY KEY,
  ai_job_id TEXT NOT NULL REFERENCES ai_job(id),
  payload_json TEXT NOT NULL,
  edited_payload_json TEXT,
  status TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')),
  requirement_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  task_id TEXT REFERENCES learning_task(id)
);
INSERT INTO ai_job_item SELECT * FROM ai_job_item_old;
DROP TABLE ai_job_item_old;
CREATE INDEX idx_ai_job_item_job ON ai_job_item(ai_job_id, created_at);
CREATE INDEX idx_ai_job_item_order ON ai_job_item(ai_job_id, sort_order);
CREATE INDEX idx_ai_job_item_task ON ai_job_item(task_id);
PRAGMA foreign_keys = ON;
