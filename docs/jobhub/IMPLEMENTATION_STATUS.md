# JobHub 实现进度与动态交接

> 这是跨窗口恢复工作的唯一动态文件。它记录当前代码状态，不替代 PRD、状态机、OpenAPI 或页面规格。任何模型开始工作前先读本文件；结束或即将中断时必须更新本文件。

## 1. 当前总状态

- 项目阶段：规格与实现准备已完成，业务代码尚未开始。
- 当前里程碑：等待开始 M1。
- 当前任务：暂无进行中的代码任务。
- 当前负责人窗口：未分配。
- 最后更新：2026-08-25

## 2. 已完成内容

- PRD v1.2：`java-jobhub-prd.md`
- 页面规格：`docs/jobhub/01-page-spec.md`
- 状态机：`docs/jobhub/02-state-machines.md`
- OpenAPI：`docs/jobhub/03-openapi.yaml`
- 数据库设计：`docs/jobhub/04-database-design.md`
- SQLite/Flyway 初始迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`
- 验收测试用例：`docs/jobhub/05-acceptance-test-cases.md`
- 技术实施方案：`docs/jobhub/06-technical-implementation.md`
- 脱敏演示数据：`fixtures/v0.1-demo-data.json`
- 实现约束：`AGENTS.md`
- 实现总控提示词：`docs/jobhub/IMPLEMENTATION_MASTER_PROMPT.md`

## 3. 当前代码事实

- `backend/` 当前只有数据库迁移脚本，尚未生成 Spring Boot 工程。
- `frontend/` 尚未生成前端工程。
- 尚未执行 Maven 测试、前端静态检查或端到端测试。
- 尚未创建真实数据库文件；禁止把 SQLite 数据库文件提交到仓库。

## 4. 里程碑状态

| 里程碑 | 范围 | 状态 | 进入条件 | 完成条件 |
|---|---|---|---|---|
| M1 | 工程骨架、Flyway、错误响应、OpenAPI 对齐、岗位 CRUD、JD 要求与差距清单 | `NOT_STARTED` | 确认技术栈与启动命令 | AT-01 至 AT-04 通过 |
| M2 | 投递状态机、下一步行动、面试与提醒 | `NOT_STARTED` | M1 完成 | AT-05 至 AT-14 通过 |
| M3 | 复盘、问题、知识点、薄弱点、学习任务 | `NOT_STARTED` | M2 完成 | AT-15 至 AT-19 通过 |
| M4 | 面试准备包、项目案例、证据、导出、最近删除 | `NOT_STARTED` | M3 完成 | AT-20 至 AT-24 通过 |

## 5. 当前窗口交接

### 当前窗口目标

未开始。下一个窗口从 M1 开始，先创建可启动的 Maven Spring Boot 后端与 React/Vite 前端骨架，再实现岗位垂直切片。

### 已完成的当前窗口工作

- 已完成规格材料和初始迁移脚本的静态准备。

### 未完成/不要重复做

- 不要重新设计 PRD、页面规格、状态机或 OpenAPI。
- 不要提前实现 M2、AI、外部通知、云同步、附件上传或综合匹配评分。
- 不要修改已存在的 V1 迁移脚本；结构变化新增 Flyway 版本。

### 下一步最小任务

1. 检查当前工作区并创建 Maven Wrapper 的 Spring Boot 后端骨架。
2. 创建 React + TypeScript + Vite 前端骨架和本地 `/api` 代理。
3. 接入 V1 Flyway，运行临时 SQLite 迁移测试。
4. 实现岗位创建、岗位列表、岗位详情和基础错误响应。
5. 完成 AT-01 的后端与前端最小验证后再进入要求确认。

## 6. 每个窗口的工作量控制

一个窗口只领取一个“可验证垂直切片”，不要领取一个完整模块或整个里程碑。推荐上限：

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
