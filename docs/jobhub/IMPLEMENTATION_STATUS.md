# JobHub 实现进度与动态交接

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：M1 第一切片实施中（PARTIAL，后端已编译通过且可启动、Flyway/SQLite 已验证、最小接口可访问；集成测试与前端尚未开始）。
- 当前里程碑：M1（工程骨架、Flyway、错误响应、OpenAPI 对齐、岗位 CRUD、JD 要求与差距清单）。
- 当前任务：M1 第一切片 — 后端骨架 + 岗位垂直切片（AT-01~AT-04）。编译与启动已验证，剩余集成测试与前端。
- 当前负责人窗口：Claude（glm-5.2）。
- 最后更新：2026-08-26。

## 2. 已完成内容

### 规格层（早于本窗口已完成）
- PRD v1.2：`java-jobhub-prd.md`
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

- `backend/` 已含完整 Spring Boot 工程结构与 job 模块业务代码（53 个 Java 文件：1 主类 + common 19 + job 33；`IdempotencyRecordMapper` 已移至 `common/idempotency/infrastructure/`）。
- `frontend/` 尚未生成。
- **后端已通过 `mvn clean compile`（53 文件 BUILD SUCCESS）；已通过 `mvn spring-boot:run` 启动（Flyway 迁移 V1 成功，Tomcat 监听 127.0.0.1:8080）；`curl GET /api/jobs` 返回 200 + 空分页**。`mvn test` 尚未运行（无测试类）。
- 运行时 SQLite 数据库文件 `backend/data/jobhub.db` 由 Flyway 创建；禁止把 SQLite 数据库文件提交到仓库（`.gitignore` 已忽略）。
- `application.yml` 配置了 `mybatis.mapper-locations: classpath:mapper/*.xml`，但项目 mapper 全部使用注解 SQL 无 XML 文件，该配置无害失效（保留，无需修改）。
- `IdempotencyInterceptor` 与 `IdempotencyBodyCachingFilter` 都注册在 `/api/**` 路径上；逻辑自洽且已随启动验证通过，但**未通过集成测试验证幂等回放与冲突的具体行为**。
- `RequirementExtractor.extract(jobId, existing)` 旧重载已废弃并抛 `UnsupportedOperationException`；服务层调用新签名 `extract(jobId, jdRawText, existing)`。

## 4. 里程碑状态

| 里程碑 | 范围 | 状态 | 进入条件 | 完成条件 |
|---|---|---|---|---|
| M1 | 工程骨架、Flyway、错误响应、OpenAPI 对齐、岗位 CRUD、JD 要求与差距清单 | `IN_PROGRESS`（第一切片 PARTIAL） | 确认技术栈与启动命令 | AT-01 至 AT-04 通过 |
| M2 | 投递状态机、下一步行动、面试与提醒 | `NOT_STARTED` | M1 完成 | AT-05 至 AT-14 通过 |
| M3 | 复盘、问题、知识点、薄弱点、学习任务 | `NOT_STARTED` | M2 完成 | AT-15 至 AT-19 通过 |
| M4 | 面试准备包、项目案例、证据、导出、最近删除 | `NOT_STARTED` | M3 完成 | AT-20 至 AT-24 通过 |

## 5. 当前窗口交接

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
  - 5 个集成测试类（`JobCrud`、`RequirementConfirmation`、`Idempotency`、`VersionConflict`、`IllegalTransition`），覆盖 AT-01~AT-04 + 幂等/版本/非法转换
  - 前端工程骨架（`frontend/` + Vite + React + TS + TanStack Query + axios）与 `JobListPage`、`JobCreatePage`、`JobDetailPage` 三页面
  - 端到端 AT-01 手动验证（粘贴 JD → 确认 → 差距 INSUFFICIENT_INFO → 保存 TO_APPLY）
  - 提交本批修复与剩余 slice 1 成果（用户未要求 commit，未自动提交）
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
  3. 未提交本批修改（用户未要求）；`backend/data/jobhub.db` 运行时生成，已由 `.gitignore` 忽略。
  4. `mybatis.mapper-locations` 配置无害失效（注解 SQL 无 XML），保留不改。
- 下一窗口只做：
  1. 编写 5 个后端集成测试覆盖 AT-01~AT-04 + 幂等/版本/非法转换（优先 `JobCrudIntegrationTest`、`RequirementConfirmationIntegrationTest`）。
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
