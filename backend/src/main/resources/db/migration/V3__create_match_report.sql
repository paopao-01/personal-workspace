-- V3：岗位匹配报告（PRD 9.1）。报告为追加式快照：保存权重、规则版本、生成时间与完整数据快照；
-- 过期判定在读取时对比输入指纹，不修改历史行。
CREATE TABLE match_report (
  id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL REFERENCES job_posting(id),
  rule_version TEXT NOT NULL,
  weights_json TEXT NOT NULL,
  report_json TEXT NOT NULL,
  input_fingerprint TEXT NOT NULL,
  generated_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX idx_match_report_job_generated ON match_report(job_id, generated_at DESC);
