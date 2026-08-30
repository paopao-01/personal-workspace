-- P1 AI 学习任务建议：保存候选采纳后创建的学习任务回链。
ALTER TABLE ai_job_item ADD COLUMN task_id TEXT REFERENCES learning_task(id);

CREATE INDEX idx_ai_job_item_task ON ai_job_item(task_id);
