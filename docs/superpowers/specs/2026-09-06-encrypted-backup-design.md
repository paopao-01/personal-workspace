# 加密备份（手动触发）最小切片设计

> 对应 PRD §10 P2 / §19 V1.0「加密备份与跨设备同步」。本切片仅实现「手动触发 → 生成加密备份文件」，恢复与定时调度留作后续独立切片。

**Goal:** 用户在设置页输入 passphrase 后一键生成 AES-256-GCM 加密的数据备份文件，备份元数据落库可列表与下载。

**Architecture:** 新增 `backup` 领域模块。后端复用既有 `ExportService.create("JSON")` 生成标准数据包，读取其文件字节，用 PBKDF2（随机 16B salt、固定高迭代）从用户 passphrase 派生 AES-256-GCM 密钥加密，密文写入 `./data/backups/<id>.enc`；元数据存入新表 `backup_record`（Flyway V24）。passphrase 与派生密钥均不落盘、不回显。前端在既有设置页加「加密备份」区块。

**Tech Stack:** Spring Boot 3.3.5 / Java 21（`javax.crypto` 内置，无新依赖）、MyBatis、Flyway V24、React + Vite + TanStack Query、`openapi-typescript` 重新生成 `types.ts`。

**Spec 权威链:** 状态机 > OpenAPI > 数据库设计 > 页面规格 > 验收用例 > 技术实施 > PRD > AGENTS.md。本切片依次更新 OpenAPI → 数据库 → 页面 → 验收 → PRD → 状态机。

## 全局约束（摘自 AGENTS.md / PRD）

- 本地单用户，只监听 `127.0.0.1`，无登录/JWT/云同步/多租户。
- 写操作幂等（`Idempotency-Key`）；备份为追加型只读历史，无状态转换。
- UTC 保存时间；UI 按用户时区显示。
- 错误响应含稳定错误码与可理解信息。
- 已执行 Flyway 迁移不可修改，结构变更新增 V24。
- passphrase 等凭据仅写入不回显；不得在日志/响应/数据库中出现明文 passphrase 或派生密钥。
- 不提交数据库文件、备份产物（`.enc`）、真实数据。

---

## 1. OpenAPI 契约（03-openapi.yaml）

### 1.1 新增路径

```yaml
  /backups:
    get:
      tags: [Backups]
      summary: 查询加密备份记录列表（最新优先）
      responses:
        '200': { description: 备份记录列表, content: { application/json: { schema: { type: array, items: { $ref: '#/components/schemas/BackupRecord' } } } } }
    post:
      tags: [Backups]
      summary: 生成加密备份（复用标准数据包，AES-256-GCM 加密；passphrase 仅写入不回显）
      parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/BackupCreateRequest' } } }
      responses:
        '201': { description: 已生成, content: { application/json: { schema: { $ref: '#/components/schemas/BackupRecord' } } } }
        '400': { $ref: '#/components/responses/ValidationError' }
        '422': { $ref: '#/components/responses/BusinessRuleError' }
  /backups/{backupId}/download:
    parameters: [ { $ref: '#/components/parameters/BackupId' } ]
    get:
      tags: [Backups]
      summary: 下载加密备份文件（application/octet-stream）
      responses:
        '200': { description: 加密备份文件, content: { application/octet-stream: { schema: { type: string, format: binary } } } }
        '404': { $ref: '#/components/responses/NotFound' }
```

### 1.2 新增参数与 schema

```yaml
parameters:
  BackupId: { name: backupId, in: path, required: true, schema: { type: string, format: uuid } }

schemas:
  BackupCreateRequest:
    type: object
    required: [passphrase]
    properties:
      passphrase: { type: string, minLength: 8, maxLength: 256, description: 仅写入；用于 PBKDF2 派生 AES-256-GCM 密钥，永不回显或存储 }
  BackupRecord:
    type: object
    required: [id, createdAt, algorithm, pbkdf2Iterations, dataExportId, fileName, sizeBytes]
    properties:
      id: { type: string, format: uuid }
      createdAt: { type: string, format: date-time }
      algorithm: { type: string, enum: [AES_256_GCM_PBKDF2] }
      pbkdf2Iterations: { type: integer, minimum: 1 }
      dataExportId: { type: string, format: uuid, description: 关联的标准数据包导出 ID }
      fileName: { type: string, description: 备份文件名，如 <id>.enc }
      sizeBytes: { type: integer, description: 密文文件字节数 }
```

## 2. 数据库（04-database-design.md + V24 迁移）

新增 `backup_record` 表，纯追加历史，无版本/状态字段：

```sql
CREATE TABLE backup_record (
  id              VARCHAR(36) PRIMARY KEY,
  created_at      VARCHAR(32) NOT NULL,           -- UTC ISO-8601
  algorithm       VARCHAR(32) NOT NULL,            -- AES_256_GCM_PBKDF2
  pbkdf2_iterations INTEGER NOT NULL,
  salt            BLOB NOT NULL,                   -- 16 字节随机盐（恢复派生密钥所需）
  iv              BLOB NOT NULL,                  -- 12 字节 GCM IV
  data_export_id  VARCHAR(36) NOT NULL,           -- 关联 data_export.id（软引用，无外键硬约束）
  file_path       VARCHAR(512) NOT NULL,           -- ./data/backups/<id>.enc
  file_name       VARCHAR(256) NOT NULL,
  size_bytes      INTEGER NOT NULL
);
CREATE INDEX idx_backup_record_created_at ON backup_record(created_at);
```

- `salt`/`iv` 落库以便未来恢复切片能解密；passphrase 与派生密钥**永不**落库。
- `data_export_id` 为软引用，不加外键（与 `resume_version` 同范式，避免迁移约束）。
- 不存明文 passphrase、不存派生密钥、不存解密后的内容。

## 3. 状态机（02-state-machines.md）

新增 §9 备份记录状态：

> **9. 备份记录**
> 备份记录为追加型只读历史，无状态转换。生成后不可修改或删除（删除留待后续切片）。恢复操作为独立切片，本切片不实现。passphrase 经 PBKDF2 派生 AES-256-GCM 密钥，密钥与 passphrase 不落盘；`salt` 与 `iv` 随记录持久化以供未来恢复派生密钥。

## 4. 页面规格（01-page-spec.md）

设置页新增「加密备份」区（复用既有设置页路由，不新增导航项）：

- 显示「立即加密备份」按钮与 passphrase 输入框（type=password，minLength 8）。
- 点击按钮 → 提交 `POST /api/backups` → 成功后刷新备份列表，提示「已生成 <fileName>」。
- passphrase 输入框在提交后立即清空（仅写入不回显）。
- 备份列表（最新优先）：创建时间（用户时区）、文件名、大小、下载按钮（`GET /backups/{id}/download`）。
- 不显示 passphrase、不显示解密内容、不提供恢复入口（留待后续切片）。

## 5. 验收用例（05-acceptance-test-cases.md）

### AT-36 加密备份生成、列表与下载

```gherkin
Given 用户已登录态有可导出数据
When 用户在设置页「加密备份」区输入 passphrase "test1234" 并点击「立即加密备份」
Then 返回 201 且响应包含 algorithm=AES_256_GCM_PBKDF2、pbkdf2Iterations、dataExportId、fileName、sizeBytes
And 响应不包含 passphrase 字样
And ./data/backups/ 下生成 <id>.enc 文件，大小与 sizeBytes 一致
When 用户请求 GET /api/backups
Then 列表包含该记录且最新优先
When 用户请求 GET /api/backups/{id}/download
Then 返回 200 且响应体字节数等于 sizeBytes
When 用户输入 passphrase 短于 8 位
Then 返回 400 ValidationError
```

## 6. 后端模块设计

包路径 `com.jobhub.backup`，文件 8 个：

- `domain/BackupRecord.java` — 不可变 record（id, createdAt, algorithm, pbkdf2Iterations, salt[], iv[], dataExportId, filePath, fileName, sizeBytes）。
- `infrastructure/BackupRecordMapper.java` — MyBatis：`insert`、`selectList`、`selectById`。
- `application/EncryptionService.java` — 纯加密工具：`deriveKey(passphrase, salt, iterations)→SecretKey`（PBKDF2WithHmacSHA256, 256bit）、`encrypt(plaintext, passphrase)→EncryptedPayload{salt,iv,ciphertext}`（AES-256-GCM, 12B IV, 16B tag）。无状态、无副作用。
- `application/BackupService.java` — `create(passphrase)`：调 `ExportService.create("JSON")` → `readExportFile` → `EncryptionService.encrypt` → 写 `./data/backups/<id>.enc` → `BackupRecordMapper.insert`。`list()`、`get(id)`、`readFileBytes(record)`。
- `api/BackupCreateRequest.java` — record，`@Valid @NotBlank @Size(min=8,max=256) passphrase`。
- `api/BackupRecordResponse.java` — `from(domain)` 工厂，**不**输出 salt/iv（仅恢复所需，列表/详情不暴露；恢复切片再暴露）。输出 id/createdAt/algorithm/pbkdf2Iterations/dataExportId/fileName/sizeBytes。
- `api/BackupController.java` — `@RestController @RequestMapping("/api")`：`POST /backups`、`GET /backups`、`GET /backups/{backupId}/download`。
- `V24__create_backup_record.sql` — 建表（见 §2）。
- `src/test/java/com/jobhub/integration/BackupIntegrationTest.java` — AT-36 集成测试。

配置：`application.yml` 加 `jobhub.backup-dir: ${JOBHUB_BACKUP_DIR:./data/backups}`；PBKDF2 迭代次数常量 `100_000`（`EncryptionService` 内常量，可配置化留待后续）。

### 密文格式

`.enc` 文件 = `salt(16) || iv(12) || ciphertext+gcmTag`。`BackupService` 写入时按此布局；`EncryptionService.encrypt` 返回 salt+iv+ciphertext，由 BackupService 拼接写文件。salt/iv 同时存库（恢复用）。

### 依赖关系

- `BackupService` 依赖 `ExportService`（已有）、`EncryptionService`、`BackupRecordMapper`、`IdGenerator`、`UtcTime`、`@Value backupDir`。
- 不依赖 ExportService 内部 mapper；仅调公共方法 `create(format)` 与 `readExportFile(export)`。

## 7. 前端模块设计

- `src/api/backup/backupApi.ts` — `createBackup(passphrase)`、`listBackups()`、`downloadBackupUrl(id)`（返回下载 URL，用 anchor 下载）；含 `useBackupQueries.ts` 合并于此文件以控文件数。
- `src/features/settings/EncryptedBackupSection.tsx` — 受控 passphrase 输入 + 提交按钮 + 列表 + 下载；提交后清空 passphrase。
- `src/features/settings/SettingsPage.tsx` — 既有，插入 `<EncryptedBackupSection />`。
- `src/api/generated/types.ts` — 重新生成，不入库。

## 8. 文件清单与单窗口边界说明

规格 6 文件 + 后端 8 文件 + 前端 3 文件 + 测试 1 文件 + `application.yml` = **约 19 文件**，超出 MASTER_PROMPT 单窗口 ≤10 文件边界。用户已明确选择「含前端 UI」，本切片作为 V1.0 新模块的首个垂直切片，无法进一步拆分后端模块边界（领域/infra/application/api 四层是既有范式）。偏差如实记录于 `IMPLEMENTATION_STATUS.md`，下一窗口恢复单窗口边界（恢复切片）。

## 9. 错误处理

- passphrase 长度 <8 或 >256 → 400 ValidationError。
- `ExportService.create` 失败（导出任务 FAILED）→ 422 BusinessRuleError「数据包生成失败」。
- 加密/写盘异常 → 422 BusinessRuleError，不产生部分 backup_record。
- 下载不存在备份 → 404 NotFound。
- 不在日志打印 passphrase。

## 10. 测试策略

- `BackupIntegrationTest`：AT-36 主路径（创建 201 + 文件存在 + 列表 + 下载 + 短 passphrase 400）；断言响应不含 `passphrase`；断言 DB 无明文 passphrase。
- 前端：`npm run gen-types && typecheck && lint && build`；E2E `p1-encrypted-backup.spec.ts`（可选，时间允许时补）。
- 后端全量 `mvn clean test` 不回归。

## 11. 不做（YAGNI）

- 不做恢复（解密 + 重新导入）——下一切片。
- 不做定时调度——下一切片。
- 不做备份删除/清理——后续。
- 不做 passphrase 强度校验（仅长度）——后续。
- 不做密钥轮换、不做多密钥。
- 不做备份内容校验和（GCM 已提供完整性）。
