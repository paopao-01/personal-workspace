-- 保留 JD 更新前的 requirement_match 历史；当前结论只读取 invalidated_at 为空的记录。
ALTER TABLE requirement_match ADD COLUMN invalidated_at TEXT;
CREATE INDEX idx_requirement_match_current ON requirement_match(requirement_id, invalidated_at);

-- SQLite 不能修改 CHECK 约束，重建 task_source 以支持学习任务直接关联岗位。
PRAGMA foreign_keys=OFF;
ALTER TABLE task_source RENAME TO task_source_legacy;
CREATE TABLE task_source (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL REFERENCES learning_task(id),
  source_type TEXT NOT NULL CHECK (source_type IN ('QUESTION', 'JOB', 'JOB_REQUIREMENT', 'SKILL', 'KNOWLEDGE_POINT', 'MANUAL')),
  source_id TEXT,
  created_at TEXT NOT NULL,
  UNIQUE (task_id, source_type, source_id)
);
INSERT INTO task_source (id, task_id, source_type, source_id, created_at)
SELECT id, task_id, source_type, source_id, created_at FROM task_source_legacy;
DROP TABLE task_source_legacy;
CREATE INDEX idx_task_source_type_source ON task_source(source_type, source_id);
PRAGMA foreign_keys=ON;
