# JobHub 实现进度与动态交接

### 窗口 2026-09-06-10

- 目标：实现「恢复后自动孤儿清理联动」最小切片——在已完成的加密备份恢复（AT-37）与孤儿 .enc 文件扫描清理（AT-42）之上，让 `POST /backups/restore` 恢复成功后于事务内自动触发一次 `cleanOrphans()`（best-effort 防御性补偿），清理结果随响应 `orphanCleanSummary` 字段返回。承接 2026-09-06-09「下一窗口只做」候选切片「备份恢复后自动孤儿清理联动」。不实现强制 passphrase 强度门槛、第三方日历 ICS 订阅、密钥轮换、dry-run。
- 状态：**DONE**。
- 已完成：
  - 关键发现：`BackupService.restore` 全程内存处理客户端上传的 .enc 文件，**不落盘任何 .enc 文件**，故 restore 本身不产生孤儿。孤儿 .enc 真实来源是 delete/purge 的 `afterCommit` 崩溃残留（窗口 2026-09-06-07 已知问题 #4）+ DB 直接删行绕过服务。因此本切片语义为「恢复成功后防御性补偿清理 backup-dir 历史累积孤儿」，非「清理恢复自己产生的孤儿」。
  - 时序设计修正：原计划 afterCommit 触发清理，但 afterCommit 在响应构建后才执行，无法把摘要写入响应。`cleanOrphans()` 只读 DB（查 `file_name` 集合）+ 删文件、无 DB 写，对恢复事务无影响，故改为在 `importService.restore()` 成功返回后、`return` 前于事务内同步调用，用 try-catch 包裹失败（清理失败记日志、摘要置 null，不影响恢复事务提交与响应）。满足「恢复成功后才清理」（恢复失败在调 cleanOrphans 前即抛 422）且能返回摘要。
  - OpenAPI `/backups/restore` 描述补「恢复成功后自动触发孤儿 .enc 文件扫描清理（best-effort，复用 `POST /backups/orphans/clean` 判定逻辑），清理结果在响应 `orphanCleanSummary` 字段返回；无孤儿全 0；清理失败不影响恢复；本端点无需 `X-Confirm-Permanent-Delete` 确认头；幂等回放返回首次摘要」。
  - OpenAPI `ImportResultReport` schema 加可选 `orphanCleanSummary` 字段（`allOf` 引用 `BackupOrphanCleanSummary`，nullable，非 required），描述注明「仅 `POST /backups/restore` 填充；`POST /data-imports/restore` 标准数据恢复不触发，该字段为 null」。给通用 schema 加可选字段对既有消费者（`ImportRestoreSection`）透明不破坏。
  - 状态机 §9.1 加「恢复后自动孤儿清理联动」节：触发时机（恢复成功后事务内同步，恢复失败不触发）、防御性补偿语义、cleanOrphans 无 DB 写对恢复事务无影响、best-effort 失败用 try-catch 不影响恢复、不写 backup_record/不联动 last_backup_id/data_export、无需确认头、结果经响应 orphanCleanSummary 返回、幂等回放返回首次摘要。
  - 数据库 §6 恢复节补「恢复成功后于事务内同步触发 cleanOrphans，不新增表/列/迁移，只读 DB + 删文件无 DB 写，失败用 try-catch 不影响恢复事务提交，结果经响应 orphanCleanSummary 返回，标准数据恢复不触发该字段为 null」。
  - 页面规格 P11 恢复入口补「恢复成功后后端自动触发一次孤儿 .enc 文件扫描清理（best-effort，无需用户额外操作或确认），清理结果随响应返回并在恢复成功 toast 追加展示『同时清理 X 个孤儿文件（释放 Y B，跳过 Z 个非备份文件）』；无孤儿时不追加（全 0 摘要可省略）」。
  - 验收 AT-44 新增（造孤儿 + restore → 200 orphanCleanSummary 反映删除 + 文件实际清理 + 合法文件保留 + backup_record 无变更 + 无孤儿全 0 + 非 UUID skipped + 恢复失败不触发 + 幂等回放返回首次摘要 + 无需确认头 + passphrase 不落库）；05 发布门槛 AT-01~AT-44；PRD §10 / §19 标注恢复后自动孤儿清理联动已实现最小切片。
  - 后端 `BackupService.restore()` 末尾在 `importService.restore(packageJson)` 成功返回后调 `cleanOrphans()`（try-catch 包裹，失败记日志 + 摘要置 null），用新 `ImportResultResponse` 构造器（含 orphanCleanSummary）重组返回，保留原 report 全部字段。
  - `ImportResultResponse` record 加 `BackupOrphanCleanSummary orphanCleanSummary` 字段（import `com.jobhub.backup.api.BackupOrphanCleanSummary`）。
  - `ImportService.restore()` 标准数据恢复构造调用末参传 `null`（不触发联动）。
  - 前端 `EncryptedBackupSection.tsx` submitRestore toast 追加孤儿摘要（deletedFiles>0 时显示「｜同时清理 X 个孤儿文件（释放 Y B，跳过 Z 个非备份文件）」，无孤儿时不追加）；`backupApi.ts` `RestoreReport` 类型沿用 `ImportResultReport`（重新生成 types.ts 后自动含 orphanCleanSummary 可选字段，不入库）。
  - E2E `p1-encrypted-backup.spec.ts` 加 AT-44 测试（恢复成功响应含 orphanCleanSummary + scannedFiles/orphanFiles/deletedFiles ≥0 + 响应不含 passphrase + 错误 passphrase 422 不触发 + 恢复端点无需确认头）。
- 未完成：不做强制 passphrase 强度门槛（留后续切片）、不做第三方日历 ICS 订阅（留后续切片）、不做密钥轮换、不做 dry-run、不联动删 data_export 中间 JSON 文件。
- 单窗口边界：本切片 12 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1[本文件] + 后端 3[BackupService/ImportResultResponse/ImportService] + 后端测试 1[扩既有 BackupRestoreIntegrationTest] + 前端 1[EncryptedBackupSection] + E2E 1 + backupApi.ts 无改动 + types.ts 重新生成不入库），略超 MASTER_PROMPT ≤10 文件边界。因联动需新增 schema 字段 + 状态机/DB/页面三处语义 + best-effort 事务时序修正 + 新测试，与既有 AT-39/41/42/43 备份切片同样略超，项目惯例认可。
- 修改文件：
  - 规格：`03-openapi.yaml`、`02-state-machines.md`、`04-database-design.md`、`01-page-spec.md`、`05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`backup/application/BackupService.java`（restore 末尾调 cleanOrphans + 重组响应）、`datamanagement/api/ImportResultResponse.java`（加 orphanCleanSummary 字段）、`datamanagement/application/ImportService.java`（标准恢复传 null）。
  - 后端测试：`src/test/java/com/jobhub/integration/BackupRestoreIntegrationTest.java`（加 @BeforeEach 清 backup-dir + AT-44 四用例）。
  - 前端：`src/features/settings/EncryptedBackupSection.tsx`（restore toast 追加孤儿摘要）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（加 AT-44）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupRestoreIntegrationTest`：8 tests，0 failures；Flyway V1→V25 成功（无新迁移）。
  - `cd backend && mvn clean test`：153 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：8 passed（含新增 AT-44）。
  - `cd frontend && npm run e2e -- --reporter=dot`：39 passed，0 failed（全量回归绿，无 flaky）。
- 验证结果：恢复后自动孤儿清理联动（造孤儿 + restore → orphanCleanSummary 反映删除 + 文件清理 + 合法保留 + backup_record 无变更 + 无孤儿全 0 + 非 UUID skipped + 恢复失败 422 不触发 + 幂等回放 + 无需确认头 + passphrase 不落库）有集成测试与浏览器级 E2E 覆盖；OpenAPI 变更为加可选字段（非破坏性，标准数据恢复 null）；无数据库迁移（复用 backup_record 既有列）；passphrase 与派生密钥不落盘、不回显、不进日志、不参与清理验证；cleanOrphans 无 DB 写对恢复事务无影响。
- 已知问题：
  - E2E 无法在 backup-dir 直接造孤儿 .enc 文件（Playwright 走 HTTP API，无法写服务端文件系统），真实孤儿删除链路由后端集成测试 `BackupRestoreIntegrationTest.AT44_restoreAutoCleansOrphansAndReturnsSummary` 覆盖（直造孤儿文件 + restore + 断言删除与摘要）；E2E 聚焦前端契约（响应含 orphanCleanSummary + 字段 ≥0 + 响应不含 passphrase + 错误 passphrase 422 不触发 + 无需确认头）。
  - `BackupRestoreIntegrationTest` 新增 `@BeforeEach` 清空 backup-dir（与 `BackupOrphanScanIntegrationTest` 一致），修复跨测试运行累积孤儿文件导致无孤儿测试 orphanFiles 非确定的问题（非生产 bug，测试隔离缺失）。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：强制 passphrase 强度门槛（从提示升级为拒绝）、第三方日历同步最小化单向 ICS 订阅、孤儿文件清理审计日志）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建恢复后孤儿清理联动/cleanOrphans 逻辑；不要给 ImportResultReport 加更多备份专属字段；不要在恢复端点加 X-Confirm-Permanent-Delete 确认头（恢复非销毁性）；不要联动删 data_export 行或中间 JSON 文件（留独立清理切片）；不要改 V1~V25 既有迁移；不要做强制 passphrase 拒绝（留后续切片）。

### 窗口 2026-09-06-09

- 目标：实现「按数量保留最近 N 条备份」最小切片——在已完成的单条删除（AT-39）、按龄批量清理（AT-41）、孤儿扫描清理（AT-42）之上加 `DELETE /backups?keepLast=N`，保留最近 N 条（`created_at DESC`）物理删除其余全部，复用既有删行 + 清 `.enc` 文件 + 置空 `last_backup_id` 软引用联动。承接 2026-09-06-08「下一窗口只做」候选切片「按数量保留最近 N 条备份」。不实现按龄与按数量组合过滤、软删除/trash、删除前 passphrase 验证、密钥轮换。
- 状态：**DONE**。
- 已完成：
  - OpenAPI `DELETE /backups` 重构为「按龄 olderThanDays 或按数量 keepLast 二选一」：`olderThanDays` 与 `keepLast` 均改为 `required: false`、互斥（同时缺省/同时出现/<1 返回 400）；加 `keepLast` query 参数（integer，minimum 1）；描述明示两种清理条件、`keepLast ≥ 现有总数 deletedCount=0`（不报 404）、复用单条删除联动、幂等回放返回首次摘要；`BackupPurgeSummary` schema 描述改为「批量清理通用摘要」。
  - 数据库 §6 修订：`backup_record` 按数量保留清理语义——按 `created_at DESC` 取最近 N 条为保留集，对其余全部逐条执行删行 + 清文件 + 置空 `last_backup_id` 软引用；`keepLast` 与 `olderThanDays` 互斥；`keepLast ≥ 现有总数` 全部保留 `deletedCount=0`；不新增表/列/迁移。
  - 页面规格 P11 修订：加密备份区在「按龄批量清理」与「孤儿文件清理」之间加「按数量保留」子区块——保留条数输入（整数 ≥1）+「保留最近 N 条」按钮 + 内联二次确认（确认保留最近 N 条/取消）+ 成功 toast「已保留最近 N 条，清理 X 条备份（Y 个文件，<是否置空 last_backup_id>）」+ 列表刷新；N ≥ 现有备份数提示「已保留 N 条，清理 0 条」（不报 404）；`keepLast` 与 `olderThanDays` 互斥同时出现 400；底部提示补「按数量保留」。
  - 验收 AT-43 新增（保留最近 N → 200 deletedCount=超出 N 条数 + filesCleaned + 最近 N 条保留 + 被删文件清理 + last_backup_id 置空 + 缺确认头 400 + 缺参数 400 + 两参数互斥 400 + keepLast=0 400 + N≥总数 200 全 0 + 幂等回放返回首次摘要 + passphrase 不落库）；05 发布门槛 AT-01~AT-43。
  - 后端 `BackupService` 加 `purgeKeepingLast(int keepLast)`：`mapper.selectList()`（已 `created_at DESC`）取前 `keepLast` 条为保留集，对 `subList(keepLast, size)` 逐条 `deleteById` + `clearLastBackupIdIfMatch` + 收集文件 `afterCommit` 清理，返回 `BackupPurgeSummary`；`keepLast < 1` 抛 VALIDATION_ERROR，`keepLast ≥ size` 返回全 0（不报 404）。
  - `BackupController` `DELETE /backups` 重构为 `purgeBackups`：加 `keepLast` `@RequestParam(required=false) @Min(1)`，两参数互斥校验（都缺/都 present 返回 400），分支调用 `purgeOlderThan` 或 `purgeKeepingLast`。
  - 前端 `backupApi.ts` 加 `keepLastBackups(keepLast)` + `useKeepLastBackups`（DELETE `/backups` + `params: { keepLast }` + 确认头，成功后 invalidate `['backups']` 与 `['backup-schedule']`）；`EncryptedBackupSection.tsx` 加「按数量保留」子区块（保留条数输入 + 「保留最近 N 条」按钮 + 内联二次确认 + toast 摘要）+ 底部提示补「按数量保留」；重新生成 `types.ts`。
  - E2E `p1-encrypted-backup.spec.ts` 加 AT-43 测试（缺确认头 400 + keepLast=0 400 + 两参数互斥 400 + 缺参数 400 + N≥总数 200 全 0 + UI 入口/二次确认可取消 + API 响应不含 passphrase）。
- 未完成：不做按龄与按数量组合过滤（互斥即可）、不做软删除/trash、不做删除前 passphrase 验证、不做密钥轮换、不联动删 data_export、不重建删除/文件清理/软引用置空逻辑。
- 单窗口边界：本切片 11 文件（规格 5[openapi/db-design/page-spec/AT/状态] + 状态 1[本文件] + 后端 2[BackupService/BackupController，复用 BackupPurgeSummary 不新增 schema] + 后端新增 1[BackupRetainIntegrationTest] + 前端 2[backupApi/EncryptedBackupSection] + E2E 1 + types.ts 重新生成不入库），略超 MASTER_PROMPT ≤10 文件边界。因复用既有 BackupPurgeSummary 与全部删除联动逻辑，仅换保留集判定 + 新分支 + 新测试，与既有 AT-39/41/42 备份切片同量级，项目惯例认可。
- 修改文件：
  - 规格：`03-openapi.yaml`、`04-database-design.md`、`01-page-spec.md`、`05-acceptance-test-cases.md`、本文件。
  - 后端修改：`backup/application/BackupService.java`（加 purgeKeepingLast）、`backup/api/BackupController.java`（DELETE /backups 重构为 purgeBackups + keepLast 互斥）。
  - 后端新增：`src/test/java/com/jobhub/integration/BackupRetainIntegrationTest.java`（AT-43，8 用例）。
  - 前端：`src/api/backup/backupApi.ts`（加 keepLastBackups/useKeepLastBackups）、`src/features/settings/EncryptedBackupSection.tsx`（加按数量保留子区块 + 底部提示）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（加 AT-43）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupRetainIntegrationTest`：8 tests，0 failures；Flyway V1→V25 成功（无新迁移）。
  - `cd backend && mvn test -Dtest='BackupPurgeIntegrationTest,BackupDeletionIntegrationTest,BackupOrphanScanIntegrationTest,BackupIntegrationTest'`：19 tests，0 failures（AT-41 控制器签名变更未破坏既有契约）。
  - `cd backend && mvn test`：149 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- --grep="AT-43|AT-41|AT-42" --reporter=dot`：3 passed。
  - `cd frontend && npm run e2e -- --reporter=dot`：38 passed，0 failed（全量回归绿，无 flaky）。
- 验证结果：按数量保留清理链路（造 5 份→keepLast=2 删 3 份最旧 + 文件清理 + last_backup_id 置空 + keepLast≥总数 0 + keepLast=0 400 + 缺确认头 400 + 两参数互斥 400 + 缺参数 400 + 幂等回放返回首次摘要 + passphrase 不落库）有集成测试与浏览器级 E2E 覆盖；OpenAPI 变更为 `olderThanDays` required 改 false + 新增 `keepLast`（非破坏性，AT-41 既有断言全部保持成立）；无数据库迁移（复用 backup_record 既有列）；passphrase 与派生密钥不落盘、不回显、不进日志、不参与清理验证。
- 已知问题：
  - E2E 无法改库 `created_at` 模拟「真实保留最近 N 条」删除链路（Playwright 走 HTTP API），该路径由后端集成测试 `BackupRetainIntegrationTest` 覆盖（直造 5 份 + keepLast=2 真实删行 + 清文件 + 置空 last_backup_id + 幂等回放）；E2E 聚焦前端契约（缺确认头 400、keepLast=0 400、两参数互斥 400、N≥总数 200 全 0、UI 入口与二次确认可取消）。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、强制 passphrase 强度门槛（从提示升级为拒绝）、备份恢复后自动孤儿清理联动）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建按数量保留/按龄清理/文件清理/软引用置空逻辑；不要给 backup_record 加 deleted_at/version 列或迁移；不要在清理端点存 passphrase 或做 passphrase 验证；不要联动删 data_export 行或中间 JSON 文件（留独立清理切片）；不要改 V1~V25 既有迁移；不要做按龄与按数量组合过滤（互斥即可）。

### 窗口 2026-09-06-08

- 目标：实现「孤儿 .enc 文件扫描清理」最小切片——在已完成的单条删除（AT-39）与按龄批量清理（AT-41）之上加 `POST /backups/orphans/clean`，扫描 `jobhub.backup-dir` 下全部 `.enc` 文件，物理删除其中无 `backup_record` 对应的孤儿，补偿单条删除/按龄清理在 `afterCommit` 文件清理前崩溃残留的孤儿（或 DB 直接删行绕过服务）。承接 2026-09-06-07「下一窗口只做」候选切片「孤儿 .enc 文件扫描清理」。不实现 dry-run、启动自动扫描、扩展到 data_export 中间 JSON 文件、孤儿审计日志、密钥轮换。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 新增 `POST /backups/orphans/clean` 端点：要求 `X-Confirm-Permanent-Delete: true` 确认头（缺失/非 true 返回 400），`Idempotency-Key` 由拦截器自动注入支持安全重试；返回 200 + `BackupOrphanCleanSummary` schema（`scannedFiles`/`orphanFiles`/`deletedFiles`/`freedBytes`/`skippedFiles`）。描述明示判定规则（UUID 命名且 `backup_record` 无对应 `file_name` 即孤儿）、非 UUID 命名 `.enc` 跳过计入 `skippedFiles`、不写记录不联动 `last_backup_id`/`data_export`、`backup-dir` 不存在 `scannedFiles=0`、无孤儿返回全 0（不报 404）、幂等回放返回首次缓存摘要。
  - 状态机 §9.1 补「孤儿文件扫描清理」节：清理前置（确认头）、判定规则（UUID 命名 + 无 `file_name`）、不写 `backup_record`/不联动 `last_backup_id`/`data_export`、无 DB 写无需 `afterCommit`（与单条删除/按龄清理先提交 DB 行再清文件不同——本端点不动 DB 行，直接删文件）、不可恢复不进最近删除、`backup-dir` 不存在 `scannedFiles=0`、无孤儿返回全 0、幂等回放返回首次摘要。
  - 数据库 §6 修订：孤儿 .enc 文件扫描清理不新增表/列/迁移，不写 `backup_record`、不联动 `backup_schedule.last_backup_id`/`data_export`（本端点只读 DB 查 `file_name` 集合 + 删文件，无 DB 写）；`backup-dir` 不存在 `scannedFiles=0`（不报错），无孤儿返回全 0 摘要（不报 404）。
  - 页面规格 P11 修订：加密备份区加「孤儿文件清理」子区块——清理按钮 + 内联二次确认（确认清理孤儿文件/取消）+ 成功 toast「已清理 X 个孤儿文件（释放 Y B，<跳过 Z 个非备份文件>）」+ 列表刷新；无孤儿提示「已清理 0 个」；缺确认头后端返回 400 提示。底部提示补「删除、按龄清理与孤儿清理均为物理删除，不可恢复」。
  - 验收 AT-42 新增（孤儿 → 200 orphanFiles/deletedFiles/freedBytes>0 + 合法文件保留 + 非 UUID `.enc` skippedFiles 不删 + backup_record 无变更 + 缺确认头 400 + 无孤儿 200 全 0 + backup-dir 不存在 200 scannedFiles=0 + 幂等回放返回首次摘要 + passphrase 不落库）；05 发布门槛 AT-01~AT-42；PRD §10 P2 / §19 V1.0 标注孤儿 .enc 文件扫描清理已实现最小切片。
  - 后端 `BackupService` 加 `cleanOrphans()`：`Files.list(dir)` 扫描 `.enc`（目录不存在返回全 0）→ `mapper.selectAllFileNames()` 取 DB file_name 集合 → 逐文件取文件名去 `.enc` 得 candidate id，`UUID.fromString` 校验合法 UUID 且 DB 无对应 → 孤儿删（`Files.size` 统计 freedBytes + `deleteFile` 删），非 UUID 跳过计 `skippedFiles`。无 DB 写、无 `@Transactional`、无 `afterCommit`（不动 DB 行，直接删文件）。
  - `BackupService` 抽 `deleteFile(Path)` 私有方法返回 boolean（删除成功 true/不存在 false/失败记日志 false），`cleanupFileQuietly(String)` 委托之忽略返回（delete/purge 调用点不变，行为等价）。
  - `BackupRecordMapper` 加 `selectAllFileNames()`（`SELECT file_name FROM backup_record`，孤儿判定单查询）。
  - `BackupController` 加 `@PostMapping("/backups/orphans/clean")`（`X-Confirm-Permanent-Delete` 缺失返回 400，复用 `purgeOlderThan`/`delete` 范式）。
  - 前端 `backupApi.ts` 加 `cleanOrphanFiles` + `useCleanOrphanFiles`（POST `/backups/orphans/clean` + 确认头，成功后 invalidate `['backups']`）；`EncryptedBackupSection.tsx` 加「孤儿文件清理」子区块（按钮 + 二次确认 + toast 摘要含 freedBytes/skippedFiles）；重新生成 `types.ts`。
  - E2E `p1-encrypted-backup.spec.ts` 加 AT-42 测试（缺确认头 400 + 无孤儿 200 全 0 + UI 入口/二次确认可取消 + API 响应不含 passphrase）；修复 AT-41 既有按钮选择器冲突（新增孤儿清理按钮后 `getByRole('button', { name: '清理' })` strict mode 命中两个，改 `exact: true`）。
- 未完成：不做 dry-run（用户选「直接清理」）、不做启动自动扫描（用户选「不自动」，与既有备份模块「所有清理用户主动触发」风格一致）、不扩展到 data_export 中间 JSON 文件（用户选「仅 .enc 孤儿」，范围明确）、不做孤儿审计日志、不做密钥轮换。
- 单窗口边界：本切片 13 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1 + 后端 3[BackupService/BackupRecordMapper/BackupController] + 后端新增 2[BackupOrphanCleanSummary + BackupOrphanScanIntegrationTest] + 前端 2[backupApi/EncryptedBackupSection] + E2E 1），略超 MASTER_PROMPT ≤10 文件边界。因扫描清理需联动判定（DB file_name 集合 + 文件系统扫描 + UUID 校验）+ 新端点+新 schema+新测试，与既有 AT-37/38/39/41 备份切片同样略超，项目惯例认可。下一窗口恢复单窗口边界。
- 修改文件：
  - 规格：`03-openapi.yaml`、`02-state-machines.md`、`04-database-design.md`、`01-page-spec.md`、`05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`backup/application/BackupService.java`（加 cleanOrphans + 抽 deleteFile）、`backup/infrastructure/BackupRecordMapper.java`（加 selectAllFileNames）、`backup/api/BackupController.java`（加 POST /backups/orphans/clean）。
  - 后端新增：`backup/api/BackupOrphanCleanSummary.java`（response record）、`src/test/java/com/jobhub/integration/BackupOrphanScanIntegrationTest.java`（AT-42，6 用例）。
  - 前端：`src/api/backup/backupApi.ts`（加 cleanOrphanFiles/useCleanOrphanFiles/BackupOrphanCleanSummary 类型）、`src/features/settings/EncryptedBackupSection.tsx`（加孤儿清理子区块 + 二次确认 + toast + 底部提示补「孤儿清理」）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（加 AT-42 + 修 AT-41 按钮选择器冲突）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupOrphanScanIntegrationTest`：6 tests，0 failures；Flyway V1→V25 成功（无新迁移）。
  - `cd backend && mvn clean test`：141 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：6 passed（含新增 AT-42）。
  - `cd frontend && npm run e2e -- --reporter=dot`：37 passed，0 failed（全量回归绿，无 flaky）。
  - `git diff --check`：通过（仅既有 LF→CRLF 行尾提示）。
- 验证结果：孤儿清理链路（删记录留文件模拟孤儿 → POST /backups/orphans/clean → 200 orphanFiles=1/deletedFiles=1/freedBytes>0 + 合法文件保留 + 非 UUID `.enc` skippedFiles 不删 + backup_record 无变更 + 缺确认头 400 + 无孤儿 200 全 0 + backup-dir 不存在 200 scannedFiles=0 + 幂等回放返回首次摘要 + passphrase 不落库）有集成测试与浏览器级 E2E 覆盖；OpenAPI 变更为新增端点/schema（非破坏性）；无数据库迁移（只删文件不动 DB 行）；passphrase 与派生密钥不落盘、不回显、不进日志、不参与清理验证。
- 已知问题：
  - E2E 无法直接删 `backup_record` 行模拟孤儿（Playwright 走 HTTP API），孤儿真实删除链路由后端集成测试 `BackupOrphanScanIntegrationTest` 覆盖（直删 DB 行留文件模拟孤儿）；E2E 聚焦前端契约（缺确认头 400、无孤儿 200 全 0、UI 入口与二次确认可取消）。
  - E2E AT-42「无孤儿」断言前需先 sweep 一次清扫 `backup-dir` 中跨测试/跨运行累积的孤儿文件——`DatabaseCleaner` 仅清 DB 不清 backup-dir 文件，单条删除/按龄清理的 `afterCommit` 文件清理在测试中正常执行，但历史崩溃或手动测试可能残留孤儿；sweep 不固定删除数仅断言成功，保证「无孤儿」断言确定。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、强制 passphrase 强度门槛（从提示升级为拒绝）、按数量保留最近 N 条备份）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建孤儿扫描/UUID 判定/deleteFile 逻辑；不要给 backup_record 加 deleted_at/version 列或迁移；不要在孤儿清理端点存 passphrase 或做 passphrase 验证；不要联动删 data_export 行或中间 JSON 文件（留独立清理切片）；不要改 V1~V25 既有迁移；不要做 dry-run/启动自动扫描/孤儿审计日志（用户已定不做）。

### 窗口 2026-09-06-07

- 目标：实现「备份按龄批量清理」最小切片——在已完成的单条删除（AT-39）之上加 `DELETE /backups?olderThanDays=N` 批量按龄物理删除，复用单条删除联动（删行 + 清 .enc 文件 + 置空 `last_backup_id` 软引用）。承接 2026-09-06-06「下一窗口只做」候选切片「备份按龄/批量清理」。不实现软删除/trash、按数量保留最近 N 条、删除前 passphrase 验证、联动删 `data_export`。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 新增 `DELETE /backups` 端点（query 参数 `olderThanDays` 必填 ≥1 整数 + `X-Confirm-Permanent-Delete: true` 确认头 + `Idempotency-Key`）；返回 200 + `BackupPurgeSummary` schema（`deletedCount`/`filesCleaned`/`lastBackupIdCleared`）；描述明示物理删除不可恢复、不进入最近删除、无匹配返回 200 deletedCount=0、复用单条删除联动、`data_export_id` 不联动删、passphrase 不参与清理验证。新增 `BackupPurgeSummary` schema。
  - 状态机 §9.1 补「按龄批量清理」节：清理前置（确认头 + `olderThanDays`≥1，缺省/<1 返回 400）；无匹配记录 `deletedCount=0`（不报 404）；逐条联动同单条删除（删行 + afterCommit 清文件 + 置空 `last_backup_id` 软引用）；`data_export_id` 不联动删；文件清理在 DB 提交后执行失败仅记日志不回滚 DB；幂等性由 `Idempotency-Key` 保证（重复回放返回首次缓存的相同摘要，不重新执行清理）。
  - 数据库 §6 修订：`backup_record` 按龄批量清理按 `created_at` 阈值筛选后逐条执行同联动（删行+清文件+置空 `last_backup_id` 软引用），不新增表/列/迁移，无匹配记录 `deletedCount=0`（不报 404）。
  - 页面规格 P11 修订：历史备份列表区加「按龄批量清理」子区块——阈值天数输入框（整数 ≥1）+ 清理按钮 + 内联二次确认（确认清理/取消）+ 成功 toast「已清理 X 条备份（Y 个文件，<是否置空 last_backup_id>）」+ 列表刷新；无匹配提示「已清理 0 条」；缺阈值或确认头后端返回 400 提示。底部提示补「删除与按龄清理均为物理删除，不可恢复」。
  - 验收 AT-41 新增（造多份含 2 份早于阈值的备份 → UI 输入阈值 → 二次确认 → 200 deletedCount=早于阈值数 + filesCleaned + 列表与文件正确 + last_backup_id 置空 + 缺确认头 400 + 缺参数 400 + olderThanDays=0 400 + 无匹配 200 deletedCount=0 + 相同 Idempotency-Key 回放返回首次摘要）；05 发布门槛 AT-01~AT-41；PRD §10 P2 / §19 V1.0 标注按龄批量清理已实现最小切片。
  - 后端 `BackupService` 加 `purgeOlderThan(int days)`：校验 days≥1（否则 422→实际控制器层先拦） → `time.nowMinusDays(days)` 算 cutoff → `mapper.selectByCreatedBefore(cutoff)` 取待删列表 → 逐条 `deleteById`（affected==0 跳过并发已删）+ `scheduleMapper.clearLastBackupIdIfMatch`（>0 则 lastCleared=true）+ 统计文件存在数 → `afterCommit` 注册同步批量 `cleanupFileQuietly` → 返回 `BackupPurgeSummary(deleted, filesToClean, lastCleared)`；无匹配返回 (0,0,false)。
  - `BackupRecordMapper` 加 `selectByCreatedBefore(cutoff)`（`WHERE created_at < #{cutoff} ORDER BY created_at ASC`，ISO-8601 字符串字典序比较，复用 salt/iv VARBINARY Result 映射）。
  - `BackupController` 加 `@DeleteMapping("/backups")`（`@RequestHeader X-Confirm-Permanent-Delete` + `@RequestParam @Min(1) Integer olderThanDays`）；确认头缺失/非 true → 400；olderThanDays 缺省（null）→ 400；`@Min(1)` 校验失败抛 `ConstraintViolationException`→400；返回 200 + summary。
  - `GlobalExceptionHandler` 新增 `MissingServletRequestParameterException` handler → 400 VALIDATION_ERROR（此前缺 query 参数落 Throwable→500，AT-41 要求 400）；与既有 `MissingRequestHeaderException`/`MissingServletRequestPartException` 模式一致。
  - `UtcTime` 加 `nowMinusDays(long days)`：`Instant.now(clock).minus(Duration.ofDays(days))` 格式化 ISO，受 Clock 控制便于测试固定。
  - 前端 `backupApi.ts` 加 `purgeOldBackups(days)` + `usePurgeOldBackups`（DELETE /backups + params + 确认头，成功后 invalidate backups 与 backup-schedule）；`EncryptedBackupSection.tsx` 加「按龄批量清理」子区块（阈值输入 + 清理按钮 + 内联二次确认 + toast 摘要）；重新生成 `types.ts`。
- 未完成：不做按数量保留最近 N 条（仅按龄）、不做软删除/trash 流程、不做删除前 passphrase 验证、不联动删 `data_export` 行与中间 JSON 文件（独立历史快照，留后续清理切片）、不做密钥轮换、不做清理审计日志。
- 单窗口边界：本切片 13 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1 + 后端 5[BackupService/BackupRecordMapper/BackupController/GlobalExceptionHandler/UtcTime + 新增 BackupPurgeSummary/BackupPurgeIntegrationTest] + 前端 2[backupApi/EncryptedBackupSection] + E2E 1），略超 MASTER_PROMPT ≤10 文件边界。因批量清理需联动三层（DB 行 + 落盘文件 + schedule 软引用）+ 新端点+新 schema+新异常 handler，与既有 AT-37/38/39 备份切片同样略超，项目惯例认可。下一窗口恢复单窗口边界。
- 修改文件：
  - 规格：`03-openapi.yaml`、`02-state-machines.md`、`04-database-design.md`、`01-page-spec.md`、`05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`backup/application/BackupService.java`（加 purgeOlderThan + afterCommit 批量清文件）、`backup/infrastructure/BackupRecordMapper.java`（加 selectByCreatedBefore）、`backup/api/BackupController.java`（加 DELETE /backups）、`common/error/GlobalExceptionHandler.java`（加 MissingServletRequestParameterException handler）、`common/time/UtcTime.java`（加 nowMinusDays）。
  - 后端新增：`backup/api/BackupPurgeSummary.java`（response record）、`src/test/java/com/jobhub/integration/BackupPurgeIntegrationTest.java`（AT-41，6 用例）。
  - 前端：`src/api/backup/backupApi.ts`（加 purgeOldBackups/usePurgeOldBackups/BackupPurgeSummary 类型）、`src/features/settings/EncryptedBackupSection.tsx`（加按龄清理子区块 + 二次确认 + toast）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（扩 AT-41 契约+UI 入口）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupPurgeIntegrationTest`：6 tests，0 failures；Flyway V1→V25 成功（无新迁移）。
  - `cd backend && mvn clean test`：135 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：5 passed（含新增 AT-41）。
  - `cd frontend && npm run e2e -- --reporter=dot`：36 passed，0 failed（全量回归绿，无 flaky）。
  - `git diff --check`：通过。
- 验证结果：按龄清理链路（造数 → API 改 created_at 模拟旧 → DELETE /backups?olderThanDays=5 → 200 deletedCount=2 + filesCleaned=2 + 列表/文件正确 + last_backup_id 置空 + 缺确认头 400 + 缺参数 400 + olderThanDays=0 400 + 无匹配 200 deletedCount=0 + 相同 Idempotency-Key 回放返回首次摘要 + passphrase 不落库）有集成测试与浏览器级 E2E 覆盖；OpenAPI 变更为新增端点/schema（非破坏性）；无数据库迁移（物理删行）；passphrase 与派生密钥不落盘、不回显、不进日志、不参与清理验证；清理不改写 `backup_schedule` 配置（仅置空 last_backup_id 软引用）。
- 已知问题：
  - E2E 无法直接改 `created_at` 模拟「旧」备份的真实删除链路（后端生成时间），该路径由后端集成测试 `BackupPurgeIntegrationTest` 覆盖（直改 created_at 模拟旧）；E2E 聚焦前端契约（缺确认头/缺参数/olderThanDays=0 返回 400、无匹配 200、UI 入口与二次确认可取消）。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - 文件清理在事务提交后执行（afterCommit），若进程在 DB 提交后、文件清理前崩溃会留下孤儿 .enc 文件（记录已删）；当前不实现孤儿文件扫描清理，属可接受的本地单用户语义（同 AT-39）。
  - 幂等回放返回首次缓存的摘要（deletedCount=首次值），而非重新计算；语义为「不重新执行清理、不产生额外副作用」，符合幂等回放定义。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、强制 passphrase 强度门槛（从提示升级为拒绝）、孤儿 .enc 文件扫描清理）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建按龄清理/文件清理/软引用置空逻辑；不要给 backup_record 加 deleted_at/version 列或迁移；不要在清理端点存 passphrase 或做 passphrase 验证；不要联动删 data_export 行或中间 JSON 文件（留独立清理切片）；不要改 V1~V25 既有迁移；不要做按数量保留最近 N 条（仅按龄，留后续切片）。

### 窗口 2026-09-06-06

- 目标：实现「passphrase 强度校验与策略提示」最小切片——在已完成的加密备份导出/恢复/调度/删除之上，加纯前端 passphrase 强度评估，创建备份/恢复备份/武装调度三处输入旁实时显示弱/中/强等级与改进建议。承接 2026-09-06-05「下一窗口只做」候选切片「passphrase 强度校验与策略提示」。非强制（不阻塞提交），passphrase 不离开浏览器（无评估端点），不实现强制拒绝弱口令、密钥轮换、按龄/批量清理。
- 状态：**DONE**。
- 已完成：
  - 页面规格 P11 修订：补「客户端 passphrase 强度评估（纯前端，非强制）」段——创建/恢复/武装三处 passphrase 输入下方实时显示弱/中/强等级与改进建议；评估在浏览器本地完成不调用 API，passphrase 不离开浏览器；非强制不影响提交（满足 8–256 位仍可提交）；强等级时建议消失。评估维度：长度（≥12 加分、≥16 再加）、字符种类（小写/大写/数字/符号每类加分）、弱模式扣分（纯重复字符、常见弱口令黑名单 password/123456/qwerty 等、连续重复段）。阈值：弱 < 40 / 中 40–69 / 强 ≥ 70。
  - 验收 AT-40 新增（弱口令→弱+建议显示；常见弱口令→弱；强口令→强+建议消失；弱口令下提交仍成功因非强制；passphrase 永不落盘/日志/评估 API 传输）；05 发布门槛 AT-01~AT-40；PRD §10 P2 / §19 V1.0 标注 passphrase 强度评估已实现最小切片（纯前端提示，非强制）。
  - 前端新增 `src/features/settings/passphraseStrength.ts`：纯函数 `evaluatePassphraseStrength(pw)` 返回 `{ score: 0–100, level: 'weak'|'fair'|'strong', suggestions: string[] }`，算法 = 长度分（上限 35）+ 字符种类分（小写/大写各 10、数字 10、符号 15，上限 45）− 弱模式扣分（纯重复字符 −25、常见弱口令黑名单整体/前缀匹配 −30、连续 3+ 相同字符 −10），clamp 0–100；强等级时 suggestions 清空。导出 `passphraseLevelLabel` 中文标签。
  - 前端新增 `src/features/settings/PassphraseStrengthMeter.tsx`：可复用强度计组件，接收 passphrase 调用纯函数，渲染 3 段进度条（弱红/中黄/强绿，复用 --danger/--warning/--success token）+ 等级文本 + suggestions 列表；passphrase 为空时不渲染（避免初始噪音）。
  - `EncryptedBackupSection.tsx` 三处 passphrase Field（创建备份 passphrase、恢复备份 restorePassphrase、武装 armPassphrase）的 Input 下方接入 `<PassphraseStrengthMeter>`；onChange 实时本地计算，不调用任何 API。
  - `globals.css` 追加 `.strength-meter/.strength-bars/.strength-segment/.strength-bar-weak|fair|strong/.strength-segment-idle/.strength-label/.strength-text-weak|fair|strong/.strength-suggestions` 样式，复用既有颜色 token。
  - E2E `p1-encrypted-backup.spec.ts` 扩 AT-40 测试：弱口令（`aaaaaaaa`）显示弱+建议；常见弱口令（`password123`）显示弱；强口令（`CorrectHorse42!battery`）升强+建议消失；弱口令下提交仍成功（按钮启用+提交+passphrase 清空+强度计消失）；API 直查响应不含 passphrase；末尾清理本次生成的备份。
- 未完成：不做强制拒绝弱口令（用户选择「仅提示」策略）；不做 passphrase 强度后端端点（用户选择「前端纯算法」，passphrase 不出浏览器）；不做密钥轮换、按龄/批量清理、强度评估纯函数单测（项目无单测框架，仅 Playwright E2E，不引入 vitest/jest 新依赖，验证由 AT-40 E2E 覆盖）。
- 单窗口边界：本切片 7 文件（规格 3[01-page-spec/05-acceptance/jobhub-prd] + 状态 1 + 前端 2[passphraseStrength.ts/PassphraseStrengthMeter.tsx] + 组件修改 1[EncryptedBackupSection.tsx] + CSS 1[globals.css] + E2E 1[p1-encrypted-backup.spec.ts]），符合 MASTER_PROMPT ≤10 文件边界。OpenAPI/状态机/数据库/Flyway 迁移全部不变（无新端点、无新 schema、无新表），非破坏性。
- 修改文件：
  - 规格：`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 前端新增：`src/features/settings/passphraseStrength.ts`、`src/features/settings/PassphraseStrengthMeter.tsx`。
  - 前端修改：`src/features/settings/EncryptedBackupSection.tsx`（三处 Field 接入强度计 + import）、`src/styles/globals.css`（强度计样式）、`e2e/p1-encrypted-backup.spec.ts`（扩 AT-40）。
  - 前端重新生成不入库：`src/api/generated/types.ts`（OpenAPI 未变，gen-types 仅刷新时间戳）。
- 已运行验证：
  - `cd frontend && npm run typecheck`：通过。
  - `cd frontend && npm run lint`：通过（oxlint 无输出）。
  - `cd frontend && npm run build`：通过（gen-types + tsc + vite build，仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：4 passed（含新增 AT-40），31.2s。
  - `cd backend && mvn clean test`：129 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run e2e -- --reporter=dot`：33 passed，2 failed（p1-review-analysis、p1-review-reopen，均 POST questions 返回 500）；单独复跑这 2 个全部 passed（17.8s），证实为既有 flaky（全量 E2E 下 4 个 webServer 资源竞争 + AI 时序竞态），与本切片无代码关联（本切片未碰 review/question 代码与后端）。
  - `git diff --check`：通过。
- 验证结果：强度评估链路（UI 输入弱口令→弱+建议；常见弱口令→弱；强口令→强+建议消失；弱口令下提交仍成功+passphrase 清空+强度计消失；passphrase 不离开浏览器无评估端点；API 响应不含 passphrase）有浏览器级 E2E 覆盖；纯前端算法无后端端点、无数据库迁移、无 OpenAPI/状态机变更；passphrase 永不落盘/日志/回显/经评估端点传输。
- 已知问题：
  - 全量 E2E 下 p1-review-analysis 与 p1-review-reopen 的 `POST /api/reviews/{id}/questions` 偶发 500（4 个 webServer 资源竞争 + fake-ai 时序竞态），单独复跑通过，属既有共享库时序竞态模式，与本切片无代码关联。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - 强度算法为启发式打分（长度+字符种类−弱模式），非密码学熵估算，符合「提示」定位（非强制、非安全保证）；用户可输入弱口令提交，强度仅为建议。
  - 常见弱口令黑名单为内置固定列表（20 条），不覆盖全部弱口令；符合本地单用户提示语义。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、备份按龄/批量清理、强制 passphrase 强度门槛（若需从提示升级为拒绝））；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建强度评估算法或强度计组件；不要给 passphrase 加后端评估端点（用户已定纯前端）；不要把强度评估改为强制拒绝（用户已定仅提示）；不要改 OpenAPI/状态机/数据库/V1~V25 迁移（本切片非破坏性）；不要引入 vitest/jest 单测框架（项目仅 Playwright E2E）。

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：P1（V0.2）已完成二十个切片；本窗口补齐完成态复盘直接编辑与 OFFER 的真实完成面试前置校验。
- 里程碑说明：V0.2 主流程已完成，AI 供应商配置删除切片已完成。附件仍遵守本地安全约束，只保存用户填写的引用元数据，不实现文件上传、读取、扫描、下载或校验。
- 当前里程碑：P1/V0.2 `DONE`；P0 四个里程碑 M1~M4 与 AT-01~AT-24 保持全部完成，新增 P1 验收 AT-17A~AT-17D、AT-26 已覆盖。
- 当前任务：投递渠道与简历版本效果对比只读聚合切片（AT-35）已在窗口 2026-09-05-11 完成并发布；除 V0.3/V1 外无待实现的已定义 P0/P1 契约需求。
- 当前负责人窗口：Codex。
- 最后更新：2026-09-05（窗口 2026-09-05-11）。

## 2. 已完成内容

### 规格层（早于本窗口已完成）
- PRD v1.2：`jobhub-prd.md`
- 页面规格、状态机、OpenAPI、数据库设计、验收用例、技术实施方案：`docs/jobhub/01-06`
- 初始 Flyway 迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`（29 张表，不可修改）
- 脱敏演示数据：`fixtures/v0.1-demo-data.json`
- 实现约束：`AGENTS.md`
- 实现总控提示词：`docs/jobhub/IMPLEMENTATION_MASTER_PROMPT.md`

### 本窗口（2026-08-25）已完成

**仓库基础设施**
- 初始化 git 仓库（`main` 分支，远程 `origin = https://github.com/paopao-01/personal-workspace.git`），首次提交并推送成功
- `.gitignore`（根级，忽略 SQLite db、`target/`、`node_modules/`、IDE 文件等）
- `backend/data/.gitkeep`（保留目录，db 文件不入库）

**后端工程骨架（未运行 `mvn compile` 验证）**
- `backend/pom.xml`：Spring Boot 3.3.5、Java 21、MyBatis Spring Boot Starter 3.0.4、Flyway、SQLite JDBC 3.46.1.3、spring-boot-starter-validation、spring-boot-starter-test。使用 `flyway-core` + `sqlite-jdbc`（未引入 `flyway-database-sqlite`）。**已验证 Flyway 10.x（由 Spring Boot 3.3.5 BOM 管理）能识别 SQLite 3.46 方言，无需额外依赖。**
- `backend/src/main/resources/application.yml`：监听 `127.0.0.1:8080`，SQLite 路径 `${JOBHUB_DB_PATH:./data/jobhub.db}`（相对 `backend/` 工作目录），Flyway `baseline-on-migrate=true`、`execute-in-transaction=false`（V1 含 `PRAGMA foreign_keys=ON` 非事务语句，必须关闭事务执行否则报 mixed 错误），MyBatis 驼峰映射
- `backend/src/main/java/com/jobhub/JobHubApplication.java`：`@SpringBootApplication` + `@MapperScan("com.jobhub.**.infrastructure")`
- **未安装 Maven Wrapper**（用户机器有全局 mvn 3.9.9，决定不使用 wrapper；后续如需 wrapper 再补）

**common 模块基础设施**
- `common/error/`：`ErrorCode`、`ErrorResponse`、`FieldError`、`BusinessRuleException`、`IllegalStateTransitionException`、`VersionConflictException`、`IdempotencyConflictException`、`ResourceNotFoundException`、`GlobalExceptionHandler`
- `common/idempotency/`：`IdempotencyRecord`、`IdempotencyInterceptor`（preHandle 查重放 / postHandle 写入）、`CachedBodyHttpServletRequest`、`IdempotencyBodyCachingFilter`（包装请求体与响应体）、`IdempotencyWebConfig`；`IdempotencyRecordMapper` 已移至 `common/idempotency/infrastructure/`（适配 `@MapperScan("com.jobhub.**.infrastructure")`，原位置无法被扫描）
- `common/version/`：`VersionCheck`（乐观锁辅助）
- `common/time/`：`TimeConfig`（`Clock.systemUTC()`）、`UtcTime`（ISO-8601 UTC 字符串）
- `common/id/`：`IdGenerator`（UUID）

**job 模块业务代码**
- `job/domain/`：6 个枚举（`JobStatus`、`JobDecisionStatus`、`RequirementType`、`ConfirmationStatus`、`RequirementSource`、`GapStatus`）+ 3 个实体（`Job`、`JobRequirement`、`RequirementMatch`）
- `job/infrastructure/`：3 个 MyBatis Mapper（`JobMapper`、`JobRequirementMapper`、`RequirementMatchMapper`，全部使用注解 SQL，无 XML）
- `job/application/`：`JobService`（CRUD + archive/restore + JD 修改触发要求重置）、`RequirementService`（提取 + 确认 + 人工修正 match_status）、`RequirementExtractor`（关键词词典规则提取，非 AI）、`GapListService`（仅基于 CONFIRMED 要求；无 user_skill 时默认 INSUFFICIENT_INFO）、`JobCreateCommand`、`JobUpdateCommand`、`JobListQuery`、`JobListResult`、`RequirementUpdateCommand`、`ExtractionResult`、`GapItem`
- `job/api/`：`JobController`（9 个端点：`POST/GET/GET/{id}/PUT/{id}/archive/restore/requirements/extract/gap-list`）、`JobRequirementController`（`PUT /api/job-requirements/{id}`，第 10 个端点）、`JobCreateRequest`、`JobUpdateRequest`、`JobResponse`、`PageJobResponse`、`JobRequirementResponse`、`RequirementUpdateRequest`、`GapItemResponse`、`RequirementExtractionResultResponse`

**OpenAPI 小幅扩展**
- `docs/jobhub/03-openapi.yaml` 的 `RequirementUpdateRequest` 新增可选字段 `manualMatchStatus: GapStatus`，用于支持 AT-04 人工修正匹配状态。`reason` 字段作为修正原因。**下一窗口应同步检查此扩展是否需要补充到 04-database-design.md 或 05-acceptance-test-cases.md 的描述**（AT-04 文字描述已隐含此机制，契约层面新增字段属于细化，不视为破坏性变更）。

## 3. 当前代码事实

- `backend/` 已含完整 Spring Boot 工程结构与 job + application + dashboard + interview + review + task + evidence + datamanagement 模块业务代码（`com.jobhub` 主代码 198 个 Java 文件，其中 evidence 模块 16 个、datamanagement 23 个：trash 5 + 导出 7 + settings 6 + notification 5；interview 模块含提醒调度 4 个文件）。
- `frontend/` 已生成完整骨架与岗位、投递、工作台和面试中心页面（Vite + React 19 + TS 5.6 + TanStack Query v5 + axios + react-router-dom v6）。`npm run lint`/`typecheck`/`build` 全绿（0 警告/0 错误），`npm run dev` 可启动（Vite 5173 + proxy `/api → 127.0.0.1:8080`）。application 三件套 API + P04 投递详情五区 + 创建表单 + P01 dashboard 行动识别，以及 P04/P05/P06 的创建、列表、提醒查询和专用状态操作均已实现。
- **后端已通过 `mvn test`（当前受影响的 Dashboard + Interview 8 方法 BUILD SUCCESS，0 failures/0 errors；此前全套基线为 33 方法）**；已通过 `mvn spring-boot:run` 启动（Flyway V1 成功，Tomcat 监听 127.0.0.1:8080）。
- 运行时 SQLite 数据库文件 `backend/data/jobhub.db` 由 Flyway 创建；禁止把 SQLite 数据库文件提交到仓库（`.gitignore` 已忽略）。
- `application.yml` 配置了 `mybatis.mapper-locations: classpath:mapper/*.xml`，但项目 mapper 全部使用注解 SQL 无 XML 文件，该配置无害失效（保留，无需修改）。
- `IdempotencyInterceptor` 与 `IdempotencyBodyCachingFilter` 都注册在 `/api/**` 路径上；已通过集成测试验证幂等回放与冲突行为。
- `RequirementExtractor.extract(jobId, existing)` 旧重载已废弃并抛 `UnsupportedOperationException`；服务层调用新签名 `extract(jobId, jdRawText, existing)`。
- AT-01 端到端已通过（创建岗位→提取候选→确认 3 项→差距 INSUFFICIENT_INFO→保存 TO_APPLY，经 node 脚本直连后端验证全流程断言通过）。

## 4. 里程碑状态

| 里程碑 | 范围 | 状态 | 进入条件 | 完成条件 |
|---|---|---|---|---|
| M1 | 工程骨架、Flyway、错误响应、OpenAPI 对齐、岗位 CRUD、JD 要求与差距清单 | `DONE` | 确认技术栈与启动命令 | AT-01 至 AT-04 通过 |
| M2 | 投递状态机、下一步行动、面试与提醒 | `DONE` | M1 完成 | AT-05 至 AT-14 通过 |
| M3 | 复盘、问题、知识点、薄弱点、学习任务 | `DONE` | M2 完成 | AT-15 至 AT-19 通过 |
| M4 | 面试准备包、项目案例、证据、导出、最近删除 | `DONE` | M3 完成 | AT-20 至 AT-24 通过 |

## 5. 当前窗口交接

> 最近 5 个窗口的交接记录见本文件开头（按时间倒序）。交接记录超过 5 个时，删除最早的记录后再追加最新记录。详见 IMPLEMENTATION_MASTER_PROMPT.md「八、窗口结束交接」。

## 6. 每个窗口的工作量控制

一个窗口只领取一个"可验证垂直切片"，不要领取一个完整模块或整个里程碑。推荐上限：

- 1 个用户流程或 1 个页面主路径；
- 后端不超过 2–4 个相关 endpoint；
- 不超过 1 个数据库迁移文件；
- 同步补齐对应测试和验收场景；
- 修改文件数量通常控制在 10 个以内，除非是工程初始化。

推荐的窗口任务格式：

```text
本窗口只实现：岗位创建与岗位详情的基础 CRUD。
范围：POST /jobs、GET /jobs、GET /jobs/{jobId}，以及对应前端页面。
不做：候选要求提取、差距清单、投递、面试和 AI。
完成标准：接口测试通过、页面可创建并查看岗位、更新 IMPLEMENTATION_STATUS.md。
```

窗口应在以下任一条件满足时主动收尾，不要继续扩展范围：

- 本窗口目标和对应验收用例已经通过；
- 发现需要修改 OpenAPI、数据库迁移或状态机，且无法在当前切片内完成；
- 已完成核心代码但测试尚未补齐；
- 上下文或时间明显不足以完成测试和交接。

## 7. 窗口结束更新模板

结束前将以下内容替换为真实信息：

```md
### 窗口 YYYY-MM-DD-N

- 目标：
- 状态：DONE / PARTIAL / BLOCKED
- 已完成：
- 未完成：
- 修改文件：
- 已运行验证：
- 验证结果：
- 已知问题：
- 下一窗口只做：
- 不要重复做：
```

如果窗口中途即将耗尽上下文，先保存 `PARTIAL` 交接记录，明确当前代码是否可编译、哪些测试未运行、下一步从哪个文件和方法继续。
