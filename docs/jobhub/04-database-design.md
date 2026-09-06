# JobHub V0.1 数据库设计与迁移方案

> 数据库以 `03-openapi.yaml` 的请求/响应模型和 `02-state-machines.md` 的状态规则为准。本设计面向本地单用户 SQLite；未来迁移 MySQL 时保持表语义、约束与索引不变。

## 1. 选型与约定

- P0 数据库：SQLite，应用启动时执行 `PRAGMA foreign_keys = ON`；使用 Flyway 管理迁移。
- 初始迁移：`backend/src/main/resources/db/migration/V1__initial_schema.sql`。
- 主键：应用层生成 UUID，数据库统一使用 `TEXT` 保存；不得依赖 SQLite `rowid` 作为外部 ID。
- 时间：UTC ISO-8601 字符串，字段名以 `_at` 结尾；面试额外保存 IANA `event_time_zone`。
- 乐观锁：所有可并发编辑的聚合根均有 `version INTEGER NOT NULL DEFAULT 0`。更新必须采用 `WHERE id = :id AND version = :version`，成功后 `version = version + 1`。
- 删除：业务主表使用 `deleted_at` 软删除；永久删除前写入/读取 `trash_item`，并遵循 30 天保留期。
- 枚举：P0 在数据库层通过 `CHECK` 固定；Java 枚举和 OpenAPI 枚举必须一一对应。`notification_channel.channel_type` 与 `channel_delivery.channel_type` 的 CHECK 在 V23 迁移中放宽为 `('BROWSER','EMAIL','WEBHOOK')`，以支持通知渠道扩展。

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
| 运维与可追溯 | `notification`、`notification_channel`、`channel_delivery`、`audit_log`、`idempotency_record`、`data_export`、`trash_item` | — | 记录提醒、通知渠道配置与各渠道独立投递状态、关键操作、重复写入和导出。 |
| AI（P1/V0.2） | `ai_provider`、`ai_job`、`ai_job_item` | `ai_provider`(V7)、`ai_job`(V7/V9/V12/V13/V14)、`ai_job_item`(V7/V8/V14) | 可切换供应商配置（api_key 仅本地、不导出不回显）、异步任务审计（模型/提示词版本、重试、失败原因、输出）与候选变更条目（逐项采纳/拒绝）；`RESUME_DRAFT`、`QUESTION_CLASSIFICATION`、`ANSWER_QUALITY_ANALYSIS`、`TASK_SUGGESTION` 只保存必要输入快照与候选，均不自动覆盖主数据。任务建议采纳后通过 `task_id` 回链新建学习任务。供应商仅允许永久删除未激活且未被 `ai_job` 引用的配置，已引用配置为保留审计记录。 |
| 模拟面试（V18–V21） | `mock_interview_session` | `mock_interview_turn` | 会话保存项目不可变快照与 AI 任务审计关联；首轮生成成功后保存讲解稿和首个追问。活动会话可保存用户作答并创建 `MOCK_INTERVIEW_FOLLOW_UP` 审计任务，成功后追加下一条 AI 追问。每个用户作答轮次至多关联一个 `MOCK_INTERVIEW_ANSWER_EVALUATION` 审计任务；成功后在该轮次保存 AI 评分、反馈、依据及完成时间。评分统计与双时间窗口对比均直接聚合已保存评分，不物化能力推断；窗口对比只在每窗至少两条评分时计算平均分及其算术差值。轮次只作为会话练习内容，绝不写回项目、技能、证据、岗位要求或任务。 |
| 简历版本（V22） | `resume_version` | — | 仅保存用户手工确认的版本名称和内容。对比按去重后的非空文本行直接计算相同、新增与删除，不调用 AI、不判断优劣，也不改写投递记录。 |
| 加密备份（V24） | `backup_record` | — | 追加型只读历史，无状态/版本。复用 `data_export` JSON 数据包经 PBKDF2 派生 AES-256-GCM 加密落盘；`salt`/`iv` 落库供恢复派生密钥，passphrase/派生密钥永不持久化；`data_export_id` 软引用无外键。 |
| 备份调度（V25） | `backup_schedule` | — | 单行配置（`singleton`）。`cronExpression` + `enabled` + `version` 经 `If-Match-Version` 乐观锁更新；`last_run_*` 由调度器系统写入（不 bump version）；`armed` 为内存状态不落盘，passphrase 永不持久化。定时生成复用 `BackupService.create`，记录与手动生成同表共存。 |

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
- 复盘分析的回答状态窗口对比使用同一实时聚合查询，不新增能力、趋势或分析结果持久化表；它只读取未删除问题及其关联的面试、投递和岗位。
- 回答质量分析候选复用 `ai_job_item.payload_json`；采纳后以问题版本锁只更新回答状态、参考答案、错误原因和改进方案，不新增冗余分析结论表。
- 学习任务建议候选复用 `ai_job_item.payload_json`；采纳后以问题版本锁创建 `learning_task` 和 `task_source`，并在 `ai_job_item.task_id` 保存回链，不自动修改问题或技能。
- `learning_task` 完成不改变 `user_skill.self_level`，也不删除历史薄弱题。
- `task_source` 使用多态来源：`QUESTION`、`JOB_REQUIREMENT`、`SKILL`、`KNOWLEDGE_POINT`、`MANUAL`。服务层校验 `source_id` 的真实存在性。
- 投递渠道与简历版本效果对比（`GET /analytics/channel-effectiveness`）是只读实时聚合，不新增任何表或外键：按 `application_record.channel`（NOT NULL 自由文本）与 `application_record.resume_version`（可空自由文本，与 `resume_version` 表无外键硬关联）的原始填写文本 `GROUP BY`，未填写简历版本归入 `null` 组。计数采用状态近似口径（见状态机 §3.1），不 JOIN `interview_schedule`，不持久化聚合结果，不输出趋势结论。

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
| 渠道投递扫描 | `channel_delivery(channel_type, status)` |

## 5. 迁移与初始化流程

1. 应用启动前备份本地数据库文件。
2. Flyway 执行 `V1__initial_schema.sql`；失败则不启动业务服务。
3. 在事务中插入单用户默认 `user_profile` 和 `user_setting`，使用 `INSERT OR IGNORE` 保证幂等。
4. 初始化标准技能、别名与知识点词典。词典须单独迁移，如 `V3__seed_taxonomy.sql`，不得与用户数据混写。
5. 未来任何表结构或枚举变更均新增版本迁移，禁止修改已经在用户设备执行过的迁移文件。

### 5.1 通知渠道与投递表语义

`notification_channel` 与 `channel_delivery` 由 V5 创建，V23 将 `channel_type` 的 `CHECK` 放宽为 `('BROWSER','EMAIL','WEBHOOK')`（SQLite 不支持直接改 CHECK，V23 走建新表→复制→DROP 旧表→重命名重建两表，保留 `UNIQUE`、外键与索引）。

- `notification_channel(id, channel_type UNIQUE, enabled, config_json, created_at, updated_at, version)`：每个渠道至多一行；`config_json` 仅 EMAIL/WEBHOOK 使用（EMAIL 为 SMTP 配置，WEBHOOK 为 url+secret+providerType）；secret 与 password 仅写入不回显、不导出。
- `channel_delivery(id, notification_id→notification(id), channel_type, status, failure_reason, attempt_count, sent_at, created_at, updated_at, UNIQUE(notification_id, channel_type))`：同一通知同一渠道至多一条投递；`status ∈ PENDING/SENT/FAILED`；幂等由 `WHERE id AND status='PENDING'` 守护；`idx_channel_delivery_pending(channel_type, status)` 支撑扫描。
- WEBHOOK 渠道投递为服务端同步 HTTP POST，按 2xx 判 SENT、非 2xx 或异常递增 `attempt_count` 至上限后 FAILED，与 EMAIL 同构，不引入新状态。

## 6. 备份、导出与恢复

- P0 的完整 JSON 导出由应用层读取逻辑聚合生成，不直接暴露 SQLite 文件。
- 导出中不得包含 `idempotency_record`、运行日志、AI 请求内容、密钥或令牌。
- 恢复流程必须先做冲突预检。P1（V0.2）已实现导入与完整恢复（PRD 9.5）：数据包为导出端点的标准 JSON 原样回传；预检将同键行分为重复（内容一致）与冲突（内容不同），外键父行缺失的行为缺父级；恢复只插入本地缺失的行，重复、冲突与缺父级行一律跳过并列出在结果报告中，不覆盖、不修改任何已有行，因此重复恢复天然幂等。排除表（用户资料/设置、审计、幂等、导出任务、回收站）按未知表跳过；导入列以数据库实际列白名单为准。
- 本地数据库文件、导出 JSON 和附件引用均应提醒用户自行备份；应用不自动读取或上传本地路径指向的内容。
- `evidence_attachment` 随证据业务数据进入 JSON/CSV 导出与恢复；删除采用软删除并进入最近删除，恢复只恢复该引用记录，不会恢复或触碰引用位置指向的文件。
- 加密备份记录 `backup_record`（V24）为追加型只读历史，无状态/版本字段，生成后不可修改。删除为物理删除（hard delete），不可恢复，不进入最近删除（`trash_item`），不新增 `deleted_at`/`version` 列（不新增迁移）。`backup_record(id, created_at, algorithm, pbkdf2_iterations, salt, iv, data_export_id, file_path, file_name, size_bytes)`：`salt` 与 `iv` 落库以供未来恢复切片从用户 passphrase 经 PBKDF2 重新派生 AES-256-GCM 密钥；passphrase 与派生密钥**永不**持久化；`data_export_id` 软引用 `data_export.id`，不加外键硬约束（与 `resume_version` 同范式）。密文文件布局为 `salt(16) || iv(12) || ciphertext+gcmTag`，落盘于 `jobhub.backup-dir`（默认 `./data/backups`）。删除记录时须配套清理 `file_path` 指向的 `.enc` 密文文件（不存在视为已清理），并清理 `backup_schedule.last_backup_id` 软引用（若指向被删 id 则置空）；`data_export_id` 软引用行不联动删除（独立历史快照，悬空可接受）。按龄批量清理（`DELETE /backups?olderThanDays=N`）按 `created_at` 阈值筛选后逐条执行同联动（删行 + 清文件 + 置空 `last_backup_id` 软引用），不新增表/列/迁移，无匹配记录时 `deletedCount=0`（不报 404）。按数量保留清理（`DELETE /backups?keepLast=N`）按 `created_at DESC` 取最近 N 条为保留集，对其余全部逐条执行同联动；`keepLast` 与 `olderThanDays` 互斥，缺一或同时出现返回 400；`keepLast ≥ 现有总数` 时全部保留 `deletedCount=0`（不报 404）。
- 加密备份恢复为无状态只读转换，不新增表或迁移，不写 `backup_record`：恢复端点接收上传的 .enc 文件与 passphrase，从密文文件头（`salt(16) || iv(12)`）拆出 salt/iv，按生成端相同 PBKDF2 参数派生密钥并 AES-256-GCM 解密，明文须为标准 JSON 数据包，再委托标准数据恢复（`ImportService.restore`）行级幂等恢复，仅插入缺失行，不覆盖已有行。因此数据库丢失（`backup_record` 清空）后仍可凭 .enc 文件与 passphrase 恢复。恢复成功（DB 提交）后自动触发一次孤儿 .enc 文件扫描清理（best-effort，复用 `POST /backups/orphans/clean` 判定逻辑：扫描 `jobhub.backup-dir` 下 `.enc`、合法 UUID 且 `backup_record` 无对应 `file_name` 即孤儿、物理删除；非 UUID `.enc` 跳过计入 `skippedFiles`；`backup-dir` 不存在返回全 0 摘要），不新增表/列/迁移，不写 `backup_record`、不联动 `backup_schedule.last_backup_id`、不联动删 `data_export`（只读 DB 查 `file_name` 集合 + 删文件，无 DB 写）；清理在恢复成功后于事务内同步执行（`cleanOrphans` 只读 DB 查 `file_name` 集合 + 删文件，无 DB 写），失败用 try-catch 包裹不影响恢复事务提交与响应；清理结果通过恢复响应的 `orphanCleanSummary` 字段返回（`ImportResultReport` 可选字段，标准数据恢复 `POST /data-imports/restore` 不触发此联动，该字段为 null）。
- 孤儿 .enc 文件扫描清理（`POST /backups/orphans/clean`）补偿单条删除/按龄清理在 `afterCommit` 文件清理前崩溃残留的孤儿（或 DB 直接删行绕过服务）：扫描 `jobhub.backup-dir` 下全部 `.enc` 文件，取文件名去 `.enc` 得 candidate id，**合法 UUID 且** `backup_record` 中无对应 `file_name` 的行即孤儿，物理删除之；非 UUID 命名的 `.enc` 跳过不删计入 `skippedFiles`。不新增表/列/迁移，不写 `backup_record`、不联动 `backup_schedule.last_backup_id`、不联动删 `data_export`（本端点只读 DB 查 `file_name` 集合 + 删文件，无 DB 写）；`backup-dir` 不存在时 `scannedFiles=0`（不报错），无孤儿时返回全 0 摘要（不报 404）。
- 定时备份调度配置 `backup_schedule`（V25）为单行可变元数据（`singleton`，主键固定为 `'singleton'`）。`backup_schedule(id, cron_expression, enabled, version, last_run_at, last_run_status, last_run_error, last_backup_id)`：`cron_expression` 须为合法标准 5/6 字段表达式，非法由应用层校验返回 422；`cron_expression` + `enabled` + `version` 经 `If-Match-Version` 乐观锁更新；`last_run_at`/`last_run_status`/`last_run_error`/`last_backup_id` 由调度器在每次轮询后系统写入（不 bump `version`，不参与乐观锁）。`armed` 为进程内存状态，**不**落盘；passphrase 与派生密钥**永不**持久化（与手动备份同一硬规则），应用重启后自动 `armed=false`，需用户重新武装。定时生成复用 `BackupService.create(passphrase)`，生成的 `backup_record` 与手动生成同表共存、同追加型只读语义，并可被删除端点物理删除。`last_backup_id` 为软引用，被删备份指向的该字段须置空，不阻塞删除。
