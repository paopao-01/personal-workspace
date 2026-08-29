-- AI 候选条目排序：保持模型输出顺序（MUST 在前等），避免同毫秒随机 UUID 导致乱序
ALTER TABLE ai_job_item ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_ai_job_item_order ON ai_job_item(ai_job_id, sort_order);
