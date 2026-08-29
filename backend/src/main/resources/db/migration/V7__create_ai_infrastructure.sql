-- P1 AI 异步任务基础设施（PRD 9.2）：可切换供应商配置 + 异步任务审计 + 候选变更条目
-- api_key 仅存本地库，不导出、不回显（同 email 渠道凭据约定）。
CREATE TABLE ai_provider (
  id TEXT PRIMARY KEY,
  provider_type TEXT NOT NULL CHECK (provider_type IN ('OPENAI_COMPATIBLE', 'ANTHROPIC')),
  name TEXT NOT NULL,
  base_url TEXT NOT NULL,
  model TEXT NOT NULL,
  api_key TEXT,
  is_active INTEGER NOT NULL DEFAULT 0 CHECK (is_active IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);

-- 异步任务：状态机 QUEUED -> RUNNING -> SUCCEEDED/FAILED；QUEUED/RUNNING -> CANCELED；
-- FAILED -> QUEUED（retry，attempt_count + 1，上限 3）。
CREATE TABLE ai_job (
  id TEXT PRIMARY KEY,
  job_type TEXT NOT NULL CHECK (job_type IN ('JD_EXTRACTION')),
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

CREATE INDEX idx_ai_job_object ON ai_job(job_type, object_id, created_at);

-- 候选变更条目：逐项接受（可编辑）/拒绝；重新生成产生新任务，不改写既有条目。
CREATE TABLE ai_job_item (
  id TEXT PRIMARY KEY,
  ai_job_id TEXT NOT NULL REFERENCES ai_job(id),
  payload_json TEXT NOT NULL,
  edited_payload_json TEXT,
  status TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')),
  requirement_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX idx_ai_job_item_job ON ai_job_item(ai_job_id, created_at);
