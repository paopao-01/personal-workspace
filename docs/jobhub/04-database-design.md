# JobHub V0.1 数据库设计与迁移方案

> 数据库以 `03-openapi.yaml` 的请求/响应模型和 `02-state-machines.md` 的状态规则为准。本设计面向本地单用户 SQLite；未来迁移 MySQL 时保持表语义、约束与索引不变。

## 1. 选型与约定

- P0 数据库：SQLite，应用启动时执行 `PRAGMA foreign_keys = ON`；使用 Flyway 管理迁移。
- 初始迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`。
- 主键：应用层生成 UUID，数据库统一使用 `TEXT` 保存；不得依赖 SQLite `rowid` 作为外部 ID。
- 时间：UTC ISO-8601 字符串，字段名以 `_at` 结尾；面试额外保存 IANA `event_time_zone`。
- 乐观锁：所有可并发编辑的聚合根均有 `version INTEGER NOT NULL DEFAULT 0`。更新必须采用 `WHERE id = :id AND version = :version`，成功后 `version = version + 1`。
- 删除：业务主表使用 `deleted_at` 软删除；永久删除前写入/读取 `trash_item`，并遵循 30 天保留期。
- 枚举：P0 在数据库层通过 `CHECK` 固定；Java 枚举和 OpenAPI 枚举必须一一对应。

## 2. 表与聚合边界

| 聚合 | 主表 | 从表/关联表 | 说明 |
|---|---|---|---|
| 用户设置 | `user_profile` | `user_setting` | 本地单用户仅一条用户记录。 |
| 技能与证据 | `skill`、`user_skill`、`project`、`evidence` | `skill_alias`、`skill_evidence`、`project_evidence` | 技能自评、证据状态和面试表现独立保存。 |
| 附件证据引用 | `evidence_attachment` | — | 一条证据可维护多条本地路径或外部链接及人工填写的类型、大小、说明；只保存引用元数据，不保存或读取文件内容。 |
| 岗位 | `job_posting` | `job_requirement`、`requirement_skill`、`requirement_match` | JD 更新导致要求重新确认，不删除历史依据。 |
| 投递 | `application_record` | `application_status_log` | 唯一的当前投递状态来源。 |
| 面试 | `interview_schedule` | `interview_reminder`、`interview_checklist_item` | 日程状态与结果分离。 |
| 复盘 | `interview_review` | `interview_question`、`question_knowledge` | 每场面试仅一份当前复盘。 |
| 学习任务 | `learning_task` | `task_source` | 支持一个任务关联多个问题、岗位和知识点。 |
| 运维与可追溯 | `notification`、`audit_log`、`idempotency_record`、`data_export`、`trash_item` | — | 记录提醒、关键操作、重复写入和导出。 |
| AI（P1/V0.2） | `ai_provider`、`ai_job`、`ai_job_item` | `ai_provider`(V7)、`ai_job`(V7/V9)、`ai_job_item`(V7/V8) | 可切换供应商配置（api_key 仅本地、不导出不回显）、异步任务审计（模型/提示词版本、重试、失败原因、输出）与候选变更条目（逐项采纳/拒绝）；`RESUME_DRAFT` 只保存用户确认简历与 JD 快照及候选草稿，不覆盖主数据。 |

## 3. 关键数据规则

### 3.1 岗位与要求

- `job_posting` 不保存投递当前状态，只保存岗位级 `decision_status` 和 `decision_reason`。
- `job_requirement.confirmation_status = CONFIRMED` 才可参与 `requirement_match` 和准备包优先级。
- 同岗位已确认要求按 `normalized_name + type` 做去重；不同原始片段仍保留在合并来源记录中。
- `requirement_match` 对每个 `job_requirement` 至多一条当前匹配记录，其依据以 JSON 文本保存为可追溯快照。

### 3.2 投递与面试

- 同一岗位默认只能有一条未删除且活动的投递。服务层在发现活动投递时，只有收到 `allowDuplicate=true` 才允许二次创建；V2 的部分唯一索引保证至多一条未确认的活动投递。二次创建将 `duplicate_confirmed_at` 设为 UTC 时间，并在同一事务写入 `audit_log`。
- `application_status_log` 是不可覆盖的状态历史；禁止物理更新历史行。
- 创建第一场面试时，若投递为 `RESUME_PASSED`，需在同一事务中转为 `INTERVIEWING` 并写状态历史。
- `interview_schedule` 取消或缺席时，结果必须为 `PENDING`；服务层负责取消尚未展示的提醒。

### 3.3 复盘、薄弱点与任务

- `interview_review.interview_id` 唯一，保证每场面试只有一份当前复盘。
- `interview_question.answer_status` 更新后，薄弱点统计实时按问题和知识点聚合，不维护容易过期的冗余累计字段。
- `learning_task` 完成不改变 `user_skill.self_level`，也不删除历史薄弱题。
- `task_source` 使用多态来源：`QUESTION`、`JOB_REQUIREMENT`、`SKILL`、`KNOWLEDGE_POINT`、`MANUAL`。服务层校验 `source_id` 的真实存在性。

## 4. 索引与查询支持

| 查询场景 | 索引 |
|---|---|
| 首页临近面试 | `interview_schedule(schedule_status, starts_at)` |
| 首页待处理投递 | `application_record(status, next_action_due_at)` |
| 投递历史 | `application_status_log(application_id, occurred_at DESC)` |
| 某岗位的确认要求 | `job_requirement(job_id, confirmation_status, sort_order)` |
| 未来提醒扫描 | `interview_reminder(status, scheduled_at, lease_until)` |
| 面试复盘问题 | `interview_question(review_id, deleted_at)` |
| 薄弱知识点 | `question_knowledge(knowledge_point_id, question_id)` + 问题的回答状态索引 |
| 任务列表 | `learning_task(status, due_at, priority)` |
| 最近删除 | `trash_item(expires_at, deleted_at)` |

## 5. 迁移与初始化流程

1. 应用启动前备份本地数据库文件。
2. Flyway 执行 `V1__initial_schema.sql`；失败则不启动业务服务。
3. 在事务中插入单用户默认 `user_profile` 和 `user_setting`，使用 `INSERT OR IGNORE` 保证幂等。
4. 初始化标准技能、别名与知识点词典。词典须单独迁移，如 `V3__seed_taxonomy.sql`，不得与用户数据混写。
5. 未来任何表结构或枚举变更均新增版本迁移，禁止修改已经在用户设备执行过的迁移文件。

## 6. 备份、导出与恢复

- P0 的完整 JSON 导出由应用层读取逻辑聚合生成，不直接暴露 SQLite 文件。
- 导出中不得包含 `idempotency_record`、运行日志、AI 请求内容、密钥或令牌。
- 恢复流程必须先做冲突预检。P1（V0.2）已实现导入与完整恢复（PRD 9.5）：数据包为导出端点的标准 JSON 原样回传；预检将同键行分为重复（内容一致）与冲突（内容不同），外键父行缺失的行为缺父级；恢复只插入本地缺失的行，重复、冲突与缺父级行一律跳过并列出在结果报告中，不覆盖、不修改任何已有行，因此重复恢复天然幂等。排除表（用户资料/设置、审计、幂等、导出任务、回收站）按未知表跳过；导入列以数据库实际列白名单为准。
- 本地数据库文件、导出 JSON 和附件引用均应提醒用户自行备份；应用不自动读取或上传本地路径指向的内容。
- `evidence_attachment` 随证据业务数据进入 JSON/CSV 导出与恢复；删除采用软删除并进入最近删除，恢复只恢复该引用记录，不会恢复或触碰引用位置指向的文件。
