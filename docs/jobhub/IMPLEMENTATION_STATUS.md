# JobHub 实现进度与动态交接

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：M2 收尾打磨中（投递、面试、提醒、dashboard 主路径和 P05 月视图/时间线切换已完成；E2E 回归仍待补齐）。
- 当前里程碑：M2（投递状态机、下一步行动、面试与提醒）进行中。AT-05~AT-14 后端已完成；面试与首页工作台主要路径可用。P05 投递状态筛选代码与后端测试已存在，月视图/列表视图切换已完成，Playwright E2E 仍待后续窗口。
- 当前任务：Playwright E2E 已覆盖 AT-01 与 AT-09；下一窗口建议继续覆盖 AT-11 面试改期替换提醒。
- 当前负责人窗口：Codex。
- 最后更新：2026-08-27。

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

- `backend/` 已含完整 Spring Boot 工程结构与 job + application + dashboard 模块业务代码（约 74 个 Java 文件：1 主类 + common 19 + job 34 + application 18 + dashboard 2；`JobMapper` 新增 selectByIds）。
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
| M2 | 投递状态机、下一步行动、面试与提醒 | `IN_PROGRESS` | M1 完成 | AT-05 至 AT-14 通过 |
| M3 | 复盘、问题、知识点、薄弱点、学习任务 | `NOT_STARTED` | M2 完成 | AT-15 至 AT-19 通过 |
| M4 | 面试准备包、项目案例、证据、导出、最近删除 | `NOT_STARTED` | M3 完成 | AT-20 至 AT-24 通过 |

## 5. 当前窗口交接

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
