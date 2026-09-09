-- 任务完成证据关联表。仿 skill_evidence / project_evidence（V1 第 82-94 行），
-- PK 双键 task_id + evidence_id，created_at 记录挂载时间。
-- 只读挂载，非状态转换：挂载/卸载不改变 learning_task.version / status / verification_*。
-- 外键不带 ON DELETE CASCADE，与 skill_evidence / project_evidence 一致——
-- 任务软删或证据软删不联动删关联行，引用方通过 trashed 字段显示"来源已删除"，恢复后自动还原。
-- 重复挂载由 INSERT OR IGNORE 静默幂等（与 EvidenceMapper.insertSkillRef / ProjectMapper.insertEvidenceRef 一致）。
CREATE TABLE task_evidence (
  task_id TEXT NOT NULL REFERENCES learning_task(id),
  evidence_id TEXT NOT NULL REFERENCES evidence(id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (task_id, evidence_id)
);

-- 支撑 GET /tasks/{taskId}/evidence 按 task_id 查询关联证据
CREATE INDEX idx_task_evidence_task ON task_evidence(task_id);
