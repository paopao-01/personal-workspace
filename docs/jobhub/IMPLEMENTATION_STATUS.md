# JobHub 实现进度与动态交接

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：P1（V0.2）第一切片已完成（设置页时区与默认提醒节点：`GET/PUT /api/settings` 落地、面试默认提醒接入用户配置、`/settings` 表单与全局时区显示）。P0 四个里程碑 M1~M4 与 AT-01~AT-24 保持全部完成。
- 当前里程碑：P1/V0.2 `IN_PROGRESS`；已完成设置切片，候选后续切片：通知中心（需先补 `/notifications` 契约）、简历定制前置、P05 投递卡片时间线等 UI 增强。
- 当前任务：P1 设置切片已完成；下一窗口建议实现通知中心（先在 OpenAPI 补 `GET /notifications`、`POST /notifications/{id}/read` 契约，V1 已有 `notification` 表），或与用户确认其他 P1 优先级。
- 当前负责人窗口：Codex。
- 最后更新：2026-08-29。

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

- `backend/` 已含完整 Spring Boot 工程结构与 job + application + dashboard + interview + review + task + evidence + datamanagement 模块业务代码（`com.jobhub` 主代码约 189 个 Java 文件，其中 evidence 模块 16 个、datamanagement 18 个：trash 5 + 导出 7 + settings 6）。
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

### 窗口 2026-08-29-7

- 目标：P1（V0.2）第一切片——设置页时区与默认提醒节点；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**（P1 方向由用户确认为"设置页时区与提醒节点"）。
- 已完成：
  - 后端 datamanagement 新增 settings 四层：`GET /api/settings`、`PUT /api/settings`（Idempotency-Key + If-Match-Version，缺版本 400、版本冲突 409）。契约（UserSettings/UserSettingsUpdateRequest schema）无需修改。
  - 校验规则：timeZone 必填且必须是合法 IANA 时区（`ZoneId.of` 校验，非法返回 `400 VALIDATION_ERROR` 及示例提示）；提醒偏移分钟数逐项 `@Min(1)`，服务端去重、倒序归一化；空数组合法，表示不生成默认提醒。
  - 面试默认提醒接入用户配置：`InterviewService.createDefaultReminders` 改为读取 `user_setting.default_reminder_offsets_json`，1440/120/30 映射 ONE_DAY/TWO_HOURS/THIRTY_MINUTES，其余为 CUSTOM；创建与改期均按当前配置重新生成；配置为空则不生成。行为不改变种子默认值 `[1440,120,30]`，既有 AT-10/AT-11 断言不受影响。
  - 前端：`api/settings` 新增 settingsApi/useSettingsQueries/useSettingsMutations；`displayTimeZone` 模块单例 + `AppLayout` 挂载时同步设置，`formatDateTime` 按用户配置时区格式化（未加载时回退浏览器本地时区）；面试事件时区显示逻辑不变。
  - `/settings` 页新增"时区与提醒"区块（页面规格 P11 首位）：IANA 时区输入、预设节点勾选（1 天/2 小时/30 分钟）、自定义节点添加、保存后按服务端版本重挂载表单（无 effect 状态同步，React Compiler lint 干净）。
  - `DatabaseCleaner` 每用例将 `user_setting` 重置为种子值，防止依赖默认提醒节点的用例跨类串扰。
  - 新增 `frontend/e2e/p1-settings-reminders.spec.ts`：UI 修改时区/关闭预设/添加自定义节点并保存 → 刷新后表单与 API 状态一致 → 通过 API 恢复种子设置并验证表单还原。
- 未完成：
  - 通知中心：OpenAPI 无 `/notifications` 端点，`notification` 表尚未被任何业务流写入（P0 提醒以面试详情列表展示）。
  - 提醒节点变更不影响已生成的提醒，仅作用于之后创建/改期的面试（符合 PRD"修改面试时间后按新时间重新生成"的语义）。
  - 时区设置仅影响 `formatDateTime` 通用时间显示；面试事件时区（创建时选择）展示逻辑未改动。
  - `/settings` 页仍缺导出历史列表（无列表端点）与设置页其余 P1 项。
- 修改文件：
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 新增：`backend/src/main/java/com/jobhub/datamanagement/{domain/UserSettings,infrastructure/UserSettingsMapper,application/SettingsService,api/UserSettingsUpdateRequest,api/UserSettingsResponse,api/SettingsController}.java`。
  - 修改：`backend/src/main/java/com/jobhub/interview/application/InterviewService.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/SettingsIntegrationTest.java`。
  - 新增：`frontend/src/api/settings/{settingsApi,useSettingsQueries,useSettingsMutations,displayTimeZone}.ts`。
  - 修改：`frontend/src/features/jobs/statusLabels.ts`、`frontend/src/components/layout/AppLayout.tsx`、`frontend/src/features/settings/SettingsPage.tsx`。
  - 新增：`frontend/e2e/p1-settings-reminders.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=SettingsIntegrationTest"` -> 2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，52 tests，0 failures，0 errors。
  - `cd frontend && npm run lint` -> 0 warning / 0 error（重构消除 set-state-in-effect warning）；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-settings-reminders.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 11 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings）。
- 验证结果：
  - 设置读写、乐观锁、时区与偏移校验、面试默认提醒按配置生成（含改期）均有集成测试覆盖；UI 保存/刷新/还原路径有 E2E 覆盖。
  - 本窗口未修改 OpenAPI（契约已声明）；未新增数据库迁移（V1 `user_setting` 表支撑）。
- 已知问题：
  - `user_setting` 无独立行级审计；乐观锁版本随更新递增。
  - E2E 共享临时库，`p1-settings-reminders.spec.ts` 结束时通过 API 恢复种子设置。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现通知中心：先在 OpenAPI 补 `GET /notifications`（列表 + 未读数）与 `POST /notifications/{id}/read` 契约，明确 notification 何时写入（提醒到期标记 SENT 时、面试改期/取消时），再实现 datamanagement 通知四层与前端入口（TopBar 未读角标或独立页面），补集成测试与 E2E。
  - 或与用户确认其他 P1 优先级（简历定制前置、P05 卡片时间线等）。
- 不要重复做：
  - 不要重建 settings 四层、`/settings` 时区表单或 `displayTimeZone` 机制。
  - 不要让提醒节点变更追溯修改已生成提醒；不要在 P0/P1 中引入系统级推送、邮件、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-6

- 目标：在 `dev` 分支实现 AT-24 JSON 导出，M4 收尾；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 扩展：新增 `GET /data-exports/{exportId}/download`（返回导出 JSON 文件，404 走 NotFound）；`DataExport.downloadUrl` 从 `format: uri` 调整为 `uri-reference`（相对 API 路径 `/api/data-exports/{exportId}/download`），为非破坏性细化；其余导出契约不变。
  - 新增 datamanagement 导出四层：`POST /api/data-exports`（Idempotency-Key，202 返回任务）、`GET /api/data-exports/{exportId}`、`GET /api/data-exports/{exportId}/download`。`ExportCreateRequest.format` 用 `@Pattern("JSON")` 校验，非法格式返回 `400 VALIDATION_ERROR`。
  - P0 在创建请求内同步完成导出：QUEUED → RUNNING → SUCCEEDED（写文件）或 FAILED（记录 failureReason），状态落 `data_export` 表。
  - `ExportDataMapper` 覆盖 23 张业务表（岗位/要求/匹配、投递/状态日志、面试/清单/提醒、复盘/问题、知识点、任务/来源、技能/别名/自评/证据关联、项目/证据/关联、notification），导出内容含业务数据、软删行和关联 ID。
  - AT-24 排除规则：不导出 `user_profile`、`user_setting`、`audit_log`、`idempotency_record`、`data_export`、`trash_item`；`application_status_log` 显式排除 `idempotency_key` 列（唯一含幂等键的业务表）。
  - 导出目录可配置：`jobhub.export-dir`（默认 `./data/exports`，测试 profile 指向 `./target/exports`）。
  - 前端 `api/settings` 新增 exportApi + `useCreateExport`；`/settings` 页新增"数据导出"区块：创建前展示数据范围与不包含项（页面规格 P11 要求），创建后显示状态 Badge、创建时间、下载链接或失败原因。
  - 新增 `frontend/e2e/at-24-data-export.spec.ts`：API 造岗位 → 真实 UI 创建导出 → 断言数据范围/排除项文案 → 下载文件 → 断言包含业务数据且不含幂等记录/审计日志。
- 未完成：
  - `/settings` 的时区、默认提醒节点区块（`GET/PUT /api/settings`）尚未实现，属 P1 候选。
  - 导出任务无历史列表端点，页面仅展示本次创建的导出；刷新后不再显示旧导出。
  - 导出为同步完成，无真正异步队列；超大数据量时请求耗时随数据增长（本地单用户可接受）。
  - P1/V0.2 的导入、简历定制、通知中心均未开始。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`（download 端点 + downloadUrl 细化）、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/src/main/resources/application.yml`（jobhub.export-dir）、`backend/src/test/resources/application-test.yml`。
  - 新增：`backend/src/main/java/com/jobhub/datamanagement/{domain/DataExport,infrastructure/DataExportMapper,infrastructure/ExportDataMapper,application/ExportService,api/ExportCreateRequest,api/DataExportResponse,api/ExportController}.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`（新增 data_export 清理）。
  - 新增：`backend/src/test/java/com/jobhub/integration/ExportIntegrationTest.java`。
  - 新增：`frontend/src/api/settings/{exportApi,useExportMutations}.ts`；修改：`frontend/src/features/settings/{SettingsPage.tsx,settingsLabels.ts}`。
  - 新增：`frontend/e2e/at-24-data-export.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ExportIntegrationTest"` -> 2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，50 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/at-24-data-export.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 10 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10），runner 自然返回。
- 验证结果：
  - AT-24 后端与浏览器路径均已覆盖：导出含业务数据及关联 ID，不含幂等记录/审计日志/回收站。
  - M4 完成，里程碑表已更新为 `DONE`；AT-01 至 AT-24 全部有自动化覆盖。
  - 本窗口 OpenAPI 变更为新增 download 端点与 downloadUrl 格式细化，非破坏性。
  - 本窗口未新增数据库迁移；V1 既有 `data_export` 表可支撑本切片。
- 已知问题：
  - `data_export.download_path` 保存服务端绝对/相对文件路径，仅由服务端写入；下载端点按 id 读取对应文件，路径不接受客户端输入。
  - 导出文件包含软删除的业务行（数据归用户所有），如需"仅导出有效数据"需另立需求。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认 P1（V0.2）优先级后启动：候选为 `/settings` 时区与默认提醒节点（契约已有 `GET/PUT /api/settings`）、通知中心（V1 已有 `notification` 表）、P05 投递卡片时间线等 P0 遗留增强；先核对对应契约与页面规格，再按既有切片流程（OpenAPI → 后端 → 前端 → 测试）实现。
- 不要重复做：
  - 不要重建 datamanagement 导出/trash 模块或 `/settings` 页已有区块。
  - 不要把 `user_profile`/`user_setting`/审计/幂等/回收站数据加入导出，除非用户明确要求并更新 AT-24。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-5

- 目标：在 `dev` 分支实现 AT-23 最近删除（软删除 + trash 恢复保留引用关系）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 扩展：新增 `DELETE /projects/{projectId}` 与 `DELETE /evidence/{evidenceId}`（Idempotency-Key + If-Match-Version，204），`EvidenceReference` 新增可选 `trashed` 布尔字段；既有 `/trash`、`/trash/{id}/restore`、`/trash/{id}/permanent`（`X-Confirm-Permanent-Delete: true` 必需）契约不变，已重新生成前端类型。
  - 新增后端 `datamanagement` 模块四层：`GET /api/trash`、`POST /api/trash/{trashId}/restore`、`DELETE /api/trash/{trashId}/permanent`。TrashService 统一管理 trash_item 写入（影响摘要 JSON、30 天 `expires_at`）、恢复（资源 `deleted_at` 置空 + `restored_at` 标记）与永久删除（`purged_at` 标记 + 硬删及关联清理）。
  - 三类实体接入软删除：project（DELETE 校验版本并记录"N 条证据引用"）、evidence（记录"N 个项目案例引用/N 项技能关联"）、interview-question（契约既有端点，实现于 review 模块，记录"N 个知识点关联"）。
  - AT-23 关键规则：被项目案例或技能引用的证据永久删除返回 `422 BUSINESS_RULE_ERROR`（不能静默永久删除）；项目永久删除清理自身 `project_evidence`；问题永久删除清理 `question_knowledge` 与 `task_source`，学习任务保留；恢复后资源 ID 不变、引用自动还原。
  - `EvidenceReference.trashed` 贯穿项目 refs 与准备包：删除后引用方显示"来源已删除"；项目编辑表单允许保留已删证据关联（后端校验忽略软删状态），恢复后自动还原。
  - 前端新增 `api/settings` trash 三件套和 `/settings` 页最近删除区（列表含类型、删除时间、过期天数、影响摘要；恢复按钮；永久删除二次确认），启用侧边栏"设置"入口。
  - `/projects` 页项目/证据行新增"删除"按钮，原生确认框展示直接和间接影响；项目证据引用与准备包证据引用显示"来源已删除"标记。
  - 新增 `frontend/e2e/at-23-trash-restore.spec.ts`：真实 UI 删除被引用证据（断言确认框影响文本）→ 项目行显示"来源已删除" → 设置页恢复 → 引用还原且 API 验证证据 ID 不变 → 独立证据永久删除成功 → 被引用证据永久删除被拒绝。
- 未完成：
  - AT-24 完整 JSON 导出尚未实现；M4 剩余收尾。
  - `/settings` 的时区、默认提醒节点、导出等区块尚未实现（页面规格 P11 属后续切片）。
  - 学习任务页尚未对"来源已删除"的任务来源做显式标记（任务列表当前不渲染问题来源）。
  - 过期 trash（超 30 天）无自动清理调度，仅保留手动永久删除入口。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`（project/evidence DELETE + EvidenceReference.trashed）、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 新增：`backend/src/main/java/com/jobhub/datamanagement/{domain/TrashItem,infrastructure/TrashMapper,application/TrashService,api/TrashItemResponse,api/TrashController}.java`。
  - 修改：`backend/src/main/java/com/jobhub/evidence/{domain/Evidence,infrastructure/ProjectMapper,infrastructure/EvidenceMapper,application/ProjectService,application/EvidenceService,api/EvidenceReferenceResponse,api/ProjectController,api/EvidenceController}.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/{infrastructure/QuestionMapper,application/ReviewService,api/ReviewController}.java`。
  - 修改：`backend/src/main/java/com/jobhub/interview/{application/EvidenceReference,infrastructure/PreparationMapper,api/EvidenceReferenceResponse}.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`（新增 trash_item 清理）。
  - 新增：`backend/src/test/java/com/jobhub/integration/TrashIntegrationTest.java`。
  - 新增：`frontend/src/api/settings/{trashApi,useTrashQueries,useTrashMutations}.ts`、`frontend/src/features/settings/{SettingsPage.tsx,settingsLabels.ts}`。
  - 修改：`frontend/src/api/projects/{projectApi,useProjectMutations}.ts`、`frontend/src/api/generated/types.ts`（重新生成）、`frontend/src/features/projects/ProjectsPage.tsx`、`frontend/src/features/interviews/InterviewPreparationPage.tsx`、`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`。
  - 新增：`frontend/e2e/at-23-trash-restore.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=TrashIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，48 tests，0 failures，0 errors（两次：接入软删除前后各一次）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/at-23-trash-restore.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 9 tests passed（AT-01/09/11/15/16/18/20/23 + P10），runner 自然返回。
- 验证结果：
  - AT-23 后端与浏览器路径均已覆盖：删除展示影响、回收站、恢复保留引用 ID、被引用证据禁止永久删除。
  - 本窗口 OpenAPI 变更仅为新增 DELETE 端点与 `EvidenceReference.trashed` 可选字段，非破坏性。
  - 本窗口未新增数据库迁移；V1 既有 `trash_item`（含 `impact_summary_json`/`expires_at`/`restored_at`/`purged_at`）与各资源表 `deleted_at` 列可支撑本切片。
- 已知问题：
  - 恢复/永久删除使用 trash 记录 ID（非资源 ID），前端已按此对接。
  - 全量 E2E 共享临时库，`at-23` 用例使用唯一后缀隔离数据。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现 AT-24 JSON 导出：先核对 OpenAPI `POST /api/data-exports`、`GET /api/data-exports/{exportId}` 与 `DataExport` schema，实现 datamanagement 导出端点，包含岗位、投递、面试、复盘、问题、知识点、任务、技能和证据，排除令牌、密钥、完整日志、idempotency_record 和未确认 AI 输入输出；M4 收尾后对照 05 文档核对 AT-20~AT-24 全部发布门槛。
- 不要重复做：
  - 不要重建 datamanagement trash 模块、`/settings` 最近删除区或 evidence 模块 CRUD。
  - 不要为垃圾箱数据实现自动清理调度、批量恢复或关联修复报告（PRD 归入后续版本）。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-4

- 目标：在 `dev` 分支实现 P10 最小项目案例与证据引用 CRUD 和 `/projects` 页面；验证无问题后合并回 `main`。
- 状态：**DONE**（`dev` 与 `main` 已合并，用户确认后均已推送到远程：`dev = 46af103`、`main = 170b9f2`）。
- 已完成：
  - OpenAPI 细化：`ProjectCaseSummary` 新增可选 `result` 字段（含“不生成虚构指标”描述）。原因：`ProjectCaseCreateRequest` 有可选 `result` 而响应缺失该字段，全字段覆盖 PUT 会在每次编辑时静默清空 `result_text`；属非破坏性细化（先例：`manualMatchStatus`），已重新生成前端类型。
  - 新增后端 `evidence` 模块四层：`GET/POST /api/projects`、`PUT /api/projects/{projectId}`、`GET/POST /api/evidence`、`PUT /api/evidence/{evidenceId}`，与 OpenAPI 端点一一对应（GET 返回数组、无分页、无 DELETE——删除属后续 AT-23）。
  - PUT 要求 `If-Match-Version`（缺失返回 400），POST/PUT 由全局幂等拦截器覆盖；更新失败（0 行受影响）返回 `409 VERSION_CONFLICT`；资源不存在返回 404。
  - 项目更新按全字段覆盖并同步 `project_evidence` 关联；证据更新同步 `skill_evidence`；引用不存在或已软删的 evidence/skill 返回 `422 BUSINESS_RULE_ERROR` 且事务回滚无副作用。
  - `urlOrPath` 仅作为文本保存，不读取、不扫描、不上传，不写入日志；未实现任何 DELETE，未触碰 `deleted_at` 字段。
  - 前端新增 `api/projects` 三件套和 `/projects` 页面（项目案例 + 证据引用两个区块，创建/编辑表单、证据多选关联、urlOrPath 隐私提示“应用不会自动读取、扫描或上传被引用的文件”）；侧边栏新增“项目与证据”入口（`/skills` 保持禁用）。
  - 准备包页项目案例区改为可跳转 `/projects`，无项目案例时空状态提供“打开项目与证据”操作（修复上一窗口已知问题）。
  - 新增 `frontend/e2e/projects-evidence-crud.spec.ts`：真实 UI 创建证据（含 urlOrPath 文本引用）→ 创建项目并关联证据 → 编辑证据名称后项目关联引用随查询刷新。
  - 修复 `at-15-quick-review.spec.ts` 中失效断言：已记录问题行的回答状态自窗口 2026-08-29-1 起渲染为行内下拉框，原 `getByText('未答出').last()` 命中隐藏 `<option>`，改为断言 select 的 `UNANSWERED` 值。该回归与本次改动无关，属既有 E2E 失效修复。
- 未完成：
  - AT-23 最近删除（软删除入口、`GET /api/trash`、恢复后引用 ID 不变）、AT-24 完整 JSON 导出尚未实现；本模块无删除端点。
  - 证据 `skillIds` 仅 API 支持，UI 暂不提供技能选择（技能列表接口未实现）；技能可见性无独立 `/skills` 页面。
  - 准备包项目匹配仍依赖 `requirement_skill -> skill_evidence -> project_evidence` 链路；用户手工创建的项目如未通过该链路关联已确认要求，不会出现在准备包中。
  - `ProjectCaseSummary.result` 现已回显，但历史窗口 seed 的测试数据仍由 e2e 夹具写入。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`（ProjectCaseSummary.result）、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 新增：`backend/src/main/java/com/jobhub/evidence/domain/{EvidenceType,ProjectCase,Evidence}.java`、`infrastructure/{ProjectMapper,EvidenceMapper}.java`、`application/{ProjectCreateCommand,EvidenceCreateCommand,ProjectService,EvidenceService}.java`、`api/{ProjectCaseCreateRequest,EvidenceCreateRequest,EvidenceReferenceResponse,ProjectCaseSummaryResponse,EvidenceResponse,ProjectController,EvidenceController}.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/ProjectEvidenceIntegrationTest.java`。
  - 新增：`frontend/src/api/projects/{projectApi,useProjectQueries,useProjectMutations}.ts`、`frontend/src/features/projects/{ProjectsPage.tsx,projectLabels.ts}`。
  - 修改：`frontend/src/api/generated/types.ts`（gen-types 重新生成）、`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`、`frontend/src/features/interviews/InterviewPreparationPage.tsx`、`frontend/src/styles/globals.css`。
  - 新增：`frontend/e2e/projects-evidence-crud.spec.ts`；修改：`frontend/e2e/at-15-quick-review.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ProjectEvidenceIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，45 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过（ProjectCaseSummary 含 result）。
  - `cd frontend && npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过，生产构建成功。
  - `cd frontend && npx playwright test e2e/projects-evidence-crud.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 8 tests passed（AT-01/09/11/15/16/18/20 + P10），runner 自然返回。
- 验证结果：
  - P10 后端与浏览器路径均已覆盖；项目案例与证据引用可由用户在 UI 中维护并进入准备包数据链路。
  - 本窗口仅细化 OpenAPI 的 `ProjectCaseSummary.result`，其余端点契约未变。
  - 本窗口未新增数据库迁移；V1 既有 `project`、`evidence`、`project_evidence`、`skill_evidence`、`skill` 表可支撑本切片。
  - E2E 首次运行需重新安装 Chromium（本机浏览器缓存被清理），`npx playwright install chromium` 后恢复；下载曾一次卡顿，重试成功。
- 已知问题：
  - 全量 E2E 共享同一临时库，跨用例数据可见；`projects-evidence-crud.spec.ts` 因此不断言全局空状态，仅用唯一后缀隔离数据。
  - E2E 仍输出 React Router v7 future flag 警告和 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现 AT-23 最近删除：为 project/evidence（及既有可删实体）补软删除命令与 `GET /api/trash`、`POST /api/trash/{id}/restore`，删除前展示直接/间接影响，恢复后引用 ID 不变；先核对 OpenAPI 既有 trash 契约再实现。
  - 或实现 AT-24 JSON 导出（`POST /api/data-exports`），排除令牌、密钥、idempotency_record 和未确认 AI 内容。
- 不要重复做：
  - 不要重建 evidence 模块 CRUD、`/projects` 页面或 AT-20 准备包聚合。
  - 不要在本切片上追加 DELETE 端点或绕过 trash 体系直接物理删除；不要提前实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-3

- 目标：在 `dev` 分支实现 AT-20 面试准备包聚合且可追溯；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增 `GET /api/interviews/{interviewId}/preparation` 后端实现，返回只读聚合的 `PreparationPack`，不复制业务数据。
  - 准备包聚合面试摘要、已确认岗位要求差距、项目案例/证据摘要、同岗位历史问题、关联未完成学习任务和面试准备事项。
  - 每个 `prioritizedItem` 都包含至少一个排序原因和 `sourceRef`；优先项来源覆盖 `JOB_REQUIREMENT`、`QUESTION`、`TASK`、`PROJECT_CASE`、`CHECKLIST`。
  - `requirements` 只基于 `CONFIRMED` 岗位要求；`PENDING` 要求不进入确定性准备结论。
  - 没有关联项目案例时返回“待补充项目案例”占位，不伪造项目描述、量化结果或综合能力分数。
  - 前端新增 `/interviews/:interviewId/preparation` 面试准备包页面；从面试详情可打开准备包；页面展示优先准备项、岗位要求与差距、可讲项目案例、历史问题、未完成任务和准备事项。
  - 新增 AT-20 Playwright E2E：构造岗位/投递/历史复盘/任务/项目证据后打开准备包，断言聚合区块可见，并检查 API 中每个优先项有 reason/sourceRef。
  - 扩展仅 `e2e` profile 可用的测试夹具接口，用于在项目/证据 CRUD 尚未实现前为 AT-20 写入项目证据测试数据。
- 未完成：
  - P10 项目案例和证据引用 CRUD 尚未实现；当前真实项目/证据只能由测试夹具或后续接口写入。
  - 准备事项仍为只读展示，未实现勾选完成操作；本窗口只覆盖准备包读取路径。
  - 准备包项目匹配当前依赖 `requirement_skill -> skill_evidence -> project_evidence` 链路；因尚无项目-技能直接关系表，未做更复杂关联。
  - 最近删除、完整 JSON 导出尚未实现。
- 修改文件：
  - 新增：`backend/src/main/java/com/jobhub/interview/application/PreparationService.java`、`PreparationPack.java`、`PreparationItem.java`、`SourceRef.java`、`ProjectCaseSummary.java`、`EvidenceReference.java`、`ChecklistItem.java`。
  - 新增：`backend/src/main/java/com/jobhub/interview/infrastructure/PreparationMapper.java`。
  - 新增：`backend/src/main/java/com/jobhub/interview/api/PreparationPackResponse.java`、`PreparationItemResponse.java`、`SourceRefResponse.java`、`ProjectCaseSummaryResponse.java`、`EvidenceReferenceResponse.java`、`ChecklistItemResponse.java`。
  - 修改：`backend/src/main/java/com/jobhub/interview/api/InterviewController.java`、`backend/src/main/java/com/jobhub/interview/infrastructure/ChecklistMapper.java`。
  - 修改：`backend/src/main/java/com/jobhub/testsupport/api/E2eReminderFixtureController.java`（仅 `e2e` profile 测试夹具扩展）。
  - 新增：`backend/src/test/java/com/jobhub/integration/PreparationIntegrationTest.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`。
  - 修改：`frontend/src/api/interviews/interviewApi.ts`、`useInterviewQueries.ts`、`frontend/src/api/tasks/taskApi.ts`。
  - 新增：`frontend/src/features/interviews/InterviewPreparationPage.tsx`。
  - 修改：`frontend/src/features/interviews/InterviewDetailPage.tsx`、`frontend/src/features/tasks/taskLabels.ts`、`frontend/src/app/routes.tsx`、`frontend/src/styles/globals.css`。
  - 新增：`frontend/e2e/at-20-preparation-pack.spec.ts`。
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=PreparationIntegrationTest"` -> BUILD SUCCESS，2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，42 tests，0 failures，0 errors。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，186 modules。
  - `cd frontend && npx playwright test e2e/at-20-preparation-pack.spec.ts --reporter=list` -> 1 passed，自然返回。
- 验证结果：
  - AT-20 后端和浏览器路径均已覆盖；准备包聚合且每个优先准备项可追溯。
  - AT-21 的关键后端规则已覆盖：无项目资料时显示“待补充”，不返回虚构项目描述、量化结果或综合能力分数。
  - 本窗口未修改 OpenAPI；使用既有 `PreparationPack` 契约。
  - 本窗口未新增数据库迁移；V1 既有 `project`、`evidence`、`project_evidence`、`skill_evidence`、`requirement_skill`、`learning_task`、`task_source` 等表可支撑本切片。
- 已知问题：
  - 项目/证据 CRUD 尚未实现，普通用户暂不能在 UI 中维护准备包项目案例；E2E 通过 `e2e` profile 测试夹具造数。
  - 准备包中的项目来源链接目前不跳转项目页，因为 `/projects` 页面未实现。
  - E2E 仍输出 React Router v7 future flag 警告和 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现 P10 最小项目案例与证据引用 CRUD：`GET/POST/PUT /api/projects`、`GET/POST/PUT /api/evidence` 的最小路径，以及 `/projects` 页面，让用户能维护准备包使用的真实项目案例和外部证据引用。
  - 保持 P0 规则：证据 `urlOrPath` 只作为文本保存，不读取、扫描或上传本地路径；不生成虚构指标。
- 不要重复做：
  - 不要重建 AT-20 preparation pack 聚合服务和页面。
  - 不要提前实现最近删除、完整 JSON 导出、AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-29-2

- 目标：在 `dev` 分支扩大切片实现 AT-18 从问题创建任务和 AT-19 完成任务不改能力；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增后端 task 模块最小四层实现：`GET /api/tasks`、`POST /api/tasks`、`PUT /api/tasks/{taskId}`、`POST /api/tasks/{taskId}/transition`。
  - 新增 `POST /api/interview-questions/{questionId}/create-task`：仅在用户确认提交后创建任务；`CREATE_NEW` 要求标题、验收标准和验证方式；`LINK_EXISTING` 可关联已有任务；`FULLY_ANSWERED` 问题拒绝创建任务。
  - 学习任务状态转换按专用命令执行：`TODO -> IN_PROGRESS/ABANDONED`、`IN_PROGRESS -> COMPLETED/ABANDONED/TODO`、`COMPLETED -> IN_PROGRESS`、`ABANDONED -> TODO`；非法转换返回 `422 ILLEGAL_STATE_TRANSITION`。
  - 任务完成可保存 `verificationResult`；未填写时显式保存“未验证完成”；完成任务不会更新 `user_skill.self_level`，也不会清除历史薄弱题。
  - 任务来源通过 `task_source` 写入 `QUESTION` 和 `KNOWLEDGE_POINT`；无来源的手工任务写入 `MANUAL`。由于 V1 `task_source.source_type` 没有 `JOB`，本窗口未支持前端/服务端写 `relatedJobIds`。
  - 前端新增 task API/hooks、`/tasks` 学习任务页面和侧边栏入口；支持手工创建、状态筛选、开始/完成/放弃/恢复，并在完成时填写验证结果。
  - 快速复盘页为 `PARTIALLY_ANSWERED/UNANSWERED` 问题新增“创建学习任务”确认表单；打开预填表单不写库，点击“确认创建”后才调用后端。
  - 新增 AT-18 Playwright E2E：构造弱问题后打开创建任务表单，断言任务数仍为 0；确认提交后任务数为 1 且 `/tasks` 可见。
- 未完成：
  - M4 准备包/项目案例/证据/导出/最近删除尚未实现。
  - `relatedJobIds` 因 V1 来源枚举缺少 `JOB` 暂未支持；后续若需要关联岗位，应新增迁移扩展 `task_source.source_type` 或调整契约。
  - 任务详情页、任务编辑表单、任务来源下钻展示仍为后续增强；本窗口只做 P09 最小列表和状态操作。
  - 复盘完成后的 reopen 仍未实现；当前已完成复盘仍不允许继续新增问题。
- 修改文件：
  - 新增：`backend/src/main/java/com/jobhub/task/domain/TaskStatus.java`、`TaskPriority.java`、`TaskSourceType.java`、`LearningTask.java`。
  - 新增：`backend/src/main/java/com/jobhub/task/application/TaskService.java`、`TaskCreateCommand.java`、`TaskUpdateCommand.java`、`TaskTransitionCommand.java`、`TaskListQuery.java`、`TaskListResult.java`、`CreateTaskFromQuestionCommand.java`。
  - 新增：`backend/src/main/java/com/jobhub/task/infrastructure/TaskMapper.java`。
  - 新增：`backend/src/main/java/com/jobhub/task/api/TaskController.java`、`TaskCreateRequest.java`、`TaskUpdateRequest.java`、`TaskTransitionRequest.java`、`CreateTaskFromQuestionRequest.java`、`LearningTaskResponse.java`、`PageTaskResponse.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/TaskIntegrationTest.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`。
  - 新增：`frontend/src/api/tasks/taskApi.ts`、`useTaskQueries.ts`、`useTaskMutations.ts`。
  - 新增：`frontend/src/features/tasks/TaskListPage.tsx`、`taskLabels.ts`。
  - 修改：`frontend/src/features/reviews/InterviewReviewPage.tsx`。
  - 修改：`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`。
  - 新增：`frontend/e2e/at-18-create-task-from-question.spec.ts`。
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=TaskIntegrationTest"` -> BUILD SUCCESS，2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，40 tests，0 failures，0 errors。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，185 modules。
  - `cd frontend && npx playwright test e2e/at-18-create-task-from-question.spec.ts --reporter=list` -> 1 passed，自然返回。
- 验证结果：
  - AT-18 后端与浏览器路径均已覆盖；预填不落库、确认后落库并写 `task_source`。
  - AT-19 后端已覆盖；完成任务保留验证结果，不改变 `user_skill.self_level`，薄弱题仍可在 `/knowledge-points/weak` 下钻查看。
  - M3 的 AT-15 至 AT-19 均已完成；可进入 M4。
  - 本窗口未新增数据库迁移；V1 既有 `learning_task`、`task_source`、`user_skill`、`skill` 表可支撑本切片。
- 已知问题：
  - `relatedJobIds` 暂不支持，原因是 V1 `task_source.source_type` 未包含 `JOB`；当前若请求中包含相关岗位会返回业务错误。
  - 任务状态转换没有单独历史表；V1 没有任务状态日志表，本窗口仅更新当前任务状态并依赖审计/后续增强。
  - E2E 仍输出 React Router v7 future flag 警告和 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 进入 M4，实现 AT-20 面试准备包聚合且可追溯：`GET /api/interviews/{interviewId}/preparation` 聚合面试、岗位要求/差距、历史问题、未完成任务、检查清单，并确保每个 `prioritizedItem` 至少有一个 reason 和 sourceRef。
  - 如 AT-20 需要项目案例最小数据，可只补读取/展示占位“待补充”，不要提前做完整项目/证据 CRUD。
- 不要重复做：
  - 不要重建 AT-15/AT-16 review、AT-17 弱点统计或 AT-18/AT-19 学习任务基础能力。
  - 不要提前实现 AT-23 最近删除、AT-24 导出、AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-29-1

- 目标：在 `dev` 分支扩大切片实现 AT-17 薄弱知识点统计，并补最小问题回答状态更新与前端可观察入口；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 后端实现 `GET /api/knowledge-points/weak`：按面试开始日期范围和可选 `jobId` 过滤，`UNANSWERED=1`、`PARTIALLY_ANSWERED=0.5`、`FULLY_ANSWERED=0`，只返回仍计入薄弱点的问题作为下钻数据。
  - 后端实现最小 `PUT /api/interview-questions/{questionId}`：要求 `If-Match-Version`，可更新题目内容、回答状态、完整复盘字段和知识点关联；回答状态更正后弱点统计实时变化。
  - 后端实现 `GET/POST /api/knowledge-points` 最小路径：支持查询知识点与创建/复用同名知识点，供复盘页关联使用。
  - 前端快速复盘页新增“关联知识点”输入；新增问题时可创建/复用知识点并关联；已记录问题可直接更正回答状态。
  - 前端新增 `/knowledge-points/weak` 薄弱知识点页面和侧边栏入口，展示加权薄弱次数、题目数和原始问题下钻。
  - 扩展 `JsonProbe` 测试辅助，支持根数组/嵌套数组数字字段断言。
- 未完成：
  - AT-18 从问题创建任务、AT-19 完成任务不改能力尚未实现。
  - `DELETE /api/interview-questions/{id}`、知识点合并、复盘 reopen 仍未实现。
  - M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 新增：`backend/src/main/java/com/jobhub/review/api/KnowledgePointCreateRequest.java`、`QuestionUpdateRequest.java`、`WeakKnowledgePointResponse.java`。
  - 新增：`backend/src/main/java/com/jobhub/review/domain/WeakKnowledgePoint.java`。
  - 新增：`backend/src/main/java/com/jobhub/review/infrastructure/WeakKnowledgePointRow.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/api/ReviewController.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/application/ReviewService.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/domain/InterviewQuestion.java`、`KnowledgePoint.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/infrastructure/QuestionMapper.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/ReviewIntegrationTest.java`、`support/JsonProbe.java`。
  - 修改：`frontend/src/api/reviews/reviewApi.ts`、`useReviewQueries.ts`、`useReviewMutations.ts`。
  - 修改：`frontend/src/features/reviews/InterviewReviewPage.tsx`。
  - 新增：`frontend/src/features/reviews/WeakKnowledgePointsPage.tsx`。
  - 修改：`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`。
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReviewIntegrationTest"` -> BUILD SUCCESS，3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，38 tests，0 failures，0 errors。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，180 modules。
- 验证结果：
  - AT-17 后端统计和回答状态更正已由集成测试覆盖。
  - 前端 AT-17 可观察路径已通过 typecheck/lint/build；本窗口未新增 Playwright 用例。
  - 本窗口未修改 OpenAPI 契约字段含义；相关路径和 schema 在既有 OpenAPI 中已声明。
  - 本窗口未新增数据库迁移；V1 既有 `knowledge_point`、`interview_question`、`question_knowledge` 表可支撑本切片。
- 已知问题：
  - `PUT /interview-questions/{id}` 缺少 `If-Match-Version` 时当前返回 `BUSINESS_RULE_ERROR`，后续可统一成项目已有的缺版本 400 体验。
  - 知识点同名复用目前采用小写归一化，尚未实现完整别名/合并记录。
  - 前端薄弱点页面为最小统计视图，尚未接入任务创建和 jobId 筛选控件。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 扩大为实现 AT-18 + AT-19：从 `PARTIALLY_ANSWERED/UNANSWERED` 问题创建或关联学习任务；任务列表、任务状态专用 transition、完成时可填写验证结果且不得修改 `user_skill.self_level` 或清除历史薄弱题。
  - 先按 OpenAPI 既有任务契约补后端 task 模块、集成测试，再补前端 `/tasks` 页面和复盘页“创建学习任务”入口。
- 不要重复做：
  - 不要重建 AT-15/AT-16 review 基础能力或 AT-17 弱点统计。
  - 不要提前实现 M4 准备包、证据、导出、最近删除。
  - 不要实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-28-4

- 目标：在 `dev` 分支实现 AT-16 完成复盘最小条件；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增后端专用命令：`POST /api/reviews/{reviewId}/complete`，要求 `If-Match-Version`，通过乐观锁把 `DRAFT` 复盘转为 `COMPLETED`。
  - 完成规则由后端强制：复盘必须为 `DRAFT`，必须有面试结果；若没有问题且 `noQuestionsRecorded=false`，返回 `422 BUSINESS_RULE_ERROR` 且保持草稿无副作用；有至少一题或明确“未记录到问题”后可完成。
  - 已有 `COMPLETED` 复盘当前切片不允许继续走保存草稿或新增问题，避免通用编辑绕过专用状态命令；后续若实现 reopen，再按状态机补完整编辑规则。
  - 前端快速复盘页新增“完成复盘”动作：展示服务端业务规则错误，完成成功后显示“已完成”状态并禁用当前切片尚未支持的编辑入口。
  - 新增 AT-16 Playwright E2E：先构造无问题草稿并验证完成返回 422，再保存一道含 `answerStatus` 的问题并完成复盘。
- 未完成：
  - AT-17 薄弱知识点统计、AT-18 问题创建任务、AT-19 任务完成不改能力尚未实现。
  - `COMPLETED -> reopen -> DRAFT` 尚未实现；当前完成后编辑路径暂时收口，后续需要按状态机补专用 reopen。
  - M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 修改：`backend/src/main/java/com/jobhub/review/api/ReviewController.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/application/ReviewService.java`。
  - 修改：`backend/src/main/java/com/jobhub/review/infrastructure/ReviewMapper.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/ReviewIntegrationTest.java`。
  - 修改：`frontend/src/api/reviews/reviewApi.ts`、`frontend/src/api/reviews/useReviewMutations.ts`。
  - 修改：`frontend/src/features/reviews/InterviewReviewPage.tsx`、`frontend/src/styles/globals.css`。
  - 新增：`frontend/e2e/at-16-complete-review.spec.ts`。
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReviewIntegrationTest"` -> BUILD SUCCESS，2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，37 tests，0 failures，0 errors。
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，179 modules。
  - `cd frontend && npx playwright test e2e/at-16-complete-review.spec.ts --reporter=list` -> 测试条目显示 1 passed；但 Playwright webServer 收尾未返回，手动中断 runner，退出码不可作为成功记录。
  - `cd frontend && npm run e2e` -> AT-01、AT-09、AT-11、AT-15、AT-16 共 5 个测试条目均显示 `ok`；但 Playwright webServer 收尾未返回，手动中断 runner，退出码不可作为成功记录。
- 验证结果：
  - AT-16 后端规则已有集成测试覆盖；非法 complete 无数据副作用。
  - AT-16 浏览器路径已观察到通过；E2E runner 收尾卡住是测试基础设施/Windows webServer 进程退出问题，不是 AT-16 用例断言失败。
  - OpenAPI 契约已包含 complete endpoint 和 schema，本窗口未修改 OpenAPI。
  - V1 已有复盘/问题表，本窗口未新增数据库迁移。
- 已知问题：
  - Playwright 在本窗口多次于所有测试条目输出完成后卡在 webServer 收尾阶段；端口 15173/18080 已确认未遗留监听，但命令未自然返回，需要后续单独排查测试基础设施。
  - E2E 运行期间仍输出 React Router v7 future flag 警告，以及 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响当前测试条目结果。
  - `git status` 会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库内文件检查。
  - Playwright E2E 目前只有 Chromium 项目；跨浏览器和移动 viewport 未纳入本切片。
  - `InterviewResponse` 实现仍未返回 OpenAPI `InterviewDetail.reviewStatus` 扩展字段；当前通过独立 review API 查询状态，不影响 AT-16。
- 下一窗口只做：
  - 实现 AT-17 薄弱知识点统计：`GET /api/knowledge-points/weak`，按时间范围统计 `UNANSWERED=1`、`PARTIALLY_ANSWERED=0.5`、`FULLY_ANSWERED=0`，并返回可下钻原始问题。
  - 如需支持问题回答状态变更来验证 AT-17 后半段，可补最小 `PUT /api/interview-questions/{id}` 的 `answerStatus` 更新路径；不要扩展到任务创建。
  - 补后端集成测试覆盖统计和回答状态修改后的重新统计；前端若增加页面入口，保持最小可观察路径。
- 不要重复做：
  - 不要重建 review 草稿、问题创建或完成复盘基础能力；AT-15、AT-16 已完成。
  - 不要提前实现 AT-18 问题创建任务、AT-20 准备包、AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-28-3

- 目标：在 `dev` 分支实现 AT-15 快速复盘最小录入；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增后端 review 模块最小实现：`GET/PUT /api/interviews/{id}/review` 与 `POST /api/reviews/{id}/questions`。
  - 复盘草稿仅允许在 `COMPLETED` 面试上保存；首次保存创建 `DRAFT`，已有草稿更新时要求 `If-Match-Version`。
  - 新增最小问题保存：问题内容 + `answerStatus` 必填；`myAnswer`、`referenceAnswer`、`errorReason` 等完整复盘字段可为空，不阻断保存。
  - 新增前端 review API hooks、`/interviews/:interviewId/review` 快速复盘页，以及完成面试详情页的“开始/继续复盘”入口。
  - 新增 `frontend/e2e/at-15-quick-review.spec.ts`，通过 API 构造已完成面试，通过真实 UI 保存最小复盘并刷新验证可继续编辑。
  - 更新测试清理顺序，覆盖 `interview_review`、`interview_question`、`question_knowledge`。
- 未完成：
  - AT-16 完成复盘最小条件尚未实现。
  - AT-17 薄弱知识点统计、AT-18 问题创建任务、AT-19 任务完成不改能力尚未实现。
  - M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 新增：`backend/src/main/java/com/jobhub/review/domain/ReviewStatus.java`、`AnswerStatus.java`、`KnowledgePoint.java`、`InterviewQuestion.java`、`InterviewReview.java`。
  - 新增：`backend/src/main/java/com/jobhub/review/infrastructure/ReviewMapper.java`、`QuestionMapper.java`。
  - 新增：`backend/src/main/java/com/jobhub/review/application/ReviewService.java`。
  - 新增：`backend/src/main/java/com/jobhub/review/api/ReviewController.java`、`ReviewUpsertRequest.java`、`QuestionCreateRequest.java`、`KnowledgePointResponse.java`、`InterviewQuestionResponse.java`、`InterviewReviewResponse.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/ReviewIntegrationTest.java`。
  - 新增：`frontend/src/api/reviews/reviewApi.ts`、`useReviewQueries.ts`、`useReviewMutations.ts`。
  - 新增：`frontend/src/features/reviews/InterviewReviewPage.tsx`、`reviewLabels.ts`。
  - 新增：`frontend/e2e/at-15-quick-review.spec.ts`。
  - 修改：`frontend/src/app/routes.tsx`、`frontend/src/features/interviews/InterviewDetailPage.tsx`、`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReviewIntegrationTest"` -> BUILD SUCCESS，1 test，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，36 tests，0 failures，0 errors。
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，179 modules。
  - `cd frontend && npm run e2e` -> 通过，AT-01、AT-09、AT-11、AT-15 共 4 tests passed。
- 验证结果：
  - AT-15 已有后端集成测试和 Playwright 浏览器级回归。
  - OpenAPI 契约已有相关接口和 schema，本窗口未修改 OpenAPI。
  - V1 已有复盘/问题/知识点表，本窗口未新增数据库迁移。
  - E2E 运行期间仍输出 React Router v7 future flag 警告，以及 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响当前测试结果。
  - 首次未提权运行 Maven/E2E 时，沙箱拒绝写入或删除 `backend/target` 临时文件；提权后验证通过。
- 已知问题：
  - `git status` 会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库内文件检查。
  - Playwright E2E 目前只有 Chromium 项目；跨浏览器和移动 viewport 未纳入本切片。
  - `InterviewResponse` 实现仍未返回 OpenAPI `InterviewDetail.reviewStatus` 扩展字段；当前 AT-15 通过独立 review API 查询状态，不影响本切片。
- 下一窗口只做：
  - 实现 AT-16 完成复盘最小条件：`POST /api/reviews/{reviewId}/complete`，当 `DRAFT` 复盘没有问题且 `noQuestionsRecorded=false` 时返回 422；有至少一题或明确 `noQuestionsRecorded=true`，且每题都有 `answerStatus` 时转为 `COMPLETED`。
  - 前端在快速复盘页增加“完成复盘”动作，展示业务规则错误并保持草稿可编辑。
  - 补 `ReviewIntegrationTest` AT-16 用例；如前端路径完成，再补 AT-16 Playwright 或把 AT-15 E2E 扩展到完成动作的可观察路径。
- 不要重复做：
  - 不要重建 review 基础 CRUD、不要重做 AT-15 E2E，除非实际回归失败。
  - 不要提前实现 AT-17 薄弱知识点统计、AT-18 任务创建、AT-20 准备包、AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-28-2

- 目标：在 `dev` 分支补齐 AT-11 面试改期替换提醒 Playwright E2E；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增 `frontend/e2e/at-11-interview-reschedule-reminders.spec.ts`：通过 API 创建岗位和投递、真实 UI 安排面试、断言默认 3 条 `PENDING` 提醒，使用 E2E-only 测试辅助将一条旧提醒标为 `SENT`，再通过真实 UI 改期并断言 `startsAt`/`eventTimeZone` 更新、旧 `PENDING` 提醒变为 `CANCELED`、新时间 3 条 `PENDING` 提醒存在、`SENT` 历史提醒保留。
  - 新增 `backend/src/main/java/com/jobhub/testsupport/api/E2eReminderFixtureController.java`：仅在 `e2e` profile 启用，用于测试夹具把提醒标为 `SENT`；不进入 OpenAPI，不在默认运行环境暴露。
  - 修改 `frontend/e2e/start-e2e-backend.ps1`：E2E 后端启动时增加 `--spring.profiles.active=e2e`。
  - 修正 AT-11 E2E 中按钮选择器歧义和后端 UTC 字符串毫秒格式差异，完整回归已通过。
- 未完成：
  - Playwright 尚未覆盖 AT-15、AT-18、AT-20。
  - M3 复盘/问题/知识点/学习任务尚未实现。
  - M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 新增：`backend/src/main/java/com/jobhub/testsupport/api/E2eReminderFixtureController.java`。
  - 新增：`frontend/e2e/at-11-interview-reschedule-reminders.spec.ts`。
  - 修改：`frontend/e2e/start-e2e-backend.ps1`。
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，174 modules。
  - `cd backend && mvn test "-Dtest=InterviewIntegrationTest"` -> BUILD SUCCESS，6 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，35 tests，0 failures，0 errors。
  - `cd frontend && npm run e2e` -> 通过，AT-01、AT-09、AT-11 共 3 tests passed。
- 验证结果：
  - AT-11 已有后端集成测试和新增 Playwright 浏览器级回归；M2 发布门槛中已实现功能的关键 E2E 覆盖推进到 AT-01/AT-09/AT-11。
  - 本地 `dev` 已提交 AT-11 成果，`main` 已快进合并到同一提交。
  - 用户随后明确授权推送到 `https://github.com/paopao-01/personal-workspace.git`，`dev` 与 `main` 已成功推送到远程。
  - E2E 运行期间仍输出 React Router v7 future flag 警告，以及 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响当前测试结果。
  - 首次未提权运行 E2E/Maven 时，沙箱拒绝删除或写入 `backend/target` 临时文件；提权后验证通过。
- 已知问题：
  - `git status` 会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库内文件检查。
  - Playwright E2E 目前只有 Chromium 项目；跨浏览器和移动 viewport 未纳入本切片。
- 下一窗口只做：
  - 进入 M3，优先实现 AT-15 快速复盘最小录入：一场 `COMPLETED` 面试且尚无复盘时，支持保存 `interviewResult=FAILED`、至少一道问题和 `answerStatus=UNANSWERED`，复盘状态为 `DRAFT`，且未填写我的回答、参考答案或错误原因不阻断保存。
  - 范围建议：先核对 OpenAPI 中 `PUT /interviews/{id}/review`、`GET /interviews/{id}/review`、`POST /reviews/{id}/questions` 是否满足 AT-15；如契约无需变化，补后端 review 模块最小实现和集成测试，再补前端 P08 快速复盘入口与页面。
  - 验证命令优先运行：`cd backend && mvn test "-Dtest=ReviewIntegrationTest"`、`cd backend && mvn test`、`cd frontend && npm run lint`、`cd frontend && npm run typecheck`、`cd frontend && npm run build`；若前端路径完成，再补并运行 AT-15 Playwright E2E。
- 不要重复做：
  - 不要重建 Playwright 基础设施、不要改回并发 E2E worker；当前 E2E 使用 15173/18080 独立端口、临时库和单 worker。
  - 不要重做 AT-01、AT-09 或 AT-11 的 E2E 覆盖，除非实际回归失败。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。
  - 不要提前实现 AT-18 任务创建、AT-20 准备包、AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-28-1

- 目标：确定下一窗口任务，并明确后续开发分支流程：在 `dev` 分支开发，验证无问题后合并到 `main`，再推送远程仓库。
- 状态：**DONE**。
- 已完成：
  - 按顺序恢复上下文并核对当前代码/测试现状。
  - 确认 `dev`、`main`、`origin/dev`、`origin/main` 当前均指向 `4727525 test(frontend): add Playwright AT-09 e2e`。
  - 明确下一窗口只补 `AT-11 面试改期会替换未触发提醒` 的 Playwright E2E。
  - 确认后端已有 `InterviewIntegrationTest.AT11_reschedule_cancelsPendingAndPreservesSent` 集成测试；下一窗口应补浏览器级回归，不重复改业务范围。
- 未完成：
  - 尚未新增 AT-11 Playwright E2E。
  - Playwright 尚未覆盖 AT-15、AT-18、AT-20。
  - M3 复盘/问题/知识点/学习任务与 M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `git status --short --branch` -> 当前在 `dev...origin/dev`，仅有本交接文档修改。
  - `git branch --all --verbose` -> `dev`、`main`、`origin/dev`、`origin/main` 均在 `4727525`。
  - 已扫描 `frontend/e2e`、`backend/src/test`、`backend/src/main/java/com/jobhub/interview`、`frontend/src/features/interviews` 中的 reschedule/reminder 相关实现。
- 验证结果：
  - 本窗口只做任务确认和交接更新，未运行后端/前端测试。
  - 分支流程已确认：下一窗口从 `dev` 开始开发；验证通过后提交到 `dev`，合并到 `main`，推送 `dev` 与 `main` 到远程。
- 已知问题：
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库状态判断。
  - 本机沙箱对 `.git/index.lock` 写入受限；切换分支/提交/合并/推送可能需要提权执行。
- 下一窗口只做：
  - 在 `dev` 分支新增 `frontend/e2e/at-11-interview-reschedule-reminders.spec.ts`，覆盖 AT-11：创建未来 `SCHEDULED` 面试及默认提醒，先将一条旧提醒通过测试辅助置为 `SENT`，执行真实 UI/API 改期，断言面试 `startsAt`/`eventTimeZone` 更新、旧 `PENDING` 提醒为 `CANCELED`、新时间 3 条 `PENDING` 提醒存在、`SENT` 历史提醒保留。
  - 验证命令优先运行：`cd frontend && npm run lint`、`cd frontend && npm run typecheck`、`cd frontend && npm run build`、`cd backend && mvn test "-Dtest=InterviewIntegrationTest"`、`cd frontend && npm run e2e`。
  - 验证通过后提交到 `dev`，合并到 `main`，推送 `dev` 与 `main` 到远程仓库。
- 不要重复做：
  - 不要重建 Playwright 基础设施、不要改回并发 E2E worker；当前 E2E 使用 15173/18080 独立端口、临时库和单 worker。
  - 不要重做 AT-01 或 AT-09 的 E2E 覆盖，除非实际回归失败。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-27-4

- 目标：在 `dev` 分支补齐 AT-09 dashboard 缺失/逾期行动 Playwright E2E；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增 `frontend/e2e/at-09-dashboard-actions.spec.ts`：通过 API 仅创建岗位作为前置数据，通过真实 UI 创建两条活动投递、执行状态转换，并在 `/dashboard` 断言缺失行动提示、逾期天数和逾期项排序优先。
  - `frontend/playwright.config.ts` 固定 `workers: 1`，避免多个 E2E 文件并发共享同一个临时 SQLite 后端导致 AT-01/AT-09 相互干扰。
  - 保留 E2E 独立端口 `15173/18080` 和临时库策略；未新增接口、未新增迁移。
- 未完成：
  - Playwright 尚未覆盖 AT-11、AT-15、AT-18、AT-20。
  - M3 复盘/问题/知识点/学习任务与 M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 新增：`frontend/e2e/at-09-dashboard-actions.spec.ts`。
  - 修改：`frontend/playwright.config.ts`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，174 modules。
  - `cd backend && mvn test "-Dtest=DashboardIntegrationTest"` -> BUILD SUCCESS，3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，35 tests，0 failures，0 errors。
  - `cd frontend && npm run e2e` -> 通过，AT-01 与 AT-09 共 2 tests passed。
- 验证结果：
  - AT-09 已有后端集成测试和新增 Playwright 浏览器级回归；M2 dashboard 缺失/逾期行动发布门槛已补齐。
  - E2E 运行期间仍输出 React Router v7 future flag 警告，以及 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响当前测试结果。
  - 首次未提权运行 E2E/Maven 时，沙箱拒绝删除或写入 `backend/target` 临时文件；提权后验证通过。
- 已知问题：
  - `git status` 会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库内文件检查。
  - Playwright E2E 目前只有 Chromium 项目；跨浏览器和移动 viewport 未纳入本切片。
- 下一窗口只做：
  - 继续补 Playwright E2E，优先 AT-11 面试改期替换提醒：创建未来 SCHEDULED 面试及默认提醒，执行真实 UI/API 改期，断言旧 PENDING 提醒 CANCELED、新时间 3 条 PENDING 提醒存在，且 SENT 历史提醒保留。
- 不要重复做：
  - 不要重建 Playwright 基础设施、不要改回并发 E2E worker；当前 E2E 使用 15173/18080 独立端口、临时库和单 worker。
  - 不要重做 AT-01 或 AT-09 的 E2E 覆盖，除非实际回归失败。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-27-3

- 目标：在 `dev` 分支建立最小 Playwright E2E 基础设施，并优先覆盖 AT-01 首次价值端到端；验证无问题后合并回 `main` 并推送。
- 状态：**DONE**。
- 已完成：
  - 新增 `@playwright/test` 开发依赖与 `npm run e2e` 脚本。
  - 新增 `frontend/playwright.config.ts`：E2E 使用独立前端端口 `15173`，后端端口 `18080`，避免复用普通开发服务；测试时区固定为 `Asia/Shanghai`。
  - 新增 `frontend/e2e/start-e2e-backend.ps1`：每次 E2E 启动前安全删除 `backend/target/jobhub-e2e.db`、`-wal`、`-shm` 临时库，再启动 Spring Boot；不触碰 `backend/data/jobhub.db`。
  - 新增 `frontend/e2e/at-01-first-value.spec.ts`：通过真实 UI 完成创建岗位、提取候选要求、确认 3 项要求、查看“信息不足”差距、保存 `TO_APPLY` 决定，并在 dashboard 断言出现“为该岗位创建投递或安排下一步行动”入口。
  - `DashboardService` 补齐 AT-01 的岗位级行动入口：`TO_APPLY` 且无活动投递的岗位会生成 dashboard action item；前端 dashboard 点击 `sourceRef.type=JOB` 的行动项会回到岗位详情。
  - `vite.config.ts` 支持 `JOBHUB_API_TARGET`，供 E2E 前端代理到独立后端端口；普通 `npm run dev` 默认仍代理 `127.0.0.1:8080`。
- 未完成：
  - Playwright 尚未覆盖 AT-09、AT-11、AT-15、AT-18、AT-20。
  - M3 复盘/问题/知识点/学习任务与 M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - 新增：`frontend/playwright.config.ts`、`frontend/e2e/start-e2e-backend.ps1`、`frontend/e2e/at-01-first-value.spec.ts`。
  - 修改：`frontend/package.json`、`frontend/package-lock.json`、`frontend/.gitignore`、`frontend/vite.config.ts`、`frontend/src/features/dashboard/DashboardPage.tsx`、`backend/src/main/java/com/jobhub/dashboard/application/DashboardService.java`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd frontend && npm run lint` -> 通过。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，174 modules。
  - `cd backend && mvn test "-Dtest=DashboardIntegrationTest"` -> BUILD SUCCESS，3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，35 tests，0 failures，0 errors。
  - `cd frontend && npm run e2e` -> 通过，AT-01 1 test passed。
  - 首次运行 E2E 前执行 `npx playwright install chromium`，已安装本机 Chromium/headless shell。
- 验证结果：
  - 后端全量集成测试、前端静态检查、生产构建和首个 Playwright E2E 场景均通过。
  - E2E 运行期间 Vite 输出 React Router v7 future flag 警告；不影响当前功能，后续可单独配置 future flags 降噪。
- 已知问题：
  - `git status` 会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；未影响仓库内文件检查。
  - Playwright E2E 目前只有 Chromium 项目；跨浏览器和移动 viewport 未纳入本切片。
- 下一窗口只做：
  - 在 `dev` 分支继续补 Playwright E2E，只覆盖 AT-09 dashboard 缺失/逾期行动：通过真实 UI 创建两条活动投递（APPLIED 无 nextAction；INTERVIEWING 有已逾期 nextActionDueAt），打开 `/dashboard` 断言补充行动提示、逾期天数和逾期项排序优先。
  - 如真实 UI 缺少造数所需入口，优先通过既有 UI/API 最小辅助完成测试数据，不扩展业务范围；仅修正 AT-09 可观察行为或测试稳定性问题。
  - 验证无问题后，将 `dev` 合并到 `main`，并推送 `dev` 与 `main` 到远程仓库。
- 不要重复做：
  - 不要重建 Playwright 基础设施或改回 5173 E2E 端口；E2E 已使用 15173/18080 独立端口和临时库。
  - 不要重做 P05 月视图、投递状态筛选、dashboard upcomingInterviews、AT-08 `allowDuplicate=true` 或面试状态命令 UI。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-27-2

- 目标：在 `dev` 分支实现 P05 面试中心月视图与时间线/月视图切换，验证无问题后合并回 `main`。
- 状态：**DONE**。
- 已完成：
  - 确认当前分支为 `dev`，未新建后端接口或数据库迁移。
  - `/interviews` 增加 `view=month` 月视图；默认仍为未来 7 天时间线。
  - 月视图按所选月份查询整月数据，提供上个月/下个月切换，并继续复用日期范围、日程状态、投递状态和面试方式筛选。
  - 抽出面试摘要展示，时间线与月视图共享公司、岗位、轮次、方式、日程状态、投递状态、准备事项数量、结果和“等待确认是否完成”提示。
  - 补充月历网格、视图切换和窄屏横向滚动样式，保持状态标签文字可见且不只依赖颜色。
- 未完成：
  - Playwright E2E 仍未建立或覆盖 AT-01、AT-09、AT-11、AT-15、AT-18、AT-20。
  - M3 复盘/问题/知识点/学习任务与 M4 准备包/证据/导出/最近删除仍未开始。
- 修改文件：
  - `frontend/src/features/interviews/InterviewListPage.tsx`
  - `frontend/src/styles/globals.css`
  - `docs/jobhub/IMPLEMENTATION_STATUS.md`
- 已运行验证：
  - `cd frontend && npm run lint` -> 通过，0 warning。
  - `cd frontend && npm run typecheck` -> 通过。
  - `cd frontend && npm run build` -> 通过；OpenAPI TS 类型生成、TS 编译和 Vite 生产构建均成功，174 modules。
  - `cd frontend && npm run dev -- --host 127.0.0.1` -> Vite dev server 已启动，URL `http://127.0.0.1:5173/`。
- 验证结果：
  - 前端静态检查与生产构建全绿；本切片只改前端视图，无后端行为变化，未运行后端测试。
- 已知问题：
  - 浏览器自动化/Playwright E2E 尚未补齐，当前只完成静态与构建验证。
  - 若后端未同时运行，访问 `/interviews` 会走现有错误态；启动后端后由 Vite proxy 访问 `/api`。
- 下一窗口只做：
  - 建立最小 Playwright E2E 基础设施，并优先覆盖 AT-01、AT-09、AT-11 中至少一个端到端场景。
- 不要重复做：
  - 不要重做 P05 月视图、投递状态筛选、dashboard upcomingInterviews、AT-08 `allowDuplicate=true` 或面试状态命令 UI。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-27-1

- 目标：按 `AGENTS.md` 恢复上下文，总结未完成任务，并确定下一窗口任务。
- 状态：**DONE**。
- 已完成：
  - 按顺序阅读并核对 `IMPLEMENTATION_STATUS.md`、`IMPLEMENTATION_MASTER_PROMPT.md`、PRD、状态机、OpenAPI、数据库设计、页面规格、验收用例和技术实施方案。
  - 扫描当前后端/前端代码结构、集成测试和前端脚本，确认 M1 已完成，M2 主路径已完成，M3/M4 尚未开始。
  - 发现交接记录中的一处过时描述：P05 “投递状态筛选”并非完全未做；当前 OpenAPI、后端 `InterviewMapper.selectListItems`、`InterviewController.list`、前端 `InterviewListPage` 已支持 `applicationStatus`，且 `InterviewIntegrationTest.interviewList_filtersByApplicationStatus_andReturnsApplicationSummary` 覆盖该筛选。
- 未完成：
  - P05 月视图和列表/月视图切换仍未实现。
  - Playwright E2E 仍未建立或覆盖发布门槛要求的 AT-01、AT-09、AT-11、AT-15、AT-18、AT-20。
  - M3 复盘、问题、知识点、薄弱点、学习任务与验证未实现。
  - M4 面试准备包、项目案例、证据、JSON 导出、最近删除未实现；OpenAPI 中已有准备包等契约但后端/前端尚未落地。
- 修改文件：
  - `docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - 未运行后端或前端测试；本窗口只做上下文核对与交接更新。
  - 已执行代码/测试结构扫描：`git status --short`、`rg --files backend/src/main/java/com/jobhub`、`rg --files frontend/src`、`rg --files backend/src/test frontend | rg "(IntegrationTest|\\.spec\\.|playwright|e2e)"`，以及针对 P05 筛选实现的 `Select-String` 检查。
- 验证结果：
  - 工作区未显示待提交业务代码变更；`git status --short` 仅出现用户级 git ignore 权限警告。
  - 后端存在 11 个集成测试类；未发现前端 Playwright/E2E 文件。
- 已知问题：
  - 当前文档仍有部分历史窗口记录保留旧说法，读取时以最新总状态和本窗口核对结果为准。
  - P05 投递状态筛选已有代码与测试，但上一次交接未及时更新，后续窗口不要重复扩展该契约，除非实际验收发现缺陷。
- 下一窗口只做：
  - 实现 P05 面试中心月视图与列表/月视图切换；复用现有 `/api/interviews`、`InterviewListItem.application.status` 和 `applicationStatus` 筛选，不新增数据库迁移。
  - 同步补前端静态验证；如范围允许，增加最小 Playwright 基础设施并优先覆盖 AT-11 或面试中心筛选/月视图冒烟。
- 不要重复做：
  - 不要重做 AT-08 `allowDuplicate=true`、dashboard upcomingInterviews、面试状态命令 UI 或 P05 投递状态筛选。
  - 不要通过普通 `PUT /interviews/{id}` 或 `PUT /applications/{id}` 改写业务状态。
  - 不要提前接入 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。

### 窗口 2026-08-26-10

- 目标：补齐 AT-08 后半 -- 用户以 `allowDuplicate=true` 显式确认后，可创建同岗位的第二条活动投递，并留下可追溯审计记录。
- 状态：**DONE**。
- 已完成：
  - 新增 `V2__allow_confirmed_duplicate_applications.sql`，为 `application_record` 增加内部字段 `duplicate_confirmed_at`；不修改已执行的 V1。
  - V2 删除旧的 `uq_application_active_per_job`，新增“未确认活动投递至多一条”的部分唯一索引与活动投递查询索引。默认规则继续由服务层强制，已确认二次投递可被安全持久化。
  - `ApplicationService.create` 在已有活动投递且未提供 `allowDuplicate=true` 时继续返回 `409 DUPLICATE_APPLICATION`；显式确认时创建新 `DRAFT` 投递，写入 `duplicate_confirmed_at`，并在同一事务追加 `audit_log`（`SECONDARY_APPLICATION_CONFIRMED`）。
  - 新增只追加的 `common/audit` mapper；活动投递检测查询增加 `LIMIT 1`，适配已确认重复投递存在时的读取语义。
  - 更新 AT-08 集成测试：断言二次创建返回 `201`、两条活动投递存在、确认时间已写入且审计记录存在；测试清理器新增 `audit_log` 清理。
  - 补充 OpenAPI 中 `allowDuplicate` 的行为说明，并同步更新数据库设计。
- 未完成：
  - P05 月视图、投递状态筛选和卡片式时间线；投递状态筛选需先扩展 `Interview` 契约或增加聚合查询响应。
  - Playwright E2E（至少 AT-01、AT-09、AT-11）以及 M3 的复盘/问题/任务。
- 修改文件：
  - 新增：`backend/src/main/resources/db/migration/V2__allow_confirmed_duplicate_applications.sql`、`backend/src/main/java/com/jobhub/common/audit/{AuditLogEntry.java,infrastructure/AuditLogMapper.java}`。
  - 修改：`backend/src/main/java/com/jobhub/application/{api/ApplicationCreateRequest.java,application/ApplicationCreateCommand.java,application/ApplicationService.java,application/DuplicateApplicationException.java,domain/Application.java,infrastructure/ApplicationMapper.java}`、`backend/src/test/java/com/jobhub/integration/{ApplicationCrudIntegrationTest.java,support/DatabaseCleaner.java}`、`docs/jobhub/{03-openapi.yaml,04-database-design.md,IMPLEMENTATION_STATUS.md}`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ApplicationCrudIntegrationTest,ApplicationIdempotencyIntegrationTest,ApplicationTransitionIntegrationTest"` -> BUILD SUCCESS，8 tests，0 failures，0 errors；Flyway 从 V1 升级 V2 成功。
  - `cd backend && mvn test` -> BUILD SUCCESS，34 tests，0 failures，0 errors。
  - `cd backend && mvn test "-Dtest=ApplicationCrudIntegrationTest"` -> BUILD SUCCESS，4 tests，0 failures，0 errors；覆盖审计原因断言。
- 下一窗口建议只做：先在 OpenAPI 中设计面试列表所需的投递状态筛选响应，再完成 P05 月视图/时间线；或单独补 Playwright E2E。不要在前端或普通 `PUT` 中绕过投递、面试状态机。
- 不要重复做：`allowDuplicate=true` 已可创建第二条活动投递并写审计；不得修改 V1，也不要移除 V2 对未确认活动投递的数据库兜底。

### 窗口 2026-08-26-9

- 目标：M2 第六切片 — 让首页工作台显示真实的即将面试，而非 OpenAPI 契约中的空占位数组。
- 状态：**DONE**。
- 已完成：
  - `DashboardService` 注入 `InterviewMapper`，读取当前 UTC 时间之后、`SCHEDULED` 的面试，按开始时间排序并限制为前 5 条；此聚合为只读，不改变面试或提醒状态。
  - `DashboardController` 将 `upcomingInterviews` 从占位类型替换为 OpenAPI 已定义的 `InterviewResponse[]`；无需新增契约字段或数据库迁移。
  - `DashboardPage` 新增“即将面试”区块，展示轮次、面试方式、事件时区和本地化开始时间，点击进入面试详情；无数据时显示明确空状态。
  - `DashboardIntegrationTest` 新增回归：未来 `SCHEDULED` 面试出现在 `upcomingInterviews`，覆盖真实响应类型而非空数组。
- 未完成：
  - P05 月视图、投递状态筛选和卡片式时间线；投递状态筛选需先扩展 `Interview` 契约或增加聚合查询响应。
  - AT-08 `allowDuplicate=true` 所需 V2 迁移、Playwright E2E，以及 M3 的复盘/问题/任务。
- 修改文件：
  - 修改：`backend/src/main/java/com/jobhub/dashboard/{application/DashboardService.java,api/DashboardController.java}`、`backend/src/test/java/com/jobhub/integration/DashboardIntegrationTest.java`、`frontend/src/features/dashboard/DashboardPage.tsx`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=DashboardIntegrationTest,InterviewIntegrationTest"` → BUILD SUCCESS，8 tests，0 failures，0 errors。
  - `cd frontend && npm run lint && npm run typecheck && npm run build` → 全绿，生产构建 174 modules。
  - 首次沙箱内 Maven 执行因无权写入 `backend/target` 失败；以授权运行同一命令后通过，非代码错误。
- 下一窗口建议只做：AT-08 后半的 V2 迁移和 `allowDuplicate=true` 行为，或先在 OpenAPI 设计后实现 P05 投递状态筛选/月视图；不要把面试状态更新塞入 dashboard 聚合。
- 不要重复做：`upcomingInterviews` 已是 `Interview[]` 契约，后端不应再返回占位对象或空数组。未来面试只展示 `SCHEDULED`，到时间后仍等待人工确认，不能自动完成。

### 窗口 2026-08-26-8

- 目标：M2 第五切片（前端）— 实现 P05 面试中心列表与 P06 的改期、完成、取消、缺席操作。
- 状态：**DONE**。
- 已完成：
  - `frontend/src/api/interviews/` 增加面试列表、改期、完成、取消和缺席 API 与 mutation hooks；四个命令均显式携带 `If-Match-Version`，并以面试 ID、命令和版本生成稳定 `Idempotency-Key`。成功后刷新面试列表/详情/提醒、关联投递与工作台缓存。
  - 新增 `/interviews` 面试中心并启用侧栏入口：默认按本地日历未来 7 天查询，可按日期范围、日程状态和面试方式筛选，点击行进入详情。
  - 面试详情在 `SCHEDULED` 时显示专用操作：改期（重新计算提醒）、标记完成（可选结果）、取消和标记未出席；非 `SCHEDULED` 状态不显示这些操作。取消和缺席均有确认提示，不暴露会被后端忽略的原因输入。
  - 改期后操作组件以新版本重建，避免再次打开时显示旧时间；面试方式统一显示中文文案。
- 未完成：
  - P05 月视图、投递状态筛选和卡片式时间线；当前后端 `Interview` 不携带投递状态，不能在不扩展契约的前提下实现该筛选。
  - dashboard 的 `upcomingInterviews` 真正聚合和前端展示、Playwright E2E、AT-08 `allowDuplicate=true` 所需 V2 迁移。
- 修改文件：
  - 修改：`frontend/src/api/interviews/{interviewApi,useInterviewQueries,useInterviewMutations}.ts`、`frontend/src/features/interviews/{InterviewDetailPage,interviewLabels}.tsx/.ts`、`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 新增：`frontend/src/features/interviews/InterviewListPage.tsx`、`frontend/src/features/interviews/components/InterviewActionSection.tsx`。
- 已运行验证：
  - `cd frontend && npm run lint` → 0 error / 0 warning。
  - `cd frontend && npm run typecheck` → 0 error。
  - `cd frontend && npm run build`（含 OpenAPI 类型重新生成）→ BUILD SUCCESS，174 modules。
  - 本地冒烟：`GET /api/interviews` 返回 1 条脱敏测试面试（`SCHEDULED`、版本 0）；`GET http://127.0.0.1:5173/interviews` 返回 200。浏览器自动化插件在本窗口不可调用，未新增 Playwright 依赖；沿用窗口 7 的浏览器页面验收结果。
- 下一窗口建议只做：扩展 dashboard upcoming 面试聚合与首页展示，或单独实现 AT-08 后半的 V2 迁移；开始前重新核对 OpenAPI 和本节未完成项。
- 不要重复做：不得用 `PUT /interviews/{id}` 改写 `scheduleStatus`；改期、完成、取消和缺席必须继续走现有专用命令端点。不要在 P0 中加入邮件、系统推送、云同步或修改 V1 迁移。

### 窗口 2026-08-26-7

- 目标：M2 第四切片（前端）— 实现“投递详情创建面试 → 面试详情查看提醒”的最小可用路径。
- 状态：**DONE**。
- 已完成：
  - 新增 `frontend/src/api/interviews/`：基于 OpenAPI 生成类型的查询与创建 API、TanStack Query 查询 hooks、创建 mutation；创建成功后更新面试详情缓存并失效投递列表、投递详情和工作台查询。
  - `ApplicationDetailPage` 的面试区从占位改为真实列表：展示场次、本地化时间、日程状态和结果；仅 `RESUME_PASSED`/`INTERVIEWING` 投递可打开创建表单。
  - 新增内联创建表单：必填轮次、开始时间、事件时区，支持模式、地点/会议链接、联系人、逐行准备清单和备注；复用全局幂等 Key，创建成功进入面试详情。
  - 新增 `/interviews/:interviewId` 面试详情页：展示关联投递、时间和时区、状态/结果、准备清单和提醒列表；过去且仍为 `SCHEDULED` 的面试显示“等待人工确认”，并明确提醒仅在打开应用时展示，不承诺系统级推送。
  - 补移动端布局：小于等于 720px 时隐藏侧栏并收紧页面边距，避免详情内容被压缩。
- 未完成：
  - P05 面试中心的 `/interviews` 列表、筛选和时间线视图。
  - 对已有 `reschedule`、`cancel`、`complete`、`no-show` 命令端点的前端操作控件；创建后面试目前只能查看。
  - dashboard 的 `upcomingInterviews` 真正聚合和前端展示、Playwright E2E、AT-08 `allowDuplicate=true` 所需 V2 迁移。
- 修改文件：
  - 新增：`frontend/src/api/interviews/{interviewApi,useInterviewQueries,useInterviewMutations}.ts`。
  - 新增：`frontend/src/features/interviews/{InterviewDetailPage,interviewLabels}.tsx/.ts`、`frontend/src/features/interviews/components/InterviewCreateSection.tsx`。
  - 修改：`frontend/src/features/applications/{ApplicationDetailPage.tsx,components/InterviewListSection.tsx}`、`frontend/src/app/routes.tsx`、`frontend/src/styles/globals.css`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
- 已运行验证：
  - `cd frontend && npm run lint` → 0 error / 0 warning。
  - `cd frontend && npm run typecheck` → 0 error。
  - `cd frontend && npm run build`（含 OpenAPI 类型重新生成）→ BUILD SUCCESS，172 modules。
  - 浏览器手工验收：在可创建面试的投递详情打开创建表单；创建测试面试后列表出现并自动进入 `INTERVIEWING`；打开面试详情可见准备清单与 1 天、2 小时、30 分钟三条 `PENDING` 提醒；桌面和 390px 移动视口均检查过，应用控制台无错误（仅 React Router 未来行为提示）。
- 验证说明：浏览器自动化环境未能稳定向原生 `input[type=date]` 写入既有投递创建表单，因此面试创建的最终请求以本地脱敏测试数据直连后端创建；创建表单打开、投递详情列表刷新、面试详情和提醒渲染均经浏览器验证。后端未在本窗口改动，沿用窗口 6 的 33 个后端测试通过结果。
- 下一窗口建议只做：P05 面试中心列表/筛选和面试状态命令 UI，并为新增交互补 Playwright 验收；不要扩展 AI、邮件、系统推送、云同步或修改 V1 迁移。

### 窗口 2026-08-26-6

- 目标：M2 第三切片 — 补齐 `mark-no-show` OpenAPI 端点，实现面试与提醒后端，覆盖 AT-10~AT-14。
- 状态：**DONE**。
- 已完成：
  - `docs/jobhub/03-openapi.yaml` 新增 `POST /interviews/{interviewId}/no-show`，与状态机和 AT-12 对齐。
  - 新增 `backend/src/main/java/com/jobhub/interview/`：面试/提醒枚举、实体、MyBatis Mapper、请求响应 DTO、事务服务和 Controller。
  - 创建首场面试时，在同一事务把投递从 `RESUME_PASSED` 推进到 `INTERVIEWING`，写入状态历史并创建 1 天/2 小时/30 分钟默认提醒。
  - 改期取消未触发提醒并生成新提醒；取消/缺席/完成清理未触发提醒；取消和缺席保持 `result=PENDING`，拒绝设置 `PASSED/FAILED`。
  - 过期提醒保持 `PENDING`，可通过站内提醒查询展示，不声明系统推送；投递详情开始返回真实面试列表。
  - 新增 AT-10~AT-14 集成测试，并扩展测试数据库清理顺序。
- 未完成：
  - 面试前端页面/API hooks 尚未实现，dashboard 的 upcoming 面试仍是占位空数组。
  - 面试准备包、复盘、提醒调度领取/通知实体留待后续切片；AT-08 `allowDuplicate=true` 仍需 V2 迁移。
- 修改文件：
  - `docs/jobhub/03-openapi.yaml`
  - `backend/src/main/java/com/jobhub/interview/`（新增面试与提醒后端模块）
  - `backend/src/main/java/com/jobhub/application/api/ApplicationController.java`
  - `backend/src/main/java/com/jobhub/application/api/ApplicationDetailResponse.java`
  - `backend/src/test/java/com/jobhub/integration/InterviewIntegrationTest.java`
  - `backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`
  - `docs/jobhub/IMPLEMENTATION_STATUS.md`
- 已运行验证：`cd backend && mvn test`。
- 验证结果：**BUILD SUCCESS**，33 个测试通过，0 failures，0 errors；包含原有 28 个回归测试和新增 AT-10~AT-14 五个测试。未修改 V1 迁移。
- 已知问题：提醒查询目前提供到期的 `PENDING` 数据，但未实现独立 scheduler/notification 表写入；dashboard/upcoming 面试和面试前端待后续窗口。
- 下一窗口只做：面试前端 API/详情与列表页面，或单独做 AT-08 后半 V2 迁移；开始前重新核对本节未完成项。
- 不要重复做：不要修改已执行的 V1 迁移，不要把取消和缺席合并为同一状态，不要在 P0 引入邮件、系统推送或云同步。

### 窗口 2026-08-26-5

- 目标：M2 第二切片（前端）— application + dashboard 页面，收尾 AT-05~AT-09 前端部分。后端 7 端点已就绪，前端类型已生成，照抄 jobs 三件套模式。
- 状态：**DONE**（本窗口目标全部达成；M2 投递状态机 + 下一步行动的前端闭环可见，AT-05~AT-09 前端 API 链路验证通过）。
- 提交：`feat(application): M2 slice 2 — frontend application + dashboard pages (AT-05..AT-09)`，推送至 `origin/feat/m2-application-backend`；随后 fast-forward 合并至 `main` 并推送，`main` 已包含全部内容（`bash.exe.stackdump` 为 Git Bash 崩溃转储、无用途，已删除并加入 `.gitignore` 的 `*.stackdump`）。
- 已完成：
  - **API 层**：
    - `frontend/src/api/applications/`：`applicationApi.ts`（6 端点纯函数，类型从 generated 导入，transition 用稳定 Idempotency-Key `transition:${applicationId}:${targetStatus}` 覆盖自动注入以支持网络重试回放）、`useApplicationQueries.ts`（useApplicationList/Detail/StatusHistory，query key `['applications', ...]`）、`useApplicationMutations.ts`（create/update/transition，onSuccess 局部 setQueryData 合并 + invalidate applications + dashboard，version 从 detail 回填）
    - `frontend/src/api/dashboard/`：`dashboardApi.ts`（getDashboardOverview）、`useDashboardQueries.ts`（useDashboardOverview，query key `['dashboard']`）
  - **Feature — applications**：
    - `applicationStatusLabels.ts`：8 状态文案 + Badge variant + `ALLOWED_TRANSITIONS` 转换矩阵（编码 02-state-machines.md §3）+ transitionTargetLabel；复用 jobs 的 formatDateTime
    - `components/applicationFormValues.ts`：ApplicationFormValues + appToValues + toCreateRequest + toUpdateRequest（**全字段覆盖写**，可空字段空值传 null）+ isoToLocalDatetime/localDatetimeToIso 双向转换（datetime-local 本地 ↔ ISO UTC）
    - `components/ApplicationForm.tsx`：create/edit 表单，复用 Field/Input/Textarea + InlineFieldError + 客户端校验
    - `ApplicationCreatePage.tsx`：从 `?jobId=` 预填，useJob 显示岗位摘要，成功 navigate 详情；错误分支 DUPLICATE_APPLICATION/IDEMPOTENCY_CONFLICT/校验/网络
    - `ApplicationDetailPage.tsx`：P04 五区 + Spinner/ErrorState 守卫
    - 五区组件：`ApplicationSummarySection`（区1 摘要 dl）、`ApplicationStatusSection`（区2 状态 Badge + 合法目标转换按钮 + ON_HOLD resume 特殊分支 + OFFER 逃生舱 checkbox + 非法转换 ConflictBanner，key 重建同步版本）、`NextActionSection`（区3 行内编辑，PUT 全字段回填，逾期/缺失提示）、`StatusTimelineSection`（区4 statusHistory 倒序）、`InterviewListSection`（区5 空数组 EmptyState 占位）
  - **Feature — dashboard**：`DashboardPage.tsx`（行动识别区块：actionItems 按 priority 1逾期/2缺失/3一般 排序 + 来源链接 + 进行中投递 + 最近岗位 + 全空首会话引导）
  - **路由 & 布局改动**：`routes.tsx`（新增 `/dashboard`、`/applications/new`、`/applications/:applicationId`，默认 `/`→`/dashboard`，`/applications/new` 在 `:applicationId` 前以保静态优先）、`Sidebar.tsx`（置顶"首页工作台"导航项）、`DecisionSection.tsx`（APPLY 提示改为"创建投递记录"NavLink → `/applications/new?jobId=`）
- 未完成（留下一窗口）：
  - 面试与提醒（AT-10~AT-14）：interview + reminder 模块后端 + 同事务推进投递 + 改期替换提醒 + 取消/缺席 + 本地提醒调度
  - AT-08 后半 `allowDuplicate=true` 创建成功：需 V2 迁移重新设计唯一索引后补全
  - Playwright E2E（AT-01/09/11/15/18/20 前端浏览器自动化验收，当前用直连后端 curl 验证 API 链路）
  - 真实浏览器点击验证（本环境 Vite dev 后台进程不稳定，未做 UI 交互；前端构建 + API 链路已验证）
- 修改文件：
  - 新增（applications API）：`frontend/src/api/applications/{applicationApi,useApplicationQueries,useApplicationMutations}.ts`
  - 新增（dashboard API）：`frontend/src/api/dashboard/{dashboardApi,useDashboardQueries}.ts`
  - 新增（applications feature）：`frontend/src/features/applications/applicationStatusLabels.ts`、`components/{applicationFormValues,ApplicationForm,ApplicationSummarySection,ApplicationStatusSection,NextActionSection,StatusTimelineSection,InterviewListSection}.tsx/.ts`、`ApplicationCreatePage.tsx`、`ApplicationDetailPage.tsx`
  - 新增（dashboard feature）：`frontend/src/features/dashboard/DashboardPage.tsx`
  - 修改：`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`、`frontend/src/features/jobs/components/DecisionSection.tsx`
  - 重新生成（不入库）：`frontend/src/api/generated/types.ts`（`npm run gen-types`，确认含 PageApplication.totalPages）
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`（本交接 + 总状态/代码事实更新）
- 已运行验证：
  - `cd frontend && npm run gen-types` → 类型重新生成成功
  - `npm run lint`（oxlint）→ 0 error / 0 warning
  - `npm run typecheck`（tsc -b --noEmit）→ 0 error
  - `npm run build`（gen-types + tsc -b + vite build）→ BUILD SUCCESS，166 模块，dist 353.20kB（gzip 109.48kB）
  - `cd backend && mvn clean test` → BUILD SUCCESS，9 类 28 方法全绿（0 failures/0 errors），后端基线未破坏
  - `mvn spring-boot:run` → Tomcat 8080 启动，Flyway V1 验证通过
  - 端到端 API 链路验证（curl 直连后端，UTF-8 文件喂 JSON 避开 Windows GBK 编码问题）：
    - 创建岗位 201 → 创建投递 201（DRAFT v0）→ AT-08 重复投递 409 ✓
    - AT-05 DRAFT→APPLIED 转换成功，时间线首条 `DRAFT->APPLIED reason=已投递简历`，PUT 不新增历史 ✓
    - AT-06 APPLIED→OFFER 非法转换 422 ✓
    - AT-07 相同 Idempotency-Key 回放返回 APPLIED，历史仍 1 条（不新增）✓
    - AT-09 dashboard actionItems=1（prio=3 一般行动，dueAt=2026-09-01，title=跟进HR）、activeApplications=1、recentJobs=3 ✓
- 验证结果：前端可 lint/typecheck/build，后端 28 测试全绿，AT-05~AT-09 前端 API 链路端到端验证通过。M2 第二切片（前端）交付完整。
- 已知问题：
  1. 真实浏览器 UI 交互未验证（Vite dev 后台进程在本环境不稳定）；前端类型/构建/错误处理已就绪，API 链路已验证，浏览器点击验证留下一窗口或 Playwright。
  2. dashboard `activeApplications` 缺岗位标题（Application 只含 jobId）；首版用 `actionItems[].sourceRef.label` 关联 applicationId 获取标题，无法关联的显示"查看详情"。后端可考虑在 dashboard 聚合时附带 job title（但 Application 对象契约不含，需扩展 DTO）。
  3. transition 稳定 Idempotency-Key `transition:${applicationId}:${targetStatus}`：残余风险是用户改 reason 重试同 target → 409 IDEMPOTENCY_CONFLICT（UI 上转换成功后按钮消失，概率极低，Toast 处理）。
  4. 后端 DashboardController 的 `upcomingInterviews`/`weakKnowledgePoints` 返回 Placeholder 类型，OpenAPI 定义为 `Interview[]`/`WeakKnowledgePoint[]`；后端实际返回空数组，前端按空数组处理不访问元素属性，运行时无碍，类型层面靠空数组规避。
  5. OFFER 转换前置真实校验仍依赖面试模块（未实现），前端用逃生舱 checkbox `allowOfferWithoutCompletedInterview`。
  6. `InterviewListSection` 接收 `Interview[]`（从 generated 的 `components['schemas']['Interview']` 取类型），当前恒空数组占位。
- 下一窗口只做：
  1. 面试与提醒后端（AT-10~AT-14）：`backend/src/main/java/com/jobhub/interview/` + `reminder/` 模块四层；创建面试同事务推进投递至 INTERVIEWING + 默认 3 条提醒；改期替换未触发提醒；取消/缺席取消提醒 + 拒绝设结果；本地提醒调度（PENDING→到期展示，不承诺系统推送）。OpenAPI 已有完整面试契约。
  2. 或补 Playwright E2E 框架 + AT-01/09 前端浏览器自动化验收。
  3. 或 AT-08 后半（需 V2 迁移重新设计 `uq_application_active_per_job` 唯一索引）。
- 不要重复做：
  - 不要重写前端 application/dashboard 页面或 API 三件套（已 DONE，lint/typecheck/build 全绿）。
  - 不要手写 Application 枚举类型（前端由 openapi-typescript 从 03-openapi.yaml 生成）。
  - 不要修改 `V1__initial_schema.sql`（AT-08 allowDuplicate 如需放宽唯一索引，新增 V2 迁移）。
  - 不要提前实现复盘/任务（M2 面试之后）、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，用 npm）。

### 窗口 2026-08-26-4

- 目标：M2 第一切片（后端）— 投递聚合 `application` 模块四层 + dashboard 聚合端点 + AT-05~AT-09 后端集成测试。前端 application 页面与面试/提醒留后续窗口。
- 状态：**DONE**（本窗口目标全部达成；M2 整体转 **IN_PROGRESS**，AT-05~AT-09 后端可验证部分全绿）。
- 已完成：
  - **application 模块四层**（`backend/src/main/java/com/jobhub/application/`）：
    - `domain/`：`ApplicationStatus`（8 状态枚举）、`Application`（聚合根，含 `transition` 状态机：矩阵编码 `EnumMap<状态,Set<允许目标>>` + ON_HOLD 往返 `previousActiveStatus` 保存/恢复 + OFFER 前置 `allowOfferWithoutCompletedInterview` 逃生舱 + DRAFT→APPLIED 校验 appliedAt/channel + `updateMeta` 全字段覆盖）、`StatusLogEntry`（不可覆盖历史实体）
    - `infrastructure/`：`ApplicationMapper`（insert/selectById/selectActiveByJobId/selectPage/selectPageCount/updateMetaByIdAndVersion/updateStatusAndPreviousByIdAndVersion/bumpVersionByIdAndVersion/selectActiveForDashboard，注解 SQL + 乐观锁三步走）、`StatusLogMapper`（insert/selectByApplication，仅 INSERT/SELECT 禁改历史）
    - `application/`：`ApplicationService`（create 含 409 查重、get/getDetail 聚合 job+statusHistory、list、update 全字段覆盖、transition 写 status_log+版本递增、listStatusHistory）、5 个 command/query/result record、`DuplicateApplicationException`
    - `api/`：`ApplicationController`（6 端点：POST/GET 列表/GET 详情/PUT/POST transition/GET status-history，If-Match-Version 缺失返回 400）、3 个 Request（JavaBean+校验）、4 个 Response（record+from）
  - **dashboard 聚合**（`backend/src/main/java/com/jobhub/dashboard/`）：`DashboardService`（actionItems 生成 APPLICATION_ACTION_DUE：缺失/逾期/一般 + priority 排序逾期优先；activeApplications；recentJobs 复用 JobMapper.selectPage；upcomingInterviews/weakKnowledgePoints 空数组）、`DashboardController`（GET /api/dashboard → DashboardOverviewResponse）、`Application.nextActionOverdue/nextActionMissing` 辅助方法、`JobMapper.selectByIds` 批量查询（避免 N+1）
  - **OpenAPI 细化**：`PageApplication` 补 `totalPages` 字段（与 PageJob 一致，非破坏性）
  - **测试**：扩展 `DatabaseCleaner`（加 application_status_log/interview_schedule/application_record 清理）、`TestFixtures`（createApplicationBody/transitionBody/updateApplicationBody）；新增 4 测试类共 10 方法
- 未完成（留下一窗口）：
  - 前端 application 页面：P04 投递详情页（5 区：摘要/当前状态/下一步行动/时间线/面试列表）+ 创建投递表单；P01 dashboard 行动识别区块（今天应做什么 + 进行中投递）
  - AT-08 后半 `allowDuplicate=true` "创建成功"：V1 部分唯一索引 `uq_application_active_per_job` 限制同岗位最多一条活动投递，本切片搁置，待后续窗口用 V2 迁移重新设计唯一索引后补全；当前 allowDuplicate=true 仍返回 409
  - 面试与提醒（AT-10~AT-14）：创建面试同事务推进投递、改期替换提醒、取消/缺席、本地提醒调度
  - Playwright E2E（AT-01/09/11/15/18/20 前端验收）
- 修改文件：
  - 新增（application/）：`domain/{ApplicationStatus,Application,StatusLogEntry}.java`、`infrastructure/{ApplicationMapper,StatusLogMapper}.java`、`application/{ApplicationCreateCommand,ApplicationUpdateCommand,ApplicationTransitionCommand,ApplicationListQuery,ApplicationListResult,DuplicateApplicationException,ApplicationService}.java`、`api/{ApplicationCreateRequest,ApplicationUpdateRequest,ApplicationTransitionRequest,ApplicationResponse,ApplicationDetailResponse,StatusLogResponse,PageApplicationResponse,ApplicationController}.java`
  - 新增（dashboard/）：`package-info.java`、`application/DashboardService.java`、`api/DashboardController.java`
  - 修改：`backend/src/main/java/com/jobhub/job/infrastructure/JobMapper.java`（加 selectByIds）
  - 修改：`backend/src/test/java/com/jobhub/integration/support/{DatabaseCleaner,TestFixtures}.java`
  - 新增：`backend/src/test/java/com/jobhub/integration/{ApplicationTransitionIntegrationTest,ApplicationIdempotencyIntegrationTest,ApplicationCrudIntegrationTest,DashboardIntegrationTest}.java`
  - 修改：`docs/jobhub/03-openapi.yaml`（PageApplication 补 totalPages）、`docs/jobhub/IMPLEMENTATION_STATUS.md`（本交接 + 总状态/里程碑状态更新）
- 已运行验证：
  - `cd backend && mvn clean compile` → BUILD SUCCESS（无编译错误）
  - `cd backend && mvn clean test` → BUILD SUCCESS，0 failures/0 errors
  - 测试统计：原 18 + 新增 10 = 28 方法全绿（ApplicationCrud 4、ApplicationIdempotency 2、ApplicationTransition 2、Dashboard 2，加原 Idempotency 3/IllegalTransition 2/JobCrud 7/RequirementConfirmation 3/VersionConflict 3）
  - 覆盖 AT：AT-05（转换写历史 + PUT 不改历史）、AT-06（非法转换 422 + 零副作用）、AT-07（幂等回放/冲突）、AT-08 前半（409 DUPLICATE_APPLICATION）、AT-09（缺失/逾期行动 + 优先排序）
- 验证结果：后端可编译、可测试，AT-05~AT-09 后端可验证部分全绿。M2 第一切片（后端）交付完整。
- 已知问题：
  1. AT-08 后半 `allowDuplicate=true` 创建成功未实现（V1 唯一索引限制），本切片搁置；当前 allowDuplicate=true 返回 409 DUPLICATE_APPLICATION，message 提示"secondary application creation is not supported in this slice"。需 V2 迁移重新设计唯一索引后补全（可能改为允许同岗位多条活动投递，或改为先终止旧投递再创建——需与 PRD"每岗位一条活动投递"规则权衡）。
  2. OFFER 转换前置"至少一场 COMPLETED 面试"本切片无法真正校验（面试模块未实现），仅支持 `allowOfferWithoutCompletedInterview=true` 逃生舱；待面试模块实现后在 ApplicationService.transition 补查询 interview_schedule 的 COMPLETED 计数。
  3. ApplicationDetail.interviews 恒空数组（面试模块未实现）；ApplicationDetailResponse.from 预留 interviews 参数重载供后续填充。
  4. dashboard 的 upcomingInterviews / weakKnowledgePoints 返回空数组占位（面试/复盘/任务模块未实现）；recentJobs 复用 JobMapper.selectPage 按 updated_at DESC 取 10 条。
  5. 后端冒烟启动（mvn spring-boot:run + curl /api/dashboard）未在本环境执行（后台启动权限受限）；行为已由 DashboardIntegrationTest 端到端验证（真实 RANDOM_PORT Tomcat + 真实 Flyway + 真实 HTTP）。
  6. transition 的 Idempotency-Key 已写入 application_status_log.idempotency_key 列（审计追溯），但未做专门断言（AT-07 未要求）。
  7. PUT 投递采用全字段覆盖写（与 job updateBasicInfo 一致），nextAction/nextActionDueAt/rejectionReason 传 null 即清空；前端实现 PUT 时需回填所有字段（前端已在 job 模块如此做）。
- 下一窗口只做：
  1. 前端 application 模块：`frontend/src/api/applications/`（applicationApi + useApplicationQueries + useApplicationMutations，复用现有 client/Idempotency/错误归一化）、`frontend/src/features/applications/`（ApplicationListPage + ApplicationCreatePage + ApplicationDetailPage 五区 + 创建表单）；OpenAPI 重新生成类型（PageApplication.totalPages 已补）。
  2. 前端 dashboard：`frontend/src/features/dashboard/DashboardPage`（今天应做什么 + 进行中投递，调 GET /api/dashboard）。
  3. 手动验证 AT-09 前端（dashboard 行动识别）+ AT-05/06/07/08 前端交互。
  4. 或先做面试/提醒后端（AT-10~AT-14）：interview 模块 + reminder 模块 + 同事务推进投递 + 提醒调度。二选一，建议先补前端 application 页面让 M2 投递闭环可见。
- 不要重复做：
  - 不要重写 application 后端四层或 dashboard 聚合（已 DONE，10 测试全绿）。
  - 不要手写 Application 枚举类型（前端由 openapi-typescript 从 03-openapi.yaml 生成）。
  - 不要修改 `V1__initial_schema.sql`（AT-08 allowDuplicate 如需放宽唯一索引，新增 V2 迁移）。
  - 不要提前实现面试/提醒/复盘/任务（M2 投递之后）、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，用 npm）。

### 窗口 2026-08-26-3

- 目标：M1 slice 1 收尾 — 创建前端工程骨架（Vite + React + TS + TanStack Query + axios）与 `JobListPage`/`JobCreatePage`/`JobDetailPage` 三页面，OpenAPI 类型生成，跑通 AT-01 端到端，提交。
- 状态：**DONE**（本窗口目标全部达成；M1 slice 1 整体转 **DONE**，M1 里程碑完成，下一窗口起步 M2）。
- 已完成：
  - 契约对齐：OpenAPI 补 3 个后端已返回但契约遗漏的字段（`PageJob.totalPages`、`JobRequirement.sortOrder`、`RequirementExtractionResult.newCount`，属细化非破坏性变更）；修正 `java-jobhub-prd.md → jobhub-prd.md` 的 4 处过时引用（AGENTS.md、01-page-spec.md、IMPLEMENTATION_MASTER_PROMPT.md、IMPLEMENTATION_STATUS.md）。
  - 前端工程：`npm create vite@latest frontend -- --template react-ts` 生成骨架（Vite 8 / React 19.2 / TS 5.6.3 / oxlint）；补依赖 `@tanstack/react-query@5.59`、`axios@1.7`、`react-router-dom@6.26`、`openapi-typescript@7.4`；`vite.config.ts`（`@` 别名 + proxy `/api → 127.0.0.1:8080`）、`tsconfig.app.json`（`strict` + `paths`）、oxlint flat config（`no-explicit-any:error`）。
  - 类型生成：`openapi-typescript` 从 `03-openapi.yaml` 生成 `src/api/generated/types.ts`（`prepare`/`build` 自动生成，不入库，`.gitignore` 忽略）；枚举（JobStatus/JobDecisionStatus/RequirementType/RequirementConfirmationStatus/GapStatus）全部字面量联合，零手写。
  - API 层：`client.ts`（axios 实例 + 请求拦截器对写操作自动注入 `Idempotency-Key` + 响应拦截器错误归一化：空 body 400 构造 VALIDATION_ERROR、JSON body 解析为 ApiError、网络错误 NetworkError）；`errors.ts`（ApiError + 判别函数 isVersionConflict/isIllegalTransition/isIdempotencyConflict/isNotFound/isValidationError）；`queryClient.ts`（业务错误不重试，mutations 不重试）。
  - Hooks：`useJobQueries`（useJobList/useJob/useJobRequirements/useGapList）+ `useJobMutations`（useCreateJob/useUpdateJob/useArchiveJob/useRestoreJob/useExtractRequirements/useUpdateRequirement），`If-Match-Version` 从 query data 回填，`onSuccess` 局部 `setQueryData` + invalidate。
  - 三页面 + 组件：`JobListPage`（表格 + URL query 筛选 + 分页 + 归档/恢复 + 空状态）、`JobCreatePage`（JobForm 创建模式，成功后进入详情不回列表）、`JobDetailPage`（P03 四区：JobSummarySection/DecisionSection/RequirementConfirmationSection/GapListSection）；通用组件 ui/layout/feedback。
  - 后端实际行为适配（不改正后端）：archive/restore/PUT 强制 `If-Match-Version`（缺失空 body 400，API 层 version 必填）；`VERSION_CONFLICT` reason 含 currentVersion（前端不解析，直接 invalidate 重读）；错误码 `NOT_FOUND`（非 RESOURCE_NOT_FOUND）；`GapItem.evidence` 恒空按空渲染；merge 端点未实现不接前端。
- 未完成（留下一窗口）：
  - M2 起步：投递状态机、下一步行动（AT-05~AT-09）；面试与提醒（AT-10~AT-14）。
  - Playwright E2E（验收门槛要求 AT-01/09/11/15/18/20 前端 E2E，本窗口用 node 脚本验证 AT-01 API 闭环，未搭 Playwright）。
  - Vite proxy 5173 未做真实浏览器点击验证（本环境后台进程管理导致 vite 不稳定，改用直连后端 8080 脚本验证；proxy 配置已由 typecheck/build 确认）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`（补 3 字段）、`AGENTS.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/IMPLEMENTATION_MASTER_PROMPT.md`、`docs/jobhub/IMPLEMENTATION_STATUS.md`（PRD 引用修正 + 本交接 + 总状态转 DONE）。
  - 修改：`.gitignore`（追加 `frontend/src/api/generated/`）。
  - 新增（frontend/）：`package.json`、`package-lock.json`、`vite.config.ts`、`tsconfig.json`、`tsconfig.app.json`、`tsconfig.node.json`、`eslint.config.js`(实际为 `.oxlintrc.json`)、`index.html`、`src/main.tsx`、`src/app/{AppProviders,routes,queryClient}.tsx`、`src/api/{client,idempotency,errors}.ts`、`src/api/jobs/{jobApi,useJobQueries,useJobMutations}.ts`、`src/components/ui/{Badge,Button,Form,Table,Spinner,EmptyState,ErrorState}.tsx`、`src/components/layout/{Sidebar,TopBar,AppLayout}.tsx`、`src/components/feedback/{Toast,toastStore,InlineFieldError,ConflictBanner}.tsx`、`src/features/jobs/{JobListPage,JobCreatePage,JobDetailPage,statusLabels}.tsx`、`src/features/jobs/components/{JobForm,jobFormValues,JobSummarySection,DecisionSection,RequirementConfirmationSection,RequirementRow,GapListSection}.tsx`、`src/styles/globals.css`、`src/api/generated/types.ts`（不入库）。
- 已运行验证：
  - `cd frontend && npm install` → 88 包，`prepare` 自动 gen-types 成功。
  - `npm run typecheck`（`tsc -b --noEmit`）→ 0 error。
  - `npm run lint`（`oxlint src`）→ 0 error / 0 warning。
  - `npm run build`（`gen-types && tsc -b && vite build`）→ 150 模块，BUILD SUCCESS，dist/index.html 0.47kB + assets/index 330.95kB（gzipped 105.27kB）。
  - `cd backend && mvn spring-boot:run` → Tomcat 8080 + Flyway v1 验证通过。
  - AT-01 端到端（node 脚本直连后端）：GET /jobs 200 → POST /jobs 201(version=0) → POST extract 200(candidates=4,newCount=4) → PUT 无 If-Match-Version 400 空 body → 确认 3 项 200(CONFIRMED,version=1) → GET gap-list 200(3 项全 INSUFFICIENT_INFO) → PUT decision 200(TO_APPLY,version=1) → GET /jobs 列表可见 TO_APPLY。断言 ✅ 全部通过。
- 验证结果：前端可编译、可 lint、可 build；后端 AT-01 端到端业务闭环验证通过。M1 slice 1 交付完整。
- 已知问题：
  1. Vite dev server 在本环境后台进程管理下不稳定（端口被占用切换 5174、nohup 后退出），未做真实浏览器点击验证；下一窗口如需前端交互验证，建议前台启动 `npm run dev` 或用 Playwright。
  2. 列表页"已确认关键要求数""差距概览"列本窗口简化（GET /jobs 不返回该字段，避免 N+1），详情页有完整差距与要求。
  3. APPLY 决定只保存 decisionStatus，不创建投递（POST /applications 后端 M2 才实现）。
  4. merge 端点 OpenAPI 有定义但后端未实现，前端未接。
  5. AT-03"人工修正记录保留"软失效仍待 V2 迁移（`RequirementMatch` 当前 deleteByJobId 硬删除）；本窗口前端 manualMatchStatus 走 PUT requirement 的 manualMatchStatus 字段，对应 AT-04 路径。
  6. Playwright E2E（AT-01/09/11/15/18/20）未补，属后续切片。
- 下一窗口只做：
  1. 起步 M2：投递状态机（POST /applications + transition）、下一步行动（AT-05~AT-09）。先 OpenAPI（已有契约）+ 后端 application 模块 + 集成测试，再前端 application 页面。
  2. 投递详情页 P04 + 创建投递表单；dashboard P01 行动识别（AT-09）。
  3. 补 Playwright E2E 框架（如需前端自动化验收）。
- 不要重复做：
  - 不要重写前端骨架或 job 三页面（已 DONE，lint/typecheck/build 全绿）。
  - 不要重新生成或手改 OpenAPI 类型（`src/api/generated/types.ts` 由 `prepare`/`build` 自动生成，`.gitignore` 已忽略）。
  - 不要改 `V1__initial_schema.sql`（结构变更新增 V2 迁移）。
  - 不要提前实现面试/提醒/复盘/任务（M2 投递之后）、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，用 npm）。

### 窗口 2026-08-26-2

- 目标：补齐 M1 slice 1 后端集成测试（AT-01~AT-04 + 幂等/版本/非法转换），让 `mvn clean test` 全绿。范围仅限后端测试 + 修复测试暴露的生产 bug，不含前端与端到端。
- 状态：**DONE**（本窗口目标全部达成；M1 slice 1 整体仍 PARTIAL，前端骨架与端到端验证待下一窗口）
- 已完成：
  - 测试基础设施：`backend/src/test/resources/application-test.yml`（测试库 `./target/jobhub-it.db`，日志降噪）、`AbstractIntegrationTest`（`@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("test")` + `TestRestTemplate` + `JdbcTemplate` + `DatabaseCleaner`）、`DatabaseCleaner`（`@Component`，每方法按 FK 顺序清表）、`TestFixtures`（JD 样例 + 请求体/头构造）、`JsonProbe`（Jackson JSON 探针，替代 MockMvc jsonPath）
  - 5 个集成测试类共 18 方法全绿（`mvn clean test` BUILD SUCCESS，0 failures/0 errors）：
    - `JobCrudIntegrationTest`（7 方法：创建/列表分页过滤/详情404/更新版本自增/archive-restore 状态机/extract 全 PENDING/gap-list 空基线）
    - `RequirementConfirmationIntegrationTest`（3 方法：AT-02 PENDING 排除/AT-03 JD 修改回退/AT-04 manualMatchStatus + reason + 快照保留）
    - `IdempotencyIntegrationTest`（3 方法：相同key+相同body 回放/相同key+不同body 409/不同key 独立创建）——验证 Filter+Interceptor 真实链路
    - `VersionConflictIntegrationTest`（3 方法：AT-22 旧版本409+当前版本/当前版本成功+1/缺失头400）
    - `IllegalTransitionIntegrationTest`（2 方法：archive 已归档 422/restore 活跃 422，含 currentState/targetState/reason + 无副作用）
  - 修复 3 处生产代码 bug（测试暴露）：
    1. `VersionCheck` 版本冲突返回旧版本而非当前版本（AT-22 要求返回当前版本供客户端刷新）：`VersionCheck.requireAffected` 参数语义从 `expectedVersion` 改为 `currentVersion`；`JobService`（updateJob/archive/restore 共 7 处）、`RequirementService`（2 处）调用方改传 `job.getVersion()`/`req.getVersion()`（selectById 读出的真实当前版本）
    2. MyBatis 注解 SQL 参数绑定缺失 `@Param`：`JobMapper`（updateBasicInfo/updateDecision/updateStatus）、`JobRequirementMapper`（updateByIdAndVersion/updateStatusByIdAndVersion）、`RequirementMatchMapper`（updateByRequirementIdAndVersion）原用裸属性名（`#{companyName}`），多参数时 MyBatis 无法解析。改用 `#{job.companyName}` 等显式 `@Param` 前缀
    3. `JobService.updateJob` JD 变更判断失效：`job.jdChanged(cmd.jdRawText())` 在 `updateBasicInfo` 覆盖 job 对象后调用，此时 job.jdRawText 已是新值故必返回 false，导致修改 JD 不触发要求回退（AT-03 失败）。改为在覆盖前快照并预计算 `originalJdChanged`
    4. `JobRequirementMapper` 三个 SELECT 用 `SELECT *`，列名 `requirement_type`/`source_type` 与 Java 属性 `type`/`source` 不映射（MyBatis 驼峰 + 下划线对单字段生效但 `type`↔`requirement_type` 非简单驼峰）。改为显式列别名 `requirement_type AS "type"`、`source_type AS "source"`（否则 selectById 返回 type=null，confirm 时把 NOT NULL 的 requirement_type 清空触发约束失败）
- 未完成（留下一窗口）：
  - 前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios）与 `JobListPage`、`JobCreatePage`、`JobDetailPage` 三页面
  - 端到端 AT-01 手动验证（粘贴 JD → 确认 → 差距 INSUFFICIENT_INFO → 保存 TO_APPLY）
  - 提交本批测试与修复（用户未要求 commit，未自动提交）
- 修改文件：
  - 修改：`backend/src/main/java/com/jobhub/common/version/VersionCheck.java`（参数语义 currentVersion + 文档）
  - 修改：`backend/src/main/java/com/jobhub/job/application/JobService.java`（7 处 requireAffected 改传 job.getVersion()；updateJob 加 JD 变更预快照）
  - 修改：`backend/src/main/java/com/jobhub/job/application/RequirementService.java`（2 处 requireAffected 改传 req.getVersion()）
  - 修改：`backend/src/main/java/com/jobhub/job/infrastructure/JobMapper.java`（3 个 update 加 @Param("job") 前缀）
  - 修改：`backend/src/main/java/com/jobhub/job/infrastructure/JobRequirementMapper.java`（2 个 update 加 @Param("r") 前缀；3 个 SELECT 加列别名）
  - 修改：`backend/src/main/java/com/jobhub/job/infrastructure/RequirementMatchMapper.java`（1 个 update 加 @Param("match") 前缀）
  - 新增：`backend/src/test/resources/application-test.yml`
  - 新增：`backend/src/test/java/com/jobhub/integration/support/AbstractIntegrationTest.java`、`DatabaseCleaner.java`、`TestFixtures.java`、`JsonProbe.java`
  - 新增：`backend/src/test/java/com/jobhub/integration/JobCrudIntegrationTest.java`、`RequirementConfirmationIntegrationTest.java`、`IdempotencyIntegrationTest.java`、`VersionConflictIntegrationTest.java`、`IllegalTransitionIntegrationTest.java`
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`（本交接 + 总状态修正）
- 已运行验证：
  - `cd backend && mvn clean test` → BUILD SUCCESS
  - 5 个测试类结果：Idempotency 3/3、IllegalTransition 2/2、JobCrud 7/7、RequirementConfirmation 3/3、VersionConflict 3/3 = 18 方法全过，0 failures/0 errors
- 验证结果：后端集成测试全绿。当前代码**可编译、可启动、可测试**。
- 已知问题：
  1. 前端骨架与端到端验证均未做，slice 1 未端到端验收（AT-01 未手动跑）。
  2. AT-03"人工修正记录保留"未实现（`RequirementMatch` 注释明确本切片简化为 `deleteByJobId` 硬删除）；本切片 AT-03 测试不断言该项。后续切片需设计 requirement_match 软失效（可能 V2 迁移加 `invalidated_at` 列）后再补断言。
  3. VersionCheck 并发场景下 `selectById` 读出的版本可能与 update 时刻不同步（极窄窗口），本切片不处理，留待后续切片在 affected=0 时重读实体。
  4. 未提交本批修改（用户未要求）；`backend/target/jobhub-it.db` 测试库运行时生成，已被 `.gitignore` 的 `backend/target/` 覆盖。
  5. `JobRequirementMapper` 三个 SELECT 改用显式列别名而非 `SELECT *`，是行为修复（原 `SELECT *` 导致 type/source 不映射）；不视为破坏性变更，仅修 bug。
- 下一窗口只做：
  1. 创建前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios），实现 `JobListPage`、`JobCreatePage`、`JobDetailPage`。
  2. Vite proxy `/api → http://127.0.0.1:8080`；OpenAPI 类型生成（禁止手写枚举）。
  3. 运行 `npm install` + `npm run lint` + `npm run build`（pnpm 不可用，用 npm）。
  4. 手动验证 AT-01 端到端。
  5. commit：`feat(job): M1 slice 1 — frontend skeleton + job pages`，`git push`。
- 不要重复做：
  - 不要重写后端集成测试或测试基础设施（已 DONE，18 方法全绿）。
  - 不要重新修复 VersionCheck / MyBatis @Param / JD 回退 / SELECT 列别名（已修）。
  - 不要修改 `V1__initial_schema.sql`（AT-03 软失效如需加列，新增 `V2` 迁移）。
  - 不要提前实现 M2、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，改用 npm）。

### 窗口 2026-08-26-1

- 目标：修复后端编译并启动验证（M1 slice 1 收尾第一步）。范围仅限编译 + 启动 + 最小接口，不含集成测试与前端。
- 状态：**DONE**（本窗口目标全部达成；M1 slice 1 整体仍 PARTIAL，集成测试与前端待下一窗口）
- 已完成：
  - `mvn clean compile` 通过：修复 2 处编译错误（`GlobalExceptionHandler` 误用 `org.springframework.validation.FieldError` 遮蔽项目 `FieldError`，删多余 import；`IdempotencyInterceptor` 误用 `ContentCachingResponseWrapper.getStatusCode()` 改 `getStatus()`）
  - 启动失败修复：`IdempotencyRecordMapper` 原在 `common.idempotency` 不被 `@MapperScan("com.jobhub.**.infrastructure")` 覆盖（显式 `@MapperScan` 禁用了 `@Mapper` 自动扫描），移至 `common.idempotency.infrastructure/`
  - Flyway/SQLite 验证：无需 `flyway-database-sqlite`；V1 含 `PRAGMA foreign_keys=ON` 非事务语句，设 `execute-in-transaction=false` 后迁移成功（29 张表）
  - 修正 DB 路径：`${JOBHUB_DB_PATH:./backend/data/jobhub.db}` → `./data/jobhub.db`（相对 `backend/` 工作目录）
  - 启动成功：`mvn spring-boot:run`，Tomcat 监听 127.0.0.1:8080
  - 最小接口验证：`curl GET /api/jobs` → 200 + `{"items":[],"total":0,"page":1,"pageSize":20,"totalPages":0}`
  - 修正状态文档计数不一致（28→29 表、~35→53 文件、JobController 9+1 端点归属、删 pom 第 24 行错误引用、记录 mapper-locations 失效配置）
- 未完成（留下一窗口）：
  - ~~5 个集成测试类（`JobCrud`、`RequirementConfirmation`、`Idempotency`、`VersionConflict`、`IllegalTransition`），覆盖 AT-01~AT-04 + 幂等/版本/非法转换~~ — **已于窗口 2026-08-26-2 完成（18 方法全绿）**
  - 前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios）与 `JobListPage`、`JobCreatePage`、`JobDetailPage` 三页面
  - 端到端 AT-01 手动验证（粘贴 JD → 确认 → 差距 INSUFFICIENT_INFO → 保存 TO_APPLY）
  - 提交本批修复与剩余 slice 1 成果（用户未要求 commit，未自动提交）
  - **更正**：本批修复（GlobalExceptionHandler/IdempotencyInterceptor/application.yml/IdempotencyRecordMapper 迁移）实际已提交于 `36f1a4c chore: update idempotency and docs`（工作区 clean）；本条原写"未提交"为过时表述。
- 修改文件：
  - 修改：`backend/src/main/java/com/jobhub/common/error/GlobalExceptionHandler.java`（删多余 import）
  - 修改：`backend/src/main/java/com/jobhub/common/idempotency/IdempotencyInterceptor.java`（`getStatusCode`→`getStatus`；加 mapper import）
  - 修改：`backend/src/main/resources/application.yml`（DB 路径、`execute-in-transaction=false`）
  - 新增：`backend/src/main/java/com/jobhub/common/idempotency/infrastructure/IdempotencyRecordMapper.java`
  - 删除：`backend/src/main/java/com/jobhub/common/idempotency/IdempotencyRecordMapper.java`（迁移至 infrastructure 子包）
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`（本交接 + 计数修正）
- 已运行验证：
  - `cd backend && mvn clean compile` → BUILD SUCCESS（53 文件，9.0s）
  - `cd backend && mvn spring-boot:run` → Started JobHubApplication in 5.353s；Flyway `Successfully applied 1 migration ... now at version v1`；`Tomcat started on port 8080`
  - `curl -i http://127.0.0.1:8080/api/jobs` → 200 + 空分页
- 验证结果：编译、迁移、启动、最小接口均通过。当前代码**可编译、可启动**。
- 已知问题：
  1. 集成测试与前端均未做，slice 1 未端到端验收（AT-01 未跑）。
  2. `IdempotencyInterceptor`/`IdempotencyBodyCachingFilter` 已启动验证但未集成测试幂等回放与冲突的具体行为。
  3. 本批修复已提交于 `36f1a4c`（工作区 clean）；`backend/data/jobhub.db` 运行时生成，已由 `.gitignore` 忽略。
  4. `mybatis.mapper-locations` 配置无害失效（注解 SQL 无 XML），保留不改。
- 下一窗口只做：
  1. ~~编写 5 个后端集成测试覆盖 AT-01~AT-04 + 幂等/版本/非法转换~~ — **已完成于窗口 2026-08-26-2**
  2. 创建前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios），实现 `JobListPage`、`JobCreatePage`、`JobDetailPage`。
  3. 运行 `npm install` + `npm run lint` + `npm run build`（pnpm 不可用，用 npm）。
  4. 手动验证 AT-01 端到端。
  5. commit：`feat(job): M1 slice 1 — job CRUD, requirement confirmation, gap list (AT-01..AT-04)` 含本批修复，`git push`。
- 不要重复做：
  - 不要重复修复编译/启动问题（已 DONE）；不要重新加 `flyway-database-sqlite`（已验证无需）。
  - 不要修改 `V1__initial_schema.sql`（含 PRAGMA，靠 `execute-in-transaction=false` 解决，不改迁移）。
  - 不要重新设计 PRD、页面规格、状态机或 OpenAPI。
  - 不要提前实现 M2、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，改用 npm）。

### 窗口 2026-08-25-1

- 目标：M1 第一切片 — 可启动工程骨架 + 岗位垂直切片（AT-01~AT-04 后端 + 前端骨架）
- 状态：**PARTIAL**
- 已完成：
  - git 仓库初始化与远程推送（commit `chore: bootstrap JobHub repository with specs and V1 migration`）
  - 后端 `pom.xml`、`application.yml`、主类、common 模块（错误/幂等/版本/时间/ID）、job 模块四层（domain/infrastructure/application/api）共约 35 个 Java 文件
  - OpenAPI `RequirementUpdateRequest` 新增 `manualMatchStatus` 可选字段
- 未完成：
  - **后端未编译验证**（`mvn clean compile` 未成功执行）
  - **后端未运行集成测试**（5 个测试类尚未编写：`JobCrudIntegrationTest`、`RequirementConfirmationIntegrationTest`、`IdempotencyIntegrationTest`、`VersionConflictIntegrationTest`、`IllegalTransitionIntegrationTest`）
  - **前端工程骨架未创建**（`frontend/` 目录不存在，无 `package.json`/`vite.config.ts`/页面）
  - **Flyway SQLite 方言兼容性未验证**（用户移除了 `flyway-database-sqlite` 依赖，需确认 Flyway 能否识别 SQLite）
  - 未运行端到端验收（AT-01 端到端、curl `/api/jobs` 等）
- 修改文件：
  - 新增：`.gitignore`、`backend/pom.xml`、`backend/src/main/resources/application.yml`、`backend/src/main/java/com/jobhub/**/*.java`（约 35 个）
  - 修改：`docs/jobhub/03-openapi.yaml`（`RequirementUpdateRequest` 新增 `manualMatchStatus`）
- 已运行验证：
  - `git init`、`git commit`、`git push -u origin main` — 成功
  - `java -version` → 21.0.5；`mvn -version` → 3.9.9；`node -v` → 24.11.1；`git --version` → 2.52.0
  - `mvn clean compile` — **未完成**（用户在编译启动后中断）
- 验证结果：未运行，下一窗口必须先跑通编译再继续。
- 已知问题：
  1. 后端代码**未编译验证**，可能存在 import 错误、类型不匹配或 MyBatis 注解 SQL 语法问题。下一窗口必须先 `cd backend && mvn clean compile` 修复所有编译错误。
  2. `flyway-database-sqlite` 依赖被用户移除；Flyway 10.x 对 SQLite 的支持需要这个依赖，否则启动时会报 `No Flyway plugin found for SQLite`。**建议恢复该依赖**，版本 `10.20.1`。
  3. `IdempotencyInterceptor` 的 `operation` 字符串包含控制器方法全名，长度可能超过 `idempotency_record.operation` 列定义（V1 表未显式限制长度，SQLite TEXT 无限制，可接受）。
  4. `RequirementExtractor` 词典覆盖有限，符合 P0 规则提取要求；候选项均 `PENDING`，不会污染结论。
  5. `requirement_skill` 表本切片未写入；候选要求只存 `normalized_name` 文本，不绑定 `skill_id`。Gap list 默认 `INSUFFICIENT_INFO`，符合 AT-01 验收。
  6. `logback` 敏感日志过滤未实现（TODO，下一窗口补）。
  7. `application.yml` 中 `spring.datasource.url` 用了 `${JOBHUB_DB_PATH:./backend/data/jobhub.db}` 相对路径；运行时取决于工作目录。若从 `backend/` 启动 mvn，相对路径会解析为 `backend/backend/data/jobhub.db`。**下一窗口建议改为绝对路径或显式 `file:` 协议**。
- 下一窗口只做：
  1. 修复后端编译错误（先 `cd backend && mvn clean compile` 通过）。
  2. 验证 Flyway 能否识别 SQLite；若不能，恢复 `flyway-database-sqlite` 依赖。
  3. `cd backend && mvn spring-boot:run` 启动后端，curl `GET http://127.0.0.1:8080/api/jobs` 返回 200 + 空分页。
  4. 编写 5 个后端集成测试覆盖 AT-01~AT-04 + 幂等/版本/非法转换。
  5. 创建前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios），实现 `JobListPage`、`JobCreatePage`、`JobDetailPage` 三页面。
  6. 运行 `pnpm install && pnpm lint && pnpm tsc --noEmit && pnpm build`（注：本机 pnpm 不可用，已确认改用 `npm install` + `npm run lint` 等）。
  7. 手动验证 AT-01 端到端（粘贴 JD → 确认 3 项 → 差距显示 INSUFFICIENT_INFO → 保存 TO_APPLY）。
  8. 二次 commit：`feat(job): M1 slice 1 — job CRUD, requirement confirmation, gap list (AT-01..AT-04)`，`git push`。
- 不要重复做：
  - 不要重新设计 PRD、页面规格、状态机或 OpenAPI（除已记录的 `manualMatchStatus` 扩展）。
  - 不要重新初始化 git 仓库或修改 `.gitignore`。
  - 不要修改 `V1__initial_schema.sql`（结构变更需新增 `V2` 迁移）。
  - 不要提前实现 M2（投递/面试/提醒）、AI、外部通知、云同步、附件上传、综合匹配评分。
  - 不要使用 pnpm（本机不可用，改用 npm）。

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
