-- 为 audit_log 增加 action + occurred_at 复合二级索引，优化带 action 等值过滤的查询与导出。
-- 复合索引等值列 action 在前、范围/排序列 occurred_at 在后：SQLite 用 action 等值定位后，
-- 在同一 B-tree 节点内沿 occurred_at 反向扫描即可同时完成 ORDER BY occurred_at DESC 排序与
-- occurred_at >= ? / <= ? 范围过滤，一次定位 + 范围扫描 + 反向排序完成。
-- 与 V26 单列索引 idx_audit_log_occurred_at 互补：action 非空走复合索引，action 为空时退回单列索引。
-- V1 建表时无二级索引，V26 补单列、V28 补复合；本地单用户审计量小可接受，补复合索引面向未来数据增长。
-- 不新增表/列，不改 V1 DDL，不动既有迁移；仅追加此索引。
CREATE INDEX idx_audit_log_action_occurred_at ON audit_log(action, occurred_at);
