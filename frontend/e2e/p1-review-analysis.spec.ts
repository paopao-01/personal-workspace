import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

function formField(page: Page, label: string) {
  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return page
    .locator('.form-field')
    .filter({
      has: page.locator('.form-label').filter({
        hasText: new RegExp(`^${escapedLabel}\\*?$`),
      }),
    })
    .first()
}

async function fillField(page: Page, label: string, value: string) {
  const input = formField(page, label).locator('input, textarea').first()
  await input.fill(value)
}

async function createCompletedInterview(
  request: APIRequestContext,
  suffix: number,
  options: { startsAt: string; result: 'PASSED' | 'FAILED' },
) {
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1-review-analysis-job-${suffix}-${crypto.randomUUID()}` },
    data: {
      companyName: `复盘分析科技-${suffix}`,
      title: 'P1 复盘分析岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E P1 复盘分析',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-p1-review-analysis-app-${suffix}-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: '2026-05-01',
      channel: 'E2E 渠道-复盘分析',
      nextAction: '完成复盘',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const transitionResponse = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-p1-review-analysis-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(transitionResponse.ok(), `${targetStatus} returned ${transitionResponse.status()}`).toBe(true)
    application = (await transitionResponse.json()) as { id: string; version: number }
  }

  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-p1-review-analysis-interview-${suffix}-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `复盘分析面试-${suffix}`,
      startsAt: options.startsAt,
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)
  let interview = (await interviewResponse.json()) as { id: string; version: number }

  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-p1-review-analysis-complete-${suffix}-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: options.result },
  })
  expect(completeResponse.ok(), `POST complete returned ${completeResponse.status()}`).toBe(true)
  interview = (await completeResponse.json()) as { id: string; version: number }
  return interview
}

test('P1 full review fields persist and cross-interview analysis aggregates reviews', async ({ page, request }) => {
  const suffix = Date.now()
  const interviewA = await createCompletedInterview(request, suffix, {
    startsAt: '2025-05-10T10:00:00Z',
    result: 'PASSED',
  })
  const interviewB = await createCompletedInterview(request, suffix + 1, {
    startsAt: '2025-05-20T14:00:00Z',
    result: 'FAILED',
  })
  const redisKpName = `Redis 缓存-${suffix}`
  const projectKpName = `项目表达-${suffix}`
  const redisKpId = (
    await (
      await request.post('/api/knowledge-points', {
        headers: { 'Idempotency-Key': `e2e-p1-review-analysis-kp-redis-${crypto.randomUUID()}` },
        data: { name: redisKpName },
      })
    ).json()
  ).id as string

  // 面试 B：纯 API 造完整复盘数据（FAILED + 2 题：1 完全答出 Redis、1 部分答出项目表达）
  const reviewB = (await (
    await request.put(`/api/interviews/${interviewB.id}/review`, {
      headers: { 'Idempotency-Key': `e2e-p1-review-analysis-review-b-${crypto.randomUUID()}` },
      data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
    })
  ).json()) as { id: string }
  await request.post(`/api/reviews/${reviewB.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-p1-review-analysis-q-b1-${crypto.randomUUID()}` },
    data: {
      content: `Redis 持久化机制对比-${suffix}`,
      answerStatus: 'FULLY_ANSWERED',
      type: '技术',
      knowledgePointIds: [redisKpId],
    },
  })
  const projectKpId = (
    await (
      await request.post('/api/knowledge-points', {
        headers: { 'Idempotency-Key': `e2e-p1-review-analysis-kp-project-${crypto.randomUUID()}` },
        data: { name: projectKpName },
      })
    ).json()
  ).id as string
  await request.post(`/api/reviews/${reviewB.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-p1-review-analysis-q-b2-${crypto.randomUUID()}` },
    data: {
      content: `讲讲你的项目经历-${suffix}`,
      answerStatus: 'PARTIALLY_ANSWERED',
      type: '项目',
      knowledgePointIds: [projectKpId],
    },
  })

  // 面试 A：UI 走完整复盘路径（展开附加字段 + 保存草稿 + 创建问题）
  await page.goto(`/interviews/${interviewA.id}/review`)
  await expect(page.getByRole('heading', { name: '快速复盘' })).toBeVisible()
  await page.getByRole('button', { name: '展开完整复盘字段' }).click()
  await fillField(page, '面试官关注点', '系统设计深度与取舍')
  await fillField(page, '岗位意愿', '较高，方向匹配')
  await fillField(page, '项目表达与真实性风险', '量化结果不足，追问细节时表达含糊')

  await formField(page, '面试结果').locator('select').selectOption('PASSED')
  await formField(page, '回答状态').locator('select').selectOption('UNANSWERED')
  await fillField(page, '面试问题', `Redis 缓存穿透怎么解决？-${suffix}`)
  await fillField(page, '关联知识点', redisKpName)

  const saveReviewResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/interviews/${interviewA.id}/review`) &&
    response.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存复盘' }).click()
  expect((await saveReviewResponse).status()).toBe(200)
  await expect(page.getByText('快速复盘已保存')).toBeVisible()

  // 刷新后完整复盘字段应持久化
  await page.reload()
  await page.getByRole('button', { name: '展开完整复盘字段' }).click()
  await expect(formField(page, '面试官关注点').locator('textarea')).toHaveValue('系统设计深度与取舍')
  await expect(formField(page, '项目表达与真实性风险').locator('textarea')).toHaveValue(
    '量化结果不足，追问细节时表达含糊',
  )

  // 逐题编辑完整复盘字段
  await page.getByRole('button', { name: '编辑详情' }).first().click()
  await fillField(page, '我的回答', '提到布隆过滤器，但漏了空值缓存')
  await fillField(page, '参考答案', '布隆过滤器 + 空值缓存 + 接口层校验')
  await formField(page, '难度').locator('select').selectOption('4')
  await fillField(page, '错误原因', '只背了概念，缺少场景化记忆')
  await fillField(page, '改进方案', '整理缓存异常场景清单并逐条演练')
  const updateQuestionResponse = page.waitForResponse((response) =>
    response.url().includes('/api/interview-questions/') &&
    response.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存问题详情' }).click()
  expect((await updateQuestionResponse).status()).toBe(200)
  await expect(page.getByText('问题详情已保存')).toBeVisible()

  await page.reload()
  await page.getByRole('button', { name: '编辑详情' }).first().click()
  await expect(formField(page, '我的回答').locator('textarea')).toHaveValue('提到布隆过滤器，但漏了空值缓存')
  await expect(formField(page, '难度').locator('select')).toHaveValue('4')

  // 跨面试聚合：用专属 2025 年 5 月窗口隔离本用例数据
  await page.goto('/reviews/analysis')
  await fillField(page, '开始日期', '2025-05-01')
  await fillField(page, '结束日期', '2025-05-31')
  const analysisResponse = page.waitForResponse((response) =>
    response.url().includes('/api/reviews/analysis') &&
    response.request().method() === 'GET',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await analysisResponse).status()).toBe(200)

  await expect(page.getByText('完全答出率 1/3')).toBeVisible()
  await expect(page.getByText(`Redis 缓存-${suffix}`)).toBeVisible()
  const redisRow = page.locator('.requirement-row').filter({ hasText: redisKpName })
  await expect(redisRow).toContainText('共 2 道题')
  await expect(redisRow).toContainText('完全答出 1 道')
  await expect(redisRow).toContainText('待巩固 1 道')
  const projectRow = page.locator('.requirement-row').filter({ hasText: projectKpName })
  await expect(projectRow).toContainText('待巩固 1 道')
  await expect(page.getByText('通过 1 / 未通过 1 / 暂不确认 0')).toBeVisible()

  // 样本外窗口：空状态
  await fillField(page, '开始日期', '2030-01-01')
  await fillField(page, '结束日期', '2030-01-31')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText('当前范围内暂无复盘记录')).toBeVisible()
})
