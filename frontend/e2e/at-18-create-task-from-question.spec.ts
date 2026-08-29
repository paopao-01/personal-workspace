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
  await formField(page, label).locator('input, textarea').first().fill(value)
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function dateInputValue(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

async function createReviewWithWeakQuestion(request: APIRequestContext) {
  const suffix = Date.now()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at18-job-${crypto.randomUUID()}` },
    data: {
      companyName: `任务闭环科技-${suffix}`,
      title: 'AT18 学习任务岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E AT-18',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-at18-application-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: dateInputValue(new Date()),
      channel: 'E2E 渠道-学习任务',
      nextAction: '完成复盘后创建学习任务',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const transitionResponse = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-at18-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(transitionResponse.ok(), `${targetStatus} returned ${transitionResponse.status()}`).toBe(true)
    application = (await transitionResponse.json()) as { id: string; version: number }
  }

  const startsAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-at18-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: 'AT18 技术一面',
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)
  let interview = (await interviewResponse.json()) as { id: string; version: number }

  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-at18-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok(), `POST complete returned ${completeResponse.status()}`).toBe(true)
  interview = (await completeResponse.json()) as { id: string }

  const reviewResponse = await request.put(`/api/interviews/${interview.id}/review`, {
    headers: { 'Idempotency-Key': `e2e-at18-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(reviewResponse.ok(), `PUT review returned ${reviewResponse.status()}`).toBe(true)
  const review = (await reviewResponse.json()) as { id: string }

  const knowledgePointResponse = await request.post('/api/knowledge-points', {
    headers: { 'Idempotency-Key': `e2e-at18-kp-${crypto.randomUUID()}` },
    data: { name: 'Redis 缓存一致性', category: 'Redis' },
  })
  expect(knowledgePointResponse.ok(), `POST knowledge-points returned ${knowledgePointResponse.status()}`).toBe(true)
  const knowledgePoint = (await knowledgePointResponse.json()) as { id: string }

  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-at18-question-${crypto.randomUUID()}` },
    data: {
      content: '缓存与数据库双写时如何处理一致性问题？',
      answerStatus: 'PARTIALLY_ANSWERED',
      knowledgePointIds: [knowledgePoint.id],
    },
  })
  expect(questionResponse.ok(), `POST question returned ${questionResponse.status()}`).toBe(true)
  return interview
}

async function taskTotal(request: APIRequestContext) {
  const response = await request.get('/api/tasks')
  expect(response.ok(), `GET /api/tasks returned ${response.status()}`).toBe(true)
  return ((await response.json()) as { total: number }).total
}

test('AT-18 task is created from a weak question only after confirmation', async ({ page, request }) => {
  const interview = await createReviewWithWeakQuestion(request)
  expect(await taskTotal(request)).toBe(0)

  await page.goto(`/interviews/${interview.id}/review`)
  await expect(page.getByRole('heading', { name: '快速复盘' })).toBeVisible()
  await page.getByRole('button', { name: '创建学习任务' }).click()
  await expect(formField(page, '任务名称').locator('input')).toBeVisible()
  expect(await taskTotal(request)).toBe(0)

  await fillField(page, '任务名称', '梳理 Redis 缓存一致性方案')
  await fillField(page, '验收标准', '能解释 Cache Aside 更新顺序和异常补偿。')
  await fillField(page, '验证方式', '口述演练并记录验证结果')

  const createTaskResponse = page.waitForResponse((response) =>
    response.url().includes('/api/interview-questions/') &&
    response.url().includes('/create-task') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '确认创建' }).click()
  expect((await createTaskResponse).ok()).toBe(true)
  await expect(page.getByText('学习任务已创建')).toBeVisible()
  expect(await taskTotal(request)).toBe(1)

  await page.goto('/tasks')
  await expect(page.getByText('梳理 Redis 缓存一致性方案')).toBeVisible()
})
