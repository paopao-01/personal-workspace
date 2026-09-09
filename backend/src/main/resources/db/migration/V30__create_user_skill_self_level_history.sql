-- 技能自评等级变更历史表。仿 application_status_log（V1 第 174-182 行）追加写只读历史。
-- 每次成功 PUT /skills/{skillId}/self-level 追加一行：首次自评 from_level 为 null（无前值），
-- 后续更新 from_level 为旧值、to_level 为新值。reason 持久化用户填写理由（nullable）。
-- idempotency_key 列存下用于追溯（PUT 端点的 Idempotency-Key 经全局 IdempotencyInterceptor 拦截重放，不重复写历史行）。
-- 追加写（append-only），不 UPDATE/DELETE，不回填 V30 前已发生的自评（只 V30 后的 PUT 才写历史）。
-- 不改变 user_skill.version 递增逻辑与三维度独立性（历史只跟踪 self_level，不动 evidence_status / interview_performance）。
CREATE TABLE user_skill_self_level_history (
  id TEXT PRIMARY KEY,
  user_skill_id TEXT NOT NULL REFERENCES user_skill(id),
  from_level INTEGER CHECK (from_level BETWEEN 0 AND 5),
  to_level INTEGER NOT NULL CHECK (to_level BETWEEN 0 AND 5),
  reason TEXT,
  idempotency_key TEXT,
  occurred_at TEXT NOT NULL
);

-- 支撑 GET /skills/{skillId}/self-level/history 按 user_skill_id 升序查询变更轨迹
CREATE INDEX idx_usself_history ON user_skill_self_level_history(user_skill_id, occurred_at);
