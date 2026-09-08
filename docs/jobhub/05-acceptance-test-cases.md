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

### AT-27 项目模拟面试首轮不覆盖用户事实

```gherkin
Given 一个已保存项目案例和一个激活的 AI 供应商
When 用户创建模拟面试并等待关联 AI 任务成功
Then 会话从 DRAFT 变为 ACTIVE
And 保存一份项目快照、AI 讲解稿和一个首个高频追问
And 项目、技能、证据、岗位要求和学习任务均不被修改
When 用户取消 DRAFT 会话或完成 ACTIVE 会话
Then 仅会话状态通过专用命令变为 CANCELED 或 COMPLETED
And 使用旧版本或非法状态转换不会产生副作用
```

### AT-28 模拟面试作答触发连续追问

```gherkin
Given 一个 ACTIVE 模拟面试会话，已保存讲解稿和首个高频追问，版本为 N
When 用户携带版本 N 和 Idempotency-Key 保存非空作答
Then 会话新增一条 USER 轮次、会话版本递增，并创建独立的连续追问 AI 任务
When 该 AI 任务成功且会话保持 ACTIVE
Then 会话新增一条 AI 连续追问，且版本再次递增
And 项目、技能、证据、岗位要求和学习任务均不被修改
When 用户用旧版本、在待处理追问期间重复作答或向非 ACTIVE 会话提交
Then 返回版本冲突或非法状态转换，且不新增轮次
And 本操作不创建学习任务
```

### AT-29 模拟面试作答评分仅保存练习反馈

```gherkin
Given 一个包含已保存 USER 作答轮次的模拟面试会话，版本为 N
When 用户携带版本 N 和 Idempotency-Key 对该 USER 轮次发起评分
Then 会话版本递增，并创建独立 MOCK_INTERVIEW_ANSWER_EVALUATION AI 审计任务
When 该任务成功
Then 对应 USER 轮次保存 1–5 分、反馈和评分依据
And 会话状态、项目、技能、证据、岗位要求和学习任务均不被修改
When 用户使用旧版本、对 AI 轮次评分、重复评分或在已取消会话上评分
Then 返回版本冲突或业务规则错误，且不新增任务或评分结果
```

### AT-30 模拟面试评分统计仅呈现原始练习数据

```gherkin
Given 不同模拟面试会话中已有 1 条评分为 4 的用户作答
When 用户查询评分统计
Then 返回评分数为 1、会话数为 1、4 分计数为 1
And averageScore 为 null，并明确显示信息不足
And 不返回能力等级、趋势或学习任务建议
When 再保存一条评分为 2 的用户作答
Then 返回平均分为 3.0、完整 1–5 分分布和按评分完成时间倒序的最近原始分数
And 项目、技能、证据、岗位要求、学习任务和会话状态均不被修改
```

### AT-31 复盘回答状态窗口对比仅呈现原始计数

```gherkin
Given 当前窗口有 1 道完全答出和 1 道未答出的问题，对比窗口有 1 道部分答出的问题
When 用户同时提供当前和对比窗口的开始、结束日期查询复盘分析
Then 返回两个窗口各自的题目总数与完全答出、部分答出、未答出原始计数
And 返回对比窗口边界
And 不返回能力等级、趋势结论或学习任务建议
And 问题、复盘、知识点、技能和学习任务均不被修改
When 用户只提供一个对比日期或未提供对比日期
Then answerStatusComparison 为 null
```

### AT-32 模拟面试评分窗口对比只呈现可解释算术差值

```gherkin
Given 当前窗口有两条已完成作答评分 4 和 5，对比窗口有两条已完成作答评分 2 和 3
When 用户提供两个完整 UTC 时间窗口查询模拟面试评分趋势
Then 两个窗口各自返回评分数、覆盖会话数、完整 1–5 分分布和平均分
And averageScoreDelta 为当前平均分减去对比平均分，即 2.0
And 页面明确该值仅描述原始练习评分变化，不代表能力等级或自动行动
And 项目、技能、证据、岗位要求、学习任务和会话状态均不被修改
When 任一窗口少于两条评分
Then 该窗口的 averageScore 和 averageScoreDelta 均为 null，并显示信息不足
```

### AT-33 多份简历版本对比只显示用户文本差异

```gherkin
Given 用户手工保存版本 A 和版本 B，且 B 比 A 新增一行项目描述并删除一行旧描述
When 用户选择 A 与 B 查询对比
Then 返回相同、新增和删除的原始非空文本行
And 不调用 AI、不判定哪个版本更好、不修改投递或任一简历版本
When 用户选择同一个版本两次
Then 返回业务规则错误且不产生副作用
```

### AT-34 WEBHOOK 渠道投递与凭据保留

```gherkin
Given 用户已配置 WEBHOOK 渠道的 webhookUrl 与 secret 但尚未启用
When 用户携带 version=0 启用 WEBHOOK 渠道
Then 渠道状态为已启用，hasCredential 为 true，响应不回显 secret
When 一条已到期提醒触发通知生成
Then 该通知带一条 channelType=WEBHOOK 的 PENDING 投递记录
When 调度器对该投递执行同步 POST 且接收端返回 2xx
Then 投递状态变为 SENT 且 sentAt 非空
When 接收端返回非 2xx 或不可达
Then 投递记录截断后的 failureReason 并递增 attempt_count，重试达上限后置 FAILED
And 站内通知始终保留，不影响其他渠道投递
When 用户对 WEBHOOK 投递调用 ack 回执端点
Then API 返回 422 且不产生数据副作用
```

### AT-35 投递渠道与简历版本效果对比仅呈现原始计数

```gherkin
Given 渠道 A 有 3 份投递（1 份 INTERVIEWING、1 份 OFFER、1 份 APPLIED），渠道 B 有 1 份投递（APPLIED）
And 简历版本 X 关联 2 份投递（1 份 OFFER），1 份投递未指定简历版本
When 用户查询投递渠道与简历版本效果对比
Then 返回 channelGroups 与 resumeVersionGroups，各组包含 applicationCount、interviewCount、offerCount 原始计数
And 渠道 A 的 applicationCount=3、interviewCount=2、offerCount=1、offerRate 为 1/3
And 渠道 B 的 applicationCount=1、offerRate 为 null 并显示信息不足
And 未指定简历版本组的 resumeVersion 为 null
And 不返回趋势结论、能力等级、归因或行动建议
And 投递、面试、简历版本、岗位、技能和证据均不被修改
When 用户提供只覆盖部分投递的日期范围
Then 计数随之更新，仍不输出趋势结论
```

### AT-36 加密备份生成、列表与下载

```gherkin
Given 用户有可导出的岗位数据
When 用户在设置页「加密备份」区输入 passphrase 并触发立即加密备份
Then 返回 201 且响应包含 algorithm=AES_256_GCM_PBKDF2、pbkdf2Iterations、dataExportId、fileName、sizeBytes
And 响应不包含 passphrase 字样，数据库不存储 passphrase 或派生密钥
And backup-dir 下生成 <id>.enc 文件，大小与 sizeBytes 一致
And backup_record 记录的 salt 为 16 字节、iv 为 12 字节
When 用户查询 GET /api/backups
Then 列表包含该记录且最新优先
When 用户请求 GET /api/backups/{id}/download
Then 返回 200 application/octet-stream 且响应体字节数等于 sizeBytes
When 用户输入 passphrase 短于 8 位
Then 返回 400 ValidationError 且不产生备份记录
When 用户下载不存在的备份 ID
Then 返回 404 NotFound
```

### AT-37 加密备份恢复（上传 .enc + passphrase，行级幂等）

```gherkin
Given 用户已生成一份加密备份（.enc 文件）且数据库有部分业务数据
When 用户清空业务数据后（或在新实例上）以 multipart 上传该 .enc 文件与正确 passphrase 调用 POST /api/backups/restore
Then 返回 200 且响应为恢复结果报告，包含 status、inserted（>0）、各表 inserted/skipped 计数
And 响应不包含 passphrase 字样，数据库不存储 passphrase 或派生密钥
And 已清空的缺失行被重新插入，原有未清空行保持不变（不覆盖）
When 用户以相同 .enc 文件与 passphrase 再次恢复
Then inserted=0 且 skippedIdentical=首次 inserted，恢复幂等
When 用户以错误 passphrase 恢复同一 .enc 文件
Then 返回 422 BusinessRuleError 且不进行任何数据插入
When 用户上传小于 28 字节（缺 salt/iv）的文件
Then 返回 422 且提示文件格式无效
When 用户未上传文件或 passphrase 短于 8 位
Then 返回 400 ValidationError
```

### AT-38 加密定时备份调度（内存武装 + cron 触发 + 重启解除武装）

```gherkin
Given 备份调度未配置或处于初始状态（enabled=false）
When 用户 PUT /api/backups/schedule 设 cronExpression="0 3 * * *" 与 enabled=true 且携带当前 version
Then 返回 200 且配置 enabled=true、armed=false、version 自增
When 用户以非法 cron 表达式（如 "not-a-cron"）更新
Then 返回 422 BusinessRuleError 且不更新配置
When 用户以旧 version 更新（版本冲突）
Then 返回 409 VersionConflict
Given enabled=true 且 cron 表达式已配置但 armed=false（未武装）
When 调度器轮询到点
Then 不生成 backup_record，last_run_status=SKIPPED_DISARMED
When 用户 POST /api/backups/schedule/arm 以合法 passphrase（>=8）武装
Then 返回 200 且 armed=true，响应不回显 passphrase
Given enabled=true 且 armed=true
When 调度器轮询到 cron 到点
Then 生成新 backup_record，last_run_status=SUCCESS、last_run_at 更新、last_backup_id 为新生成记录 ID
And 生成的备份文件可凭 passphrase 恢复（链路同 AT-37）
When 备份生成失败（如 backup-dir 不可写）
Then last_run_status=FAILED、last_run_error 有值，不产生部分 backup_record 或残留密文文件
When 应用重启（内存 passphrase 清空，armed 自动变 false）后轮询到点
Then 不生成备份，last_run_status=SKIPPED_DISARMED
And 任何时刻数据库不存储 passphrase 或派生密钥，armed 仅反映内存状态
```

### AT-39 加密备份删除（物理删除 + 落盘文件清理 + 软引用置空）

```gherkin
Given 用户已生成一份加密备份（.enc 文件）且 backup-dir 下存在对应 <id>.enc 文件
When 用户在设置页「加密备份」区点击该记录的删除按钮并确认
Then 返回 204 且备份列表不再包含该记录
And backup-dir 下 <id>.enc 文件不存在
When 用户以 GET /api/backups/{id}/download 查询该已删除备份
Then 返回 404 NotFound
When 用户以 DELETE /api/backups/{id} 删除不存在的备份 ID
Then 返回 404 NotFound
When 用户以 DELETE /api/backups/{id} 删除但未携带 X-Confirm-Permanent-Delete: true 确认头
Then 返回 400 ValidationError 且不删除任何记录或文件
Given 被删备份 id 等于 backup_schedule.last_backup_id
When 用户删除该备份
Then 返回 204 且 backup_schedule.last_backup_id 置空，armed 内存状态不受影响
When 用户以相同 Idempotency-Key 重复删除同一已删除备份（幂等回放）
Then 返回 204（幂等回放，不产生副作用）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-40 passphrase 强度评估（纯前端提示，弱口令提交由后端拒绝）

```gherkin
Given 用户在设置页「加密备份」区的任意一处 passphrase 输入框（创建备份 / 恢复备份 / 武装调度器）
When 用户输入一个弱 passphrase（如 "aaaaaaaa" 或 "password"）
Then 输入框下方实时显示强度等级为「弱」并给出改进建议列表，文案提示「弱口令将无法提交」
And 该评估在浏览器本地完成，不调用任何 API，passphrase 不离开浏览器
When 用户改进 passphrase 至满足长度（≥12）、含大小写与数字（如 "CorrectHorse42"）
Then 强度等级升为「中」（或「强」）且改进建议列表逐步消失
When 用户输入虽弱但满足 8–256 位长度限制的 passphrase 并点击提交（创建备份 / 武装调度器）
Then 后端返回 400 拒绝（弱口令 score<40），toast 显示 score 与失败规则
And passphrase 不进行加密/落盘/武装，passphrase 在提交后立即清空，强度提示随输入清空一同消失
And 任何时刻 passphrase 永不落盘、不进日志、不被评估 API 传输（无评估端点）
```

### AT-45 passphrase 强度强制门槛（创建/武装拒绝弱/中口令，要求 strong；恢复豁免）

```gherkin
Given 服务已启动
When 以弱 passphrase（如 "aaaaaaaa"，score<40）调用 POST /api/backups（创建）
Then 返回 400，错误码 VALIDATION_ERROR，message 含 "score=" 与 "≥70"
And 不生成 backup_record，不写 .enc 文件（强度评估在加密前拦截）
When 以弱 passphrase 调用 POST /api/backups/schedule/arm（武装）
Then 返回 400 VALIDATION_ERROR，armed 仍为 false（未写入内存武装）
When 以中 passphrase（如 "CorrectHorse42"，score 40–69）调用创建与武装
Then 创建返回 400（中口令同样被拒）且武装返回 400 armed 仍为 false（未达 strong 一律拒绝）
When 以强 passphrase（如 "CorrectHorse42!battery"，score≥70）调用创建与武装
Then 创建返回 201 且武装返回 200 armed=true
When 以弱 passphrase 调用 POST /api/backups/restore（恢复，上传合法 .enc + 弱 passphrase）
Then 不返回强度 400——恢复端点不强制门槛（passphrase 已与备份绑定），解密失败按 422 处理
And 任意端点的响应与日志均不含 passphrase，passphrase 永不落盘/不回显/不进日志
```

### AT-41 备份按龄批量清理（物理删除早于阈值的全部记录与密文文件）

```gherkin
Given 用户已生成多份加密备份，其中至少 2 份 created_at 早于「当前 UTC − N 天」
When 用户在设置页历史备份区输入阈值天数 N 并点击「清理」按钮
Then 弹出内联二次确认「将物理删除 N 天前的全部备份，不可恢复，是否继续？」
When 用户确认
Then 返回 200 且清理摘要 deletedCount 等于早于阈值的记录数、filesCleaned 等于清理的 .enc 文件数
And 备份列表不再包含早于阈值的记录，新于阈值的记录保留
And backup-dir 下早于阈值的 .enc 文件不存在，新于阈值的保留
When 被删集合包含 backup_schedule.last_backup_id 指向的记录
Then last_backup_id 被置空，lastBackupIdCleared=true，armed 内存状态不受影响
When 用户以 DELETE /api/backups?olderThanDays=N 但未携带 X-Confirm-Permanent-Delete: true 确认头
Then 返回 400 ValidationError 且不删除任何记录或文件
When 用户以 DELETE /api/backups（缺 olderThanDays 参数）
Then 返回 400 ValidationError
When 用户以 olderThanDays=0 或负数
Then 返回 400 ValidationError
When 阈值范围内无匹配备份（deletedCount=0）
Then 返回 200 且 deletedCount=0，不报 404（空集合法）
When 用户以相同 Idempotency-Key 重复清理（幂等回放）
Then 返回 200 且摘要与首次一致（deletedCount 复用首次缓存值，幂等回放不重新执行清理、不产生副作用）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-42 孤儿 .enc 文件扫描清理（物理删除无 backup_record 对应的落盘密文文件）

```gherkin
Given backup-dir 下存在无对应 backup_record 行的 .enc 孤儿文件（如进程在删除事务 afterCommit 文件清理前崩溃残留，或 DB 直接删行绕过服务）
And backup-dir 下同时存在有对应 backup_record 行的合法 .enc 文件
And backup-dir 下存在非 UUID 命名的 .enc 文件（如用户随手放入的无关文件 notes.enc）
When 用户在设置页点击「清理孤儿文件」按钮
Then 弹出内联二次确认「将物理删除无记录对应的孤儿 .enc 文件，不可恢复，是否继续？」
When 用户确认（POST /api/backups/orphans/clean 携带 X-Confirm-Permanent-Delete: true）
Then 返回 200 且清理摘要 orphanFiles 等于孤儿数、deletedFiles 等于实际删除数、freedBytes 大于 0
And backup-dir 下孤儿 .enc 文件不存在，合法 .enc 文件保留，非 UUID 命名的 .enc 文件保留（skippedFiles 计数）
And backup_record 表无任何变更（本端点不写记录、不联动 last_backup_id、不联动删 data_export）
When 用户未携带 X-Confirm-Permanent-Delete: true 确认头
Then 返回 400 ValidationError 且不删除任何文件
When backup-dir 下无孤儿文件
Then 返回 200 且 scannedFiles=合法文件数、orphanFiles=0、deletedFiles=0、freedBytes=0（不报 404）
When backup-dir 不存在
Then 返回 200 且 scannedFiles=0、orphanFiles=0、deletedFiles=0（不报错）
When 用户以相同 Idempotency-Key 重复清理（幂等回放）
Then 返回 200 且摘要与首次一致（幂等回放不重新执行清理、不产生额外副作用）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-43 备份按数量保留清理（保留最近 N 条，删除其余全部）

```gherkin
Given 用户已生成多份加密备份（created_at 各异，列表最新优先）
When 用户在设置页按数量保留区输入保留阈值 N 并点击「保留最近 N 条」按钮
Then 弹出内联二次确认「将物理删除除最近 N 条外的全部备份，不可恢复，是否继续？」
When 用户确认（DELETE /api/backups?keepLast=N 携带 X-Confirm-Permanent-Delete: true）
Then 返回 200 且清理摘要 deletedCount 等于超出 N 条的记录数、filesCleaned 等于清理的 .enc 文件数
And 备份列表仅保留最近 N 条（按 created_at DESC），其余记录消失
And backup-dir 下被删记录的 .enc 文件不存在，最近 N 条的 .enc 文件保留
When 被删集合包含 backup_schedule.last_backup_id 指向的记录
Then last_backup_id 被置空，lastBackupIdCleared=true，armed 内存状态不受影响
When 用户以 DELETE /api/backups?keepLast=N 但未携带 X-Confirm-Permanent-Delete: true 确认头
Then 返回 400 ValidationError 且不删除任何记录或文件
When 用户以 DELETE /api/backups（缺 olderThanDays 与 keepLast 两个参数）
Then 返回 400 ValidationError
When 用户以 olderThanDays 与 keepLast 同时出现
Then 返回 400 ValidationError
When 用户以 keepLast=0 或负数
Then 返回 400 ValidationError
When 保留阈值 N ≥ 现有备份数（deletedCount=0）
Then 返回 200 且 deletedCount=0，不报 404（空集合法，全部保留）
When 用户以相同 Idempotency-Key 重复清理（幂等回放）
Then 返回 200 且摘要与首次一致（deletedCount 复用首次缓存值，幂等回放不重新执行清理、不产生副作用）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-44 恢复后自动孤儿清理联动

```gherkin
Given backup-dir 下存在孤儿 .enc 文件（合法 UUID 命名但 backup_record 无对应 file_name，模拟 afterCommit 崩溃残留或 DB 直接删行）
And 用户持有一份合法加密备份 .enc 文件与正确 passphrase
When 用户在设置页恢复入口上传 .enc 文件并输入 passphrase，点击「恢复备份」（POST /api/backups/restore）
Then 返回 200 且响应含恢复结果摘要（inserted/skippedIdentical/skippedConflict/skippedMissingParent/failed）
And 响应含 orphanCleanSummary 字段，其中 orphanFiles ≥ 1、deletedFiles ≥ 1、freedBytes > 0
And backup-dir 下孤儿 .enc 文件已被物理删除，合法（有 backup_record 对应）的 .enc 文件保留
And backup_record 无任何变更（恢复不写 backup_record，自动清理只删文件不动 DB）
When 恢复前 backup-dir 下无孤儿 .enc 文件（全部为合法备份或非 UUID 文件）
Then 返回 200 且 orphanCleanSummary 为全 0 摘要（scannedFiles 反映扫描总数，orphanFiles=0、deletedFiles=0）
When 用户上传错误的 passphrase 或损坏的 .enc 文件（GCM 认证失败）
Then 返回 422 且不触发孤儿清理（响应不含 orphanCleanSummary 或恢复失败不产生清理副作用）
When 恢复时 backup-dir 不存在
Then 返回 200 且 orphanCleanSummary.scannedFiles=0、orphanFiles=0（自动清理不报错，恢复结果不受影响）
When backup-dir 下存在非 UUID 命名的 .enc 文件（如用户随手放入的无关文件）
Then 自动清理跳过该文件（计入 orphanCleanSummary.skippedFiles，不删除）
When 用户以相同 Idempotency-Key 重复恢复（幂等回放）
Then 返回 200 且响应（含 orphanCleanSummary）与首次一致（幂等回放不重新执行恢复与清理、不产生额外副作用）
And 自动清理失败时不影响已提交的恢复结果（恢复已成功，orphanCleanSummary 反映尽力清理的结果）
And 恢复端点无需 X-Confirm-Permanent-Delete 确认头（恢复非销毁性操作）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-46 恢复后弱/中口令重设提示（恢复成功后内存评估未达 strong 置 recommended；恢复仍豁免门槛）

```gherkin
Given 用户持有一份用弱 passphrase（如 "aaaaaaaa"，score<40）创建的合法加密备份 .enc 文件与正确 passphrase
When 用户在设置页恢复入口上传该 .enc 文件并输入弱 passphrase，点击「恢复备份」（POST /api/backups/restore）
Then 返回 200 且响应含 passphraseResetRecommended=true（恢复本身不拒绝，端点仍豁免强度门槛，仅提示）
And 恢复成功 toast 追加展示「此备份口令未达强，建议重新创建备份时设置更强口令」
Given 用户持有一份用中 passphrase（如 "CorrectHorse42"，score 40–69）创建的合法加密备份 .enc 文件与正确 passphrase
When 用户上传该 .enc 文件并输入中 passphrase 恢复
Then 返回 200 且响应含 passphraseResetRecommended=true（中口令同样建议重设为强）
When 用户用强 passphrase（如 "CorrectHorse42!battery"，score≥70）创建的备份恢复
Then 返回 200 且响应 passphraseResetRecommended=false，恢复成功 toast 不追加重设提示
When 用户上传错误的 passphrase 或损坏的 .enc 文件（GCM 认证失败）
Then 返回 422 且不进行强度评估（响应不含 passphraseResetRecommended，不产生提示副作用）
When 用户以相同 Idempotency-Key 重复恢复（幂等回放）
Then 返回 200 且响应（含 passphraseResetRecommended）与首次一致（幂等回放不重新评估、不产生副作用）
When 用户调用 POST /api/data-imports/restore（标准数据恢复）
Then 响应 passphraseResetRecommended 为 null（标准恢复不评估 passphrase）
And 任意端点的响应与日志均不含 passphrase，passphrase 永不落盘/不回显/不进日志
And 响应不回显 passphrase 的 score 或 level 等派生信息（仅返回布尔 passphraseResetRecommended）
```

### AT-47 孤儿文件清理审计日志（清理每个孤儿文件后向 audit_log 追加一条；best-effort 不阻塞）

```gherkin
Given backup-dir 下存在 2 个无 backup_record 对应的合法 UUID 命名孤儿 .enc 文件，且 backup_record 有一条合法记录对应一个 .enc 文件
When 用户在设置页孤儿清理入口二次确认后点击「清理孤儿文件」（POST /api/backups/orphans/clean，携带 X-Confirm-Permanent-Delete: true）
Then 返回 200 且 BackupOrphanCleanSummary 的 deletedFiles=2、freedBytes>0、skippedFiles=0（合法文件保留）
And audit_log 表新增 2 条记录，每条 resource_type=BACKUP_FILE、resource_id=被删文件名去 .enc 的 UUID、action=BACKUP_ORPHAN_CLEANED、before/after_snapshot_json 为 null、reason 含 freedBytes、occurred_at 为非空 UTC ISO
And 响应不回显审计信息（BackupOrphanCleanSummary 不含审计字段），passphrase 不落库/不回显/不进日志
When backup-dir 下无孤儿（仅有合法 .enc 文件或目录不存在）
Then 返回 200 且 deletedFiles=0，audit_log 未新增任何记录（空操作无可追溯）
Given backup-dir 下还存在 1 个非 UUID 命名的 .enc 文件（如 notes.enc）
When 用户点击「清理孤儿文件」
Then 非 UUID 文件被跳过计入 skippedFiles=1 且不删除，audit_log 不为被跳过的文件新增记录（只为被删孤儿写）
When 用户恢复一份加密备份（POST /api/backups/restore）且 backup-dir 存在孤儿 .enc 文件
Then 恢复成功且联动 cleanOrphans 删除孤儿后，audit_log 同样为每个被删孤儿新增一条 BACKUP_ORPHAN_CLEANED 记录
When 用户以相同 Idempotency-Key 重复调用孤儿清理或恢复（幂等回放）
Then 返回首次缓存的摘要，audit_log 不新增重复记录（幂等回放不重新执行清理、不重复写审计）
```

### AT-48 强制 strong 口令门槛升级（创建/武装要求 score≥70；恢复提示扩到 weak+fair）

```gherkin
Given 服务已启动，且 backup-dir 与 backup_record 已清空
When 以中 passphrase（如 "CorrectHorse42"，score 40–69）调用 POST /api/backups（创建）
Then 返回 400，错误码 VALIDATION_ERROR，message 含 "score=" 与 "≥70"（中口令不再放行，要求 strong）
And 不生成 backup_record，不写 .enc 文件（加密前拦截）
And backup_record 行数为 0，backup-dir 下无 .enc 文件
When 以中 passphrase 调用 POST /api/backups/schedule/arm（武装）
Then 返回 400 VALIDATION_ERROR，message 含 "≥70"，armed 仍为 false（未写入内存武装）
When 以强 passphrase（如 "CorrectHorse42!battery"，score≥70）调用创建与武装
Then 创建返回 201 且武装返回 200 armed=true
When 用户持有一份用中 passphrase（score 40–69）创建的合法加密备份 .enc 文件与正确中 passphrase
And 上传该 .enc 文件并以中 passphrase 调用 POST /api/backups/restore（恢复）
Then 返回 200 且响应 passphraseResetRecommended=true（中口令同样建议重设为强，恢复端点豁免门槛仅提示）
And 恢复端点不返回强度 400（豁免门槛，passphrase 已与备份绑定），解密成功即恢复
And 任意端点的响应与日志均不含 passphrase，passphrase 永不落盘/不回显/不进日志
And 响应不回显 score 或 level 等派生信息（恢复端点仅返回布尔 passphraseResetRecommended）
```

### AT-49 孤儿清理审计日志查询（GET /backups/orphans/audit 只读分页查询 BACKUP_ORPHAN_CLEANED）

```gherkin
Given backup-dir 下存在 2 个无 backup_record 对应的合法 UUID 命名孤儿 .enc 文件
When 用户在设置页孤儿清理入口二次确认后点击「清理孤儿文件」（POST /api/backups/orphans/clean，携带 X-Confirm-Permanent-Delete: true）
Then 返回 200 且 deletedFiles=2，audit_log 表新增 2 条 BACKUP_ORPHAN_CLEANED 记录
When 用户调用 GET /api/backups/orphans/audit?page=1&pageSize=20（只读，不携带确认头与幂等键）
Then 返回 200 且 items 含 2 条记录，每条 id 为非空 UUID、resourceId 为被删文件名去 .enc 的 UUID、action 为 BACKUP_ORPHAN_CLEANED、reason 含 freedBytes、occurredAt 为非空 UTC ISO
And total=2、page=1、pageSize=20、totalPages=1
And 响应不含 passphrase、不含 resourceType（固定省略）、不含 before/afterSnapshotJson（恒 null 省略）
And 记录按 occurred_at DESC 排序（最新优先）
When backup-dir 无孤儿且无历史审计记录时调用 GET /api/backups/orphans/audit?page=1
Then 返回 200 且 items=[]、total=0、totalPages=0
When 用户恢复一份加密备份（POST /api/backups/restore）且 backup-dir 存在孤儿 .enc 文件
Then 恢复联动 cleanOrphans 删除孤儿后，GET /api/backups/orphans/audit 同样返回这些删除的 BACKUP_ORPHAN_CLEANED 记录（不区分独立/恢复来源）
When 用户调用 GET /api/backups/orphans/audit?page=1&pageSize=1
Then 返回 200 且 items 仅 1 条（最新一条）、total=2、totalPages=2
When 用户传入非法分页参数（page=0 或 pageSize=0 或 pageSize=101）
Then 返回 400 VALIDATION_ERROR
```

### AT-50 全量审计日志查询（GET /audit-logs 只读分页查询，可按 action/resourceType 过滤）

```gherkin
Given audit_log 表存在多种 action 的记录：1 条二次投递确认（SECONDARY_APPLICATION_CONFIRMED，resourceType=APPLICATION）、2 条需求变更（REQUIREMENT_MERGED 与 REQUIREMENT_UPDATED，resourceType=JOB_REQUIREMENT）、孤儿清理产生的 BACKUP_ORPHAN_CLEANED 记录（resourceType=BACKUP_FILE）
When 用户调用 GET /api/audit-logs?page=1&pageSize=20（只读，不携带确认头与幂等键，不传过滤参数）
Then 返回 200 且 items 含全部 action 类型记录，每条 id 为非空 UUID、resourceType 为非空、resourceId 为非空、action 为已知 action 之一、reason 为非空、occurredAt 为非空 UTC ISO
And total=记录总数、page=1、pageSize=20、totalPages=1（或按数量向上取整）
And 响应不含 passphrase、不含 before/afterSnapshotJson（恒 null 省略）
And 记录按 occurred_at DESC 排序（最新优先）
When 用户调用 GET /api/audit-logs?action=BACKUP_ORPHAN_CLEANED
Then 返回 200 且 items 仅含 BACKUP_ORPHAN_CLEANED 记录，每条 resourceType=BACKUP_FILE
And 该记录集与 GET /api/backups/orphans/audit 返回的记录 id 集合一致（字段含 resourceType，其余语义一致）
When 用户调用 GET /api/audit-logs?resourceType=APPLICATION
Then 返回 200 且 items 仅含 SECONDARY_APPLICATION_CONFIRMED 记录，每条 resourceType=APPLICATION
When 用户调用 GET /api/audit-logs?action=REQUIREMENT_MERGED&resourceType=JOB_REQUIREMENT
Then 返回 200 且 items 仅含 REQUIREMENT_MERGED 记录，每条 resourceType=JOB_REQUIREMENT
When 用户调用 GET /api/audit-logs?action=NONEXISTENT_ACTION
Then 返回 200 且 items=[]、total=0、totalPages=0（过滤无匹配，不报错）
When audit_log 表为空时调用 GET /api/audit-logs?page=1
Then 返回 200 且 items=[]、total=0、totalPages=0
When 用户调用 GET /api/audit-logs?page=1&pageSize=1
Then 返回 200 且 items 仅 1 条（最新一条）、total=记录总数、totalPages=按总数向上取整
When 用户传入非法分页参数（page=0 或 pageSize=0 或 pageSize=101）
Then 返回 400 VALIDATION_ERROR
```

### AT-51 备份删除/批量清理审计日志（单条删除/按龄清理/按数量保留清理每被删记录写一条；best-effort 不阻塞、不回显；全量查询可查）

```gherkin
Given 服务已启动且 backup_record 与 audit_log 已清空，且已生成 1 份加密备份
When 用户以 DELETE /api/backups/{backupId} 携带 X-Confirm-Permanent-Delete: true 删除该备份（携带唯一 Idempotency-Key）
Then 返回 204 且 backup_record 该行消失、落盘 .enc 文件被清理
And audit_log 表新增 1 条记录，resource_type=BACKUP_RECORD、resource_id=被删备份 id、action=BACKUP_DELETED、before/after_snapshot_json 为 null、reason 含可读说明、occurred_at 为非空 UTC ISO
And 响应不回显审计信息（删除无响应体），passphrase 不落库/不回显/不进日志
When 无匹配记录删除（如 deleteById 返回 0 的并发场景或空批量集合）
Then audit_log 不新增任何记录（空操作无可追溯）
Given 用户已生成 3 份加密备份（created_at 各异，2 份早于阈值，1 份最新）
When 用户以 DELETE /api/backups?olderThanDays=N 携带 X-Confirm-Permanent-Delete: true 按龄清理（删除 2 份旧备份）
Then 返回 200 且 deletedCount=2
And audit_log 表新增 2 条 action=BACKUP_PURGED_BY_AGE 记录，每条 resource_type=BACKUP_RECORD、resource_id 对应一个被删备份 id、reason 含可读说明、occurred_at 为非空 UTC ISO
And 按龄清理无匹配记录（deletedCount=0）时 audit_log 不新增任何记录
Given 用户已生成 5 份加密备份（created_at 各异，列表最新优先）
When 用户以 DELETE /api/backups?keepLast=2 携带 X-Confirm-Permanent-Delete: true 按数量保留（删除除最近 2 条外的 3 份）
Then 返回 200 且 deletedCount=3
And audit_log 表新增 3 条 action=BACKUP_PURGED_BY_COUNT 记录，每条 resource_type=BACKUP_RECORD、resource_id 对应一个被删备份 id
When 用户以相同 Idempotency-Key 重复单条删除或批量清理（幂等回放）
Then 返回首次缓存的响应（204 或相同摘要），audit_log 不新增重复记录（幂等回放不重新执行、不重复写审计）
When 用户调用 GET /api/audit-logs?action=BACKUP_DELETED
Then 返回 200 且 items 仅含 BACKUP_DELETED 记录，每条 resourceType=BACKUP_RECORD
When 用户调用 GET /api/audit-logs?resourceType=BACKUP_RECORD
Then 返回 200 且 items 仅含 BACKUP_DELETED/BACKUP_PURGED_BY_AGE/BACKUP_PURGED_BY_COUNT 三类记录，每条 resourceType=BACKUP_RECORD
And 任何时刻数据库不存储 passphrase 或派生密钥，审计记录不含 passphrase
```

### AT-52 审计日志时间范围过滤（GET /audit-logs 加可选 from/to，ISO UTC，含边界，可与 action/resourceType 组合）

```gherkin
Given audit_log 表存在多条 occurred_at 各异的记录（如 2026-09-01T08:00:00Z / 2026-09-03T10:00:00Z / 2026-09-05T12:00:00Z / 2026-09-07T14:00:00Z）
When 用户调用 GET /api/audit-logs?from=2026-09-03T00:00:00Z
Then 返回 200 且 items 仅含 occurred_at >= from 的记录（2026-09-03/05/07 三条），每条 occurredAt 非空 UTC ISO
And total=符合范围的记录数、按 occurred_at DESC 排序
When 用户调用 GET /api/audit-logs?to=2026-09-05T23:59:59Z
Then 返回 200 且 items 仅含 occurred_at <= to 的记录（2026-09-01/03/05 三条）
When 用户调用 GET /api/audit-logs?from=2026-09-03T00:00:00Z&to=2026-09-05T23:59:59Z
Then 返回 200 且 items 仅含 occurred_at 在 [from, to] 范围内的记录（2026-09-03/05 两条）
And 边界含等号：from=2026-09-03T10:00:00Z 时返回的记录含 occurred_at 恰为该值的记录
When 用户调用 GET /api/audit-logs?from=2026-09-03T00:00:00Z&action=BACKUP_DELETED
Then 返回 200 且 items 仅含同时满足 occurred_at >= from 且 action=BACKUP_DELETED 的记录
When 用户调用 GET /api/audit-logs?from=2026-09-07T00:00:00Z&to=2026-09-01T00:00:00Z（from > to）
Then 返回 200 且 items=[]、total=0（合法但无匹配，不报 400）
When 用户调用 GET /api/audit-logs?from=2026-09-03（非法 ISO 格式，缺时间部分）
Then 返回 400 VALIDATION_ERROR
When 用户调用 GET /api/audit-logs?from=not-a-date
Then 返回 400 VALIDATION_ERROR
When 用户调用 GET /api/audit-logs?to=2026/09/05
Then 返回 400 VALIDATION_ERROR
When 用户调用 GET /api/audit-logs?from=2026-09-03T10:00:00Z&resourceType=BACKUP_RECORD
Then 返回 200 且 items 仅含同时满足时间范围与 resourceType=BACKUP_RECORD 的记录
When 用户不传 from 与 to（仅 action/resourceType 或无过滤）
Then 返回 200 且不过滤时间范围（既有行为不变，向后兼容）
And 响应不含 passphrase、不含 before/afterSnapshotJson（恒 null 省略）
And 任何时刻数据库不存储 passphrase 或派生密钥
```

### AT-53 密钥轮换（POST /backups/{backupId}/rotate-key 就地重加密：旧口令解密 → 新口令重新加密，备份 id 与明文数据不变）

```gherkin
Given 用户已用强口令（score>=70）创建一条加密备份 backup_record（id=X），记其 salt_old/iv_old/size_old
And backup_schedule.last_backup_id=X（若存在该软引用）
When 用户调用 POST /api/backups/X/rotate-key（携带 Idempotency-Key，body {oldPassphrase=创建口令, newPassphrase=另一强口令}）
Then 返回 200 且响应为更新后的 BackupRecordResponse，id=X、createdAt 不变、algorithm=AES_256_GCM_PBKDF2、pbkdf2Iterations=100000 不变、dataExportId 不变、fileName 不变
And backup_record 行的 salt != salt_old、iv != iv_old、size_bytes 反映新密文大小（可能 != size_old）
And 落盘 .enc 文件已用新口令+新 salt/iv 重新加密（旧密文被覆盖），新 salt/iv 与 DB 一致
And 临时文件已被清理（不存在非 .enc 后缀残留）
When 用 oldPassphrase 经 POST /api/backups/restore 恢复同一 .enc 文件
Then 返回 422（passphrase 错误或备份文件损坏，GCM 认证失败）——旧口令已不可解密
When 用 newPassphrase 经 POST /api/backups/restore 恢复同一 .enc 文件
Then 返回 200 且恢复成功（inserted/skipped 与首次恢复语义一致），证明明文数据未变
When 用 newPassphrase 经 GET /api/backups/X/download 下载
Then 返回 200 且文件可下载
And last_backup_id 仍=X（id 不变，软引用不受影响）
And 审计日志有一条 action=BACKUP_KEY_ROTATED、resourceType=BACKUP_RECORD、resourceId=X、reason 含轮换说明、occurredAt 非空
And 任何时刻 DB 不存储 passphrase 或派生密钥，响应不含 passphrase/salt/iv/score/level
When 用户调用 POST /api/backups/X/rotate-key（body {oldPassphrase=错误口令, newPassphrase=强口令}）
Then 返回 422（passphrase 错误或备份文件损坏）
And backup_record 的 salt/iv/size_bytes 均未变（仍为轮换后的值），.enc 文件未变，审计无新增
When 用户调用 POST /api/backups/X/rotate-key（body {oldPassphrase=正确旧口令, newPassphrase=弱口令 score<70}）
Then 返回 400 VALIDATION_ERROR，message 含 score 与 >=70
And backup_record 的 salt/iv/size_bytes 均未变，.enc 文件未变，审计无新增（fail fast：强度不足时不解密不落盘不写审计）
When 用户调用 POST /api/backups/不存在ID/rotate-key（body {oldPassphrase=任意, newPassphrase=强口令}）
Then 返回 404
When 用户携带同一 Idempotency-Key 重复调用 POST /api/backups/X/rotate-key
Then 返回首次缓存的相同响应，backup_record 的 salt/iv 不再变化，审计不重复写入（幂等回放不重新执行）
When 用户调用 POST /api/backups/X/rotate-key（body {oldPassphrase=正确口令, newPassphrase=与old相同}）
Then 返回 200（相同口令不拒绝，仍刷新 salt/iv 为新值，合法重加密）
And 审计有一条新 BACKUP_KEY_ROTATED 记录（叠加既有）
When GET /api/audit-logs?action=BACKUP_KEY_ROTATED
Then 返回 200 且 items 含上述轮换审计记录
When GET /api/audit-logs?resourceType=BACKUP_RECORD
Then 返回 200 且 items 含 BACKUP_DELETED/BACKUP_PURGED_BY_AGE/BACKUP_PURGED_BY_COUNT/BACKUP_KEY_ROTATED 各类（若均有写入）
```

### AT-54 审计日志导出（GET /audit-logs/export 即时下载 CSV/JSON，复用 action/resourceType/from/to 过滤）

```gherkin
Given audit_log 表有多条不同 action/resourceType/occurred_at 的记录（覆盖至少 BACKUP_ORPHAN_CLEANED、REQUIREMENT_MERGED、BACKUP_DELETED 三类）
When 用户调用 GET /api/audit-logs/export?format=json（无过滤参数）
Then 返回 200 且 Content-Type 为 application/json，Content-Disposition 含 attachment 与 audit-logs- 且 .json
And 响应体为 JSON 数组，每元素含 id/resourceType/resourceId/action/reason/occurredAt，省略 beforeSnapshotJson/afterSnapshotJson
And 响应不含 passphrase
And 数组按 occurredAt DESC 排序（最新优先），长度等于 GET /api/audit-logs（逐页累加）的全量记录数
When 用户调用 GET /api/audit-logs/export?format=csv（无过滤参数）
Then 返回 200 且 Content-Type 为 text/csv，Content-Disposition 含 attachment 与 .csv
And 响应体以 UTF-8 BOM 开头，首行为表头 id,resourceType,resourceId,action,reason,occurredAt
And 每条记录一行，行尾为 CRLF，字段用 RFC 4180 转义（含逗号/引号/换行的字段用双引号包裹，内部双引号转义为两个双引号）
And 行数（不含表头）等于 JSON 导出的数组长度
When 用户调用 GET /api/audit-logs/export?format=json&action=BACKUP_ORPHAN_CLEANED
Then 返回 200 且数组每条 action=BACKUP_ORPHAN_CLEANED（过滤生效，少于全量）
When 用户调用 GET /api/audit-logs/export?format=csv&resourceType=BACKUP_RECORD
Then 返回 200 且每行（除表头）的 resourceType 列为 BACKUP_RECORD
When 用户调用 GET /api/audit-logs/export?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
Then 返回 200 且 JSON 数组（默认 format=json）每条 occurredAt 落在 [from,to] 闭区间内
When 用户调用 GET /api/audit-logs/export?format=json&action=NONEXISTENT
Then 返回 200 且响应体为 [] （空结果不报 400）
When 用户调用 GET /api/audit-logs/export?format=csv&action=NONEXISTENT
Then 返回 200 且响应体为 BOM + 表头行 + 无数据行（空结果仅表头）
When audit_log 表为空时调用 GET /api/audit-logs/export?format=json
Then 返回 200 且响应体为 []
When audit_log 表为空时调用 GET /api/audit-logs/export?format=csv
Then 返回 200 且响应体为 BOM + 表头行（无数据行）
When 用户调用 GET /api/audit-logs/export?format=xml
Then 返回 400 VALIDATION_ERROR（format 非 csv/json）
When 用户调用 GET /api/audit-logs/export?from=not-a-date
Then 返回 400（from 非 ISO-8601 UTC）
When 用户调用 GET /api/audit-logs/export?to=2026/09/05
Then 返回 400（to 非 ISO-8601 UTC）
When 用户调用 GET /api/audit-logs/export?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z
Then 返回 200 且 JSON 为 [] （from>to 空结果不报 400）
And 全程后端不写文件系统（无 data/exports 下审计导出文件残留），不创建 data_export 记录，不动 audit_log/backup_record 表
```

### AT-55 audit_log occurred_at 二级索引（V26 迁移补索引，查询/导出排序与范围过滤行为不变）

```gherkin
Given Flyway 已执行 V26 迁移（V1→V26 成功，schema_version=26）
When 检查 audit_log 表索引（PRAGMA index_list('audit_log')）
Then 存在名为 idx_audit_log_occurred_at 的索引，且其建索引列为 occurred_at
And 该索引为非唯一索引（audit_log 允许多条相同 occurred_at）
Given audit_log 表有多条 occurred_at 递增的记录
When 用户调用 GET /api/audit-logs?page=1&pageSize=20
Then 返回 200 且按 occurred_at DESC 排序（索引可反向扫描，结果与加索引前一致）
When 用户调用 GET /api/audit-logs?from=2026-09-03T00:00:00Z&to=2026-09-05T23:59:59Z
Then 返回 200 且仅含 occurred_at 落在 [from,to] 闭区间的记录（范围过滤行为不变）
When 用户调用 GET /api/audit-logs/export?format=json
Then 返回 200 且 JSON 数组按 occurredAt DESC 排序（导出行为不变）
When 用户调用 GET /api/audit-logs/export?format=csv&from=2026-09-03T00:00:00Z
Then 返回 200 且每行 occurredAt 列 >= from（范围过滤行为不变）
And 全程查询与导出结果与加索引前完全一致（索引为性能优化，不改变语义结果）
```

### AT-56 批量密钥轮换（POST /backups/rotate-keys 逐条就地重加密：同一旧口令解密 → 同一新口令重新加密多个备份，逐条独立事务，部分成功不阻塞其他）

```gherkin
Given 用户已用强口令（score>=70）创建三条加密备份（id=A/B/C，均用同一 OLD_PASSPHRASE 加密），记各 salt_old/iv_old/size_old
When 用户调用 POST /api/backups/rotate-keys（携带 Idempotency-Key，body {backupIds=[A,B,C], oldPassphrase=OLD_PASSPHRASE, newPassphrase=另一强口令 NEW_PASSPHRASE}）
Then 返回 200 且响应为 RotateKeysSummary，total=3、rotated=3、failed=0
And results 数组含 3 项，每项 {backupId, status=SUCCESS}（reason 省略），顺序与去重后 backupIds 一致
And backup_record A/B/C 的 salt != salt_old、iv != iv_old、size_bytes 反映新密文大小
And 落盘 .enc 文件均已用 NEW_PASSPHRASE + 各自新 salt/iv 重新加密（旧密文被覆盖），临时文件已清理
And 审计日志有三条 action=BACKUP_KEY_ROTATED、resourceType=BACKUP_RECORD、resourceId 分别为 A/B/C、reason 含轮换说明、occurredAt 非空（每成功条一条）
When 用 OLD_PASSPHRASE 经 POST /api/backups/restore 恢复 A 的 .enc 文件
Then 返回 422（旧口令已不可解密）
When 用 NEW_PASSPHRASE 经 POST /api/backups/restore 恢复 A 的 .enc 文件
Then 返回 200 且恢复成功（明文数据未变）
And last_backup_id 不受影响（A/B/C id 不变）
And 任何时刻 DB 不存储 passphrase 或派生密钥，响应不含 passphrase/salt/iv/score/level
Given 用户已用强口令创建三条备份（id=A/B/C，A/B 用 OLD_PASSPHRASE，C 用另一口令 WRONG）
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[A,B,C], oldPassphrase=OLD_PASSPHRASE, newPassphrase=NEW_PASSPHRASE}）
Then 返回 200 且 RotateKeysSummary total=3、rotated=2、failed=1
And results 含 A/B 的 status=SUCCESS、C 的 status=FAILED 且 reason 含 passphrase 错误或备份文件损坏
And A/B 的 salt/iv 已更新为新值，C 的 salt/iv/size_bytes 未变（无副作用，逐条独立事务）
And 审计日志仅对 A/B 各写一条 BACKUP_KEY_ROTATED，C 无审计（失败条不写）
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[X,Y,Z 均用错误旧口令], oldPassphrase=OLD_PASSPHRASE, newPassphrase=NEW_PASSPHRASE}）
Then 返回 200 且 total=3、rotated=0、failed=3（全部失败也 200，摘要反映结果）
And 三条备份的 salt/iv/size_bytes 均未变，审计无新增
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[A,B,C], oldPassphrase=OLD_PASSPHRASE, newPassphrase=弱口令 score<70}）
Then 返回 400 VALIDATION_ERROR，message 含 score 与 >=70（fail fast：newPassphrase 弱不处理任何备份）
And A/B/C 的 salt/iv/size_bytes 均未变，.enc 文件未变，审计无新增（不解密不落盘不写审计）
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[不存在ID], oldPassphrase=任意, newPassphrase=强口令}）
Then 返回 200 且 total=1、rotated=0、failed=1，该条 status=FAILED reason 含记录不存在
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[], oldPassphrase=OLD, newPassphrase=NEW}）
Then 返回 400（backupIds 空数组非法）
When 用户调用 POST /api/backups/rotate-keys（body {backupIds=[A,A,A], oldPassphrase=OLD, newPassphrase=NEW}）
Then 返回 200 且 total=1（去重后只处理一次 A）、rotated=1、failed=0
When 用户携带同一 Idempotency-Key 重复调用 POST /api/backups/rotate-keys
Then 返回首次缓存的相同摘要，salt/iv 不再变化，审计不重复写入（幂等回放不重新执行）
When GET /api/audit-logs?action=BACKUP_KEY_ROTATED
Then 返回 200 且 items 含上述批量轮换审计记录
```

## 8. 发布门槛

- AT-01 至 AT-56 必须全部通过；状态转换和数据安全场景不得以人工口头验证替代自动化测试。
- 后端集成测试必须在临时 SQLite 数据库中执行迁移；前端端到端测试必须覆盖 AT-01、AT-09、AT-11、AT-15、AT-18、AT-20。
- 合并前运行 OpenAPI 引用校验、数据库迁移测试、后端测试和前端静态检查；任一失败不得发布。
