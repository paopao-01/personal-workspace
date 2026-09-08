-- 为 audit_log.occurred_at 增加二级索引，优化审计日志查询与导出的排序与时间范围过滤。
-- occurred_at 以 TEXT 存 UTC ISO（如 2026-09-07T13:00:00Z），ISO-8601 字典序与时间序一致，
-- 普通 B-tree 索引同时服务 ORDER BY occurred_at DESC（含反向扫描）与 occurred_at >= ? / <= ? 范围过滤。
-- V1 建表时未加索引，此前全表扫描；本地单用户审计量小可接受，本切片补索引以面向未来数据增长。
-- 不新增表/列，不改 V1 DDL，不动既有迁移；仅追加此索引。
CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
