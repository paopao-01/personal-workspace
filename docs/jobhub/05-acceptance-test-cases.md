# JobHub V0.1 验收测试用例

> 测试以用户可观察结果为准。建议后端使用 Spring Boot 集成测试覆盖 API 与数据库事务，前端使用 Playwright 覆盖核心路径。测试数据见 `fixtures/v0.1-demo-data.json`。

## 1. 通用约定

- Given 中的时间均为 UTC，展示断言按 `Asia/Shanghai` 执行。
- 所有写操作携带唯一 `Idempotency-Key`；涉及编辑的请求携带当前 `If-Match-Version`。
- 断言同时检查 HTTP 响应、数据库持久化和必要的关联副作用。
- 状态和错误码的权威来源是 `02-state-machines.md`。

## 2. 首次价值与岗位分析

### AT-01 首次会话可以完成最小闭环

```gherkin
Given 用户没有岗位、技能或证据资料
When 用户创建一份 JD，并确认 3 项候选要求（候选不足时确认全部）
And 用户查看差距清单并保存岗位决定 TO_APPLY
Then 岗位创建成功且显示已确认要求
And 差距中缺少用户资料的要求显示 INSUFFICIENT_INFO
And 首页显示“为该岗位创建投递或安排下一步行动”的推荐入口
And 系统未要求用户创建技能、项目或上传附件
```

### AT-02 候选要求在确认前不参与结论

```gherkin
Given 一个岗位有 2 项 CONFIRMED 要求和 1 项 PENDING 要求
When 用户请求该岗位的 gap-list
Then 返回结果只包含 2 项已确认要求
And PENDING 要求仍可在岗位详情的待确认区域查看
And 准备包不以该 PENDING 要求生成优先准备项
```

### AT-03 修改 JD 后使结论待重新确认

```gherkin
Given 岗位已有 CONFIRMED 要求和差距记录
When 用户更新 jdRawText
Then 原有未删除要求变为 PENDING
And 原差距记录不再作为当前 gap-list 的结论
And 原始 JD 片段与人工修改记录仍可追溯
```

### AT-04 用户可以修正匹配状态

```gherkin
Given 一项 CONFIRMED 要求当前为 SELF_REPORTED_NO_EVIDENCE
When 用户将其改为 NOT_MET 并填写原因
Then gap-list 返回 NOT_MET 和该修正原因
And 系统保留由技能/证据得出的原始依据快照
```

## 3. 投递与下一步行动

### AT-05 合法投递转换会写入不可覆盖历史

```gherkin
Given 一条状态为 DRAFT 的投递，且包含投递日期与渠道
When 用户转换为 APPLIED
Then 当前状态为 APPLIED
And application_status_log 新增一条 DRAFT 到 APPLIED 的记录
And 普通 PUT 更新请求不能修改该历史记录
```

### AT-06 非法转换被拒绝且不产生副作用

```gherkin
Given 一条状态为 DRAFT 的投递
When 用户请求转换到 OFFER
Then 响应为 422 ILLEGAL_STATE_TRANSITION
And 响应包含 currentState=DRAFT、targetState=OFFER 和可理解原因
And 投递状态、版本号和状态历史均不变化
```

### AT-07 幂等提交不会创建重复状态历史

```gherkin
Given 一条状态为 DRAFT 的投递
When 使用相同 Idempotency-Key 连续两次提交 DRAFT 到 APPLIED
Then 两次请求均返回同一成功结果
And 仅有一条新的 application_status_log
When 使用相同键但请求目标状态改为 WITHDRAWN
Then 返回 409 IDEMPOTENCY_CONFLICT
```

### AT-08 活动投递需要显式确认二次创建

```gherkin
Given 某岗位已有一条状态为 INTERVIEWING 的投递
When 用户未携带 allowDuplicate 创建同岗位投递
Then 返回 409 DUPLICATE_APPLICATION
When 用户携带 allowDuplicate=true 创建
Then 创建成功并写入审计记录说明用户确认二次投递
```

### AT-09 首页能识别缺失和逾期行动

```gherkin
Given 一条 APPLIED 投递没有 nextAction
And 一条 INTERVIEWING 投递的 nextActionDueAt 早于当前时间
When 用户打开 dashboard
Then 缺失行动的投递显示补充行动提示
And 逾期行动显示逾期天数并位于一般任务之前
```

## 4. 面试与提醒

### AT-10 创建首场面试推进投递

```gherkin
Given 一条状态为 RESUME_PASSED 的投递
When 用户创建一场 SCHEDULED 面试
Then 面试创建成功
And 投递在同一事务中变为 INTERVIEWING
And 状态历史新增 RESUME_PASSED 到 INTERVIEWING
And 默认 1 天、2 小时、30 分钟提醒被创建
```

### AT-11 面试改期会替换未触发提醒

```gherkin
Given 一场未来 SCHEDULED 面试及 3 条 PENDING 提醒
When 用户改期到新的开始时间和事件时区
Then 面试 startsAt 与 eventTimeZone 更新
And 旧 PENDING 提醒均为 CANCELED
And 新时间对应的 3 条 PENDING 提醒被创建
And 已经 SENT 的历史提醒不会被删除
```

### AT-12 取消或缺席面试不能保留结果

```gherkin
Given 一场 SCHEDULED 面试
When 用户取消该面试
Then 日程状态为 CANCELED 且 result=PENDING
And 所有未触发提醒被 CANCELED
And API 拒绝再将该场面试结果设为 PASSED 或 FAILED
```

### AT-13 到达结束时间不自动完成面试

```gherkin
Given 一场 startsAt 已过去的 SCHEDULED 面试
When 定时任务运行或用户刷新页面
Then 面试仍为 SCHEDULED
And 首页/面试详情显示等待用户确认的复盘提示
```

### AT-14 本地提醒不承诺应用关闭时的推送

```gherkin
Given 应用未运行且提醒时间已过
When 用户重新打开应用
Then 对应提醒可显示为已到期的站内提醒
And 产品界面不声明曾发送系统级推送
```

### AT-26 多实例提醒领取与失败重试

```gherkin
Given 一条已到期的 PENDING 提醒
When 两个调度实例同时扫描
Then 只有一个实例可以领取该提醒的租约
And 该提醒最多生成一条站内通知

Given 一条状态为 FAILED 且带失败原因的提醒
When 用户携带当前版本执行重试命令
Then 提醒变为 PENDING 且失败原因被清除
And 尝试次数保留并可再次被调度
When 用户使用旧版本重复执行重试命令
Then API 返回版本冲突且不产生数据副作用
```

## 5. 复盘、问题与学习任务

### AT-15 快速复盘允许最小录入

```gherkin
Given 一场 COMPLETED 面试且尚无复盘
When 用户保存 interviewResult=FAILED、1 道问题和 answerStatus=UNANSWERED
Then 复盘状态为 DRAFT
And 用户可离开后继续编辑
And 未填写我的回答、参考答案或错误原因不阻断保存
```

### AT-16 完成复盘需要满足最小条件

```gherkin
Given 一份 DRAFT 复盘没有问题且 noQuestionsRecorded=false
When 用户请求 complete
Then 返回 422 业务规则错误
When 用户添加一题并为其填写 answerStatus 后再次 complete
Then 复盘状态变为 COMPLETED
```

### AT-17 修改回答状态会更新可下钻薄弱点

```gherkin
Given 同一知识点有 1 题 UNANSWERED 和 1 题 PARTIALLY_ANSWERED
When 用户查询薄弱知识点
Then 加权薄弱次数为 1.5 且可返回两道原始问题
When 用户把第一题更新为 FULLY_ANSWERED
Then 加权薄弱次数更新为 0.5
```

### AT-17A AI 问题分类必须经用户采纳

```gherkin
Given 一道已有自定义类型且版本为 N 的面试问题
When 用户发起 AI 分类并等待任务成功
Then 返回一个 PROPOSED 候选分类，问题原类型保持不变
When 用户编辑候选分类并携带问题版本 N 采纳
Then 问题类型更新为编辑后的分类，问题版本递增
And 回答状态、答案、知识点保持不变
When 用户拒绝候选或使用旧版本采纳
Then 问题类型不变，并分别返回 REJECTED 或版本冲突
```

### AT-17B AI 回答质量分析必须经用户采纳

```gherkin
Given 一道已填写“我的回答”且版本为 N 的面试问题
When 用户发起 AI 回答质量分析并等待任务成功
Then 返回一个 PROPOSED 候选，问题原回答状态、参考答案、错误原因和改进方案保持不变
When 用户编辑候选并携带问题版本 N 采纳
Then 仅回答状态、参考答案、错误原因和改进方案更新，问题版本递增
And 问题内容、问题类型、我的回答、难度和知识点保持不变
When “我的回答”为空、用户拒绝候选或使用旧版本采纳
Then 分别返回业务规则错误、REJECTED 或版本冲突，且问题记录不产生副作用
```

### AT-17C AI 学习任务建议必须经用户采纳

```gherkin
Given 一道 PARTIALLY_ANSWERED 问题关联知识点 Redis
When 用户发起 AI 学习任务建议并等待任务成功
Then 返回一个 PROPOSED 的可编辑任务候选
And 数据库中没有新增 learning_task
When 用户编辑标题、验收标准和验证方式，并携带问题当前版本采纳
Then 创建一条 TODO 学习任务
And 任务通过 task_source 关联原问题和已有 Redis 知识点
And 候选状态变为 ACCEPTED 并回链任务 ID
When 用户拒绝候选或使用旧版本采纳
Then 分别返回 REJECTED 或版本冲突，且不创建任务
```

### AT-17D AI 供应商删除保护配置与审计

```gherkin
Given 一个激活供应商 A、一个未激活且未被任务引用的供应商 B，以及一个已被 AI 任务引用的供应商 C
When 用户携带 B 的当前 version 删除 B
Then 返回 204 且供应商列表不再包含 B
When 用户删除激活供应商 A
Then 返回 422 BUSINESS_RULE_ERROR 且 A 仍为激活状态
When 用户删除已被任务引用的供应商 C
Then 返回 422 BUSINESS_RULE_ERROR 且 C 和关联任务保持不变
When 用户使用旧 version 删除 B
Then 返回 409 VERSION_CONFLICT 且不产生删除副作用
```

### AT-18 从问题创建任务必须由用户确认

```gherkin
Given 一道 PARTIALLY_ANSWERED 问题关联知识点 Redis
When 用户打开“创建学习任务”预填表单但未提交
Then 数据库中没有新增 learning_task
When 用户确认标题、验收标准和验证方式后提交
Then 创建任务并通过 task_source 关联原问题
```

### AT-19 完成任务不自动修改能力

```gherkin
Given 用户 Redis 的 selfLevel=2，且关联任务状态为 IN_PROGRESS
When 用户将任务转换为 COMPLETED 并保存验证结果
Then 任务为 COMPLETED 且保留验证结果
And Redis selfLevel 仍为 2
And 历史薄弱题仍可在统计下钻中查看
```

## 6. 面试准备包

### AT-20 准备包聚合且可追溯

```gherkin
Given 一场未来面试关联已确认岗位要求、项目案例、历史问题和未完成任务
When 用户请求 preparation pack
Then 返回要求差距、可讲项目案例、历史问题、任务和检查清单
And 每个 prioritizedItem 至少包含一个排序原因和 sourceRef
And 返回中不包含 PENDING 岗位要求作为确定性准备结论
```

### AT-21 准备包不伪造项目案例或能力分数

```gherkin
Given 某项必须要求没有任何项目案例或证据
When 用户打开 preparation pack
Then 对应项显示待补充/信息不足
And 不返回虚构项目描述、量化结果或综合能力分数
```

## 7. 数据安全与失败处理

### AT-22 乐观锁冲突不覆盖他人/旧页面修改

```gherkin
Given 用户在两个页面打开同一任务，初始 version=3
When 页面 A 更新成功
And 页面 B 使用 If-Match-Version=3 更新
Then 页面 B 返回 409 VERSION_CONFLICT 和当前版本
And 页面 A 的更新内容保持不变
```

### AT-23 软删除与恢复保留引用关系

```gherkin
Given 一条证据被学习任务或项目案例引用
When 用户请求删除该证据
Then 删除确认展示直接和间接影响
And 证据进入最近删除，引用方显示“来源已删除”
When 用户在 30 天内恢复该证据
Then 原引用恢复可用，且证据 ID 不变
```

### AT-24 导出数据排除机密与运行记录

```gherkin
Given 用户已经创建岗位、投递、面试、复盘、任务和证据
When 用户创建 JSON 数据导出并等待 SUCCEEDED
Then 导出包含业务数据及关联 ID
And 不包含访问令牌、密钥、完整应用日志、idempotency_record 或未确认 AI 输入输出
```

### AT-25 附件证据库只保存引用元数据

```gherkin
Given 用户已经创建一条证据
When 用户为该证据登记多条本地路径或外部链接，并填写可选类型、大小和说明
Then 系统返回独立的附件引用记录，支持列表查询、版本保护的编辑和软删除
And 系统不读取、扫描、上传、下载或校验路径/链接指向的内容
And 删除记录进入最近删除，恢复后引用位置文本和元数据保持不变
And JSON/CSV 导出包含 evidence_attachment 表，恢复时只插入缺失的附件引用记录
```

## 8. 发布门槛

- AT-01 至 AT-27 必须全部通过；状态转换和数据安全场景不得以人工口头验证替代自动化测试。
- 后端集成测试必须在临时 SQLite 数据库中执行迁移；前端端到端测试必须覆盖 AT-01、AT-09、AT-11、AT-15、AT-18、AT-20。
- 合并前运行 OpenAPI 引用校验、数据库迁移测试、后端测试和前端静态检查；任一失败不得发布。
