-- 为 audit_log 增加 freed_bytes 列，把孤儿清理审计释放字节数从 reason 文本子串提升为结构化字段。
-- freed_bytes 存被删孤儿文件释放的字节数：BACKUP_ORPHAN_CLEANED 行写入被删文件 Files.size 值，
-- 其余 action 行为 NULL（与 before_snapshot_json 的 null 范式一致），供查询/导出直接取值无需解析 reason 文本。
-- 此前释放字节数以 freedBytes=N 子串嵌在 reason 文本里需前端正则解析，V27 起改为结构化列，
-- reason 去掉该子串为纯可读说明。历史行 freed_bytes 不回填（仅追加列、不回填，历史行该列为 NULL，
-- 本地单用户历史行极少且测试库每次 Flyway 重建无历史行）。
-- 不新增表，不动 V1 DDL 与 V1~V26 既有迁移；结构变更经新增版本完成，仅追加此列。
ALTER TABLE audit_log ADD COLUMN freed_bytes INTEGER;
