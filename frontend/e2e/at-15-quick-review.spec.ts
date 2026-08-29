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

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function dateInputValue(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

async function createCompletedInterview(request: APIRequestContext) {
  const suffix = Date.now()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at15-job-${crypto.randomUUID()}` },
    data: {
      companyName: `快速复盘科技-${suffix}`,
      title: 'AT15 快速复盘岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E AT-15',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-at15-application-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: dateInputValue(new Date()),
      channel: 'E2E 渠道-快速复盘',
      nextAction: '完成面试后复盘',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const transitionResponse = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-at15-transition-${targetStatus}-${crypto.randomUUID()}`,
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
    headers: { 'Idempotency-Key': `e2e-at15-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: 'AT15 技术一面',
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)
  let interview = (await interviewResponse.json()) as { id: string; version: number }

  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-at15-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok(), `POST complete returned ${completeResponse.status()}`).toBe(true)
  interview = (await completeResponse.json()) as { id: string; version: number }
  return interview
}

test('AT-15 quick review saves a minimal draft and can be reopened', async ({ page, request }) => {
  const interview = await createCompletedInterview(request)

  await page.goto(`/interviews/${interview.id}`)
  await expect(page.getByRole('heading', { name: 'AT15 技术一面' })).toBeVisible()
  await page.getByRole('button', { name: '开始/继续复盘' }).click()
  await page.waitForURL(new RegExp(`/interviews/${interview.id}/review$`))

  await expect(page.getByRole('heading', { name: '快速复盘' })).toBeVisible()
  await formField(page, '面试结果').locator('select').selectOption('FAILED')
  await formField(page, '回答状态').locator('select').selectOption('UNANSWERED')
  await fillField(page, '面试问题', 'Redis 缓存一致性如何保证？')

  const saveReviewResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/interviews/${interview.id}/review`) &&
    response.request().method() === 'PUT',
  )
  const createQuestionResponse = page.waitForResponse((response) =>
    response.url().includes('/api/reviews/') &&
    response.url().includes('/questions') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存复盘' }).click()
  expect((await saveReviewResponse).ok()).toBe(true)
  expect((await createQuestionResponse).ok()).toBe(true)

  await expect(page.getByText('快速复盘已保存')).toBeVisible()
  await expect(page.getByText('草稿')).toBeVisible()
  await expect(page.getByText('Redis 缓存一致性如何保证？')).toBeVisible()
  // 已记录问题的回答状态渲染为行内下拉框，断言其值而不是隐藏的 option 文本
  await expect(
    page
      .locator('.requirement-row')
      .filter({ hasText: 'Redis 缓存一致性如何保证？' })
      .getByLabel('更新回答状态'),
  ).toHaveValue('UNANSWERED')
  await expect(page.getByText('我的回答')).toHaveCount(0)
  await expect(page.getByText('参考答案')).toHaveCount(0)
  await expect(page.getByText('错误原因')).toHaveCount(0)

  await page.goto('/dashboard')
  await page.goto(`/interviews/${interview.id}/review`)
  await expect(page.getByText('草稿')).toBeVisible()
  await expect(page.getByText('Redis 缓存一致性如何保证？')).toBeVisible()
})
