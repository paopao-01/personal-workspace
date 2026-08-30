# JobHub V0.1 技术实施方案

## 1. 固定决策

| 层级 | 决策 | 原因 |
|---|---|---|
| 仓库 | 单仓库、前后端分目录 | 共享 PRD、OpenAPI、测试数据与实现约束，降低模型协作偏差。 |
| 前端 | React + TypeScript + Vite | 适合桌面 Web 工作台；组件与业务模块可独立开发。 |
| 前端状态 | TanStack Query 管理服务端数据；局部 UI 状态使用 React state | 不把后端事实复制成全局可变状态。 |
| 后端 | Java 21 + Spring Boot 3.x + Spring MVC | 长期支持 Java 基线，便于实现 REST、校验、调度和事务。 |
| 持久化 | MyBatis + Flyway + SQLite JDBC | P0 使用显式 SQL，便于落实状态机、部分唯一索引和本地部署。 |
| 数据库 | SQLite 单文件数据库 | 本地单用户无需单独安装 MySQL；后续可按迁移策略切换 MySQL。 |
| API 契约 | `docs/jobhub/03-openapi.yaml` 为唯一来源 | 前端类型和后端接口都需对齐该文件。 |
| 测试 | JUnit + Spring Boot 集成测试；Playwright E2E | 分别验证领域规则和用户闭环。 |

依赖版本由 Maven Wrapper 与 `package-lock.json`/`pnpm-lock.yaml` 固定。新增依赖必须说明用途，不因“可能有用”而引入。

## 2. 工程结构

```text
jobhub/
├── AGENTS.md
├── docs/jobhub/
│   ├── 01-page-spec.md
│   ├── 02-state-machines.md
│   ├── 03-openapi.yaml
│   ├── 04-database-design.md
│   ├── 05-acceptance-test-cases.md
│   └── 06-technical-implementation.md
├── fixtures/
│   └── v0.1-demo-data.json
├── frontend/
│   ├── src/
│   │   ├── app/                 # 路由、QueryClient、应用级布局
│   │   ├── api/                 # OpenAPI 生成/封装的客户端与类型
│   │   ├── components/          # 无业务含义的通用组件
│   │   ├── features/            # 按领域分组的页面、表单和 hooks
│   │   │   ├── dashboard/
│   │   │   ├── jobs/
│   │   │   ├── applications/
│   │   │   ├── interviews/
│   │   │   ├── reviews/
│   │   │   ├── tasks/
│   │   │   └── skills/
│   │   └── styles/
│   └── package.json
└── backend/
    ├── src/main/java/com/jobhub/
    │   ├── common/              # 错误码、时钟、ID、幂等、乐观锁
    │   ├── dashboard/
    │   ├── job/
    │   ├── application/
    │   ├── interview/
    │   ├── review/
    │   ├── task/
    │   ├── skill/
    │   ├── evidence/
    │   └── datamanagement/
    ├── src/main/resources/db/migration/
    ├── src/test/
    └── pom.xml
```

每个后端业务模块最多分为 `api`、`application`、`domain`、`infrastructure` 四层：

- `api`：Controller、请求/响应 DTO；不包含业务判断。
- `application`：用例服务、事务边界、命令处理。
- `domain`：状态机、实体规则、领域异常；不依赖 HTTP 或 SQL。
- `infrastructure`：MyBatis Mapper、SQL、文件导出、调度实现。

## 3. 前后端协作规则

### 3.1 API 与类型

- 后端路径必须以 `/api` 为前缀，对应 `03-openapi.yaml` 的 server URL。
- 前端在开发环境通过 Vite proxy 转发 `/api`；生产交付时由 Spring Boot 提供前端构建后的静态文件，用户只启动一个本地服务。
- 修改接口前必须先修改 OpenAPI，再修改后端与前端；禁止“先改实现，最后补文档”。
- 由 OpenAPI 生成或校验 TypeScript 类型；前端不手写与后端重复的枚举。
- 所有错误遵循 `{ code, message, traceId, fieldErrors? }`。前端针对 `VERSION_CONFLICT`、`ILLEGAL_STATE_TRANSITION`、`IDEMPOTENCY_CONFLICT` 提供专门提示。

### 3.2 状态与事实

- 前端不得自行推演或修改投递、面试、复盘、提醒、任务状态；只能调用专用动作接口后以响应为准刷新。
- `PUT` 仅编辑元数据，状态转换走 `POST .../transition`、`complete`、`cancel`、`reschedule` 等命令接口。
- 任何自动分析仅生成候选内容；P0 不接入 AI，后续 AI 也不得覆盖用户已确认事实。问题分类采纳必须携带问题当前版本，后端只在专用采纳命令中更新 `question_type`。

## 4. 后端实现要点

### 4.1 事务边界

以下操作必须在单个数据库事务中完成：

- 投递状态更新 + `application_status_log` 写入。
- 从 `RESUME_PASSED` 创建首场面试 + 投递转为 `INTERVIEWING` + 默认提醒生成。
- 面试改期 + 旧 PENDING 提醒取消 + 新提醒生成。
- 取消/完成/缺席面试 + 未触发提醒取消。
- 合并知识点 + 题目关联迁移与去重。
- 从面试问题创建任务 + `task_source` 写入。

### 4.2 幂等与并发

- HTTP 过滤器/拦截器负责读取 `Idempotency-Key`，用“操作名 + key”查询 `idempotency_record`。
- 首次成功写入响应状态和响应 JSON；重复请求原样返回。相同键但请求摘要不同返回 `409`。
- 更新服务必须验证 `If-Match-Version`；Mapper 以受影响行数为 0 判断版本冲突。
- AI 问题分类、回答质量分析与学习任务建议的采纳必须校验问题版本；回答分析只能更新回答状态、参考答案、错误原因和改进方案，不能覆盖用户原回答或其他问题字段；任务建议采纳在同一事务中创建任务及来源关联。
- 定时提醒领取采用条件更新：只允许 `PENDING` 变为 `PROCESSING`。P0 单实例运行，但仍保留该条件以避免重复展示。

### 4.3 时间、隐私与本地安全

- API 接收和返回时间点使用 UTC；前端转换为用户当前时区展示，并显示面试事件时区。
- 服务默认仅监听 `127.0.0.1`，不自动暴露到局域网。
- 数据库、导出文件和日志目录必须可配置；日志不得写入完整 JD、面试回答、证据内容、外部 AI 请求、令牌或本地路径指向的文件内容。
- 外部链接与本地路径只保存为文本引用，禁止后台扫描、读取或上传。

## 5. 前端实现要点

- 页面实现顺序严格遵循页面规格的三条关键路径：首次价值、面试准备、改进闭环。
- 统一使用表单校验、保存中状态、保存成功反馈和字段级错误提示。
- 列表筛选条件写入 URL query，便于刷新和分享本地视图；不保存敏感文本到 URL。
- 空状态只显示单一推荐动作；统计数据不足时显示原始数量或不展示，不制造趋势图。
- 设计优先保证键盘可达、焦点可见、文字与状态标签的对比度；颜色不得是传达状态的唯一方式。

## 6. 开发里程碑

| 阶段 | 可交付结果 | 对应验收 |
|---|---|---|
| M1 基础骨架 | 前后端启动、Flyway V1、错误响应、OpenAPI 校验、岗位 CRUD | AT-01 至 AT-04 |
| M2 投递与面试 | 投递状态机、下一步行动、面试与提醒 | AT-05 至 AT-14 |
| M3 复盘与任务 | 快速复盘、题目、薄弱点、任务验证 | AT-15 至 AT-19 |
| M4 准备与数据管理 | 面试准备包、项目案例、导出、最近删除 | AT-20 至 AT-24 |

每个里程碑结束前，先通过相关 API/集成测试，再补齐前端 E2E；不得只完成页面视觉而未实现状态规则。

## 7. 完成定义

一个功能只有同时满足以下条件才算完成：

1. OpenAPI、数据库迁移、后端实现和前端调用已同步更新。
2. 状态机约束由后端测试覆盖，非法操作不会写入数据。
3. 页面具备加载、空、失败和权限/数据缺失（本地单用户下为数据缺失）状态。
4. 对应验收场景通过，且不破坏现有场景。
5. 不引入未经 PRD 明确允许的 AI 自动写入、外部上传或跨设备同步。
