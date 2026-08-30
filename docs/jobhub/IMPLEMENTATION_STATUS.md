# JobHub 实现进度与动态交接

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：P1（V0.2）进行中，已完成十八个切片：AI 基础设施与 JD 结构化提取；设置页时区与默认提醒节点；提醒到期调度 + 通知中心闭环；复盘 reopen；技能画像页 + Dashboard 弱点真实聚合；要求合并；可解释岗位匹配报告；完整复盘分析；数据导入与完整恢复；浏览器与邮件提醒；CSV 导出；简历定制草稿；复杂恢复报告；附件证据引用元数据库；多实例提醒协调与失败重试；AI 面试问题分类；AI 回答质量分析；AI 学习任务建议。
- 里程碑说明：V0.2 主流程已完成；当前仅剩按需补充的 `ai_provider` 删除端点。附件仍遵守本地安全约束，只保存用户填写的引用元数据，不实现文件上传、读取、扫描、下载或校验。
- 当前里程碑：P1/V0.2 `IN_PROGRESS`；P0 四个里程碑 M1~M4 与 AT-01~AT-24 保持全部完成，新增 P1 验收 AT-26、AT-17A、AT-17B 已覆盖。
- 当前任务：AI 学习任务建议候选已完成；`ai_provider` 删除端点仍按需保留，后续窗口可按需求决定是否补充。
- 当前负责人窗口：Codex。
- 最后更新：2026-08-30（窗口 2026-08-30-07）。

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

### 窗口 2026-08-30-07

- 目标：完成 P1 AI 学习任务建议候选垂直切片；候选仅供人工编辑和采纳，采纳后才创建 TODO 学习任务及可追溯来源。
- 状态：**DONE**。
- 已完成：
  - 先更新 OpenAPI、状态机、数据库设计、页面规格、PRD、技术实施说明和 AT-17C；新增 `POST /interview-questions/{questionId}/ai-task-suggestion`，扩展 `TASK_SUGGESTION`、`LEARNING_TASK` 和候选 `taskId` 契约。
  - 新增 Flyway `V14__link_ai_task_suggestions.sql` 保存采纳后任务 ID；新增 `V15__allow_task_suggestion_ai_jobs.sql` 扩展 AI 任务/条目约束，保持 V1~V13 不变。
  - 新增 `TASK_SUGGESTION_V1` handler：只读取问题、用户回答、参考答案、错误原因、改进方案和已有知识点；输出一个可编辑学习任务候选，不创造事实、知识点或任务。
  - 采纳前候选保持 `PROPOSED`；采纳要求问题当前版本，使用既有任务命令在同一事务创建 TODO 任务、QUESTION/KNOWLEDGE_POINT 来源并回链 `taskId`；过期版本不产生任务副作用。
  - 复盘页新增 AI 学习任务建议区，支持生成、轮询、编辑任务字段、采纳创建或拒绝；保留原有手工创建学习任务路径。
  - 集成测试覆盖弱问题限制、候选不自动创建任务、采纳字段编辑、来源关联、任务回链和问题版本冲突；新增浏览器 E2E 覆盖主路径。
  - 同步修正 E2E 的精确文本定位、AI provider 显式激活和跨测试日期窗口隔离，避免共享测试数据库造成误报。
- 未完成：
  - `ai_provider` 删除端点仍按需保留。
  - 不自动创建学习任务、不自动提升技能等级、不自动清除薄弱点、不读取或上传证据引用位置内容。
- 修改文件：
  - 规格：`docs/jobhub/01-page-spec.md`、`02-state-machines.md`、`03-openapi.yaml`、`04-database-design.md`、`05-acceptance-test-cases.md`、`06-technical-implementation.md`、`jobhub-prd.md`、本文件。
  - 后端：`V14__link_ai_task_suggestions.sql`、`V15__allow_task_suggestion_ai_jobs.sql`、任务建议 handler、AI 任务服务/API/模型/Mapper、`AiIntegrationTest`。
  - 前端：AI API/hooks、复盘页任务建议区、假 AI 服务、任务建议 E2E，以及相关回归用例隔离修正。
- 已运行验证：
  - `mvn -Dtest=AiIntegrationTest test`：6 tests，0 failures，0 errors。
  - `mvn clean test`：86 tests，0 failures，0 errors；Flyway V1→V15 全部通过。
  - `npm run gen-types`、`npm run typecheck`、`npm run lint`、`npm run build`：全部通过。
  - `npm run e2e -- e2e/p1-ai-task-suggestion.spec.ts --reporter=list`：1 passed。
  - `npm run e2e -- --reporter=list`：25 passed，0 failed。
- 已知问题：
  - Playwright 仍输出既有 React Router v7 future flag 与 Node `NO_COLOR` warning，不影响断言。
  - Git 仍可能输出用户级 `C:\Users\35433/.config/git/ignore` 权限 warning，不影响仓库文件检查。
- 下一窗口只做：
  - 按用户需求决定是否补充 `ai_provider` 删除端点；否则 P1/V0.2 当前规划切片完成。
- 不要重复做：
  - 不要修改 V1~V15；不要让 AI 自动创建任务或覆盖用户事实；不要增加附件读取、上传、扫描或下载。

### 窗口 2026-08-29-17

- 目标：P1 第十一切片——AI 异步任务基础设施（PRD 9.2）+ 首个场景 JD 结构化提取；用户已明确同意接入 AI 并要求供应商可随时切换；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - Flyway 迁移 `V7__create_ai_infrastructure.sql`（ai_provider / ai_job / ai_job_item）与 `V8__add_ai_job_item_sort_order.sql`（条目排序，保持模型输出顺序）。
  - 供应商抽象：`AiChatClient` 接口 + `AiClientFactory` 按 provider_type 路由；实现 `OpenAiCompatibleClient`（/chat/completions，Bearer 鉴权，覆盖 OpenAI/DeepSeek/Kimi/GLM/Qwen/Ollama 等）与 `AnthropicClient`（/v1/messages，x-api-key + anthropic-version）。基于 Spring RestClient，无新增 SDK 依赖；连接 15s/读取 180s 超时。api_key 仅存 ai_provider.api_key，永不回显、不导出（ExportService 表清单不含新表）；更新时省略 key 表示保留。
  - 异步任务状态机（PRD 9.2）：QUEUED -> RUNNING -> SUCCEEDED/FAILED；QUEUED/RUNNING -> CANCELED；FAILED -> QUEUED（retry，attempt_count+1，上限 3，超出 422）。所有转移单次 WHERE 守卫。任务审计字段：job_type、object_id/object_version、provider_id/provider_type/model、prompt_version、attempt_count、failure_reason、input_snapshot（JD 原文快照）、output_json、started/finished_at。事务提交后才投递执行器（afterCommit），避免执行器读不到未提交行。
  - 执行器 `AiJobExecutor`：单线程守护线程顺序执行；取消后的完成转移不生效（结果丢弃）。
  - 首个场景 JD_EXTRACTION（prompt_version JD_EXTRACTION_V1）：系统提示词约束只输出 JSON 数组、只提炼 JD 明确要求、type 限 MUST/BONUS；解析宽松剥离围栏，非法条目跳过，空结果判 FAILED；上限 20 条。
  - 候选变更确认：`POST /ai-job-items/{id}/accept`（可带编辑 payload，字段级合并）创建 source_type=AI、PENDING 的 job_requirement 并回链 requirement_id；`/reject` PROPOSED -> REJECTED；重复采纳 422。重新生成 = 新任务，既有条目与确认状态不受影响（PRD 9.2）。
  - 前端：`api/ai` API 层 + hooks；设置页 `AiProviderSection`（列表/新增/编辑/切换激活/测试连通，类型含 OpenAI 兼容与 Anthropic，key 密码框留空保持）；岗位详情页 `AiExtractionSection`（AI 提取按钮 → 任务状态徽章/失败原因/重试/取消 → 候选条目采纳（可编辑）/拒绝 → 采纳后进入既有要求确认区）。
  - E2E 基建：`e2e/fake-ai-server.mjs`（Node OpenAI 兼容假供应商，端口 18090）注册进 playwright webServer。
  - 集成测试 `AiIntegrationTest` 3 用例（JDK HttpServer 假供应商走真实 HTTP）：供应商切换/凭据保留/409；JD 提取全流程（SUCCEEDED、非法类型跳过、采纳带编辑生成 AI 候选要求、拒绝、重新生成保留历史、重复采纳 422）；失败带原因/重试递增/取消/终态取消 422/测试端点失败原因。
  - E2E `p1-ai-extraction.spec.ts`：配置激活假供应商 → 岗位创建提取任务 → 候选 2 条 → UI 采纳/拒绝 → API 校验 AI 来源 PENDING 要求 → 重新生成不影响历史 → 设置页激活徽章可见。
- 未完成：
  - 简历定制草稿（PRD 9.4）为下一窗口任务，基于本切片基础设施新增 RESUME_DRAFT 处理器即可。
  - ai_provider 无删除端点（可编辑/停用由激活切换覆盖，删除按需补契约）。
  - AI 结果确认目前仅需求类条目；「回答质量分析、任务建议」等场景按需扩展 handler。

### 窗口 2026-08-30-01

- 目标：完成 P1 简历定制草稿（PRD 9.4）并验证后合并主分支。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 增加 `RESUME_DRAFT` 与 `sourceText`；AI 任务历史查询改为同时返回 JD 提取和简历草稿。
  - Flyway V9 扩展 `ai_job.job_type`，重建依赖外键后的 `ai_job_item`，保留既有数据与排序字段。
  - 新增简历草稿 handler：输入快照包含用户确认简历与岗位 JD；只生成一个可编辑 DRAFT，不允许新增未经确认的事实。
  - 岗位详情页新增简历定制区，支持提交、轮询查看、编辑候选文本和复制；草稿不会进入岗位要求采纳流程，也不会覆盖原简历。
  - 新增 `ResumeDraftHandlerTest`，覆盖 DRAFT 输出和事实约束提示词。
- 验证结果：`mvn clean test` 通过（77 tests, 0 failures, 0 errors）；前端 `npm run typecheck`、`npm run lint`、`npm run build` 通过。
- 修改文件：OpenAPI、数据库设计、V9 迁移、AI 后端服务/处理器/API/Mapper、前端 AI API/hooks/岗位详情与草稿区、单元测试。
- 下一步：复杂恢复报告与完整附件证据库；多实例提醒协调与更完整失败重试策略；AI 问题分类/回答质量分析/任务建议。

### 窗口 2026-08-30-02

- 目标：完成 P1 复杂恢复报告（PRD 9.5）并验证后合并主分支。
- 状态：**DONE**。
- 已完成：
  - 扩展恢复预检报告，新增数据包 SHA-256 指纹，便于确认预览和实际恢复针对同一份数据。
  - 扩展恢复结果报告，新增报告 ID、恢复时间、整体状态（完成/有跳过/有失败）和逐行动作明细。
  - 逐行记录 `INSERTED`、`DUPLICATE_IDENTICAL`、`CONFLICT`、`MISSING_PARENT`、`FAILED`、`INVALID_PACKAGE`、`UNKNOWN_TABLE`，保留用户事实优先和只插入缺失行语义。
  - 设置页展示报告元数据和可展开的逐行恢复动作；OpenAPI、前端生成类型和后端模型同步更新。
  - 导入集成测试新增报告 ID、指纹、状态和逐行插入动作断言。
- 验证结果：`mvn clean test` 通过（78 tests, 0 failures, 0 errors）；前端 `npm run typecheck`、`npm run lint`、`npm run build` 通过。
- 修改文件：ImportService、导入响应模型、逐行结果模型、DataImportIntegrationTest、OpenAPI、ImportRestoreSection、状态记录。
- 下一步：完整附件证据库；多实例提醒协调与更完整失败重试策略；AI 问题分类/回答质量分析/任务建议。

### 窗口 2026-08-30-03

- 目标：按计划完成附件证据引用库，保持本地路径和外部链接只作为文本引用保存；验证后交接下一项 P1 任务。
- 状态：**DONE**。
- 已完成：
  - 先更新 OpenAPI，新增 `GET /evidence-attachments`、`POST /evidence/{evidenceId}/attachments`、附件引用的 PUT/DELETE，以及 `EvidenceAttachment` / `EvidenceAttachmentCreateRequest` 契约；同步页面规格、数据库设计、PRD 和新增 AT-25。
  - 新增 Flyway `V10__create_evidence_attachment.sql`，独立保存证据 ID、来源类型（`LOCAL_PATH`/`EXTERNAL_URL`）、用户填写的位置、可选 MIME 类型/大小/说明、UTC 时间和版本号；不保存文件字节。
  - 新增附件引用 CRUD 后端与前端 API，写操作带幂等拦截，更新/删除使用 `If-Match-Version`；删除进入最近删除，支持恢复和永久清理，证据永久清理时同步处理其附件元数据。
  - JSON/CSV 导出增加 `evidence_attachment`，导入按外键顺序恢复且保持只插入缺失行语义；测试数据库清理同步增加附件表。
  - 新增 `/evidence-attachments` 附件引用库页面：按证据登记多条引用、编辑人工元数据、删除，展示引用位置与安全提示；侧边栏加入入口。
  - 新增附件后端集成测试和 Playwright 页面验收，覆盖创建幂等、版本冲突、未知证据、软删除/恢复、导入恢复和 UI 登记流程。
- 未完成：
  - 不实现文件上传、下载、内容读取、扫描、自动 MIME/大小探测或校验；如未来需要，必须先单独定义文件大小、格式、存储、导出和敏感信息规则，并取得明确需求。
  - 多实例提醒协调/失败重试、AI 问题分类/回答质量分析/任务建议、`ai_provider` 删除端点仍未实现。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端：`backend/src/main/resources/db/migration/V10__create_evidence_attachment.sql`、`backend/src/main/java/com/jobhub/evidence/**` 新增附件模型/服务/接口/Mapper，导入导出/回收站及相关测试同步修改。
  - 前端：`frontend/src/api/evidenceAttachment/**`、`frontend/src/features/evidence/EvidenceAttachmentsPage.tsx`、路由、侧边栏、样式、设置标签和 Playwright 测试。
- 已运行验证：
  - `cd backend && mvn clean test`（隔离临时构建目录） -> BUILD SUCCESS，81 tests，0 failures，0 errors；Flyway V1→V10 迁移通过。
  - `cd frontend && npm run typecheck` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-evidence-attachments.spec.ts --reporter=list` -> 1 passed。
- 验证结果：
  - 附件引用 CRUD、乐观锁、幂等、最近删除恢复、导入导出和页面主路径均通过自动化验证；没有访问或上传引用位置指向的文件内容。
- 已知问题：
  - 默认 Maven `target` 目录存在环境级文件锁，本窗口回归测试使用临时 `target-codex` 构建目录验证；临时配置和产物已清理，`backend/pom.xml` 无功能改动。
  - Playwright 仍输出既有 React Router v7 future flag 与 Node `NO_COLOR` warning，不影响测试结果。
- 下一窗口只做：
  - 优先实现多实例提醒协调与失败重试策略：先检查当前提醒调度/投递状态字段和状态机，补契约、迁移（如确有需要）、集成测试，再实现调度锁/重试退避和前端失败状态展示。
  - 若用户优先 AI，则先确认问题分类、回答质量分析或任务建议的输入范围与人工确认边界，再复用现有 AI 异步任务基础设施。
- 不要重复做：
  - 不要增加文件上传、读取、扫描、下载或自动校验；不要修改已执行的 V1~V9 迁移。
  - 不要重建附件引用 CRUD、V10 迁移、导入导出接入或 `/evidence-attachments` 页面。

### 窗口 2026-08-30-04

- 目标：完成 P1 多实例提醒协调与失败重试策略，修复提醒调度先标记成功导致失败不可追踪的问题。
- 状态：**DONE**。
- 已完成：
  - 先更新 OpenAPI、状态机、数据库设计、页面规格、PRD 与 AT-26；新增 `POST /reminders/{reminderId}/retry`，返回 `attemptCount`。
  - 新增 Flyway `V11__coordinate_reminder_dispatch.sql`：提醒尝试次数、租约截止时间、租约令牌；为 `notification.reminder_id` 增加唯一索引，阻止重复站内通知。
  - 调度改为扫描候选后逐条独立事务领取：`PENDING` 或过期 `PROCESSING` 才能原子领取；成功转 `SENT`，异常保存截断后的失败原因并转 `FAILED`；旧租约令牌不能完成后续状态转移。
  - 失败提醒通过带 `If-Match-Version` 的专用重试命令重新进入 `PENDING`，清除当前失败原因但保留累计尝试次数；取消、完成、缺席和改期会清除未完成提醒的租约并取消 `FAILED` 记录。
  - 通知创建按 `reminder_id` 幂等查找并补齐启用渠道，租约接管不会重复生成站内通知。
  - 面试详情页展示失败原因和尝试次数，并提供重试按钮及成功/失败反馈。
  - 集成测试新增过期租约接管、尝试次数、通知唯一性、失败重试、旧版本 409 和重试后再次调度覆盖。
- 未完成：
  - AI 问题分类、回答质量分析、任务建议仍待后续窗口；`ai_provider` 删除端点仍按需保留。
  - 本窗口不扩展浏览器/邮件渠道的多实例投递租约；已有渠道失败重试策略保持不变。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/02-state-machines.md`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`jobhub-prd.md`、本文件。
  - 后端：`backend/src/main/resources/db/migration/V11__coordinate_reminder_dispatch.sql`、提醒调度/重试/通知幂等实现、`ReminderDispatchIntegrationTest`。
  - 前端：面试提醒 API、mutation、面试详情提醒计划和生成的 OpenAPI 类型。
- 已运行验证：
  - `mvn clean test`：83 tests，0 failures，0 errors；Flyway 在临时 SQLite 库成功执行 11 个迁移。
  - `npm run typecheck`、`npm run lint`、`npm run build`：全部通过。
  - `npm run e2e -- e2e/at-11-interview-reschedule-reminders.spec.ts`：1 passed；首次普通权限启动因 Maven 子进程目录权限提前退出，允许写入构建目录后重跑通过。
- 下一窗口只做：
  - 优先实现 AI 面试问题分类：先补 OpenAPI/状态与验收契约，再复用异步任务基础设施生成可编辑候选分类，支持逐项采纳/拒绝并补齐集成与 E2E 测试；不扩展回答质量分析或任务建议。
- 不要重复做：
  - 不要重新修改提醒租约或扩大为文件上传、系统级推送、跨设备送达；不要实现 AI 静默写入用户事实。

### 窗口 2026-08-30-05

- 目标：完成 P1 AI 面试问题分类垂直切片：异步任务、可编辑候选、逐项采纳/拒绝和版本保护。
- 状态：**DONE**。
- 已完成：
  - 先更新 OpenAPI、状态机、数据库设计、页面规格、技术实施说明和 AT-17A；新增问题分类任务创建/历史查询接口，扩展 AI 任务和候选分类枚举。
  - 新增 Flyway `V12__allow_question_classification_ai_jobs.sql`，允许既有 `ai_job` 保存 `QUESTION_CLASSIFICATION`，不修改已执行迁移。
  - 新增问题分类处理器：只读取问题内容快照，输出一个固定分类候选和理由；任务仍按 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELED` 异步执行。
  - 分类候选保持 `PROPOSED`，前端可编辑分类后逐项采纳或拒绝；采纳必须携带问题当前版本，只更新 `question_type` 并同步递增问题与复盘版本，不改回答、答案或知识点。
  - 复盘问题页接入任务状态、失败提示、轮询、重新分类、候选编辑、采纳和拒绝反馈。
  - 集成测试覆盖分类任务、候选不自动写入、编辑后采纳、旧版本冲突和拒绝；浏览器 E2E 覆盖真实复盘页面主路径。
- 未完成：
  - AI 回答质量分析、任务建议仍待后续窗口；`ai_provider` 删除端点仍按需保留。
  - 本窗口不扩展 AI 自动修改回答、知识点或学习任务，也不扩展邮件、浏览器通知和文件处理。
- 修改文件：
  - 规格：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/02-state-machines.md`、`docs/jobhub/04-database-design.md`、`docs/jobhub/01-page-spec.md`、`docs/jobhub/05-acceptance-test-cases.md`、`docs/jobhub/06-technical-implementation.md`、本文件。
  - 后端：`backend/src/main/resources/db/migration/V12__allow_question_classification_ai_jobs.sql`、AI 分类处理器/服务/控制器、复盘问题版本更新、`AiIntegrationTest`。
  - 前端：AI API 与查询 mutation、`QuestionClassificationSection`、复盘页、E2E 假供应商和 `p1-ai-question-classification.spec.ts`、生成的 OpenAPI 类型。
- 已运行验证：
  - `mvn test -Dtest=AiIntegrationTest`（隔离 SQLite 库）：4 tests，0 failures，0 errors；Flyway V1→V12 通过。
  - `mvn test`（隔离 SQLite 库）：84 tests，0 failures，0 errors。
  - `npm run gen-types`、`npm run typecheck`、`npm run lint`、`npm run build`：全部通过。
  - `npm run e2e -- e2e/p1-ai-question-classification.spec.ts --reporter=list`：1 passed；Playwright 输出既有 React Router future flag 与 Node `NO_COLOR` warning，成功后 Windows 子进程回收需手动中断，不影响该测试断言结果。
- 下一窗口只做：
  - 优先实现 AI 回答质量分析：先定义只读输入快照、质量维度和候选改进建议的 OpenAPI/验收契约，再复用 AI 异步任务与人工确认基础设施；不自动修改回答、参考答案或问题状态。
- 不要重复做：
  - 不要重新修改 V11 提醒租约或 V12 分类迁移；不要把分类候选直接写成用户事实；不要增加文件上传、读取、扫描或下载。

### 窗口 2026-08-30-06

- 目标：完成 P1 AI 回答质量分析垂直切片：异步任务、可编辑候选、人工采纳/拒绝和问题版本保护。
- 状态：**DONE**。
- 已完成：
  - 先更新 OpenAPI、状态机、数据库设计、页面规格、技术实施说明和 AT-17B；新增回答分析任务创建端点，并让问题 AI 历史按任务类型筛选。
  - 新增 Flyway `V13__allow_answer_quality_analysis_ai_jobs.sql`，允许 `ANSWER_QUALITY_ANALYSIS`，不修改 V1~V12。
  - 新增 `ANSWER_QUALITY_ANALYSIS_V1` 处理器：输入只快照问题、用户原回答和现有参考答案；输出一个含总体评价、建议回答状态、参考答案、错误原因和改进方案的可编辑候选。
  - “我的回答”为空时拒绝创建任务；候选保持 `PROPOSED`，失败、取消、拒绝和重新分析均不改问题主记录。
  - 采纳必须携带问题当前版本，并使用字段级更新只写 `answer_status`、`reference_answer`、`error_reason`、`improvement_plan`；问题内容、类型、我的回答、难度和知识点保持不变，同时递增问题及复盘版本。
  - 复盘页新增回答质量分析区，支持按题发起、轮询、失败提示、重新分析、编辑候选、采纳和拒绝；分类与回答分析使用独立查询键，互不覆盖。
  - 集成测试覆盖空回答、候选不自动写入、任务历史隔离、编辑后采纳、字段保留、旧版本冲突和拒绝；浏览器 E2E 覆盖页面编辑并采纳主路径。
- 未完成：
  - AI 学习任务建议仍待下一窗口；`ai_provider` 删除端点仍按需保留。
  - 本窗口不自动创建学习任务，不修改用户原回答，不扩展文件处理、通知或外部同步。
- 修改文件：
  - 规格：`docs/jobhub/01-page-spec.md`、`02-state-machines.md`、`03-openapi.yaml`、`04-database-design.md`、`05-acceptance-test-cases.md`、`06-technical-implementation.md`、本文件。
  - 后端：`V13__allow_answer_quality_analysis_ai_jobs.sql`、`AnswerQualityAnalysisHandler`、AI 任务服务/控制器/载荷、复盘问题字段级更新、`AiIntegrationTest`。
  - 前端：AI API/hooks、`AnswerQualityAnalysisSection`、复盘页、分类查询隔离、假 AI 供应商、`p1-ai-answer-quality.spec.ts`。
- 已运行验证：
  - `mvn -Dtest=AiIntegrationTest test`（隔离 SQLite 库）：5 tests，0 failures，0 errors；Flyway V1→V13 通过。
  - `mvn test`（隔离 SQLite 库，正确设置 `jobhub.export-dir`）：85 tests，0 failures，0 errors。
  - `npm run gen-types`、`npm run typecheck`、`npm run lint`、`npm run build`：全部通过。
  - `npm run e2e -- e2e/p1-ai-answer-quality.spec.ts --reporter=list`：1 passed；Windows 下测试完成后 Playwright 子进程回收仍需手动终止，三个测试端口均已关闭。
- 已知问题：
  - `backend/target` 在当前 Windows 环境被外部进程占用，本窗口使用临时隔离构建目录完成验证，结束前已恢复 `pom.xml` 并删除临时目录。
  - E2E 仍输出既有 React Router future flag 和 Node `NO_COLOR` warning，不影响断言。
- 下一窗口只做：
  - AI 学习任务建议候选：先定义输入范围、候选字段和人工确认契约，再复用现有 AI 任务基础设施；只在用户确认后创建学习任务及来源关联。
- 不要重复做：
  - 不要修改 V12/V13；不要让 AI 自动覆盖问题、回答或自动创建学习任务；不要增加附件读取、上传、扫描或下载。

- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/04-database-design.md`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`job/domain/JobRequirement.java`、`job/application/RequirementService.java`、`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`、`frontend/playwright.config.ts`、`frontend/src/api/generated/types.ts`（重新生成，不入库）。
  - 新增：`backend/src/main/resources/db/migration/V7__create_ai_infrastructure.sql`、`V8__add_ai_job_item_sort_order.sql`、`backend/src/main/java/com/jobhub/ai/**`（domain 8 + infrastructure 3 + application 8 + api 7 个文件）、`backend/src/test/java/com/jobhub/integration/AiIntegrationTest.java`、`frontend/src/api/ai/{aiApi,useAiQueries}.ts`、`frontend/src/features/settings/AiProviderSection.tsx`、`frontend/src/features/jobs/AiExtractionSection.tsx`、`frontend/e2e/{fake-ai-server.mjs,p1-ai-extraction.spec.ts}`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=AiIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，77 tests，0 failures，0 errors（Flyway V1→V8 迁移通过）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-ai-extraction.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 21 tests passed（含新增 p1-ai-extraction）。
- 验证结果：
  - AI 链路（供应商配置/切换/测试 → 任务入队 → 异步执行 → 候选条目逐项采纳（可编辑）/拒绝 → source=AI 候选要求进入既有确认流 → 失败重试与取消 → 重新生成不覆盖已确认内容）有集成测试（真实 HTTP 假供应商）与浏览器级 E2E 覆盖。
  - OpenAPI 变更为新增 tag/路径/schema，非破坏性；数据库变更走 V7/V8 迁移。
  - 用户事实优先：AI 只产出候选，采纳后仍为 PENDING，需用户显式确认；api_key 不回显不导出。
- 已知问题：
  - V7 执行后补 V8 加 sort_order（模型输出顺序需要稳定排序）；后续新增列一律新迁移。
  - 任务执行器为单线程顺序执行；外部供应商超时上限 180s，阻塞后续任务（本地单用户可接受）。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现简历定制草稿（PRD 9.4）：新增 RESUME_DRAFT 任务处理器与相关页面，只引用已确认经历/技能/证据，AI 仅重写表达，建议可溯源，确认前保持草稿。
- 不要重复做：
  - 不要重建 ai_provider/ai_job/ai_job_item 基础设施或执行器；不要在任何响应中回显 api_key；不要把 AI 表纳入导出。
  - 不要让 AI 自动确认候选或修改业务数据（只创建候选变更，用户逐项确认）。
  - 不要提前实现第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-16

- 目标：P1 第十切片——CSV 导出（PRD 18 / V0.2「面向分析的 CSV 导出」）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - Flyway 迁移 `V6__allow_csv_export.sql`：重建 `data_export` 表放宽 CHECK（format ∈ JSON/CSV），存量行迁移，V1~V5 未改动。
  - OpenAPI：`ExportCreateRequest.format` 枚举扩展为 [JSON, CSV]；`DataExport` schema 新增必填 `format`；下载端点 summary 说明 CSV 为 ZIP。
  - 后端 `ExportService`：`create(format)` 支持 CSV——复用 `collectTables()` 的 23 张业务表数据，逐表写 `.csv`（UTF-8 BOM 便于 Excel、RFC 4180 转义、列名取自 `PRAGMA table_info` 且排除 `application_status_log.idempotency_key`、空表仅表头）打包为 `jobhub-export-{id}.zip`。下载端点按 format 区分 Content-Type（application/zip）与文件名后缀。
  - 前端设置页数据导出区块：格式单选（JSON 完整备份 / CSV 分析用 ZIP）+ 按格式显示按钮文案与下载链接后缀。
  - 集成测试 `ExportIntegrationTest` 新增 `P1_csvExportPackagesBusinessTablesAsZipWithoutIdempotencyKey`：CSV 导出 202/SUCCEEDED/format=CSV、下载 application/zip、ZIP 含全部业务表、BOM 与列头正确、业务行在列、幂等键列排除、XML 格式 400。原 `AT24_exportRejectsNonJsonFormat…` 更新为 XML（CSV 自本切片起合法）。
  - E2E `p1-csv-export.spec.ts`：UI 选择 CSV → 创建导出 → 完成徽章与 ZIP 下载链接 → 下载响应为 application/zip 且 PK 魔数正确（ZIP 内容解析由集成测试覆盖）。
- 未完成：
  - 「复杂恢复报告」「完整附件证据库」仍属后续（依赖附件体系，暂无需求）。
  - CSV 导出为同步任务，与 JSON 一致；无异步队列。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/04-database-design.md`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`datamanagement/{application/ExportService,api/ExportController,api/ExportCreateRequest,api/DataExportResponse,domain/DataExport}.java`、`backend/src/test/java/com/jobhub/integration/ExportIntegrationTest.java`、`frontend/src/api/settings/{exportApi,useExportMutations}.ts`、`frontend/src/features/settings/SettingsPage.tsx`、`frontend/src/api/generated/types.ts`（重新生成，不入库）。
  - 新增：`backend/src/main/resources/db/migration/V6__allow_csv_export.sql`、`frontend/e2e/p1-csv-export.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ExportIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，74 tests，0 failures，0 errors（Flyway V1→V6 迁移通过）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-csv-export.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 20 tests passed（含新增 p1-csv-export）。
- 验证结果：
  - CSV 导出链路（格式选择 → 任务创建 → ZIP 下载 → 内容合规）有集成测试与浏览器级 E2E 覆盖。
  - OpenAPI 变更为枚举扩展与 DataExport 响应新增 format 字段（向后兼容的响应扩展）；数据库变更走 V6 迁移。
- 已知问题：
  - `data_export` 存量行 format 均为 JSON，V6 重建表无数据风险。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认后实现 AI 异步任务基础设施 + 简历定制草稿（需用户明确同意接入 AI），或其他按需增强。
- 不要重复做：
  - 不要重建 CSV 导出逻辑或设置页格式选择；不要把 idempotency_key 列写入 CSV。
  - 不要提前实现 AI、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-15

- 目标：P1 第九切片——浏览器与邮件提醒（PRD 9.3：用户主动授权、测试通知、渠道独立发送状态、站内通知兜底）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - Flyway 迁移 `V5__create_notification_channels.sql`：`notification_channel`（channel_type 唯一 BROWSER/EMAIL、enabled、config_json 存 EMAIL SMTP 配置含凭据）与 `channel_delivery`（notification_id + channel_type 唯一、status PENDING/SENT/FAILED、failure_reason、attempt_count、sent_at）及待投递索引。两表不参与导出/导入（凭据不外流）。
  - pom 新增 `spring-boot-starter-mail`（编译）与 `com.icegreen:greenmail-junit5:2.1.3`（测试，内置 SMTP 3025 端口）。
  - OpenAPI：新增 `GET/PUT /notification-channels/{channelType}`（首次保存以 0 作为 If-Match-Version；启用 EMAIL 需已有 smtpHost 与 toAddress；password 仅写入不回显，凭据存在与否见 hasCredential）、`POST /notification-channels/{channelType}/test`（站内测试通知始终创建；EMAIL 同步投递返回最终状态，未配置返回 422；BROWSER 由前端展示后回执）、`POST /notifications/{notificationId}/channel-deliveries/BROWSER/ack`（幂等回执，仅 BROWSER）、`GET /notifications/{notificationId}`（单条含 deliveries）；`Notification` schema 新增 `deliveries` 数组。
  - 后端：`NotificationChannelService`（配置合并/校验/首存插入路径 + 乐观锁、为已启用渠道生成 PENDING 投递、测试通知、浏览器回执幂等 upsert）、`EmailDeliveryService`（按渠道配置动态构建 JavaMailSender 逐条投递；失败记录原因并递增尝试次数、状态保持 PENDING 以便重试，达 3 次置 FAILED；密码存本地库不回显）、`ChannelDeliveryScheduler`（`jobhub.channel-scan-delay-ms`，默认 60s/e2e 1s/test 1h）。`NotificationService.createFromReminder` 在通知创建后为已启用渠道生成投递记录；`list()/get()` 携带 deliveries。
  - 前端：`api/notifications/channelApi + useChannelQueries + useChannelMutations`；`useBrowserNotifications` hook（BROWSER 启用且 `Notification.permission === 'granted'` 时，对轮询新到达的未读通知弹系统通知并回执；首次加载仅记录不补发；权限被拒/展示失败仅跳过浏览器渠道）；TopBar 接入 hook；设置页新增“通知渠道”区块（`NotificationChannelSection`：浏览器卡片启用开关 + 权限状态 + 测试通知；邮件卡片 SMTP 表单 + 密码留空保持既有凭据 + 保存 + 测试邮件并显示成功/失败原因）。
  - 集成测试 `NotificationChannelIntegrationTest` 4 用例：①EMAIL 真实 SMTP（GreenMail）投递成功、3 封邮件主题匹配、投递 SENT；②SMTP 不可达 → 重试 3 次后 FAILED 且失败原因落库、站内通知保留；③配置校验（缺主机 422、缺版本 400、旧版本 409）、password 不回显且免密更新保留凭据、BROWSER 默认投影；④BROWSER 回执幂等置 SENT、EMAIL 回执拒绝 422、未知通知 404、BROWSER 测试通知 PENDING、EMAIL 未配置测试 422。
  - E2E `p1-notification-channels.spec.ts`：启用双渠道 → 造到期面试 → 断言每条通知带 BROWSER/EMAIL 两条投递 → headless 权限被拒场景（Notification.permission === 'denied'，前端不回执，BROWSER 保持 PENDING，验证 PRD 兜底）→ API 逐条 ack 置 SENT → EMAIL 不可达重试耗尽后 FAILED 带原因 → 通知页站内可见 → 收尾标记已读避免污染 p1-notifications。
  - 既有 E2E `p1-notifications.spec.ts` 计数断言改为按本用例轮次名过滤（全量共享库中通知用例增加后的隔离改造），`p1-notification-channels` 结尾标记已读保证角标断言稳定。
- 未完成：
  - 邮件无附件/HTML 模板，纯文本；邮件重试为固定 3 次被动重试（更完善的重试策略 PRD 归入后续）。
  - 浏览器渠道的服务端推送（Service Worker/Push）未实现，展示由前端轮询驱动（本地单用户可接受）。
  - headless 浏览器中 Notification.permission 恒为 denied，E2E 无法覆盖“授权后前端自动回执”路径（由 API ack 断言 + 手工验证覆盖）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/pom.xml`、`backend/src/main/resources/application.yml`、`backend/src/main/resources/application-e2e.yml`、`backend/src/test/resources/application-test.yml`、`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`。
  - 修改（backend）：`datamanagement/application/NotificationService.java`、`datamanagement/api/NotificationResponse.java`、`datamanagement/domain/Notification.java`。
  - 新增（backend）：`db/migration/V5__create_notification_channels.sql`、`datamanagement/domain/{ChannelType,DeliveryStatus,NotificationChannel,ChannelDelivery}.java`、`datamanagement/infrastructure/{NotificationChannelMapper,ChannelDeliveryMapper,ChannelDeliveryScheduler}.java`、`datamanagement/application/{NotificationChannelService,EmailDeliveryService,EmailChannelConfig}.java`、`datamanagement/api/{NotificationChannelController,NotificationChannelResponse,NotificationChannelUpdateRequest,ChannelDeliveryResponse,ChannelTestResultResponse}.java`、`backend/src/test/java/com/jobhub/integration/NotificationChannelIntegrationTest.java`。
  - 新增（frontend）：`src/api/notifications/{channelApi,useChannelQueries,useChannelMutations,useBrowserNotifications}.ts`、`src/features/settings/NotificationChannelSection.tsx`、`e2e/p1-notification-channels.spec.ts`；修改 `src/components/layout/TopBar.tsx`、`src/features/settings/SettingsPage.tsx`、`e2e/p1-notifications.spec.ts`、`src/api/generated/types.ts`（重新生成，不入库）。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=NotificationChannelIntegrationTest"` -> 4 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，73 tests，0 failures，0 errors（Flyway V1→V5 迁移通过）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-notification-channels.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 19 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/notification-channels/reopen/skills/merge/match-report/review-analysis/data-import）。
- 验证结果：
  - 渠道链路（授权启用 → 配置校验 → 测试通知 → 提醒联动生成渠道投递 → EMAIL 真实 SMTP 发送成功/失败重试记录 → 浏览器回执幂等 → 站内通知始终保留）有集成测试（含 GreenMail 真实 SMTP）与浏览器级 E2E 覆盖。
  - OpenAPI 变更为新增端点/schema 与 Notification.deliveries 可选数组，非破坏性；数据库变更走 V5 迁移。
  - 渠道失败不回滚、不影响站内通知；凭据仅存本地库且不导出、不回显。
- 已知问题：
  - headless 浏览器 Notification.permission 恒为 denied，“授权后前端自动回执”路径无法在 E2E 覆盖（API ack 由集成测试覆盖）。
  - E2E 共享临时库：`p1-notification-channels` 结尾标记已读、`p1-notifications` 按轮次名过滤，后续新增通知类用例须沿用该隔离约定。
  - EMAIL 投递依赖用户自配 SMTP；端口不可达时每次尝试 10s 连接超时，重试窗口最长约 30s（e2e 1s 扫描）。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认后实现下一个 P1 切片（候选：CSV 导出、AI 异步任务基础设施 + 简历定制草稿——后者需用户明确同意接入 AI）。
- 不要重复做：
  - 不要重建渠道配置/投递服务或设置页渠道区块；不要让渠道失败影响站内通知或业务流程。
  - 不要在任何响应中回显 SMTP 密码；不要把 notification_channel/channel_delivery 纳入导出。
  - 不要提前实现 AI、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-14

- 目标：P1 第八切片——数据导入与完整恢复（PRD 9.5：标准数据包导入、冲突预检、恢复预览、恢复结果报告）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI：新增 `POST /data-imports/validate`（只读预检 + 恢复预览）与 `POST /data-imports/restore`（Idempotency-Key；执行恢复）及 `DataImportPackage`/`ImportTablePreview`/`ImportIssue`/`ImportValidationReport`/`ImportTableResult`/`ImportResultReport` schema。数据包 = 导出端点生成的标准 JSON 原样回传（format/exportedAt/tables），前端文件选择仅做客户端读取，不走 multipart。
  - 后端 `ImportService`（datamanagement，基于 JdbcTemplate）：预检语义——同键行已存在且内容一致 → duplicateIdentical（跳过）；同键已存在但内容不同 → CONFLICT 问题（用户事实优先，恢复时保留现状）；外键父行在数据库与数据包中均不存在 → MISSING_PARENT（跳过）。恢复语义：只插入缺失行，不修改、不覆盖任何已有行，重复恢复天然幂等（第二次全部重复跳过）。键规则：默认主键 id，复合主键表（question_knowledge/skill_evidence/project_evidence/requirement_skill）按组合键；`user_skill.user_id` 指向不导出的 user_profile（本机单例）豁免预检；插入列以 `PRAGMA table_info` 白名单为准（表名来自硬编码清单，值全参数化）；已知 23 表按外键安全顺序处理，未知表（含排除表 user_setting 等）记录 UNKNOWN_TABLE 问题并跳过。违反业务唯一键（如 knowledge_point.normalized_name）的行进入 ROW_FAILED 问题，不中断其余行。
  - 内容比较按导出行包含的列进行，数字与数字字符串视为相等（SQLite TEXT 亲和列往返）。
  - 前端 `/settings` 新增“数据导入与恢复”区块（`ImportRestoreSection`）：文件选择 → 预检并预览（影响范围：每表行数/将插入/重复/冲突/缺父级 + 问题明细，最多展示 20 条）→ window.confirm 展示插入/跳过汇总 → 确认恢复 → 结果报告横幅（插入/跳过/失败计数 + 问题明细）。
  - `04-database-design.md` 第 6 节更新为 P1 已实现导入/恢复语义（原文“P0 仅定义导出”）。
  - 集成测试 `DataImportIntegrationTest` 3 用例：①端到端（真实导出 → UUID 整体重映射构造全缺失数据包 → validate insertableRows==totalRows → restore inserted==totalRows → GET 岗位可读 → 重复恢复 inserted==0 && skippedIdentical==totalRows）；②冲突跳过且保留现状（改包内岗位标题 → conflict 问题 → 恢复后数据库标题不变）；③缺父级跳过 + 未知表跳过 + 空指针提醒可插入 + 非法数据包（format 不符/缺 tables）validate/restore 均 422。
  - E2E `frontend/e2e/p1-data-import.spec.ts`：真实导出 → JS 重映射 UUID 并改写 normalized_name → UI 选文件 → 预检通过 → 确认恢复（dialog accept）→ 恢复完成且失败 0 → API 校验恢复后的岗位 → 二次预检全部重复跳过 → 二次恢复插入 0 行。
- 未完成：
  - 恢复报告暂无持久化（同步返回 + 前端展示，无 data_import 表；刷新后不保留历史报告）。
  - 冲突行不支持“以数据包覆盖”模式（PRD 未要求，用户事实优先默认保留现状；如需覆盖另立需求并更新契约）。
  - 违反业务唯一键的行只在结果报告中列为 ROW_FAILED，预检阶段不预判（normalized_name 等业务唯一键不在预检模型内）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/04-database-design.md`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`frontend/src/features/settings/SettingsPage.tsx`。
  - 新增（backend）：`datamanagement/application/ImportService.java`、`datamanagement/api/{DataImportController,ImportValidationResponse,ImportResultResponse,ImportIssueResponse}.java`。
  - 新增（backend test）：`backend/src/test/java/com/jobhub/integration/DataImportIntegrationTest.java`。
  - 新增（frontend）：`src/api/settings/importApi.ts`、`src/features/settings/ImportRestoreSection.tsx`、`e2e/p1-data-import.spec.ts`；`src/api/generated/types.ts`（重新生成，不入库）。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=DataImportIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，69 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-data-import.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 18 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen/skills/merge/match-report/review-analysis/data-import）。
- 验证结果：
  - 导入链路（预检 → 预览 → 恢复 → 幂等重复恢复 → 冲突保留现状 → 缺父级跳过 → 非法包 422）有集成测试与浏览器级 E2E 覆盖。
  - OpenAPI 变更为新增端点与 schema，非破坏性；未新增数据库迁移（无新表）。
  - 恢复不覆盖、不删除任何已有数据；预检为只读。
- 已知问题：
  - E2E 共享临时库：导出包含其他用例数据，用例内通过 UUID 重映射定位本用例岗位；数据包行数随全量用例数据增长，恢复仍幂等。
  - 导入大文件（数万行）未做性能优化，逐行 INSERT；本地单用户量级可接受。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认后实现下一个 P1 切片（候选：简历定制草稿——需先建 AI 任务基础设施与用户明确同意、浏览器与邮件提醒、CSV 导出）。
- 不要重复做：
  - 不要重建导入/恢复服务或设置页导入区块；不要让恢复覆盖或删除已有行（冲突必须跳过并报告）。
  - 不要把 user_profile/user_setting/audit_log/idempotency_record/data_export/trash_item 纳入导入范围。
  - 不要提前实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-13

- 目标：P1 第七切片——完整复盘分析（PRD 8.6 完整复盘 + 16.2 指标；P08 附加字段补全 + 跨面试聚合）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - Flyway 迁移 `V4__add_project_expression_risk.sql`：interview_review 新增 `project_expression_risk` 列（复盘级“项目表达与真实性风险”，页面规格 P08 要求；V1~V3 未改动）。
  - OpenAPI：`InterviewReview`/`ReviewUpsertRequest` 新增 `projectExpressRisk`（nullable，maxLength 5000）；新增 `GET /reviews/analysis`（query：from/to 按面试开始日期、jobId）与 `ReviewAnalysis`（内含 timeRange 内联对象）/`RateFraction`/`ReviewQuestionStats`/`ReviewKnowledgePointStat`/`ReviewQuestionTypeStat`/`ReviewInterviewResultSummary` schema。分析只输出原始计数与分数片段（numerator/denominator），不输出趋势性结论（PRD 16.2）。
  - 后端复盘附加字段：`saveDraft` 全链路支持 interviewerFocus/jobInterest/projectExpressRisk（问题级 myAnswer/referenceAnswer/difficulty/errorReason/improvementPlan 既有 PUT 已支持）。PUT 语义为全字段替换，不带 `projectExpressRisk` 再次保存会清空该字段（集成测试覆盖）。
  - 后端聚合分析：`ReviewService.analysis` 汇总——问题回答计数（含完全答出率分子/分母）、按知识点聚合（仅含有关联问题的知识点，questionCount DESC, name ASC）、按问题类型聚合（null 表示未填写类型）、面试结果汇总（reviewCount/withResultCount/passed/failed/pending）。JOIN 过滤与薄弱点查询同构（软删行排除；DRAFT 与 COMPLETED 复盘均计入，契约描述已注明）。
  - 前端复盘页：新增“展开完整复盘字段”折叠区（面试官关注点/岗位意愿/项目表达与真实性风险），保存草稿始终携带全部复盘级字段（折叠未改字段回显服务端值，不丢数据）；“已记录问题”每行新增“编辑详情”内联表单（问题类型/难度 1-5/我的回答/参考答案/错误原因/改进方案，经既有 PUT 保存）；行摘要显示问题类型。
  - 前端新增 `/reviews/analysis` 复盘分析页（侧边栏“复盘分析”入口）：时间范围筛选（回显 from/to）、问题回答情况概览（完全答出率以 1/3 形式的分子/分母展示，分母为 0 显示“—”）、知识点表现（“待巩固 N 道”/“全部答出”徽章）、问题类型分布、空状态与样本量提示文案。
  - 集成测试：`ReviewAnalysisIntegrationTest` 2 用例（跨面试聚合计数/比率/知识点/类型/结果汇总 + from 过滤与 jobId 过滤；空库与未知 jobId 返回零值）；`ReviewIntegrationTest` 新增 `P1_fullReviewExtraFieldsRoundtripOnReviewAndQuestion`（复盘级字段往返 + 问题级完整字段往返 + 全字段替换清空语义）。
  - E2E `frontend/e2e/p1-review-analysis.spec.ts`：UI 展开完整字段保存 → 刷新持久化 → 逐题编辑详情保存 → 刷新持久化 → 5 月时间窗隔离断言聚合（完全答出率 1/3、知识点行计数、结果汇总）→ 样本外窗口空状态。
- 未完成：
  - “薄弱点改善”两时间窗口对比（PRD 16.2）未实现——属趋势类结论，留待有明确需求时补契约。
  - 分析页暂无 jobId 筛选控件（契约已支持 jobId 参数，与薄弱点页保持一致）。
  - 面试难度按 PRD 8.6 描述为“面试难度”，现契约沿用 OpenAPI 既有问题级 difficulty 建模，未新增面试级难度字段。
  - `backend/data/exports/` 为运行时导出目录，仍未纳入 .gitignore（保持历史现状，不提交）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 修改（backend）：`review/{domain/InterviewReview,infrastructure/ReviewMapper,infrastructure/QuestionMapper,application/ReviewService,api/ReviewUpsertRequest,api/InterviewReviewResponse,api/ReviewController}.java`、`backend/src/test/java/com/jobhub/integration/ReviewIntegrationTest.java`。
  - 新增（backend）：`db/migration/V4__add_project_expression_risk.sql`、`review/domain/ReviewAnalysis.java`、`review/infrastructure/{AnalysisStatusCountRow,AnalysisKnowledgePointStatRow,AnalysisQuestionTypeStatRow,AnalysisResultCountRow}.java`、`review/api/ReviewAnalysisResponse.java`、`backend/src/test/java/com/jobhub/integration/ReviewAnalysisIntegrationTest.java`。
  - 修改（frontend）：`src/api/reviews/{reviewApi,useReviewQueries}.ts`、`src/app/routes.tsx`、`src/components/layout/Sidebar.tsx`、`src/features/reviews/InterviewReviewPage.tsx`、`src/api/generated/types.ts`（重新生成）。
  - 新增（frontend）：`src/features/reviews/ReviewAnalysisPage.tsx`、`e2e/p1-review-analysis.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReviewAnalysisIntegrationTest"` -> 2 tests，0 failures，0 errors。
  - `cd backend && mvn test "-Dtest=ReviewAnalysisIntegrationTest,ReviewIntegrationTest"` -> 7 tests（ReviewIntegrationTest 5 tests 全绿，含新增 P1 往返用例）。
  - `cd backend && mvn test` -> BUILD SUCCESS，66 tests，0 failures，0 errors（Flyway V1→V4 迁移通过）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-review-analysis.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 17 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen/skills/merge/match-report/review-analysis）。
- 验证结果：
  - 完整复盘字段链路（UI 展开 → 保存 → 持久化 → 全字段替换语义）与跨面试聚合（计数、分数片段、过滤）均有集成测试与浏览器级 E2E 覆盖。
  - 本窗口 OpenAPI 变更为新增字段/端点，非破坏性；数据库变更走 V4 迁移，符合迁移纪律。
  - 分析为只读聚合，不修改复盘/问题/知识点数据。
- 已知问题：
  - JsonProbe 对 JSON null 返回字符串 "null"，新测试断言按此约定书写。
  - 聚合 SQL 与薄弱点查询在 questionTypeStats 排序上仅保证 questionCount DESC（同数排序未定义），测试用 containsExactlyInAnyOrder 断言。
  - 复盘页快速表单的面试结果下拉默认停留在 FAILED（既有行为），用户未选择时以 FAILED 保存；如需改为无默认值需另立需求。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认后实现下一个 P1 切片（候选：数据导入与完整恢复、简历定制草稿——后者需 AI 基础设施与用户明确同意、浏览器与邮件提醒）。
- 不要重复做：
  - 不要重建复盘附加字段持久化、`/reviews/analysis` 聚合或 `/reviews/analysis` 页面。
  - 不要在分析输出中加入综合百分比或趋势性结论（PRD 16.2：显示分母与时间范围，样本不足只给原始数量）。
  - 不要让分析修改复盘/问题/知识点数据；分析只读。
  - 不要提前实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-12

- 目标：P1 第六切片——可解释岗位匹配报告（PRD 9.1，用户选定方向）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - 新增 Flyway 迁移 `V3__create_match_report.sql`（match_report 表：job_id、rule_version、weights_json、report_json 快照、input_fingerprint、generated_at + job/generated 索引；V1/V2 未改动）。
  - OpenAPI 新增：`POST /jobs/{jobId}/match-reports`（201 生成快照）、`GET /jobs/{jobId}/match-reports/latest`（200 最新报告 + stale）与 `MatchReport`/`MatchStatusSummary`/`MatchScoreBreakdown`/`MatchReportItem` schema。
  - 计分规则 MATCH_RULE_V1：必须要求与加分要求分别汇总；权重 MUST=3、BONUS=1；满足=1、自报无证据=0.5、未满足=0；**缺少资料/待确认的要求不计入分母**（PRD 9.1“缺少资料不按零分处理”）；输出加权分数片段（numerator/denominator）而非综合百分比。
  - 投递建议（不自动做决定）：NEED_MORE_INFO（无已确认必须要求或分母为 0）/ LOW_MATCH（有未满足必须项）/ STRONG_MATCH（全部必须要求有证据）/ PARTIAL_MATCH，均附理由列表。
  - 过期标记：生成时保存输入指纹（SHA-256 over 排序后的 类型|标准名|状态 行）；GET latest 时重算对比，不一致则 stale=true（历史快照不修改）；重新生成即清除。
  - 前端：`matchReportApi` + hooks（404 视为未生成）；岗位详情页新增“匹配报告”区块——建议徽章与理由、MUST/BONUS 加权分数与汇总、逐项状态/原文/理由列表、过期横幅 + 重新生成按钮。
  - 集成测试 `MatchReportIntegrationTest` 3 用例：生成（汇总/计分 4.5/9/LOW_MATCH）；输入变化 → stale=true → 重新生成 → stale=false 且汇总更新（2 项有证据）；无确认要求 → NEED_MORE_INFO + 未知岗位 404。
  - E2E `frontend/e2e/p1-match-report.spec.ts`：API 造两个不同匹配状态的 MUST 候选 → UI 生成报告 → 建议与分数断言 → API 修改输入 → stale 横幅 → 重新生成后消失。
- 未完成：
  - 匹配报告历史列表查询（仅 latest 端点）；快照对比视图留待有需求时补契约。
  - “技能、经验和项目证据分开展示”以需求类型分组近似（EXPERIENCE 类型 → 经验），证据引用明细仍在差距清单展示；如需按证据维度重新分组需补契约。
  - 证据状态仍为存储维度，未自动推导；interviewPerformance 维度无数据源。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`、`frontend/src/features/jobs/JobDetailPage.tsx`。
  - 新增：`backend/src/main/resources/db/migration/V3__create_match_report.sql`、`backend/src/main/java/com/jobhub/job/{domain/MatchReport,infrastructure/MatchReportMapper,application/MatchReportContent,application/MatchReportService,api/MatchReportResponse,api/JobMatchReportController}.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/MatchReportIntegrationTest.java`、`frontend/src/api/jobs/{matchReportApi,useMatchReportQueries}.ts`、`frontend/src/features/jobs/components/MatchReportSection.tsx`、`frontend/e2e/p1-match-report.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=MatchReportIntegrationTest"` -> 3 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，63 tests，0 failures，0 errors（Flyway V1→V3 迁移通过）。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-match-report.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 16 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen/skills/merge/match-report）。
- 验证结果：
  - 匹配报告链路（生成快照 → 可解释分数与建议 → 输入变化 stale → 重新生成）有集成测试与浏览器级 E2E 覆盖。
  - 本窗口 OpenAPI 变更为新增端点与 schema；数据库变更走 V3 迁移，符合迁移纪律。
- 已知问题：
  - 计分权重（MUST=3/BONUS=1）与规则版本当前为固定值，权重配置 UI 未提供（快照已保存权重，未来可加配置端点）。
  - 报告无删除/清理端点；历史快照随生成次数累积（本地单用户量级可接受）。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认后实现下一个 P1 切片（候选：完整复盘分析、数据导入与完整恢复、简历定制草稿——后者需 AI 基础设施与用户明确同意）。
- 不要重复做：
  - 不要重建匹配报告计算/持久化/前端区块。
  - 不要把匹配分数改成综合百分比或伪精确数字（PRD 原则：可解释、不按零分处理）。
  - 不要让报告生成修改差距/要求/证据数据；stale 判定只读。
  - 不要提前实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-11

- 目标：P1 第五切片——要求合并（`POST /job-requirements/merge`），OpenAPI 契约存量清零；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - 后端：`RequirementService.merge` 专用命令——来源要求软删除并写 `merged_into_requirement_id` 指向目标（表预留字段，保留原始记录与合并溯源），逐来源写 `REQUIREMENT_MERGED` 审计；守卫规则：目标存在、来源非空（`@NotEmpty` 400）、来源不得含目标、来源必须同岗位且为 PENDING（已确认/已忽略来源 422，防止破坏既有差距结论）。`JobRequirementMapper.mergeInto` 单语句转移。响应返回合并后目标要求。
  - 前端：`jobApi.mergeRequirements` + `useMergeRequirements`（局部更新 requirements 缓存）；候选要求确认区为 PENDING 行新增勾选框，选中 ≥2 项时显示合并操作条（目标为第一项；跨类型选择禁用合并，对应页面规格“批量操作仅限同类候选项”）；合并前 window.confirm 展示影响，成功后清空选择并刷新。
  - 集成测试：`RequirementMergeIntegrationTest` 2 用例——合并成功（来源软删 + merged_into 指向目标 + 审计存在 + 列表移除）；跨岗位 422 且来源不删、目标同时作为来源 422、已确认来源 422、空来源 400。
  - E2E：`frontend/e2e/p1-requirement-merge.spec.ts`（无提示词 JD 提取 MUST 分组 → UI 勾选两条同类候选 → 合并确认框 → 来源行移出 → API 校验列表）。
- 未完成：
  - 无（本切片范围）。P1 新功能候选见总状态当前任务。
- 修改文件：
  - 修改：`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/src/main/java/com/jobhub/common/audit/AuditLogEntry.java`、`backend/src/main/java/com/jobhub/job/infrastructure/JobRequirementMapper.java`、`backend/src/main/java/com/jobhub/job/application/RequirementService.java`、`backend/src/main/java/com/jobhub/job/api/JobRequirementController.java`、`frontend/src/api/jobs/{jobApi,useJobMutations}.ts`、`frontend/src/features/jobs/components/RequirementConfirmationSection.tsx`。
  - 新增：`backend/src/main/java/com/jobhub/job/api/RequirementMergeRequest.java`、`backend/src/test/java/com/jobhub/integration/RequirementMergeIntegrationTest.java`、`frontend/e2e/p1-requirement-merge.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=RequirementMergeIntegrationTest"` -> 2 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，60 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-requirement-merge.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 15 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen/skills/merge）。
- 验证结果：
  - 合并链路（UI 勾选 → 确认 → 来源软删 + merged_into + 审计 → 列表移除）有集成测试与浏览器级 E2E 覆盖。
  - 本窗口未修改 OpenAPI（merge 契约已声明）；未新增数据库迁移（`merged_into_requirement_id` 为 V1 预留列）。
- 已知问题：
  - 提取器 rawText 取关键词 ±20/30 字符窗口，相邻关键词的窗口文本可能互相包含，E2E 行级文本断言不可靠（以 API 与选择状态断言为准）。
  - 合并仅支持 PENDING 来源；已确认要求的合并/拆分语义未定义（需要时先补契约）。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 与用户确认 P1 新功能优先级后开工（候选：可解释匹配分数、完整复盘分析、简历定制草稿、数据导入）。匹配分数属 PRD V0.2 且依赖差距清单与证据数据，建议优先；数据导入依赖导出格式，其次。
- 不要重复做：
  - 不要重建合并命令或候选确认区 UI。
  - 不要允许已确认/已忽略要求作为合并来源（会静默改变差距结论）。
  - 不要提前实现 AI 异步分析、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-10

- 目标：P1 第四切片——`/skills` 技能画像页 + Dashboard 弱点真实聚合（契约存量清零第一步）；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 细化：`SkillProfile.selfLevel` 与 `evidenceStatus` 允许 null（键仍 required），描述“尚无自评记录时为 null，界面显示‘未评估’”；`version` 描述明确首次 PUT self-level 以 0 作为 If-Match-Version。非破坏性细化，已重新生成前端类型。
  - 新增后端 `skill` 模块四层：`GET /skills/profile`（skill LEFT JOIN user_skill，全部未删除技能按名称排序返回，无自评记录时 selfLevel/evidenceStatus 为 null、version 0）+ `PUT /skills/{skillId}/self-level`（If-Match-Version 缺失 400；首次设置以 INSERT OR IGNORE 创建 user_skill（evidence_status 默认 NO_EVIDENCE），唯一键冲突按 409 处理；已有记录走乐观锁更新 version+1；selfLevel 0..5 由 `@Min/@Max` 校验，非法返回 400）。
  - 三维度独立性由实现保证：self-level 更新不触碰 evidence_status 与 interview_performance_json；`reason` 字段接受但不持久化（user_skill 无对应列，schema 未定义存储）。
  - Dashboard `weakKnowledgePoints` 替换占位空数组为真实聚合：`DashboardService` 注入 `ReviewService`，调用 `weakKnowledgePoints(null, null, null)`（全时段），响应映射复用 `WeakKnowledgePointResponse`；删除 `WeakKnowledgePointPlaceholder`。
  - 前端：`api/skills` 三件套；新增 `/skills` 页（技能画像列表：自评/证据/面试表现三维度 Badge 独立展示，行内等级下拉 + 保存，编辑草稿仅作用于自评维度）；侧边栏新增“技能画像”入口（`/skills`）。
  - e2e 夹具 `POST /api/e2e/jobs/{jobId}/seed-project-evidence` 新增可选 `skillName` body 参数（默认 "Redis" 保持 at-20 行为），供全量 E2E 用唯一技能名避免跨用例同名歧义。
  - 集成测试：`SkillProfileIntegrationTest`（空列表 → 未评估投影 → 首次自评创建 + 版本语义 0 → 乐观锁更新 → 409/400/404 守卫 → 三维度独立）；`DashboardIntegrationTest` 新增弱点聚合用例（完整复盘链路后 dashboard 返回该知识点，weightedWeaknessCount=1）。
  - E2E：新增 `frontend/e2e/p1-skills-profile.spec.ts`（JD 提取确认 Redis → 夹具造唯一命名技能 → 未评估展示 → UI 首次自评 3 → 持久化 → API 校验 NO_EVIDENCE 未被覆盖）。
- 未完成：
  - `POST /job-requirements/merge` 要求合并（契约存量最后一项，见总状态当前任务）。
  - `interviewPerformance` 维度无生产写入路径，恒为 null（界面显示“未评估”）。
  - 技能无生产创建路径：skill 行仅由 e2e 夹具/后续功能产生；页面空状态已说明。
  - 证据状态目前是存储维度（默认 NO_EVIDENCE），未按 skill_evidence 关联自动推导（PRD 8.3 要求三维度独立，推导逻辑留待明确需求）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/src/main/java/com/jobhub/dashboard/{application/DashboardService,api/DashboardController}.java`、`backend/src/main/java/com/jobhub/testsupport/api/E2eReminderFixtureController.java`、`backend/src/test/java/com/jobhub/integration/DashboardIntegrationTest.java`、`frontend/src/app/routes.tsx`、`frontend/src/components/layout/Sidebar.tsx`。
  - 新增：`backend/src/main/java/com/jobhub/skill/{domain/SkillProfile,infrastructure/SkillProfileMapper,application/SkillProfileService,api/SkillProfileResponse,api/SelfLevelUpdateRequest,api/SkillController}.java`。
  - 新增：`backend/src/test/java/com/jobhub/integration/SkillProfileIntegrationTest.java`。
  - 新增：`frontend/src/api/skills/{skillApi,useSkillQueries,useSkillMutations}.ts`、`frontend/src/features/skills/{SkillsPage.tsx,skillLabels.ts}`、`frontend/e2e/p1-skills-profile.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=SkillProfileIntegrationTest"` -> 2 tests，0 failures，0 errors。
  - `cd backend && mvn test "-Dtest=DashboardIntegrationTest"` -> 4 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，58 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-skills-profile.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 14 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen/skills）。
- 验证结果：
  - 技能画像（未评估投影、首次自评创建、乐观锁、三维度独立）与 Dashboard 弱点聚合均有集成测试与浏览器级 E2E 覆盖。
  - 本窗口 OpenAPI 变更为可空细化，非破坏性；未新增数据库迁移。
- 已知问题：
  - `user_skill` 表无 `deleted_at` 列，自评记录不可软删（技能删除属未来需求）。
  - 全量 E2E 共享临时库，多用例可创建同名 "Redis" 技能——夹具已支持 `skillName` 参数，新用例应传唯一名称。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现要求合并：核对 OpenAPI `POST /job-requirements/merge` 请求/响应 schema 与 01 页面规格“合并同类候选要求”交互，实现 job 模块合并命令（合并后保留原始名称与来源，重复项不重复计数），补集成测试；前端如页面规格要求则在候选要求确认区提供合并入口。
  - 或按用户指示进入其他 P1 功能。
- 不要重复做：
  - 不要重建 skill 模块、`/skills` 页或 Dashboard 弱点聚合。
  - 不要让自评等级修改联动证据状态或面试表现；不要在无 user_skill 时伪造维度值。
  - 不要提前实现 AI、邮件、系统推送、第三方日历、云同步、多租户或附件上传。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-9

- 目标：P1 第三切片——复盘 reopen；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 扩展：新增 `POST /reviews/{reviewId}/reopen`（Idempotency-Key + If-Match-Version；200 返回 InterviewReview，404/409/422 IllegalTransition），与状态机第 5 章 `COMPLETED ──reopen──> DRAFT` 对齐。
  - 后端：`ReviewMapper.reopen`（`review_status='DRAFT'` 单次转移，WHERE version + COMPLETED + 未删除）；`ReviewService.reopen` 专用命令——仅 COMPLETED 可 reopen，从 DRAFT reopen 返回 `422 ILLEGAL_STATE_TRANSITION`；问题、知识点与学习任务来源关联全部保留，仅状态回退并递增版本。
  - 前端：`reviewApi.reopenReview` + `useReopenReview`（局部更新 + 失效复盘查询）；快速复盘页完成态横幅改为“复盘已完成。如需补充或修改问题，可重新打开复盘（问题与任务关联会保留）”并提供“重新打开”按钮；reopen 后编辑入口自动恢复（`isCompletedReview` 重新计算），可继续新增问题并再次完成。
  - 集成测试：`ReviewIntegrationTest` 新增 `P1_reopenCompletedReviewKeepsQuestionsAndAllowsEditing`——缺版本 400、reopen 后状态 DRAFT 且问题保留、DRAFT 再 reopen 返回 422 ILLEGAL_STATE_TRANSITION、reopen 后可新增问题并再次完成。
  - E2E：新增 `frontend/e2e/p1-review-reopen.spec.ts`（API 造完成态复盘 → UI 点“重新打开”→ 状态回草稿 → 补充第二个问题 → 再次完成 → API 校验 2 个问题 COMPLETED）。
  - 修复 `at-16-complete-review.spec.ts` 的 `getByText('复盘已完成')` 断言：完成态横幅文案更新后与 toast 同时可见导致 strict mode violation，改用 `.first()`。
- 未完成：
  - `/skills` 技能画像页、Dashboard `weakKnowledgePoints` 聚合、`POST /job-requirements/merge` 要求合并仍待实现（契约已声明，见总状态当前任务）。
  - 编辑 COMPLETED 复盘的“仍满足完成条件可直接保持 COMPLETED”路径（状态机 5 章后半）未实现——当前编辑已完成复盘必须先 reopen，语义更严格但不违反状态机。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`。
  - 修改：`backend/src/main/java/com/jobhub/review/{infrastructure/ReviewMapper,application/ReviewService,api/ReviewController}.java`。
  - 修改：`backend/src/test/java/com/jobhub/integration/ReviewIntegrationTest.java`。
  - 修改：`frontend/src/api/reviews/{reviewApi,useReviewMutations}.ts`、`frontend/src/features/reviews/InterviewReviewPage.tsx`、`frontend/e2e/at-16-complete-review.spec.ts`。
  - 新增：`frontend/e2e/p1-review-reopen.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReviewIntegrationTest"` -> 4 tests，0 failures，0 errors。
  - `cd backend && mvn test` -> BUILD SUCCESS，55 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-review-reopen.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 13 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings/notifications/reopen）。
- 验证结果：
  - reopen 链路（完成态 UI 入口 → 状态回退 → 继续编辑 → 再次完成）有集成测试与浏览器级 E2E 覆盖。
  - 本窗口 OpenAPI 变更为新增端点，非破坏性；未新增数据库迁移。
- 已知问题：
  - 完成态横幅文案含“复盘已完成”前缀，页面内同时存在 toast 与横幅时用 `.first()` 断言（已在 at-16 与新 E2E 中处理）。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现 `/skills` 技能画像页：`GET /skills/profile`（聚合 skill/user_skill/skill_evidence 三维度，无 user_skill 的技能也要返回）+ `PUT /skills/{skillId}/self-level`（If-Match-Version + reason），前端启用“能力与证据”导航并新增 `/skills` 页（三维度独立展示，修改自评不触发其他维度变化）；顺带补 Dashboard `weakKnowledgePoints` 真实聚合（复用 `WeakKnowledgePointMapper` 查询，替换占位空数组）。补集成测试与 E2E。
- 不要重复做：
  - 不要重建 review reopen 链路；不要让 reopen 修改问题/知识点/任务数据。
  - 不要在无 `user_skill` 时伪造技能等级或证据状态（无资料显示“未评估”）。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

### 窗口 2026-08-29-8

- 目标：P1 第二切片——提醒到期调度 + 通知中心闭环；验证无问题后合并回 `main` 并推送远程。
- 状态：**DONE**。
- 已完成：
  - OpenAPI 扩展：新增 tag `Notifications`、`GET /notifications`（最近 100 条）、`POST /notifications/{notificationId}/read`（幂等）与 `Notification` schema（id、reminderId 可空、title、content、readAt 可空、createdAt）、参数 `NotificationId`。
  - datamanagement 新增 Notification 四层：列表/按 id 查询/单次 `read_at` 转移标记已读；`NotificationService.markRead` 幂等（已读重复调用返回当前状态，不存在 404）。
  - interview 模块新增到期调度：`ReminderMapper.selectDue`（PENDING 且 scheduled_at <= now，join 面试取轮次名，LIMIT 200）+ `markSent`（单次 PENDING→SENT 转移）；`ReminderDispatchService.dispatchDue()` 仅在转移成功时生成一条站内通知（`面试提醒：{轮次}` + 提醒节点文案），重复扫描不产生重复通知；`ReminderDispatchScheduler`（infrastructure）按 `jobhub.reminder-scan-delay-ms` 固定延迟扫描、启动后 1s 首扫（覆盖重启前累积到期提醒）；`SchedulerConfig` 开启 `@EnableScheduling`。
  - 配置：默认扫描间隔 60s；新增 `application-e2e.yml`（1s 间隔/0.5s 首扫，供浏览器级断言）；test profile 置 1h（集成测试直调服务保证确定性）。均为环境变量可覆盖。
  - 前端：`api/notifications` 三件套（查询 5s 轮询、标记已读局部更新缓存）；TopBar 新增"通知"入口与未读数角标；新增 `/notifications` 页（列表、未读/已读标记、逐条标记已读、空状态说明），路由已加，无侧边栏项（页面规格未定义通知独立导航）。
  - `DatabaseCleaner` 新增 `notification` 表清理（在 interview_reminder 之前，FK 顺序正确）。
  - 新增 `frontend/e2e/p1-notifications.spec.ts`：API 造岗位/投递/一场 10 分钟后开始的面试（三条默认提醒全部到期）→ 轮询 API 等待调度生成 3 条通知 → TopBar 角标显示 3 → 打开通知页标记第一条已读 → 未读角标同步为 2 → API 校验恰有一条已读。
- 未完成：
  - 全部已读按钮未做（留后续按需）。
  - FAILED 提醒状态仍无生产路径（P0 无外部投递渠道，不会失败）；失败原因展示仅保留字段。
  - 通知暂无删除/清理策略；列表固定最近 100 条。
  - 复盘 reopen、`/skills` 技能画像、Dashboard 弱点聚合、要求合并等仍待后续窗口（见总状态当前任务）。
- 修改文件：
  - 修改：`docs/jobhub/03-openapi.yaml`、`docs/jobhub/IMPLEMENTATION_STATUS.md`、`backend/src/main/resources/application.yml`、`backend/src/test/resources/application-test.yml`、`backend/src/test/java/com/jobhub/integration/support/DatabaseCleaner.java`、`backend/src/main/java/com/jobhub/interview/infrastructure/ReminderMapper.java`、`frontend/src/app/routes.tsx`、`frontend/src/components/layout/TopBar.tsx`。
  - 新增（backend）：`datamanagement/{domain/Notification,infrastructure/NotificationMapper,application/NotificationService,api/NotificationResponse,api/NotificationController}.java`；`interview/{infrastructure/DueReminderRow,infrastructure/ReminderDispatchScheduler,infrastructure/SchedulerConfig,application/ReminderDispatchService}.java`。
  - 新增：`backend/src/main/resources/application-e2e.yml`、`backend/src/test/java/com/jobhub/integration/ReminderDispatchIntegrationTest.java`。
  - 新增（frontend）：`src/api/notifications/{notificationApi,useNotificationQueries,useNotificationMutations}.ts`、`src/features/notifications/NotificationsPage.tsx`、`e2e/p1-notifications.spec.ts`。
- 已运行验证：
  - `cd backend && mvn test "-Dtest=ReminderDispatchIntegrationTest"` -> 2 tests，0 failures，0 errors（到期转移+去重+已读幂等；未来/取消提醒跳过）。
  - `cd backend && mvn test` -> BUILD SUCCESS，54 tests，0 failures，0 errors。
  - `cd frontend && npm run gen-types` -> 通过；`npm run lint` -> 0 warning / 0 error；`npm run typecheck` -> 通过；`npm run build` -> 通过。
  - `cd frontend && npx playwright test e2e/p1-notifications.spec.ts --reporter=list` -> 1 passed。
  - `cd frontend && npx playwright test --reporter=list` -> 12 tests passed（AT-01/09/11/15/16/18/20/23/24 + P10 + P1 settings + P1 notifications）。
- 验证结果：
  - 提醒到期→SENT→通知→已读的完整链路有集成测试与浏览器级 E2E 覆盖；去重与幂等语义（单次状态转移）已验证。
  - 本窗口 OpenAPI 变更为新增端点与 schema，非破坏性；未新增数据库迁移（V1 `notification` 表支撑）。
  - e2e 调度 1s 间隔下 E2E 稳定通过；test 调度 1h 避免后台任务干扰集成断言。
- 已知问题：
  - 通知生成时间文案使用提醒计划时间（UTC 字符串），前端展示时间用 `formatDateTime` 按用户时区渲染。
  - E2E 共享临时库；`p1-notifications` 依赖 e2e profile 的 1s 调度间隔，本地复现需以 e2e 脚本启动后端。
  - E2E 仍输出 React Router v7 future flag 警告与 Node `NO_COLOR`/`FORCE_COLOR` warning；不影响结果。
  - `git status` 仍会显示用户级 `C:\Users\35433/.config/git/ignore` 权限 warning；不影响仓库内文件检查。
- 下一窗口只做：
  - 实现复盘 reopen：OpenAPI 补 `POST /reviews/{reviewId}/reopen`（Idempotency-Key + If-Match-Version；COMPLETED→DRAFT，保留问题与任务关联），ReviewService 加专用命令（仅 COMPLETED 可 reopen），前端快速复盘页加"重新打开"动作，补集成测试与 E2E。
  - 或按用户指示调整 P1 优先级。
- 不要重复做：
  - 不要重建通知四层、调度器或 `/notifications` 页；不要为提醒引入邮件/浏览器推送等外部渠道。
  - 不要把"全部已读"提前实现（除非用户要求）；不要追溯修改已生成通知。
  - 不要通过普通 `PUT` 改写投递、面试、提醒、复盘或任务状态。

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
