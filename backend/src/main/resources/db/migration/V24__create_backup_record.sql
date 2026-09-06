-- 加密备份记录（追加型只读历史，无状态/版本字段）。
-- salt + iv 落库以供未来恢复切片派生密钥；passphrase 与派生密钥永不持久化。
-- data_export_id 软引用 data_export.id，不加外键硬约束（与 resume_version 同范式）。
CREATE TABLE backup_record (
  id                 VARCHAR(36) PRIMARY KEY,
  created_at         VARCHAR(32) NOT NULL,
  algorithm          VARCHAR(32) NOT NULL,
  pbkdf2_iterations  INTEGER NOT NULL,
  salt               BLOB NOT NULL,
  iv                 BLOB NOT NULL,
  data_export_id     VARCHAR(36) NOT NULL,
  file_path          VARCHAR(512) NOT NULL,
  file_name          VARCHAR(256) NOT NULL,
  size_bytes         INTEGER NOT NULL
);

CREATE INDEX idx_backup_record_created_at ON backup_record(created_at);
