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

## 6. 复盘、问题与薄弱点

### 6.4 AI 问题分类候选

问题分类使用独立的异步 AI 任务，任务状态为 `QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELED`。
任务完成后只产生 `PROPOSED` 候选，用户可以编辑分类后逐项采纳，或直接拒绝；采纳必须携带问题当前版本，成功后才将候选分类写入 `question_type` 并递增问题及复盘版本。任务失败、取消或拒绝不改变问题原有类型和其他人工记录。

默认分类候选值为 `TECHNICAL`、`PROJECT_EXPERIENCE`、`SYSTEM_DESIGN`、`BEHAVIORAL`、`DOMAIN`、`OTHER`，人工编辑仍可使用既有自定义类型。

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

知识点合并时，源知识点的题目关联迁移至目标知识点，并以问题 ID 去重；历史名称与合并记录保留。

## 7. 学习任务状态机

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
