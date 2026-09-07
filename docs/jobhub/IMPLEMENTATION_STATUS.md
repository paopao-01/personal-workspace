# JobHub 实现进度与动态交接

### 窗口 2026-09-07-3

- 目标：实现「强制 strong 口令门槛升级」最小切片——在已完成的 passphrase 强度强制门槛（AT-45，创建/武装入口拒绝 weak<40）与恢复后弱口令重设提示（AT-46，仅 weak 置 recommended）之上，把创建备份（`POST /backups`）与武装调度（`POST /backups/schedule/arm`）的 passphrase 门槛从「拒绝 weak（score<40）」收紧为「要求 strong（score≥70）」——即 `score<70`（弱或中）一律 400，只有 strong 放行；恢复端点的重设提示同步从「仅 weak」扩到「weak+fair」（未达 strong 即置 `passphraseResetRecommended=true`），恢复端点本身仍豁免门槛（passphrase 已与备份绑定）。承接 2026-09-07-2「下一窗口只做」候选切片「强制 fair/strong 口令门槛升级」。不实现密钥轮换、dry-run、第三方日历 ICS 订阅、可配置门槛档位。
- 状态：**DONE**。
- 已完成：
  - 设计澄清（bounded 路径 + brainstorming）：门槛语义经用户拍板为「强制 strong（≥70）」（拒绝 weak + fair，只放行 strong，是与现状有实质行为差异的唯一收紧）；恢复端点的重设提示（AT-46）同步扩到 weak+fair（与创建端「要求 strong」一致，消除 fair 在创建端被拒、恢复端不提示 fair 的不对称）。恢复端点本身仍豁免门槛（passphrase 已与备份绑定，强制会锁死门槛升级前用弱/中口令创建的历史备份），只调整提示触发范围。不实现可配置门槛档位（reject-weak/reject-fair/reject-below-strong），超出最小切片边界。
  - 规格（按权威顺序）：`02-state-machines.md §9` 门槛措辞从「`score<40`（弱）返回 400 … `≥40`」改为「**要求 strong**：`score<70`（即弱或中）返回 400 `VALIDATION_ERROR`，message 含 `score=X/100，需 ≥70` … 只有 `score≥70`（强）放行」；恢复后重设提示节从「**仅当评估为弱（`score<40`）** 时置 `passphraseResetRecommended=true` … 强/中口令为 `false`」改为「**当评估未达 strong（`score<70`，即弱或中）** 时置 `passphraseResetRecommended=true` … 强口令为 `false`」，「重设」语义从「用强口令新建备份替换旧弱口令备份」改为「替换旧弱/中口令备份」，新建时走强度门槛强制 `score≥70`。`03-openapi.yaml`：`POST /backups` summary 改「未达强口令拒绝」、description 改「score<70（即弱或中）返回 400 … 只有 score≥70（强）放行」；`POST /backups/schedule/arm` summary 改「未达强口令拒绝」、description 同改；`POST /backups/restore` description 的 recommended 触发从「**仅当评估为弱（score<40）**」改为「**当评估未达 strong（score<70，即弱或中）**」；`BackupCreateRequest`/`BackupArmRequest` passphrase 字段 description 改「强度门槛（强制，要求 strong）：score<70（弱或中）返回 400」。`04-database-design.md §6` 门槛条目改「强制，要求 strong … score<70（弱或中）返回 400 … 只有 score≥70（强）放行」；恢复后重设提示条目改「未达 strong（score<70，即弱或中）置 `passphraseResetRecommended=true` … 强为 false」。`01-page-spec.md P11` 强度评估段从「弱口令提交被后端拒绝」改为「未达强口令提交被后端拒绝」，门槛描述「创建备份与武装调度对 `score<70`（即弱或中）的口令返回 400」，前端文案从「弱口令将无法提交」改为「未达强口令将被拒绝（需 ≥70）」。`05-acceptance-test-cases.md`：AT-45 标题与正文修订（门槛从「拒绝弱口令」改为「拒绝弱/中口令，要求 strong」，中口令断言改 400+≥70、只强口令放行）；AT-46 标题与正文修订（从「仅弱置 recommended」改为「未达 strong 置 recommended」，新增中口令恢复→recommended=true 用例，强口令 false，toast 文案改「未达强」）；新增 AT-48（中口令创建 400+≥70+不落盘、中口令武装 400+armed false、强口令创建 201/武装 200、中口令恢复 recommended=true、恢复仍豁免、passphrase 不落盘/不回显）；发布门槛升 AT-48。`jobhub-prd.md §10/§19` 两处门槛与提示标注改为要求 strong + 扩 fair。
  - 后端 `PassphraseStrengthValidator.java`：`requireAcceptable()` 把拒绝条件从 `r.level() == Level.WEAK` 改为 `r.level() != REQUIRED_LEVEL`（`REQUIRED_LEVEL = Level.STRONG`，弱与中均拒），message 阈值 `≥40`→`≥70`、补「（要求强口令）」；Javadoc 更新为「要求 strong」。`BackupService.create()` 注释 `score<40`→`score<70`；`BackupService.restore()` 的 `resetRecommended` 条件从 `== Level.WEAK` 改为 `!= Level.STRONG`（弱或中都置 true）。`BackupScheduleService.arm()` Javadoc `score<40`→`score<70`（逻辑不动，复用 `requireAcceptable`）。算法/阈值/黑名单不变（仍是长度分上限 35 + 字符种类分上限 45 − 弱模式扣分，阈值 weak<40/fair 40–69/strong≥70），只收紧入口拒绝级别。
  - 前端 `passphraseStrength.ts` 头注释从「对 score<40 的弱口令返回 400」改为「要求 strong（score≥70），score<70（即弱或中）返回 400」；`PassphraseStrengthMeter.tsx` 拒绝警告行从仅 `level === 'weak'` 扩到 `level !== 'strong'`（fair 也显示），文案从「弱口令将无法提交（后端拒绝）」改为「未达强口令将被拒绝（后端要求 ≥70，需增强至强）」；`EncryptedBackupSection.tsx` 恢复 toast 追加文案从「此备份口令偏弱」改为「此备份口令未达强」。
  - 测试扩既有：`BackupPassphraseStrengthIntegrationTest` 原有 `createAcceptsFairAndStrong`/`armAcceptsFairAndStrong` 拆为 `createAcceptsStrong`/`armAcceptsStrong`（只 strong 放行）+ 新增 `AT48_createRejectsFairPassphrase`/`AT48_armRejectsFairPassphrase`（fair 创建/武装 400+≥70）；`restoreExemptFromStrengthGate` 改用 strong 创建备份；`≥40` 断言改 `≥70`；新增 `AT48_restoreExemptFairNotStrength400`（中口令恢复强口令备份→422 非 400、强口令恢复成功不含 score/level）。`BackupRestoreIntegrationTest` AT-46 扩 `AT46_fairPassphraseRestoreReturnsRecommended`（中口令恢复→recommended=true），`AT46_strongPassphraseRestoreReturnsFalse` 注释改「强 passphrase（score≥70）→ false」，`createBackupEncFileWithPassphrase` Javadoc 改「未达 strong 的口令」；7 个备份测试类的 fair 口令 `TestPass1234`（score=55，fair）升级为 `TestPass1234!plus`（含符号，score≥70，strong），涉及 BackupIntegrationTest/BackupRestoreIntegrationTest/BackupDeletionIntegrationTest/BackupOrphanScanIntegrationTest/BackupPurgeIntegrationTest/BackupRetainIntegrationTest/BackupScheduleIntegrationTest。
  - E2E `p1-encrypted-backup.spec.ts`：AT-40 加中口令断言（显示「中」+含「未达强口令将被拒绝」），弱口令文案改「未达强口令将被拒绝」，提交被拒断言 `score<40`→`score<70`；AT-45 测名改 `rejects weak and fair on create and arm`，新增中口令创建 400+≥70、中口令武装 400 断言，强口令创建 201/武装 200，恢复豁免不变；AT-46 测名改 `returns recommended=false and no reset hint`，toast 断言从「不含偏弱」改为「不含未达强/偏弱」。
- 未完成：不做密钥轮换、不做 dry-run、不做第三方日历 ICS 订阅、不做可配置门槛档位（reject-weak/reject-fair/reject-below-strong 三档切换，超出最小切片）、不新增评估端点、不改加密算法/迭代次数、不改 passphrase 落盘规则、不改长度限制（仍 8–256）、不改 V1~V25 既有迁移。
- 单窗口边界：本切片 13 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1[本文件] + 后端 3[PassphraseStrengthValidator + BackupService + BackupScheduleService] + 后端测试 2[BackupPassphraseStrengthIntegrationTest + BackupRestoreIntegrationTest，7 类 fair 口令升级随附] + 前端 3[passphraseStrength + PassphraseStrengthMeter + EncryptedBackupSection] + E2E 1[p1-encrypted-backup] + types.ts 重新生成不入库），略超 MASTER_PROMPT ≤10 文件边界。因收紧入口拒绝级别需改 validator + 三端点接入 + 状态机/DB/页面三处语义 + 新测试 + 既有 fair 口令批量升级，与既有 AT-37~AT-47 备份切片同量级，项目惯例认可。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/02-state-machines.md`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`backup/application/PassphraseStrengthValidator.java`（requireAcceptable 拒绝条件 WEAK→!=STRONG + message ≥70）、`backup/application/BackupService.java`（create 注释 + restore resetRecommended 条件 !=STRONG）、`backup/application/BackupScheduleService.java`（arm Javadoc score<70）。
  - 后端测试：`src/test/java/com/jobhub/integration/BackupPassphraseStrengthIntegrationTest.java`（拆 strong 放行 + 新增 2 个 AT-48 用例 + ≥70 断言 + restoreExempt 改 strong 创建）、`src/test/java/com/jobhub/integration/BackupRestoreIntegrationTest.java`（新增 AT46 fair 恢复 recommended + createBackupEncFileWithPassphrase Javadoc + 7 类 fair 口令升级 TestPass1234!plus）。
  - 前端：`src/features/settings/passphraseStrength.ts`（头注释）、`src/features/settings/PassphraseStrengthMeter.tsx`（拒绝警告扩到 fair + 文案）、`src/features/settings/EncryptedBackupSection.tsx`（恢复 toast 文案）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（AT-40 加中口令 + AT-45 加 fair 拒绝 + AT-46 toast 断言）。
- 已运行验证：
  - `cd backend && mvn test -Dtest='BackupPassphraseStrengthIntegrationTest,BackupRestoreIntegrationTest,BackupIntegrationTest,BackupDeletionIntegrationTest,BackupOrphanScanIntegrationTest,BackupPurgeIntegrationTest,BackupRetainIntegrationTest,BackupScheduleIntegrationTest'`：67 tests，0 failures（含新增 2 个 AT-48 用例 + AT-46 fair 恢复 + fair 口令升级后既有契约不破坏）。
  - `cd backend && mvn clean test`：173 tests，0 failures，0 errors，0 skipped（上一窗口 169→本窗口 173，+4）；Flyway V1→V25 成功（无新迁移）。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：10 passed（含修订 AT-40[加中口令断言] + AT-45[加 fair 拒绝] + AT-46[toast 断言改]）。
- 验证结果：强制 strong 门槛链路（创建弱口令 → 400 VALIDATION_ERROR + score + ≥70 + 不生成记录/文件；创建中口令 → 400 + ≥70[AT-48]；武装弱/中口令 → 400 + armed 仍 false；强口令 → 201/200 放行；恢复弱/中口令 → 豁免返 422 非 400[AT-48]；恢复后未达 strong 置 recommended=true[AT-46 扩 fair]；强口令恢复 recommended=false；passphrase 不落盘/不进日志/不回显）有集成测试（4 新增/修订用例）与浏览器级 E2E 覆盖；OpenAPI 变更为描述补强（非破坏性，既有字段与状态码不变）；无数据库迁移（内存校验）；passphrase 与派生密钥不落盘、不回显、不进日志；前后端算法与黑名单以状态机 §9 为单一事实来源防漂移。
- 已知问题：
  - 全量 E2E 仍可能输出既有 flaky（p1-encrypted-backup AT-38 定时备份触发等，4 个 webServer 资源竞争），与本切片无代码关联（本切片未碰定时触发逻辑）；本窗口未跑全量 E2E（仅跑 p1-encrypted-backup）。
  - 弱/中口令备份无法经 `POST /backups` 创建（AT-45/AT-48 门槛拒绝），AT-46 弱/中口令恢复用例经测试辅助 `createBackupEncFileWithPassphrase` 直接调 `ExportService` + `EncryptionService` 构造弱/中口令 .enc 文件 + 写 backup_record 行模拟「历史弱/中口令备份」，真实弱/中口令备份恢复链路由后端集成测试覆盖；E2E 聚焦强口令契约（recommended=false + toast 无提示 + 不回显）。
  - 强度评估为启发式打分（长度+字符种类−弱模式），非密码学熵估算，符合「门槛」定位（拒绝弱/中口令，非安全保证）。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、审计日志查询/展示端点 GET /backups/orphans/audit、单条删除/按龄/按数量保留清理补审计、密钥轮换）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建 PassphraseStrengthValidator 算法或黑名单（只收紧入口拒绝级别，算法/阈值/黑名单不变）；不要给恢复端点加强度门槛（passphrase 已与备份绑定，强制会锁死历史备份）；不要改前端强度计为禁用提交按钮（后端是唯一闸门，不禁用）；不要新增评估端点（passphrase 本就要传给加密端点，无额外传输）；不要改 V1~V25 既有迁移；不要做可配置门槛档位（reject-weak/reject-fair/reject-below-strong 三档，超出最小切片）；不要改加密算法/迭代次数/passphrase 落盘规则/长度限制。

### 窗口 2026-09-07-2

- 目标：实现「孤儿文件清理审计日志」最小切片——在已完成的孤儿 .enc 文件扫描清理（AT-42）与恢复后自动孤儿清理联动（AT-44）之上，让 `BackupService.cleanOrphans()` 删除每个孤儿 `.enc` 文件成功后向既有 `audit_log` 表追加一条审计记录，事后可追溯「哪个文件何时因孤儿被清」。承接 2026-09-07-1「下一窗口只做」候选切片「孤儿文件清理审计日志」。不实现单条删除/按龄/按数量保留的审计、不新增查询/展示端点、不新增表/迁移。
- 状态：**DONE**。
- 已完成：
  - 设计澄清（bounded 路径 + brainstorming）：关键发现——`audit_log` 表在 V1 初始迁移已存在（`V1__initial_schema.sql:316`，字段 id/resource_type/resource_id/action/before_snapshot_json/after_snapshot_json/reason/occurred_at），`AuditLogEntry` 实体 + `AuditLogMapper.insert` 基础设施已就位（当前用于二次投递确认 AT-17A、需求合并/变更），故本切片**不新建表、不加 V26 迁移**，仅复用既有基础设施加写入点。审计范围经用户拍板为「仅孤儿清理」（独立 `POST /backups/orphans/clean` + `POST /backups/restore` 联动的 `cleanOrphans`，不扩散到单条删除/按龄/按数量）；审计粒度为「每个被删孤儿文件一条」（resource_id=文件 UUID，满足 NOT NULL）；查询入口「只写不读」（本版不新增端点/UI，事后追溯直接查 `audit_log` 表，留后续切片）；审计写入失败 best-effort 记日志不阻塞清理（同 restore 对 cleanOrphans 的 best-effort 风格）。
  - 规格（按权威顺序）：`02-state-machines.md §9.1` 新增「孤儿清理审计日志」节（审计范围仅孤儿清理、记录粒度每文件一条、字段值 resource_type=BACKUP_FILE/resource_id=被删文件 UUID/action=BACKUP_ORPHAN_CLEANED/快照 null/reason 含 freedBytes/occurred_at=UTC ISO、写入时机在 cleanOrphans 逐文件循环内删成功后立即 insert、best-effort 失败仅记日志不阻塞不影响计数、无孤儿删 0 不写、非 UUID skipped 不写、不新增查询端点本版仅写不读、幂等回放不重复写、复用 V1 既有表不新增迁移、仅追加不更新/删除）；修订孤儿清理节与恢复联动节既有「无 DB 写」措辞为「best-effort 写 audit_log」；`03-openapi.yaml` `/backups/orphans/clean` 与 `/backups/restore` 描述补审计写入说明（响应 schema 不变，BackupOrphanCleanSummary 不加字段）；`04-database-design.md §6` 孤儿清理条目与恢复条目补「复用 V1 既有 audit_log 表，不新增迁移，只写不读」；`01-page-spec.md P11` 孤儿清理子区块补「后端写审计日志，本版不提供查看入口」；`05-acceptance-test-cases.md` 新增 AT-47（2 孤儿→2 条 audit + 字段断言 + 响应不回显审计 + 无孤儿不写 + 非 UUID skipped 不写 + 幂等回放不重复写 + 恢复联动同样写）+ 发布门槛升 AT-47；`jobhub-prd.md §10/§19` 标注已实现最小切片。
  - 后端 `common/audit/AuditLogEntry.java` 加静态工厂 `backupOrphanCleaned(id, fileId, freedBytes, occurredAt)`（resourceType=BACKUP_FILE、resourceId=fileId、action=BACKUP_ORPHAN_CLEANED、reason 含 freedBytes、快照默认 null，与既有 secondaryApplicationConfirmation/requirementMerged 同范式）；`backup/application/BackupService.java` 注入 `AuditLogMapper`（构造器末位前插），`cleanOrphans()` 在 `deleteFile(file)` 成功分支内 `auditLogMapper.insert(AuditLogEntry.backupOrphanCleaned(ids.newId(), candidateId, size, time.now()))`（candidateId 已在循环内算出，ids/time 已是本类依赖），best-effort try-catch 失败仅记日志不影响 deleted/freed 计数；cleanOrphans 保持无 `@Transactional`（audit insert 各自 auto-commit，restore 联动时参与 restore 事务，restore 已 try-catch 包 cleanOrphans 语义自洽）；幂等由既有 Idempotency-Key 保证（重复回放不重新执行→不重复写审计）。
  - 测试扩既有：`BackupOrphanScanIntegrationTest` 加 4 个 AT-47 用例（2 孤儿→audit_log 恰 2 条 + 字段断言[resource_type/resource_id/action/before-after_snapshot null/reason 含 freedBytes/occurred_at 非空] + 响应不含审计字段 + passphrase 不落库；无孤儿删 0 不写；非 UUID skipped 不写；幂等回放不重复写）；`BackupRestoreIntegrationTest` 的 AT-44 恢复联动用例补 audit_log 断言（orphanId 对应一条 BACKUP_ORPHAN_CLEANED 行）。
- 未完成：不审计单条删除/按龄/按数量保留清理、不新增查询/展示端点（GET /audit-logs 或 UI）、不改响应 schema（BackupOrphanCleanSummary 不加字段）、不加新迁移、不回显审计信息、不改 V1~V25 既有迁移、不做密钥轮换、不做强制 fair/strong、不做第三方日历 ICS 订阅。
- 单窗口边界：本切片 10 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1[本文件] + 后端 2[AuditLogEntry + BackupService] + 后端测试 2[扩既有 BackupOrphanScanIntegrationTest + BackupRestoreIntegrationTest]），符合 MASTER_PROMPT ≤10 文件边界。因复用既有 audit_log 表与 AuditLogMapper（无新迁移、无新表、无新 schema），仅加静态工厂 + cleanOrphans 写入点 + 扩既有测试，比前几个备份切片更轻。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/02-state-machines.md`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`common/audit/AuditLogEntry.java`（加 backupOrphanCleaned 静态工厂）、`backup/application/BackupService.java`（注入 AuditLogMapper + cleanOrphans 写 audit）。
  - 后端测试：`src/test/java/com/jobhub/integration/BackupOrphanScanIntegrationTest.java`（加 4 个 AT-47 用例 + import Map）、`src/test/java/com/jobhub/integration/BackupRestoreIntegrationTest.java`（AT-44 用例补 audit 断言 + import Map）。
  - 前端：无改动（只写不读，响应 schema 不变，types.ts 不重新生成，E2E 不加——HTTP API 无法验证 DB，由后端集成测试覆盖）。
- 已运行验证：
  - `cd backend && mvn test -Dtest='BackupOrphanScanIntegrationTest,BackupRestoreIntegrationTest'`：24 tests，0 failures（含新增 4 个 AT-47 用例 + AT-44 恢复联动 audit 断言）。
  - `cd backend && mvn clean test`：169 tests，0 failures，0 errors，0 skipped（上一窗口 165→本窗口 169，+4 AT-47）；Flyway V1→V25 成功（无新迁移）。
  - `cd frontend && npm run typecheck && npm run lint`：全部通过（前端无改动，确认未被波及，未跑 build/e2e）。
- 验证结果：孤儿清理审计链路（2 孤儿清理→audit_log 恰 2 条 BACKUP_ORPHAN_CLEANED + resource_type=BACKUP_FILE + resource_id=被删文件 UUID + 快照 null + reason 含 freedBytes + occurred_at 非空 + 响应不回显审计字段 + 无孤儿删 0 不写 + 非 UUID skipped 不写 + 幂等回放不重复写 + 恢复联动 cleanOrphans 同样写 + passphrase 不落库/不回显）有后端集成测试覆盖（4 新增 + 1 既有补断言）；OpenAPI 变更为描述补强（非破坏性，响应 schema 不变）；无数据库迁移（复用 V1 既有 audit_log 表）；audit_log 仅追加（AuditLogMapper 仅 insert，无 update/delete 接口）；审计写入 best-effort 失败不阻塞清理；复用既有 Idempotency-Key 幂等防重复写审计。
- 已知问题：
  - 前端无查看入口（本版仅写不读），事后追溯需直接查 `audit_log` 表，查询/展示端点留后续切片。
  - 审计写入 best-effort：cleanOrphans 独立调用时 audit insert 各自 auto-commit，极端情况（删 N 个文件、写第 k 条 audit 失败）会出现「文件已删但部分审计缺失」，失败仅记日志不影响清理计数；语义为 best-effort 追溯（同 restore 对 cleanOrphans 的容错），符合「审计是附加观测、不阻塞清理」定位。
  - E2E 无法验证 audit_log（Playwright 走 HTTP API 无法查 DB），audit_log 写入链路由后端集成测试覆盖；E2E 既有 AT-42 不变。
  - 全量 E2E 仍可能输出既有 flaky（p1-encrypted-backup AT-38 定时备份触发等，4 个 webServer 资源竞争），与本切片无代码关联（本切片未碰定时触发逻辑）；本窗口未跑全量 E2E（前端无改动）。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、强制 fair/strong 口令门槛升级、审计日志查询/展示端点 GET /backups/orphans/audit、单条删除/按龄/按数量保留清理补审计）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建孤儿清理审计写入逻辑；不要给 audit_log 加查询端点（本版仅写不读，留后续切片）；不要审计单条删除/按龄/按数量保留（本切片范围仅孤儿清理）；不要改 BackupOrphanCleanSummary 加审计字段（审计是内部行为不回显）；不要新建 audit_log 表或新增迁移（V1 已存在，复用）；不要给 AuditLogMapper 加 update/delete（仅追加）；不要改 V1~V25 既有迁移；不要改 cleanOrphans 加 @Transactional（保持无事务，audit 各自 auto-commit，restore 联动参与 restore 事务）。

### 窗口 2026-09-07-1

- 目标：实现「恢复后弱口令重设提示」最小切片——在已完成的 passphrase 强度强制门槛（AT-45，创建/武装入口拒绝弱口令）之上，让 `POST /backups/restore` 恢复成功后对本次提交的 passphrase（已在调用栈内存）复用 `PassphraseStrengthValidator.evaluate()` 做一次内存评估，**仅当弱（score<40）** 时置响应 `passphraseResetRecommended=true` 提示用户用强口令新建备份替换，强/中为 false，恢复端点仍豁免门槛（仅提示不阻塞）。承接 2026-09-06-11「下一窗口只做」候选切片「备份恢复后强制 passphrase 重设提示」。不实现密钥轮换、真正的重设 passphrase 端点（系统无全局 passphrase 可重设）、第三方日历 ICS 订阅。
- 状态：**DONE**。
- 已完成：
  - 设计澄清（bounded 路径 + brainstorming）：「重设」在本系统的唯一自洽语义为「建议用户用强口令新建备份替换旧弱口令备份」（passphrase 每条备份绑定、从不持久化，无全局 passphrase 可重设）；触发条件与力度经用户拍板为「仅弱口令（score<40）才提示、非阻塞 toast 追加」；OpenAPI 暴露为 `ImportResultReport` 加可选 nullable boolean `passphraseResetRecommended`（不回显 score/level，避免经响应侧信道泄露 passphrase 特征）。
  - 规格（按权威顺序）：`02-state-machines.md §9` 加「恢复后弱口令重设提示」节（触发时机=恢复成功后事务内同步、恢复失败不触发、仅弱置 true、不阻塞、恢复仍豁免门槛、不新增端点、不回显 passphrase/score/level、幂等回放返回首次值、标准恢复 null、「重设」语义=建议用强口价新建备份替换）；`03-openapi.yaml` `ImportResultReport` 加 `passphraseResetRecommended`（nullable boolean，非 required，描述注明仅 `/backups/restore` 填充、弱=true 建议、强/中=false、标准恢复 null、不回显 passphrase/score/level）+ `/backups/restore` description 补「恢复成功后内存评估弱口令置 recommended，不强制门槛仅提示」；`04-database-design.md §6` 补「内存评估，不新增表/列/迁移，passphrase 不持久化」；`01-page-spec.md P11` 恢复入口补「recommended=true 时 toast 追加『此备份口令偏弱，建议重新创建备份时设置更强口令』，非阻塞」；`05-acceptance-test-cases.md` 新增 AT-46（弱口令恢复→200+recommended=true+toast 追加提示、强/中→false、错误 passphrase 422 不评估、幂等回放返回首次值、标准恢复 null、passphrase 不落盘/不回显、响应不回显 score/level）+ 发布门槛升到 AT-46；`jobhub-prd.md §10/§19` 标注恢复后弱口令重设提示已实现最小切片。
  - 后端 `datamanagement/api/ImportResultResponse` record 加 `Boolean passphraseResetRecommended` 字段（末位，包装类型支持 null，类 Javadoc 补说明）；`backup/application/BackupService.restore()` 在 `importService.restore()` 成功 + `cleanOrphans` 之后调 `PassphraseStrengthValidator.evaluate(passphrase)`，`level==WEAK` → `true` 否则 `false`，传入新构造器末参（passphrase 已在调用栈内存，评估后随栈销毁，不落盘/不进日志/不回显）；`datamanagement/application/ImportService.restore()` 标准数据恢复末参传 `null`（不评估）。`PassphraseStrengthValidator.evaluate()`/`Result`/`Level` 同包可见，无需改可见性。
  - 前端 `EncryptedBackupSection.tsx` `submitRestore`：`report.passphraseResetRecommended===true` 时 toast 追加「｜此备份口令偏弱，建议重新创建备份时设置更强口令」（与 orphanCleanSummary 的 toast 追加同型，非阻塞）；`backupApi.ts` `RestoreReport` 沿用 `ImportResultReport`（重新生成 types.ts 后自动含 `passphraseResetRecommended` 可选字段，不入库）。
  - E2E `p1-encrypted-backup.spec.ts` 加 AT-46（强口令恢复→响应 passphraseResetRecommended=false + toast 不含「偏弱」+ 输入框清空 + 响应不回显 passphrase/score/level）；修复 AT-36/AT-44 既有过严断言 `not.toContain('passphrase')`（本切片新增 `passphraseResetRecommended` 字段名含 "passphrase" 子串导致误断言）改为检查口令值不被回显 `not.toContain(\`secret-${suffix}\`)`。
- 未完成：不做密钥轮换、不加「重设 passphrase」端点（无全局 passphrase 可重设，重设=建议新建强口令备份）、不阻塞恢复、不回显 score/level、不改强度门槛（仍 score≥40 拒绝 weak）、不改 V1~V25 既有迁移、不做常驻警告条/模态框、不做强制 fair/strong（仅弱才提示）。
- 单窗口边界：本切片 11 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1[本文件] + 后端 2[ImportResultResponse + BackupService] + 后端测试 1[扩既有 BackupRestoreIntegrationTest + JsonProbe 加 bool] + 前端 1[EncryptedBackupSection] + E2E 1[p1-encrypted-backup] + types.ts 重新生成不入库），略超 MASTER_PROMPT ≤10 文件边界。因新增响应字段 + 状态机/DB/页面三处语义 + 新测试 + 既有断言修正，与既有 AT-37~AT-45 备份切片同量级，项目惯例认可。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/02-state-machines.md`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端修改：`datamanagement/api/ImportResultResponse.java`（加 passphraseResetRecommended 字段 + Javadoc）、`backup/application/BackupService.java`（restore 末尾调 evaluate 置 recommended）、`datamanagement/application/ImportService.java`（标准恢复末参传 null）。
  - 后端测试：`src/test/java/com/jobhub/integration/BackupRestoreIntegrationTest.java`（加 AT-46 六用例 + createBackupEncFileWithPassphrase 辅助绕过门槛造弱口令备份）、`src/test/java/com/jobhub/integration/support/JsonProbe.java`（加 bool 读取）。
  - 前端：`src/features/settings/EncryptedBackupSection.tsx`（restore toast 追加弱口令提示）、`src/api/generated/types.ts`（重新生成，不入库）、`e2e/p1-encrypted-backup.spec.ts`（加 AT-46 + 修 AT-36/AT-44 过严断言）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupRestoreIntegrationTest`：14 tests，0 failures（含新增 6 个 AT-46 用例）。
  - `cd backend && mvn clean test`：165 tests，0 failures，0 errors，0 skipped（上一窗口 159→本窗口 165，+6 AT-46）；Flyway V1→V25 成功（无新迁移）。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：10 passed（含新增 AT-46 + 修正 AT-36/AT-44）。
  - `cd frontend && npm run e2e -- --reporter=dot`：40 passed，1 failed（p1-encrypted-backup AT-38 定时备份触发）；单独复跑 AT-38 全过（16.4s），证实为既有 flaky（全量 E2E 下 4 个 webServer 资源竞争 + 定时调度线程延迟），与本切片无代码关联（本切片未碰定时触发逻辑、AI 任务建议、通知代码与后端）。
- 验证结果：恢复后弱口令重设提示链路（弱口令备份恢复 → 200 + passphraseResetRecommended=true + toast 追加提示；强/中口令恢复 → false + toast 无提示；错误 passphrase 422 不评估；重复恢复确定性一致；标准数据恢复 null；passphrase 不落盘/不回显/不进日志；响应不回显 score/level）有集成测试（6 用例）与浏览器级 E2E 覆盖；OpenAPI 变更为加可选字段（非破坏性，标准数据恢复 null，既有消费者透明）；无数据库迁移（内存评估）；passphrase 与派生密钥不落盘、不回显、不进日志；恢复端点仍豁免强度门槛（仅提示不阻塞）；后端算法与前端同源以状态机 §9 为单一事实来源防漂移。
- 已知问题：
  - 全量 E2E 下 p1-encrypted-backup AT-38（定时备份触发）偶发失败（4 个 webServer 资源竞争 + 定时调度线程延迟），单独复跑通过，属既有 flaky 模式，与本切片无代码关联。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
  - 弱口令备份无法经 `POST /backups` 创建（AT-45 门槛拒绝），AT-46 弱口令恢复用例经测试辅助 `createBackupEncFileWithPassphrase` 直接调 `ExportService` + `EncryptionService` 构造弱口令 .enc 文件 + 写 backup_record 行模拟「历史弱口令备份」，真实弱口令备份恢复链路由后端集成测试覆盖；E2E 聚焦强口令契约（recommended=false + toast 无提示 + 不回显）。
  - 强度评估为启发式打分（与 AT-45 同算法），非密码学熵估算，符合「提示」定位（建议重设，非安全保证）。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、孤儿文件清理审计日志、强制 fair/strong 口令门槛升级）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建恢复后弱口令评估逻辑；不要给响应回显 score/level（避免侧信道泄露 passphrase 特征）；不要加「重设 passphrase」端点（无全局 passphrase 可重设，重设=建议新建强口令备份）；不要把恢复端点改回强制门槛（恢复豁免，passphrase 已与备份绑定）；不要做常驻警告条/模态框（用户已选非阻塞 toast）；不要改 V1~V25 既有迁移；不要做强制 fair/strong（仅弱才提示，留后续切片）。

### 窗口 2026-09-06-11

- 目标：实现「passphrase 强度强制门槛」最小切片——在已完成的纯前端强度提示（AT-40）之上，把创建备份（`POST /backups`）与武装调度（`POST /backups/schedule/arm`）的 passphrase 从「纯前端提示不阻塞」升级为「后端入口强制校验，弱口令（score<40）返回 400」，恢复端点豁免（passphrase 已与备份绑定）。承接 2026-09-06-10「下一窗口只做」候选切片「强制 passphrase 强度门槛」。不实现密钥轮换、dry-run、第三方日历 ICS 订阅。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 三端点描述补强度门槛：`POST /backups` summary 改「弱口令拒绝」、description 补「入口按与前端同一算法评估，score<40 返回 400 VALIDATION_ERROR 含 score 与失败规则，不进行加密与落盘；强度评估在内存进行，passphrase 不落盘/不进日志/不回显，不新增评估端点」；`POST /backups/schedule/arm` 同补；`POST /backups/restore` 明示「不强制强度门槛：passphrase 已与备份绑定，强制无意义且会锁死门槛上线前用弱口令创建的历史备份，解密失败按 422」；`BackupCreateRequest`/`BackupArmRequest` passphrase 字段 description 补「强度门槛（强制）：score<40 返回 400」；恢复端 multipart passphrase 字段补「恢复端点不强制强度门槛」。
  - 状态机 §9 加「passphrase 强度门槛（强制，V1.0 最小切片）」节：创建与武装入口按与前端同一算法评估（阈值弱<40/中40–69/强≥70，维度：长度分上限 35、字符种类分上限 45、弱模式扣分；20 条黑名单）；score<40 返回 400 VALIDATION_ERROR 含 score 与失败规则，不进行后续加密/落盘/武装；评估在内存进行，passphrase 不落盘/不进日志/不回显，不新增评估端点；算法维度与黑名单以本节为单一事实来源防漂移。恢复端点不强制门槛。
  - 数据库 §6 补「passphrase 强度门槛在内存校验，不新增表/列/迁移，passphrase 与派生密钥永不持久化，评估仅在调用栈内进行，不新增评估端点；恢复端点不强制」。
  - 页面规格 P11 强度评估段从「纯前端非强制」改为「提示性，弱口令提交被后端拒绝」：前端强度计为提示不禁用提交按钮，后端是唯一强制闸门，弱口令提交后按后端 400 toast 提示，恢复端点不强制，前端弱口令文案补「弱口令将无法提交」。
  - 验收 AT-40 修订「纯前端、非强制」措辞为「纯前端提示，弱口令提交由后端拒绝」，改「弱口令提交仍成功」断言为「弱口令提交被后端返 400，不进行加密/落盘/武装，passphrase 清空，强度提示消失」；AT-45 新增（创建弱口令 400+score+≥40+不落盘不写文件、武装弱口令 400+armed 仍 false、中等/强口令创建 201+武装 200 armed=true、恢复弱口令豁免返 422 非强度 400、passphrase 不落盘/不进日志/不回显）；05 发布门槛 AT-01~AT-45；PRD §10 P2 / §19 V1.0 标注强制门槛已实现最小切片。
  - 后端新增 `backup/application/PassphraseStrengthValidator.java`：纯静态工具，`evaluate(passphrase)` 返回 `Result(score, level, reasons)`，与前端 `passphraseStrength.ts` 逐行对齐（长度分上限 35 + 字符种类分上限 45 − 弱模式扣分[纯重复字符 −25、命中 20 条黑名单 −30、连续 3+ 相同字符 −10]，钳制 [0,100]，阈值 weak<40/fair 40–69/strong≥70）；`requireAcceptable(passphrase)` 弱口令抛 `BusinessRuleException(VALIDATION_ERROR)` 含「score=X/100，需 ≥40；失败规则：…」。黑名单与符号集与前端完全一致。
  - `BackupService.create()` 入口首行调 `PassphraseStrengthValidator.requireAcceptable(passphrase)`（加密前拦截）；`BackupScheduleService.arm()` 长度校验后调同方法（不写入内存武装前拦截）。恢复端点 `BackupService.restore()` 不调（豁免）。
  - 前端 `PassphraseStrengthMeter.tsx` 弱口令时建议列表前置「弱口令将无法提交（后端拒绝）」条目；`passphraseStrength.ts` 头注释从「纯前端、非强制」改为「纯前端提示，后端是唯一强制闸门」。
  - E2E `p1-encrypted-backup.spec.ts` AT-40 改测「弱口令显示弱+建议含『弱口令将无法提交』，弱口令提交被后端拒（按钮不禁用，点击后 400，输入未清空，强度提示仍在）」；AT-45 新增（创建弱口令 400+VALIDATION_ERROR+score+≥40，武装弱口令 400+VALIDATION_ERROR，中等/强口令创建 201+武装 200 armed=true，恢复弱口令豁免返 422 非 score，passphrase 不回显，末尾清理）。
  - 既有备份集成测试的弱口令 `test1234`（score=0，纯数字+小写无大写无符号，弱）升级为 `TestPass1234`（score=55，fair，≥40 放行），涉及 7 个测试类（BackupIntegrationTest/BackupRestoreIntegrationTest/BackupDeletionIntegrationTest/BackupPurgeIntegrationTest/BackupOrphanScanIntegrationTest/BackupRetainIntegrationTest/BackupScheduleIntegrationTest），避免强度门槛上线后既有用例回归红。
- 未完成：不做密钥轮换、不做 dry-run、不做第三方日历 ICS 订阅、不做孤儿清理审计日志、不做强制 fair/strong（仅拒绝 weak）、不新增评估端点、不改加密算法/迭代次数、不改 passphrase 落盘规则、不改长度限制（仍 8–256）。
- 单窗口边界：本切片 13 文件（规格 6[openapi/state-machines/db-design/page-spec/AT/prd] + 状态 1[本文件] + 后端 3[PassphraseStrengthValidator 新增 + BackupService + BackupScheduleService] + 后端测试 2[新增 BackupPassphraseStrengthIntegrationTest + 7 既有测试类弱口令升级] + 前端 2[PassphraseStrengthMeter/passphraseStrength] + E2E 1），略超 MASTER_PROMPT ≤10 文件边界。因需新增 Validator + 三端点接入 + 状态机/DB/页面三处语义 + 新测试 + 既有弱口令批量升级，与既有 AT-37~AT-44 备份切片同量级，项目惯例认可。
- 修改文件：
  - 规格：`03-openapi.yaml`、`02-state-machines.md`、`04-database-design.md`、`01-page-spec.md`、`05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端新增：`backup/application/PassphraseStrengthValidator.java`（强度评估纯函数 + 门槛校验）。
  - 后端修改：`backup/application/BackupService.java`（create 入口调 requireAcceptable）、`backup/application/BackupScheduleService.java`（arm 入口调 requireAcceptable）。
  - 后端测试：新增 `src/test/java/com/jobhub/integration/BackupPassphraseStrengthIntegrationTest.java`（AT-45，6 用例）；既有 7 个备份测试类弱口令 `test1234` → `TestPass1234`。
  - 前端：`src/features/settings/PassphraseStrengthMeter.tsx`（弱口令建议前置「弱口令将无法提交」）、`src/features/settings/passphraseStrength.ts`（头注释改）、`e2e/p1-encrypted-backup.spec.ts`（AT-40 改测 + AT-45 新增）、`src/api/generated/types.ts`（重新生成，不入库）。
- 已运行验证：
  - `cd backend && mvn test -Dtest=BackupPassphraseStrengthIntegrationTest`：6 tests，0 failures；Flyway V1→V25 成功（无新迁移）。
  - `cd backend && mvn test -Dtest='BackupIntegrationTest,BackupRestoreIntegrationTest,BackupDeletionIntegrationTest,BackupPurgeIntegrationTest,BackupOrphanScanIntegrationTest,BackupRetainIntegrationTest,BackupScheduleIntegrationTest'`：47 tests，0 failures（弱口令升级后既有契约不破坏）。
  - `cd backend && mvn clean test`：159 tests，0 failures，0 errors，0 skipped；Flyway V1→V25 成功。
  - `cd frontend && npm run gen-types && npm run typecheck && npm run lint && npm run build`：全部通过（构建仅有既有 chunk-size 提示）。
  - `cd frontend && npm run e2e -- e2e/p1-encrypted-backup.spec.ts --reporter=dot`：9 passed（含修订 AT-40 与新增 AT-45）。
  - `cd frontend && npm run e2e -- --reporter=dot`：36 passed，3 failed（p1-encrypted-backup AT-38 定时备份触发、p1-ai-task-suggestion、p1-notifications）；单独复跑这 3 个全部 passed（21.4s），证实为既有 flaky（全量 E2E 下 4 个 webServer 资源竞争 + AI 时序竞态），与本切片无代码关联（本切片未碰定时触发逻辑、AI 任务建议、通知代码与后端）。
- 验证结果：强度强制门槛链路（创建弱口令 → 400 VALIDATION_ERROR + score + ≥40 + 不生成记录/文件；武装弱口令 → 400 + armed 仍 false；中等/强口令 → 201/200 放行；恢复弱口令 → 豁免返 422 非强度 400；passphrase 不落盘/不进日志/不回显）有集成测试与浏览器级 E2E 覆盖；OpenAPI 变更为描述补强（非破坏性，既有字段与状态码不变）；无数据库迁移（内存校验）；passphrase 与派生密钥不落盘、不回显、不进日志、不参与清理验证；前后端算法与黑名单以状态机 §9 为单一事实来源防漂移。
- 已知问题：
  - 全量 E2E 下 p1-encrypted-backup AT-38（定时备份触发）、p1-ai-task-suggestion、p1-notifications 偶发失败（4 个 webServer 资源竞争 + AI 时序竞态 + 定时调度线程延迟），单独复跑通过，属既有共享库时序竞态模式，与本切片无代码关联。
  - 全量 E2E 仍输出既有 React Router future flag 与 Node `NO_COLOR` 提示，不影响断言。
  - Git 仍可能显示既有 LF→CRLF 行尾提示，不影响仓库检查。
  - 强度算法为启发式打分（长度+字符种类−弱模式），非密码学熵估算，符合「门槛」定位（拒绝弱口令，非安全保证）；用户可输入中等强度（fair）口令提交。
- 下一窗口只做：由用户指定下一个 V1.0/高级趋势最小切片（候选：第三方日历同步最小化单向 ICS 订阅、孤儿文件清理审计日志、备份恢复后强制 passphrase 重设提示）；先定义 OpenAPI、状态机、数据库语义、页面路径和验收场景，再开发。
- 不要重复做：不要重建 PassphraseStrengthValidator 算法或黑名单；不要给恢复端点加强度门槛（passphrase 已与备份绑定，强制会锁死历史备份）；不要改前端强度计为禁用提交按钮（后端是唯一闸门，不禁用）；不要新增评估端点（passphrase 本就要传给加密端点，无额外传输）；不要改 V1~V25 既有迁移；不要做强制 fair/strong（仅拒绝 weak，留后续切片）；不要改加密算法/迭代次数/passphrase 落盘规则/长度限制。

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

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：P1（V0.2）已完成二十一个切片；本窗口实现强制 strong 口令门槛升级（AT-48），把创建/武装入口从「拒绝 weak」收紧为「要求 strong（≥70）」，恢复后重设提示同步扩到 weak+fair。
- 里程碑说明：V0.2 主流程已完成，AI 供应商配置删除切片已完成。附件仍遵守本地安全约束，只保存用户填写的引用元数据，不实现文件上传、读取、扫描、下载或校验。
- 当前里程碑：P1/V0.2 `DONE`；P0 四个里程碑 M1~M4 与 AT-01~AT-24 保持全部完成，新增 P1 验收 AT-17A~AT-17D、AT-26 已覆盖。
- 当前任务：强制 strong 口令门槛升级切片（AT-48）已在窗口 2026-09-07-3 完成并发布；除 V0.3/V1 外无待实现的已定义 P0/P1 契约需求。
- 当前负责人窗口：Codex。
- 最后更新：2026-09-07（窗口 2026-09-07-3）。

## 2. 已完成内容

- PRD v1.2：`jobhub-prd.md`
- 页面规格、状态机、OpenAPI、数据库设计、验收用例、技术实施方案：`docs/jobhub/01-06`
- 初始 Flyway 迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`（29 张表，不可修改）
- 脱敏演示数据：`fixtures/v0.1-demo-data.json`
- 实现约束：`AGENTS.md`
- 实现总控提示词：`docs/jobhub/IMPLEMENTATION_MASTER_PROMPT.md`

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

> 最近 5 个窗口的交接记录按倒序排列在文档开头（标题与「## 1. 当前总状态」之间）。为控制文档体积，本文件只保留最近 5 个窗口的交接内容，更早的历史交接已移除，可经 git 历史按需查阅。新窗口完成后追加到文档开头，并在交接记录超过 5 个时移除最旧的一条。

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
