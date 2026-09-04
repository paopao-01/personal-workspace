-- 连续追问使用独立 AI 审计任务；SQLite 重建 ai_job 以扩展 job_type CHECK。
PRAGMA foreign_keys = OFF;
ALTER TABLE mock_interview_turn RENAME TO mock_interview_turn_v19_old;
ALTER TABLE mock_interview_session RENAME TO mock_interview_session_v19_old;
ALTER TABLE ai_job_item RENAME TO ai_job_item_v19_old;
ALTER TABLE ai_job RENAME TO ai_job_v19_old;
CREATE TABLE ai_job (
  id TEXT PRIMARY KEY,
  job_type TEXT NOT NULL CHECK (job_type IN ('JD_EXTRACTION', 'RESUME_DRAFT', 'QUESTION_CLASSIFICATION', 'ANSWER_QUALITY_ANALYSIS', 'TASK_SUGGESTION', 'MOCK_INTERVIEW', 'MOCK_INTERVIEW_FOLLOW_UP')),
  object_id TEXT NOT NULL, object_version INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
  provider_id TEXT NOT NULL REFERENCES ai_provider(id), provider_type TEXT NOT NULL, model TEXT NOT NULL,
  prompt_version TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, failure_reason TEXT, input_snapshot TEXT NOT NULL,
  output_json TEXT, started_at TEXT, finished_at TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
INSERT INTO ai_job SELECT * FROM ai_job_v19_old;
DROP TABLE ai_job_v19_old;
CREATE TABLE ai_job_item (
  id TEXT PRIMARY KEY, ai_job_id TEXT NOT NULL REFERENCES ai_job(id), payload_json TEXT NOT NULL, edited_payload_json TEXT,
  status TEXT NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED')), requirement_id TEXT,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, task_id TEXT REFERENCES learning_task(id)
);
INSERT INTO ai_job_item SELECT * FROM ai_job_item_v19_old;
DROP TABLE ai_job_item_v19_old;
CREATE INDEX idx_ai_job_object ON ai_job(job_type, object_id, created_at);
CREATE INDEX idx_ai_job_item_job ON ai_job_item(ai_job_id, created_at);
CREATE INDEX idx_ai_job_item_order ON ai_job_item(ai_job_id, sort_order);
CREATE INDEX idx_ai_job_item_task ON ai_job_item(task_id);
CREATE TABLE mock_interview_session (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES project(id),
  status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELED')),
  ai_job_id TEXT UNIQUE REFERENCES ai_job(id),
  follow_up_ai_job_id TEXT REFERENCES ai_job(id),
  project_snapshot TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);
INSERT INTO mock_interview_session (id, project_id, status, ai_job_id, project_snapshot, created_at, updated_at, version)
  SELECT id, project_id, status, ai_job_id, project_snapshot, created_at, updated_at, version FROM mock_interview_session_v19_old;
DROP TABLE mock_interview_session_v19_old;
CREATE TABLE mock_interview_turn (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES mock_interview_session(id),
  turn_number INTEGER NOT NULL,
  speaker TEXT NOT NULL CHECK (speaker IN ('AI', 'USER')),
  content TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(session_id, turn_number)
);
INSERT INTO mock_interview_turn SELECT * FROM mock_interview_turn_v19_old;
DROP TABLE mock_interview_turn_v19_old;
CREATE INDEX idx_mock_interview_session_project ON mock_interview_session(project_id, created_at DESC);
CREATE INDEX idx_mock_interview_session_follow_up_job ON mock_interview_session(follow_up_ai_job_id);
PRAGMA foreign_keys = ON;
