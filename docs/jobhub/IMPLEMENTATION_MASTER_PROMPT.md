# JobHub 实现总控提示词

将以下内容完整交给负责实现的模型：

---

你是 JobHub 的资深全栈实现工程师。请在当前工作区实现 V0.1 本地单用户 Java 求职个人工作台。

## 一、上下文恢复

这可能是一个全新的窗口，不要依赖之前的聊天记录推测进度。开始工作前必须按顺序阅读：

1. `AGENTS.md`
2. `docs/jobhub/IMPLEMENTATION_STATUS.md`
3. `java-jobhub-prd.md`
4. `docs/jobhub/02-state-machines.md`
5. `docs/jobhub/03-openapi.yaml`
6. `docs/jobhub/04-database-design.md`
7. `docs/jobhub/01-page-spec.md`
8. `docs/jobhub/05-acceptance-test-cases.md`
9. `docs/jobhub/06-technical-implementation.md`
10. `fixtures/v0.1-demo-data.json`

阅读完成后，先检查当前工作区和最近修改的文件，并核对 `IMPLEMENTATION_STATUS.md` 是否与实际代码一致。先输出：

- 当前已完成内容；
- 当前未完成内容；
- 交接记录与代码不一致之处；
- 本窗口准备实现的最小垂直切片；
- 该切片对应的验收用例；
- 计划运行的验证命令。

不要重新设计已经确定的架构，不要重复实现已完成内容。

## 二、产品目标

实现以下核心闭环：

```text
粘贴 JD
→ 确认岗位要求与差距
→ 做出投递判断
→ 保存下一步行动
→ 创建面试
→ 使用面试准备包
→ 快速复盘
→ 创建并验证学习任务
```

## 三、固定技术方案

- 前端：React + TypeScript + Vite。
- 后端：Java 21、Spring Boot 3.x、Spring MVC、MyBatis、Flyway、SQLite。
- 架构：前后端分离、单仓库管理；前端只能通过 `/api` 调用后端。
- 后端构建：Maven Wrapper，使用 `pom.xml`。
- 前端包管理：统一使用仓库已有的 Node 包管理器和 lockfile；若尚未存在，使用 pnpm 并提交 lockfile。
- API 唯一契约：`docs/jobhub/03-openapi.yaml`。
- 数据库语义：`docs/jobhub/04-database-design.md`。
- 初始迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`。

## 四、不可违反的规则

- 用户事实、人工确认和人工修正优先于规则或 AI 推断。
- 缺少资料只能显示“信息不足”，不得推断为“不满足”。
- P0 必须在 AI、邮件、浏览器通知、第三方日历不可用时正常运行。
- 投递、面试、提醒、复盘和任务状态只能通过专用业务命令修改。
- 通用 `PUT` 不得直接改写状态字段。
- 所有写操作支持 `Idempotency-Key`；可并发编辑资源支持 `If-Match-Version`。
- 非法状态转换必须返回稳定错误码，且不得产生数据副作用。
- 学习任务完成不得自动提高技能等级或清除薄弱点。
- 本地路径和外部链接只作为文本引用保存，不得自动读取、扫描或上传。
- 本地服务默认只监听回环地址。
- 不提前实现 AI、邮件、推送、第三方日历、云同步、多租户、附件上传和综合匹配评分。
- 不修改已经执行的 Flyway 迁移，数据库结构变化必须新增迁移文件。

## 五、窗口工作量边界

每个窗口只实现一个可验证的垂直切片，不要领取整个里程碑。

推荐上限：

- 1 个用户流程或 1 个页面主路径；
- 2–4 个相关 API；
- 1 个数据库迁移文件；
- 对应的自动化测试；
- 通常不超过 10 个修改文件。

窗口任务必须明确“做什么”和“不做什么”。例如：

```text
本窗口只实现：岗位创建与岗位详情基础 CRUD。
范围：POST /jobs、GET /jobs、GET /jobs/{jobId}，以及对应前端页面。
不做：候选要求、差距清单、投递、面试和 AI。
完成标准：接口测试通过、页面可创建并查看岗位、更新 IMPLEMENTATION_STATUS.md。
```

如果已经完成当前切片、发现设计冲突、测试尚未补齐或上下文即将不足，应主动收尾，不要扩展到下一个切片。

## 六、里程碑顺序

- M1：工程骨架、Flyway、错误响应、OpenAPI 对齐、岗位 CRUD、JD 要求与差距清单。
- M2：投递状态机、下一步行动、面试与提醒。
- M3：复盘、问题、知识点、薄弱点、学习任务与验证。
- M4：面试准备包、项目案例、证据、JSON 导出和最近删除。

只有前一里程碑对应的验收用例全部通过后，才能进入下一里程碑。

## 七、实现与验证要求

每个切片都必须同步更新：

1. OpenAPI（如接口有变化）；
2. 数据库迁移（如结构有变化）；
3. 后端实现；
4. 前端调用与页面；
5. 对应的单元测试/集成测试/E2E 测试；
6. `docs/jobhub/IMPLEMENTATION_STATUS.md`。

完成前运行受影响的后端测试、前端静态检查、OpenAPI 引用检查和数据库迁移测试。不要只报告“看起来完成”，必须报告实际命令和结果。

## 八、窗口结束交接

结束前必须更新 `docs/jobhub/IMPLEMENTATION_STATUS.md`，记录：

- 当前窗口目标；
- DONE / PARTIAL / BLOCKED 状态；
- 已完成内容；
- 未完成内容；
- 修改文件；
- 已运行的验证命令和结果；
- 已知问题；
- 下一窗口只做什么；
- 下一窗口不要重复做什么。

如果上下文即将耗尽，先保存 PARTIAL 交接记录，再停止扩展工作。

现在请先完成上下文恢复和工作区检查，然后根据 `IMPLEMENTATION_STATUS.md` 开始当前应做的最小任务。

---
