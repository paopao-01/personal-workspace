# JobHub V0.1 领域状态机与业务规则

> 本文是状态转换的唯一业务依据。通用更新接口不得绕过本文直接改写状态字段；接口契约见 `03-openapi.yaml`。

## 1. 通用规则

### 1.1 状态转换约束

- 每次状态转换必须使用专用命令（如 `transition`、`complete`、`cancel`、`reschedule`），写入操作者、发生时间、原状态、目标状态、备注和幂等键。
- 写操作需带 `Idempotency-Key`。同一资源、同一操作、同一键在 24 小时内重复提交，返回首次成功结果；同键但请求体不同返回 `409 IDEMPOTENCY_CONFLICT`。
- 需要并发保护的资源带 `version`。更新时客户端提交 `If-Match-Version`；版本不匹配返回 `409 VERSION_CONFLICT` 和当前版本。
- 非法转换返回 `422 ILLEGAL_STATE_TRANSITION`，其中包含 `currentState`、`targetState` 和 `reason`；不得产生局部修改。
- 归档/软删除不改变历史记录。被引用内容不应静默永久删除；来源被删除时，引用方显示“来源已删除”。

### 1.2 活动状态定义

| 对象 | 活动状态 |
|---|---|
| 投递 | `DRAFT`、`APPLIED`、`RESUME_PASSED`、`INTERVIEWING`、`ON_HOLD` |
| 面试 | `SCHEDULED` |
| 任务 | `TODO`、`IN_PROGRESS` |

`OFFER`、`REJECTED`、`WITHDRAWN`、`CANCELED`、`NO_SHOW`、`COMPLETED`、`ABANDONED` 均为非活动状态，但其中部分可以通过专用“重新打开”操作恢复。

## 2. 岗位、岗位要求与投递决定

### 2.1 岗位归档状态

```text
ACTIVE ──archive──> ARCHIVED
ARCHIVED ──restore──> ACTIVE
```

| 操作 | 前置条件 | 副作用 |
|---|---|---|
| archive | 岗位为 `ACTIVE` | 隐藏于默认列表；保留投递、面试、复盘和历史。 |
| restore | 岗位为 `ARCHIVED` | 重新显示于默认列表；不自动恢复已归档投递。 |

岗位归档不等同删除，也不自动取消关联面试或投递。永久删除仅能从最近删除区发起，必须显示关联数据影响范围并二次确认。

### 2.2 岗位要求确认状态

```text
PENDING ──confirm──> CONFIRMED
PENDING ──ignore──> IGNORED
CONFIRMED ──edit/reconfirm──> CONFIRMED
CONFIRMED ──ignore──> IGNORED
IGNORED ──restore──> PENDING
```

| 状态 | 含义 | 是否参与差距与准备优先级 |
|---|---|---|
| `PENDING` | 规则/未来 AI 提出的候选项，或 JD 修改后待重新确认项 | 否 |
| `CONFIRMED` | 用户确认或编辑后的要求 | 是 |
| `IGNORED` | 用户认为不适用的候选项 | 否 |

编辑已确认要求会保留原始 JD 片段和修改记录。合并要求时，目标要求保留为 `CONFIRMED`，源要求标记为已合并并不得重复计入统计；删除候选项只允许软删除。

**JD 更新规则**：修改 `jdRawText` 后，将所有未删除的候选要求置为 `PENDING`，并使已有差距结论失效；既有人工修正和来源记录保留，仅不再作为当前结论。

### 2.3 投递决定

投递决定是岗位层面的用户意图，不是投递流程状态。初始值为空，界面显示“未决定”。

| 值 | 允许变更 | 规则 |
|---|---|---|
| `TO_APPLY` | 可改为任意决定 | 准备投递，尚无有效投递。 |
| `APPLY` | 创建有效投递时设置；可改为其他值 | 若存在有效投递，默认展示该值。 |
| `DEFER` | 可改为任意决定 | 应建议填写暂缓理由或重新查看日期，但不强制。 |
| `IGNORE` | 可改为任意决定 | 不自动归档岗位。 |

从 `APPLY` 创建投递时，岗位保留投递决定但不重复保存投递当前状态。若同岗位已有有效投递，创建二次投递须由用户明确确认。

## 3. 投递状态机

```text
DRAFT ──submit──> APPLIED ──resume-pass──> RESUME_PASSED ──start-interviewing──> INTERVIEWING ──offer──> OFFER
  │                    │                         │                       └──reject──> REJECTED
  └──withdraw──> WITHDRAWN                       └──reject──> REJECTED
                                                     │
任一活动状态 ──hold──> ON_HOLD ──resume──> 原活动状态
任一活动状态 ──withdraw──> WITHDRAWN
APPLIED / RESUME_PASSED / INTERVIEWING ──reject──> REJECTED
```

`ON_HOLD` 必须保存 `previousActiveStatus`，恢复时只能回到该状态；若原状态缺失，返回非法转换。`OFFER`、`REJECTED`、`WITHDRAWN` 为终止状态，V0.1 不支持直接恢复；用户应创建新的二次投递。

| 当前状态 | 允许目标状态 | 业务条件 |
|---|---|---|
| `DRAFT` | `APPLIED`、`WITHDRAWN`、`ON_HOLD` | 提交为 `APPLIED` 时必须有投递日期和渠道。 |
| `APPLIED` | `RESUME_PASSED`、`REJECTED`、`WITHDRAWN`、`ON_HOLD` | 被拒可记录拒绝原因。 |
| `RESUME_PASSED` | `INTERVIEWING`、`REJECTED`、`WITHDRAWN`、`ON_HOLD` | 创建第一场面试可在同一事务内进入 `INTERVIEWING`。 |
| `INTERVIEWING` | `OFFER`、`REJECTED`、`WITHDRAWN`、`ON_HOLD` | 进入 `OFFER` 前至少有一场完成面试，或用户提供明确备注确认例外。 |
| `ON_HOLD` | 保存的 `previousActiveStatus`、`WITHDRAWN` | 恢复使用专用 `resume` 操作。 |

每次变更写入 `application_status_log`。投递处于活动状态时，页面应提示并支持维护下一步行动；行动缺失不阻断状态转换。

### 3.1 效果对比聚合口径（只读，非状态转换）

`GET /analytics/channel-effectiveness` 是只读聚合，不产生状态转移，也不修改任何投递、面试或简历版本。为避免误导，计数采用状态近似口径并在此固定：

- `applicationCount`：`status != 'DRAFT'` 且 `deleted_at IS NULL`（已投递，排除待投递草稿）。
- `interviewCount`：`status IN ('INTERVIEWING','OFFER')`（已进入面试阶段；不 JOIN `interview_schedule` 统计 COMPLETED 面试）。
- `offerCount`：`status = 'OFFER'`。
- `offerRate`：`applicationCount >= 2` 时为 `offerCount / applicationCount`，否则为 `null`（信息不足）。

渠道与简历版本均按 `application_record` 的 `channel`、`resume_version` 字段**原始填写文本**分组，不做归一化、合并或去重；未填写简历版本的投递归入 `resumeVersion` 为 `null` 的组。聚合只输出原始计数与可选 Offer 率，不输出趋势结论、能力等级、归因或行动建议。

## 4. 面试日程与结果状态机

### 4.1 日程状态

```text
SCHEDULED ──complete──> COMPLETED
SCHEDULED ──cancel──> CANCELED
SCHEDULED ──mark-no-show──> NO_SHOW
```

| 操作 | 前置条件 | 副作用 |
|---|---|---|
| create | 投递状态为 `RESUME_PASSED` 或 `INTERVIEWING` | 若为 `RESUME_PASSED`，同一事务切换投递至 `INTERVIEWING`。 |
| reschedule | 日程为 `SCHEDULED` | 更新开始时间/事件时区，取消未触发旧提醒并生成新提醒。 |
| complete | 日程为 `SCHEDULED` | 日程变为 `COMPLETED`，面试结果保持或设为 `PENDING`；提醒停止。 |
| cancel | 日程为 `SCHEDULED` | 变为 `CANCELED`，取消未触发提醒，面试结果强制为 `PENDING`。 |
| mark-no-show | 日程为 `SCHEDULED` | 变为 `NO_SHOW`，取消未触发提醒，面试结果强制为 `PENDING`。 |

面试时间到达不会自动执行 `complete`。`COMPLETED`、`CANCELED`、`NO_SHOW` 在 V0.1 为终止日程状态；更正记录通过新建面试和备注完成，不允许直接改回 `SCHEDULED`。

### 4.2 面试结果

面试结果独立于日程状态，取值为 `PENDING`、`PASSED`、`FAILED`。

| 日程状态 | 允许结果 |
|---|---|
| `SCHEDULED` | `PENDING` |
| `COMPLETED` | `PENDING`、`PASSED`、`FAILED` |
| `CANCELED`、`NO_SHOW` | 仅 `PENDING` |

结果可以在 `COMPLETED` 后由 `PENDING` 更新为 `PASSED/FAILED`，并记录更新时间。更新单场面试结果不自动改变投递状态；系统可建议用户更新投递，但必须经确认。

## 5. 提醒状态机

```text
PENDING ──claim──> PROCESSING ──displayed──> SENT
                               └──failed──> FAILED
PENDING / PROCESSING / FAILED ──cancel──> CANCELED
FAILED ──regenerate──> CANCELED + 新建 PENDING
```

- 提醒唯一键：`interviewId + reminderType + scheduledAt`。
- 改期：取消所有尚未展示的旧提醒，再按新时间创建提醒；已发送记录保留。
- 取消/缺席/完成面试：取消所有 `PENDING` 或 `PROCESSING` 提醒。
- P0 仅保证应用运行期间计算到期状态及用户进入应用后的可见性；不承诺系统级推送，也不做自动重试。
- `FAILED` 必须保存失败原因。用户重新生成提醒时，不修改失败记录，而是取消它并创建新的 `PENDING` 记录。
- 调度器使用短时租约令牌领取到期提醒；租约有效期内同一提醒只能由一个实例处理，租约过期后才允许其他实例接管。
- 展示失败不会自动无限重试。用户可通过“重试”命令将 `FAILED` 提醒重新置为 `PENDING`，该命令必须携带提醒版本并保留尝试次数。

### 5.1 渠道投递模式

每条通知按已启用渠道独立生成 `channel_delivery` 记录，`status` 取值为 `PENDING`、`SENT`、`FAILED`，无 `PROCESSING` 或 `CANCELED`，且不随提醒状态机流转。各渠道独立记录发送状态与失败原因，一个渠道失败不阻塞其他渠道，站内通知始终保留兜底。

| 渠道 | 投递模式 | 状态转移 |
|---|---|---|
| `BROWSER` | 前端展示系统通知后调用 `ack` 回执 | `PENDING ──ack──> SENT`；`ack` 仅 BROWSER，其他渠道调用返回 422 |
| `EMAIL` | 调度器异步 SMTP 投递，失败记录原因并递增尝试次数 | `PENDING ──sent──> SENT`；`PENDING ──failed──> PENDING`（重试）或 `FAILED`（达上限） |
| `WEBHOOK` | 调度器同步 HTTP POST 到用户配置的 URL，按 HTTP 响应判定 | `PENDING ──2xx──> SENT`；`PENDING ──非 2xx 或异常──> PENDING`（重试）或 `FAILED`（达上限） |

`WEBHOOK` 投递成功判定为接收端返回 2xx；非 2xx 或连接/超时异常记录截断后的失败原因并递增 `attempt_count`，达重试上限后置 `FAILED`。`WEBHOOK` 不引入新状态，状态空间与 `EMAIL` 一致。`WEBHOOK` 渠道不接受 `ack`，调用返回 422。

## 6. 复盘、问题与薄弱点

### 6.4 AI 问题分类候选

问题分类使用独立的异步 AI 任务，任务状态为 `QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELED`。
任务完成后只产生 `PROPOSED` 候选，用户可以编辑分类后逐项采纳，或直接拒绝；采纳必须携带问题当前版本，成功后才将候选分类写入 `question_type` 并递增问题及复盘版本。任务失败、取消或拒绝不改变问题原有类型和其他人工记录。

默认分类候选值为 `TECHNICAL`、`PROJECT_EXPERIENCE`、`SYSTEM_DESIGN`、`BEHAVIORAL`、`DOMAIN`、`OTHER`，人工编辑仍可使用既有自定义类型。

### 6.5 AI 回答质量分析候选

回答质量分析使用独立异步任务，只有“我的回答”非空的问题可以发起。输入只包含问题内容、用户回答与现有参考答案快照；输出只产生一个 `PROPOSED` 候选，包含总体评价、建议回答状态、参考答案、错误原因和改进方案。

采纳必须携带问题当前版本，并且只更新 `answer_status`、`reference_answer`、`error_reason` 和 `improvement_plan`，递增问题及复盘版本。问题内容、问题类型、用户原回答、难度和知识点不得被 AI 采纳操作修改。失败、取消、拒绝或版本冲突均不得改变问题记录；重新分析不得覆盖已采纳结果。

### 6.6 AI 学习任务建议候选

学习任务建议使用独立异步任务，只有回答状态为 `PARTIALLY_ANSWERED` 或 `UNANSWERED` 的问题可以发起。输入只包含问题、回答状态、已有改进信息和已关联知识点快照；输出一个 `LEARNING_TASK` 的 `PROPOSED` 候选，包含任务标题、学习目标、验收标准、验证方式、优先级和预计耗时。

采纳必须由用户携带问题当前版本，并在同一事务中创建一条 `TODO` 学习任务，写入 `QUESTION` 来源和快照中的已有 `KNOWLEDGE_POINT` 来源，再将候选标记为 `ACCEPTED` 并回链任务 ID。候选中的知识点只能是问题当前已有的知识点，不能由 AI 或采纳操作新增；任务创建失败或版本冲突不得留下部分数据。拒绝、失败、取消和重新生成均不创建任务，也不修改问题、技能或既有任务。

### 6.7 AI 供应商配置删除

AI 供应商不是业务事实，不进入最近删除；删除操作为不可恢复的配置清理。只有非激活且未被 `ai_job.provider_id` 引用的供应商可以删除，删除必须携带当前版本。激活中的供应商必须先切换到其他供应商；已有任务引用的供应商必须保留，以维持任务审计和失败重试所需的配置关联。版本冲突、业务规则拒绝或幂等冲突均不得产生部分删除。

### 6.1 复盘状态

```text
NOT_STARTED ──save-draft──> DRAFT ──complete──> COMPLETED
DRAFT ──discard──> NOT_STARTED
COMPLETED ──reopen──> DRAFT
```

| 操作 | 前置条件 |
|---|---|
| save draft | 面试日程为 `COMPLETED`；允许只有部分字段。 |
| complete | 有面试结果，且“至少一题”或“未记录到问题”为真；每题均有回答状态。 |
| reopen | 当前为 `COMPLETED`；保留问题和任务关联。 |

同一面试只有一份当前复盘。编辑 `COMPLETED` 复盘时，如果仍满足完成条件可保持 `COMPLETED`；若删除/修改后不再满足，服务端拒绝保存或要求先 `reopen`。

### 6.2 问题回答状态

`FULLY_ANSWERED`、`PARTIALLY_ANSWERED`、`UNANSWERED` 是可直接更正的事实字段，不是工作流状态。变更后必须重新计算相关知识点在当前查询范围内的加权薄弱次数：完全答出为 0，部分答出为 0.5，未答出为 1。

复盘分析的时间窗口对比是只读查询：只返回两个窗口内上述三种状态的原始数量和题目总数，不改变问题、复盘、知识点、技能或学习任务，也不推断能力或趋势。

知识点合并时，源知识点的题目关联迁移至目标知识点，并以问题 ID 去重；历史名称与合并记录保留。

## 7. 模拟面试会话状态机

```text
DRAFT ──首轮生成成功──> ACTIVE ──complete──> COMPLETED
DRAFT / ACTIVE ──cancel──> CANCELED
```

- 创建只接受已保存且未删除的项目案例；服务端将该案例的场景、方案、问题和结果序列化为不可变快照，并创建独立 `MOCK_INTERVIEW` AI 任务。
- 仅当 AI 任务成功且返回一份讲解稿和一个首个追问时，才保存两个 `AI` 轮次并将会话从 `DRAFT` 变为 `ACTIVE`。失败、取消或无效输出不修改项目、证据、技能、岗位要求或学习任务。
- `complete` 仅允许 `ACTIVE → COMPLETED`；`cancel` 仅允许 `DRAFT/ACTIVE → CANCELED`，取消尚未终态的 AI 任务。状态变更必须通过专用命令并携带会话当前版本。
- 会话为 `ACTIVE` 时，用户可携带会话当前版本，通过专用作答命令保存对当前 AI 追问的回答；该命令新增一条 `USER` 轮次、递增会话版本，并创建独立的 `MOCK_INTERVIEW_FOLLOW_UP` AI 审计任务。任务成功且会话仍为 `ACTIVE` 时，才新增下一条 `AI` 追问并再次递增版本。
- 仅最新轮次为 AI 且没有待处理连续追问任务时可作答；旧版本、非活动会话、重复提交或任务尚未终态均不得新增轮次。结束或取消会话时必须取消尚未终态的连续追问任务。
- 用户可对任一已保存的 `USER` 轮次发起一次专用评分命令（携带会话当前版本）。该命令创建独立 `MOCK_INTERVIEW_ANSWER_EVALUATION` AI 审计任务并递增会话版本；任务成功后只在该作答轮次保存 1–5 分、反馈和依据，不改变会话状态或任何用户事实。评分任务失败、取消、旧版本、重复评分、非 `USER` 轮次均不得写入评分结果。
- 评分统计只读取已成功保存的评分；它返回评分数量、会话数量、1–5 分分布和最近五次评分。少于两条评分时不得计算平均分，且任何数量下都不得输出能力等级、改进趋势或自动行动建议。
- 评分窗口对比是只读查询：调用方必须提供当前和对比两个完整 UTC 时间窗口。每个窗口返回评分数量、会话数量、1–5 分分布；窗口内少于两条评分时平均分为 `null`，两个平均分均存在时才返回“当前减对比”的平均分差。该差值仅描述原始练习评分的算术变化，不改变会话、项目、技能、证据、岗位要求或任务，也不创建自动行动。
- 本切片不自动创建任何学习任务，也不根据评分修改项目、技能、证据、岗位要求或任务。

## 8. 学习任务状态机

```text
TODO ──start──> IN_PROGRESS ──complete──> COMPLETED
TODO / IN_PROGRESS ──abandon──> ABANDONED
IN_PROGRESS ──reset──> TODO
COMPLETED ──reopen──> IN_PROGRESS
ABANDONED ──restore──> TODO
```

| 操作 | 规则 |
|---|---|
| complete | 可填写验证方式和验证结果；未验证也允许完成，但显式标记“未验证完成”。 |
| abandon | 备注可选；保留来源问题、岗位和知识点关联。 |
| reopen/restore | 不删除已填写的学习产出和验证结果，改为保留历史。 |

任务完成不会自动提升技能自评等级，也不会自动把薄弱点清零。只有用户显式修改技能自评或后续面试表现改善时，相关显示才可变化。

## 9. 备份记录

备份记录为追加型只读历史，无状态转换。生成后不可修改；删除为物理删除（hard delete），不可恢复，不进入最近删除（`trash_item`）：经 `DELETE /backups/{backupId}` 物理删除记录行并配套清理落盘密文文件，须携带 `X-Confirm-Permanent-Delete: true` 确认头。恢复为无状态只读转换：上传 .enc 文件 + passphrase → 解密 → 行级幂等恢复，不改写任何 `backup_record`，也不产生恢复记录。

- passphrase 经 PBKDF2（随机 16B salt、10 万次迭代）派生 AES-256-GCM 密钥加密标准数据包；passphrase 与派生密钥永不落盘、不回显、不进日志。
- `salt` 与 `iv` 随记录持久化，以供未来恢复切片从用户 passphrase 重新派生密钥；`data_export_id` 软引用导出记录，不加外键。
- 备份生成失败不得产生部分 `backup_record` 记录或残留密文文件。
- **passphrase 强度门槛（强制，V1.0 最小切片）**：创建备份（`POST /backups`）与武装调度器（`POST /backups/schedule/arm`）在入口按与前端纯函数同一算法评估 passphrase 强度，阈值弱<40/中40–69/强≥70（维度：长度分上限 35、字符种类分上限 45、弱模式扣分；常见弱口令黑名单 20 条）。**要求 strong**：`score<70`（即弱或中）返回 400 `VALIDATION_ERROR`，message 含 `score=X/100，需 ≥70` 与失败规则（长度不足/缺字符种类/命中弱模式），不进行后续加密/落盘/武装；只有 `score≥70`（强）放行。强度评估在内存进行，passphrase 不落盘、不进日志、不回显；不新增评估端点（passphrase 本就要传给加密端点派生密钥，无额外传输；与 AT-40 纯前端提示算法一致、阈值对齐）。算法维度与黑名单以本节为单一事实来源，前后端实现以此为准防漂移。
- 恢复端点（`POST /backups/restore`）**不**强制强度门槛：passphrase 已与既有备份绑定（用于解密历史密文），强制无意义且会锁死门槛上线前用弱口令创建的历史备份；解密失败仍按 422（GCM 认证失败）处理，与强度无关。
- 恢复端点接收上传的 .enc 文件（密文布局 `salt(16) || iv(12) || ciphertext+gcmTag`）+ passphrase，从文件头拆出 salt/iv，按生成端相同 PBKDF2 参数派生密钥并 AES-256-GCM 解密；GCM 认证失败即 passphrase 错误或文件损坏，返回 422，不进行任何恢复。
- 解密得到的明文须为标准 JSON 数据包（`{format, exportedAt, tables}`）；恢复语义与标准数据恢复一致：只插入缺失行，重复/冲突/缺父级行跳过并列出，不覆盖、不修改已有行（用户事实优先），重复恢复同一备份天然幂等。
- 定时备份调度（V25 `backup_schedule` 单行配置）：
  - 调度配置为可变元数据（`cronExpression` + `enabled`），携带 `version`，经 `If-Match-Version` 乐观锁更新；`cronExpression` 须为合法标准 5/6 字段表达式，非法返回 422。
  - 武装（`armed`）为进程内存状态：用户经 `POST /backups/schedule/arm` 注入 passphrase 后存于 volatile 内存，永不落盘、不回显、不进日志；应用重启自动解除武装（`armed=false`），需用户重新武装。
  - 调度器固定间隔轮询：`enabled=true` 且 `armed=true` 且 cron 到点（距上次运行已过最近一个 cron 周期）时，复用 `BackupService.create(passphrase)` 生成加密备份；成功追写 `backup_record` 并更新 `last_run_*`，失败记 `last_run_status=FAILED`+`last_run_error`，不产生部分 `backup_record` 或残留密文文件。
  - `enabled=true` 但 `armed=false`（未武装，含重启后）到点记 `last_run_status=SKIPPED_DISARMED`，不生成备份。
  - `backup_record` 仍为追加型只读历史，定时生成与手动生成记录同表共存；定时生成记录可被删除端点物理删除（语义同手动生成记录）。

### 9.1 备份删除

删除为物理销毁操作，不可恢复，不进入最近删除（`trash_item`，不套用 §8.2 软删除流程）：

- 删除前置：`backup_record` 行存在（不存在返回 404）；携带 `X-Confirm-Permanent-Delete: true` 确认头（缺失或非 `true` 返回 400）。
- `backup_record` 无 `version` 列，删除不使用 `If-Match-Version` 乐观锁；删除即销毁，幂等性由 `Idempotency-Key` 保证（重复删除同一资源命中幂等回放，未命中则资源已不存在返回 404）。
- passphrase 不参与删除验证：删除即销毁密文与 salt/iv 密钥材料，无需再用 passphrase 校验。
- 删除联动：物理删除 `backup_record` 行；配套清理 `jobhub.backup-dir` 下的 `.enc` 密文文件（文件不存在视为已清理，不报错）；若被删 id 等于 `backup_schedule.last_backup_id`，将该软引用置空（不影响 armed 内存状态与调度配置）。
- `data_export_id` 软引用的导出记录行不联动删除：`data_export` 为独立历史快照，悬空软引用无外键阻拦，保留可追溯。
- 删除失败不得产生部分副作用：文件已删但记录行未删（或反之）须在事务内回滚到一致状态（文件清理在 DB 提交后执行，文件清理失败仅记日志不回滚 DB，避免悬留记录却丢失文件）。
- 删除审计：事务内 `mapper.deleteById` 返回非 0（实际删行）后立即 best-effort 向既有 `audit_log` 表追加一条 `action=BACKUP_DELETED` 审计记录（见下文「备份删除/批量清理审计日志」节），`resource_type=BACKUP_RECORD`、`resource_id`=被删备份 id；写入失败仅记日志不阻塞删除、不影响响应，审计随事务提交/回滚（强一致）；幂等性由 `Idempotency-Key` 保证（重复回放不重新执行→不重复写审计）。

**按龄批量清理**：`DELETE /backups?olderThanDays=N` 物理删除 `created_at` 早于「当前 UTC − N 天」的全部 `backup_record`，语义同单条删除：

- 清理前置：携带 `X-Confirm-Permanent-Delete: true` 确认头（缺失或非 `true` 返回 400）；必须指定 `olderThanDays`（≥1）作为清理阈值，缺省或 <1 返回 400（防止误清空全部备份）。
- 无匹配记录返回 `deletedCount=0`（不返回 404，批量操作空集合法）。
- 逐条联动同单条删除：删 `backup_record` 行 + `afterCommit` 清理 `.enc` 文件（文件不存在视为已清理不报错，不计入 `filesCleaned`）+ 若被删集合含 `last_backup_id` 则置空该软引用（`lastBackupIdCleared=true`）。
- `data_export_id` 软引用行不联动删除（同单条删除语义）。
- 文件清理在 DB 提交后执行，文件清理失败仅记日志不回滚 DB（同单条删除）；返回清理摘要（`deletedCount`/`filesCleaned`/`lastBackupIdCleared`），幂等性由 `Idempotency-Key` 保证（重复回放返回首次缓存的相同摘要，不重新执行清理）。
- 删除审计：事务内逐条 `mapper.deleteById` 返回非 0 后立即 best-effort 向既有 `audit_log` 表追加一条 `action=BACKUP_PURGED_BY_AGE` 审计记录（见下文「备份删除/批量清理审计日志」节），`resource_type=BACKUP_RECORD`、`resource_id`=该被删备份 id；写入失败仅记日志不阻塞清理、不影响摘要计数，审计随事务提交/回滚；幂等性由 `Idempotency-Key` 保证（重复回放不重新执行→不重复写审计）。无匹配记录 `deletedCount=0` 时不写审计。
- **按数量保留清理**（`DELETE /backups?keepLast=N`）语义同按龄清理，仅 action 取 `BACKUP_PURGED_BY_COUNT`（按 `created_at DESC` 取最近 N 条为保留集，对其余全部逐条删行 + best-effort 写审计 + `afterCommit` 清文件 + 置空 `last_backup_id`）；`keepLast ≥ 现有总数` 时无备份被删、不写审计；其余联动、best-effort、幂等、不回显语义同按龄清理。

**孤儿文件扫描清理**：`POST /backups/orphans/clean` 扫描 `jobhub.backup-dir` 下全部 `.enc` 文件，物理删除其中无 `backup_record` 对应的孤儿文件，补偿单条删除/按龄清理在 `afterCommit` 文件清理前崩溃残留的孤儿（或 DB 直接删行绕过服务）：

- 清理前置：携带 `X-Confirm-Permanent-Delete: true` 确认头（缺失或非 `true` 返回 400）。
- 判定规则：备份文件名恒为 `<id>.enc`（id 为 UUID），取文件名去 `.enc` 得 candidate id；**合法 UUID 且** `backup_record` 中无对应 `file_name` 的行即孤儿，删除之；非 UUID 命名的 `.enc`（如用户随手放入的无关文件）跳过不删，计入 `skippedFiles`，避免误删。
- 不写 `backup_record`、不联动 `backup_schedule.last_backup_id`、不联动删 `data_export`：本端点只读 `backup_record`（查 `file_name` 集合）+ 删文件 + best-effort 写 `audit_log` 审计（见下节「孤儿清理审计日志」），无需 `afterCommit`（与单条删除/按龄清理先提交 DB 行再清文件不同——本端点不动 `backup_record` 行，直接删文件并追加审计即可）。
- 不可恢复，不进入最近删除（`trash_item`）；passphrase 不参与清理验证（同单条删除硬规则）。
- `backup-dir` 不存在时 `scannedFiles=0`（不报错）；无孤儿时返回全 0 摘要（不报 404，空集合法）。
- 返回清理摘要（`scannedFiles`/`orphanFiles`/`deletedFiles`/`freedBytes`/`skippedFiles`），幂等性由 `Idempotency-Key` 保证（重复回放返回首次缓存的相同摘要，不重新执行清理、不产生额外副作用）。

**恢复后自动孤儿清理联动**：`POST /backups/restore` 恢复成功（DB 提交）后自动触发一次孤儿 `.enc` 文件扫描清理，复用 `POST /backups/orphans/clean` 的判定与清理逻辑（扫描 `jobhub.backup-dir` 下 `.enc`、合法 UUID 且 `backup_record` 无对应 `file_name` 即孤儿、物理删除；非 UUID `.enc` 跳过计入 `skippedFiles`；`backup-dir` 不存在返回全 0 摘要）：

- 触发时机：恢复成功（`ImportService.restore` 完成、恢复事务提交前）后于事务内同步调用 `cleanOrphans`；恢复失败（passphrase 错误、文件损坏、非合法 JSON）在到达恢复前即返回 422，不触发清理。
- 该自动清理为防御性补偿：恢复本身不落盘 `.enc` 文件，补偿的是既有 afterCommit 崩溃残留或 DB 直接删行绕过服务留下的孤儿。
- `cleanOrphans` 只读 `backup_record`（查 `file_name` 集合）+ 删文件 + best-effort 写 `audit_log` 审计（见「孤儿清理审计日志」节），对恢复事务无实质影响；best-effort：清理与审计失败均用 try-catch 包裹，不影响恢复事务提交与响应（恢复已成功，`orphanCleanSummary` 反映尽力清理的结果，失败时为 null）。
- 不写 `backup_record`、不联动 `backup_schedule.last_backup_id`、不联动删 `data_export`（同独立孤儿清理端点：只读 `backup_record` 查 `file_name` 集合 + 删文件 + best-effort 写 `audit_log`）。
- 无需 `X-Confirm-Permanent-Delete` 确认头：恢复非销毁性操作，附带清理是 best-effort 防御，用户已主动发起恢复即视为授权。
- 清理结果通过恢复响应的 `orphanCleanSummary` 字段返回（`ImportResultReport` 可选字段）；`POST /data-imports/restore` 标准数据恢复不触发此联动，该字段缺省。
- 幂等性由恢复的 `Idempotency-Key` 保证：重复回放命中幂等记录返回首次缓存的完整响应（含 `orphanCleanSummary`），不重新执行恢复与清理。

**孤儿清理审计日志**：`POST /backups/orphans/clean` 与 `POST /backups/restore` 联动的 `cleanOrphans` 在删除每个孤儿 `.enc` 文件成功后，向既有 `audit_log` 表追加一条审计记录（仅追加，不更新/删除），供物理删除操作事后追溯：

- 审计范围：仅孤儿文件清理（独立 `POST /backups/orphans/clean` 与恢复联动的 `cleanOrphans`）。单条删除、按龄清理、按数量保留清理的审计见下文「备份删除/批量清理审计日志」节。
- 记录粒度：**每个被删孤儿文件一条**。`resource_type=BACKUP_FILE`、`resource_id`=被删文件名去 `.enc` 的 UUID、`action=BACKUP_ORPHAN_CLEANED`、`before_snapshot_json`/`after_snapshot_json` 均为 `null`（与既有二次投递确认/需求变更用法一致，不存快照）、`reason` 含被释放字节数（如 `Orphan .enc file with no matching backup_record, removed by orphan scan cleanup (freedBytes=N).`）、`occurred_at`=UTC ISO。
- 写入时机：在 `cleanOrphans` 逐文件循环内，删孤儿文件成功后立即 `auditLogMapper.insert`。无孤儿删除 0 个时不写审计（空操作无可追溯）；非 UUID 命名的 `.enc`（`skippedFiles`）不写审计（未删除）。
- best-effort：审计写入失败用 try-catch 包裹仅记日志，不阻塞清理循环、不影响恢复事务提交与响应、不影响 `BackupOrphanCleanSummary` 计数（`deletedFiles` 反映实际删除的文件数，与审计是否落库无关）。语义同 restore 对 `cleanOrphans` 的 best-effort 容错。
- 不写 `backup_record`（只读其 `file_name` 集合判定孤儿）、不联动 `backup_schedule.last_backup_id`、不联动删 `data_export`；仅向 `audit_log` 追加。
- 查询入口：审计写入不回显到响应（`BackupOrphanCleanSummary` 不加字段），事后追溯经 `GET /backups/orphans/audit` 分页查询（见下节「孤儿清理审计日志查询」），本写入节不涉及读取。
- 幂等性：独立孤儿端点由其 `Idempotency-Key` 保证（重复回放返回首次缓存摘要不重新执行 → 不重复写审计）；恢复联动由恢复的 `Idempotency-Key` 保证（重复回放不重新执行恢复与清理 → 不重复写审计）。无需额外去重。
- `audit_log` 表在 V1 初始迁移已存在，**不新增表/列/迁移**；审计记录仅追加，不提供更新或删除接口（同既有 `AuditLogMapper` 仅 `insert`）。

**备份删除/批量清理审计日志**：`DELETE /backups/{backupId}`（单条删除）、`DELETE /backups?olderThanDays=N`（按龄清理）、`DELETE /backups?keepLast=N`（按数量保留清理）在事务内逐条 `mapper.deleteById` 返回非 0（实际删行）后，向既有 `audit_log` 表追加一条审计记录（仅追加，不更新/删除），供物理删除操作事后追溯：

- 审计范围：单条删除、按龄批量清理、按数量保留清理三类 `backup_record` 物理删除操作。孤儿文件清理审计见上节「孤儿清理审计日志」，两者 `action`/`resource_type` 不同，互不重叠。
- 记录粒度：**每个被删 `backup_record` 行一条**。单条删除写 1 条；批量清理按实际被删记录数逐条写（每行一条）。`resource_type=BACKUP_RECORD`（删的是记录行+附带清文件，与孤儿清理的 `BACKUP_FILE` 区分）、`resource_id`=被删 `backup_record` 的 id（UUID）、`action` 按来源区分：单条删除=`BACKUP_DELETED`、按龄清理=`BACKUP_PURGED_BY_AGE`、按数量保留清理=`BACKUP_PURGED_BY_COUNT`；`before_snapshot_json`/`after_snapshot_json` 均为 `null`（与既有审计用法一致，不存快照）、`reason` 含来源可读说明（如 `Backup record deleted by single delete.` / `Backup record purged by age olderThanDays=N.` / `Backup record purged by count keepLast=N.`）、`occurred_at`=UTC ISO。
- 写入时机：在 `delete`/`purgeOlderThan`/`purgeKeepingLast` 的 `@Transactional` 事务内，`mapper.deleteById` 返回非 0（实际删行）后立即 `auditLogMapper.insert`。无匹配记录删除 0 条时不写审计（空操作无可追溯）；并发删除（`deleteById` 返回 0，记录刚被另一事务删除）跳过不写审计。
- best-effort：审计写入失败用 try-catch 包裹仅记日志，不阻塞删除/清理、不影响 `BackupPurgeSummary`/删除响应计数；审计随事务提交/回滚（强一致——删行回滚则审计回滚，删行提交则审计提交），与孤儿清理审计的「事务外 best-effort、可能删后审计缺失」语义不同（本节审计与删行同事务，强一致）。
- 不回显：审计写入不回显到响应（`BackupPurgeSummary` 不加字段，单条删除无响应体），审计是内部行为；passphrase 不参与（审计记录从不存 passphrase）。
- 查询入口：事后追溯经 `GET /audit-logs` 全量查询（按 `action=BACKUP_DELETED`/`BACKUP_PURGED_BY_AGE`/`BACKUP_PURGED_BY_COUNT` 或 `resourceType=BACKUP_RECORD` 过滤，见下文「全量审计日志查询」节）；不经 `GET /backups/orphans/audit`（该端点仅返回 `BACKUP_ORPHAN_CLEANED`）。
- 幂等性：由各端点的 `Idempotency-Key` 保证（重复回放返回首次缓存摘要/响应不重新执行 → 不重复写审计）。无需额外去重。
- `audit_log` 表在 V1 初始迁移已存在，**不新增表/列/迁移**；审计记录仅追加，不提供更新或删除接口（同既有 `AuditLogMapper` 仅 `insert`）。

**孤儿清理审计日志查询（只读）**：`GET /backups/orphans/audit` 分页查询 `audit_log` 表中 `action=BACKUP_ORPHAN_CLEANED` 的审计记录，供物理删除孤儿文件操作事后追溯（承接「仅写不读」的查询入口缺口）。

- 查询范围：仅 `action=BACKUP_ORPHAN_CLEANED`（独立 `POST /backups/orphans/clean` 与恢复联动的 `cleanOrphans` 两类来源均写此 action，本端点**不区分来源**——当前审计 schema 无来源字段，按 action 过滤即可）。投递确认（`SECONDARY_APPLICATION_CONFIRMED`）、需求增删改合并（`REQUIREMENT_*`）等其他 action **不经本端点暴露**（本切片范围仅孤儿清理审计，不扩散到全量审计查询）。
- 每条记录字段：`id`（审计记录 UUID）、`resourceId`（被删孤儿文件名去 `.enc` 的 UUID）、`action`（固定 `BACKUP_ORPHAN_CLEANED`）、`reason`（含 `freedBytes=N` 的可读说明，原样呈现，非结构化字段——前端如需展示字节数可解析该子串）、`occurredAt`（UTC ISO）。不返回 `resourceType`（固定 `BACKUP_FILE`，冗余省略）、不返回 `before/afterSnapshotJson`（孤儿清理恒为 null，无展示价值）。
- 排序：按 `occurred_at DESC`（最新优先）。
- 分页：复用全局 `page`（从 1 起，默认 1）与 `pageSize`（1–100，默认 20）参数；`offset = (page - 1) * pageSize`。响应 `items + page + pageSize + total + totalPages`（对齐 `PageJob`/`PageApplication`）。空表返回 `items=[]`、`total=0`、`totalPages=0`。
- 只读查询：**不写** `audit_log`、**不需** `X-Confirm-Permanent-Delete` 确认头（非销毁性）、**不需** `Idempotency-Key`（GET 幂等天然）、**不动** `backup_record`/文件系统。响应不含 passphrase（审计记录本身从不存 passphrase）。
- 无二级索引：`audit_log` 表 V1 起无任何二级索引，查询走 `ORDER BY occurred_at DESC` 全表扫描；本地单用户审计量小（仅孤儿清理追加写入），全表扫描性能可接受，**不新增索引迁移**。若未来数据量增长可另开迁移补 `occurred_at` 索引。
- `AuditLogMapper` 在既有 `insert` 之外新增只读 `selectPageByAction` + `countByAction` 方法（仅 SELECT，不违背「仅追加、不提供更新/删除」语义）。

**全量审计日志查询（只读）**：`GET /audit-logs` 分页查询 `audit_log` 表全量审计记录，供关键用户确认与不可覆盖操作的跨域统一事后追溯（承接 `GET /backups/orphans/audit` 仅覆盖孤儿清理的备份域便捷入口，本端点为通用查询入口，**不废弃、不替代**前者）。

- 查询范围：全量 `audit_log`（所有 action、所有 resourceType）。支持两个可选过滤参数 `action` 与 `resourceType`，均可空、可单独或组合使用，按字符串精确匹配；空=不过滤返回全量。只读暴露既有写入值，不新增 action：`SECONDARY_APPLICATION_CONFIRMED`（二次投递确认，`resourceType=APPLICATION`）、`REQUIREMENT_MERGED`/`REQUIREMENT_UPDATED`/`REQUIREMENT_DELETED`（需求合并/编辑/删除，`resourceType=JOB_REQUIREMENT`）、`BACKUP_ORPHAN_CLEANED`（孤儿清理，`resourceType=BACKUP_FILE`）、`BACKUP_DELETED`（单条删除，`resourceType=BACKUP_RECORD`）、`BACKUP_PURGED_BY_AGE`（按龄批量清理，`resourceType=BACKUP_RECORD`）、`BACKUP_PURGED_BY_COUNT`（按数量保留清理，`resourceType=BACKUP_RECORD`）。`action=BACKUP_ORPHAN_CLEANED` 时本端点返回与 `GET /backups/orphans/audit` 相同的记录集，但字段含 `resourceType`（本端点不省略，因全量查询 resourceType 不固定）。
- 每条记录字段：`id`（审计记录 UUID）、`resourceType`（资源类型，全量查询不固定故返回）、`resourceId`（关联资源 id）、`action`（动作类型）、`reason`（可读说明，原样呈现）、`occurredAt`（UTC ISO）。省略 `before/afterSnapshotJson`（当前所有审计记录快照恒为 null，无展示价值，与 `GET /backups/orphans/audit` 一致）；响应不含 passphrase（审计记录本身从不存 passphrase）。
- 排序：按 `occurred_at DESC`（最新优先），与 `GET /backups/orphans/audit` 一致。
- 分页：复用全局 `page`（从 1 起，默认 1）与 `pageSize`（1–100，默认 20）参数；`offset = (page - 1) * pageSize`。响应 `items + page + pageSize + total + totalPages`（对齐 `PageJob`/`PageBackupOrphanAuditEntry`）。空表或过滤无匹配返回 `items=[]`、`total=0`、`totalPages=0`。
- 只读查询：**不写** `audit_log`、**不需** `X-Confirm-Permanent-Delete` 确认头（非销毁性）、**不需** `Idempotency-Key`（GET 幂等天然）、**不动** `backup_record`/文件系统/任何业务表。响应不含 passphrase。
- 无二级索引：`audit_log` 表 V1 起无任何二级索引，查询走 `ORDER BY occurred_at DESC` 全表扫描（带可选 `WHERE action=? AND resource_type=?`）；本地单用户审计量小，全表扫描性能可接受，**不新增索引迁移**。
- `AuditLogMapper` 在既有 `selectPageByAction`/`countByAction` 之外新增只读 `selectPage(action, resourceType, pageSize, offset)` + `count(action, resourceType)` 方法（动态 SQL，参数 null 不加条件，仅 SELECT，不违背「仅追加、不提供更新/删除」语义）。

**恢复后弱口令重设提示**：`POST /backups/restore` 恢复成功后，对用户本次提交的 passphrase（已在调用栈内存中，用于解密）复用强度评估纯函数（与 §9 强度门槛同一算法，单一事实来源）做一次内存评估。**当评估未达 strong（`score<70`，即弱或中）** 时置响应 `passphraseResetRecommended=true`，提示用户该备份口令未达强、建议用强口令新建备份替换；强口令为 `false`。

- 触发时机：恢复成功（`ImportService.restore` 完成、`cleanOrphans` 之后）于事务内同步评估；恢复失败（passphrase 错误、文件损坏、非合法 JSON）在到达恢复前即返回 422，不触发评估。
- 恢复端点**仍豁免强度门槛**：评估仅为提示，不阻塞、不拒绝恢复、不返回强度 400（passphrase 已与既有备份绑定，强制无意义且会锁死历史备份，详见 §9 恢复端点豁免条目）。
- 评估在内存进行，passphrase 不落盘、不进日志、不回显；响应仅返回布尔 `passphraseResetRecommended`，**不**回显 passphrase 或 `score`/`level` 等派生信息（避免经响应侧信道泄露 passphrase 特征）；不新增评估端点。
- 「重设」语义：系统无全局 passphrase 可重设（passphrase 每条备份绑定、从不持久化，仅武装时存内存），故「重设」即建议用户用强口令**新建一条备份**替换旧弱/中口令备份（新建时走强度门槛强制 `score≥70`），用户可随后删除旧备份。本端点不实现密钥轮换、不自动新建/删除备份、不阻塞后续操作。
- 结果通过恢复响应的 `passphraseResetRecommended` 字段返回（`ImportResultReport` 可选字段，nullable boolean）；`POST /data-imports/restore` 标准数据恢复不触发此评估，该字段为 null。
- 幂等性由恢复的 `Idempotency-Key` 保证：重复回放命中幂等记录返回首次缓存的完整响应（含 `passphraseResetRecommended` 首次值），不重新执行评估。

## 8. 能力、证据与删除状态

### 8.1 技能维度

`self_level`（0–5）、`evidence_status`（`NO_EVIDENCE / WEAK / VALID`）和 `interview_performance` 是相互独立的字段。修改任一字段不得自动覆盖另外两项；JD 差距与任务完成都不能直接修改 `self_level`。

### 8.2 软删除与最近删除

```text
ACTIVE ──soft-delete──> TRASHED ──restore──> ACTIVE
TRASHED ──permanent-delete──> PURGED
```

- `PURGED` 不可恢复。
- 默认保留期为 30 天；到期清理前应再次检查是否被任务或简历引用。
- 删除来源记录后，关联学习任务保留并显示“来源已删除”。
