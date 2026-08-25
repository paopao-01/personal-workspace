PRAGMA foreign_keys = ON;

CREATE TABLE user_profile (
  id TEXT PRIMARY KEY,
  display_name TEXT,
  job_target TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE user_setting (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL UNIQUE REFERENCES user_profile(id),
  time_zone TEXT NOT NULL DEFAULT 'Asia/Shanghai',
  default_reminder_offsets_json TEXT NOT NULL DEFAULT '[1440,120,30]',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE skill (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL UNIQUE,
  category TEXT,
  is_system INTEGER NOT NULL DEFAULT 0 CHECK (is_system IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE TABLE skill_alias (
  id TEXT PRIMARY KEY,
  skill_id TEXT NOT NULL REFERENCES skill(id),
  alias TEXT NOT NULL,
  normalized_alias TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL
);

CREATE TABLE user_skill (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES user_profile(id),
  skill_id TEXT NOT NULL REFERENCES skill(id),
  self_level INTEGER NOT NULL DEFAULT 0 CHECK (self_level BETWEEN 0 AND 5),
  evidence_status TEXT NOT NULL DEFAULT 'NO_EVIDENCE' CHECK (evidence_status IN ('NO_EVIDENCE', 'WEAK', 'VALID')),
  interview_performance_json TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0,
  UNIQUE (user_id, skill_id)
);

CREATE TABLE project (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  scenario TEXT NOT NULL,
  approach TEXT NOT NULL,
  problem_solved TEXT NOT NULL,
  result_text TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE evidence (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL CHECK (type IN ('PROJECT_CODE', 'GIT_REPOSITORY', 'ARTICLE', 'ARCHITECTURE_DIAGRAM', 'API_DOCUMENT', 'LOAD_TEST_REPORT', 'LOG_OR_MONITORING', 'INTERVIEW_ANSWER', 'WORK_EXPERIENCE')),
  title TEXT NOT NULL,
  where_used TEXT,
  problem_solved TEXT,
  approach TEXT,
  result_text TEXT,
  url_or_path TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE skill_evidence (
  skill_id TEXT NOT NULL REFERENCES skill(id),
  evidence_id TEXT NOT NULL REFERENCES evidence(id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (skill_id, evidence_id)
);

CREATE TABLE project_evidence (
  project_id TEXT NOT NULL REFERENCES project(id),
  evidence_id TEXT NOT NULL REFERENCES evidence(id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (project_id, evidence_id)
);

CREATE TABLE job_posting (
  id TEXT PRIMARY KEY,
  company_name TEXT NOT NULL,
  title TEXT NOT NULL,
  jd_raw_text TEXT NOT NULL,
  source TEXT,
  source_url TEXT,
  location TEXT,
  salary_range TEXT,
  decision_status TEXT CHECK (decision_status IN ('TO_APPLY', 'APPLY', 'DEFER', 'IGNORE')),
  decision_reason TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE job_requirement (
  id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL REFERENCES job_posting(id),
  raw_text TEXT NOT NULL,
  normalized_name TEXT,
  requirement_type TEXT NOT NULL CHECK (requirement_type IN ('MUST', 'BONUS', 'RESPONSIBILITY', 'EXPERIENCE', 'DOMAIN', 'TO_CONFIRM')),
  proficiency_text TEXT,
  confirmation_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (confirmation_status IN ('PENDING', 'CONFIRMED', 'IGNORED')),
  source_type TEXT NOT NULL DEFAULT 'RULE' CHECK (source_type IN ('RULE', 'USER', 'AI')),
  sort_order INTEGER NOT NULL DEFAULT 0,
  merged_into_requirement_id TEXT REFERENCES job_requirement(id),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE requirement_skill (
  requirement_id TEXT NOT NULL REFERENCES job_requirement(id),
  skill_id TEXT NOT NULL REFERENCES skill(id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (requirement_id, skill_id)
);

CREATE TABLE requirement_match (
  id TEXT PRIMARY KEY,
  requirement_id TEXT NOT NULL UNIQUE REFERENCES job_requirement(id),
  match_status TEXT NOT NULL CHECK (match_status IN ('SATISFIED_WITH_EVIDENCE', 'SELF_REPORTED_NO_EVIDENCE', 'NOT_MET', 'INSUFFICIENT_INFO', 'PENDING_CONFIRMATION')),
  evidence_snapshot_json TEXT NOT NULL DEFAULT '[]',
  manual_override_reason TEXT,
  calculated_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE application_record (
  id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL REFERENCES job_posting(id),
  applied_at TEXT NOT NULL,
  channel TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN', 'ON_HOLD')),
  previous_active_status TEXT CHECK (previous_active_status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING')),
  resume_version TEXT,
  expected_salary TEXT,
  contact TEXT,
  next_action TEXT,
  next_action_due_at TEXT,
  rejection_reason TEXT,
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_application_active_per_job
  ON application_record(job_id)
  WHERE deleted_at IS NULL AND status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'ON_HOLD');

CREATE TABLE application_status_log (
  id TEXT PRIMARY KEY,
  application_id TEXT NOT NULL REFERENCES application_record(id),
  from_status TEXT CHECK (from_status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN', 'ON_HOLD')),
  to_status TEXT NOT NULL CHECK (to_status IN ('DRAFT', 'APPLIED', 'RESUME_PASSED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN', 'ON_HOLD')),
  reason TEXT,
  idempotency_key TEXT,
  occurred_at TEXT NOT NULL
);

CREATE TABLE interview_schedule (
  id TEXT PRIMARY KEY,
  application_id TEXT NOT NULL REFERENCES application_record(id),
  round_name TEXT NOT NULL,
  starts_at TEXT NOT NULL,
  event_time_zone TEXT NOT NULL,
  mode TEXT CHECK (mode IN ('ONLINE', 'ONSITE', 'PHONE')),
  meeting_url_or_address TEXT,
  contact TEXT,
  schedule_status TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (schedule_status IN ('SCHEDULED', 'COMPLETED', 'CANCELED', 'NO_SHOW')),
  result TEXT NOT NULL DEFAULT 'PENDING' CHECK (result IN ('PENDING', 'PASSED', 'FAILED')),
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  CHECK ((schedule_status = 'SCHEDULED' AND result = 'PENDING') OR schedule_status = 'COMPLETED' OR (schedule_status IN ('CANCELED', 'NO_SHOW') AND result = 'PENDING'))
);

CREATE TABLE interview_checklist_item (
  id TEXT PRIMARY KEY,
  interview_id TEXT NOT NULL REFERENCES interview_schedule(id),
  text TEXT NOT NULL,
  completed INTEGER NOT NULL DEFAULT 0 CHECK (completed IN (0, 1)),
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE interview_reminder (
  id TEXT PRIMARY KEY,
  interview_id TEXT NOT NULL REFERENCES interview_schedule(id),
  reminder_type TEXT NOT NULL CHECK (reminder_type IN ('ONE_DAY', 'TWO_HOURS', 'THIRTY_MINUTES', 'CUSTOM')),
  scheduled_at TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELED')),
  failure_reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 0,
  UNIQUE (interview_id, reminder_type, scheduled_at)
);

CREATE TABLE interview_review (
  id TEXT PRIMARY KEY,
  interview_id TEXT NOT NULL UNIQUE REFERENCES interview_schedule(id),
  review_status TEXT NOT NULL DEFAULT 'NOT_STARTED' CHECK (review_status IN ('NOT_STARTED', 'DRAFT', 'COMPLETED')),
  interview_result TEXT CHECK (interview_result IN ('PENDING', 'PASSED', 'FAILED')),
  no_questions_recorded INTEGER NOT NULL DEFAULT 0 CHECK (no_questions_recorded IN (0, 1)),
  overall_feeling TEXT,
  interviewer_focus TEXT,
  job_interest TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE knowledge_point (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL UNIQUE,
  category TEXT,
  is_system INTEGER NOT NULL DEFAULT 0 CHECK (is_system IN (0, 1)),
  merged_into_knowledge_point_id TEXT REFERENCES knowledge_point(id),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE TABLE interview_question (
  id TEXT PRIMARY KEY,
  review_id TEXT NOT NULL REFERENCES interview_review(id),
  content TEXT NOT NULL,
  question_type TEXT,
  my_answer TEXT,
  reference_answer TEXT,
  answer_status TEXT NOT NULL CHECK (answer_status IN ('FULLY_ANSWERED', 'PARTIALLY_ANSWERED', 'UNANSWERED')),
  difficulty INTEGER CHECK (difficulty BETWEEN 1 AND 5),
  error_reason TEXT,
  improvement_plan TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE question_knowledge (
  question_id TEXT NOT NULL REFERENCES interview_question(id),
  knowledge_point_id TEXT NOT NULL REFERENCES knowledge_point(id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (question_id, knowledge_point_id)
);

CREATE TABLE learning_task (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  task_type TEXT,
  priority TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  estimated_minutes INTEGER CHECK (estimated_minutes > 0),
  due_at TEXT,
  learning_goal TEXT,
  acceptance_criteria TEXT,
  verification_method TEXT,
  verification_result TEXT,
  output_url TEXT,
  status TEXT NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  abandoned_at TEXT,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE task_source (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL REFERENCES learning_task(id),
  source_type TEXT NOT NULL CHECK (source_type IN ('QUESTION', 'JOB_REQUIREMENT', 'SKILL', 'KNOWLEDGE_POINT', 'MANUAL')),
  source_id TEXT,
  created_at TEXT NOT NULL,
  UNIQUE (task_id, source_type, source_id)
);

CREATE TABLE notification (
  id TEXT PRIMARY KEY,
  reminder_id TEXT REFERENCES interview_reminder(id),
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  read_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE audit_log (
  id TEXT PRIMARY KEY,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  action TEXT NOT NULL,
  before_snapshot_json TEXT,
  after_snapshot_json TEXT,
  reason TEXT,
  occurred_at TEXT NOT NULL
);

CREATE TABLE idempotency_record (
  id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL,
  operation TEXT NOT NULL,
  request_fingerprint TEXT NOT NULL,
  response_status INTEGER NOT NULL,
  response_body_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  UNIQUE (idempotency_key, operation)
);

CREATE TABLE data_export (
  id TEXT PRIMARY KEY,
  format TEXT NOT NULL CHECK (format = 'JSON'),
  status TEXT NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  download_path TEXT,
  failure_reason TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE trash_item (
  id TEXT PRIMARY KEY,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL,
  display_name TEXT NOT NULL,
  impact_summary_json TEXT NOT NULL DEFAULT '[]',
  deleted_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  restored_at TEXT,
  purged_at TEXT,
  UNIQUE (resource_type, resource_id, deleted_at)
);

CREATE INDEX idx_job_requirement_job_confirmation ON job_requirement(job_id, confirmation_status, sort_order);
CREATE INDEX idx_application_status_action_due ON application_record(status, next_action_due_at);
CREATE INDEX idx_application_status_log_application_occurred ON application_status_log(application_id, occurred_at DESC);
CREATE INDEX idx_interview_schedule_status_starts ON interview_schedule(schedule_status, starts_at);
CREATE INDEX idx_interview_reminder_status_scheduled ON interview_reminder(status, scheduled_at);
CREATE INDEX idx_interview_question_review_active ON interview_question(review_id, deleted_at);
CREATE INDEX idx_question_knowledge_knowledge_question ON question_knowledge(knowledge_point_id, question_id);
CREATE INDEX idx_learning_task_status_due_priority ON learning_task(status, due_at, priority);
CREATE INDEX idx_trash_item_expiry ON trash_item(expires_at, deleted_at);

INSERT OR IGNORE INTO user_profile (id, display_name, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000001', '本地用户', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT OR IGNORE INTO user_setting (id, user_id, time_zone, default_reminder_offsets_json, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Asia/Shanghai', '[1440,120,30]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
