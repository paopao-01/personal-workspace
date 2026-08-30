import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function dateInputValue(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

async function fillField(page: Page, label: string, value: string) {
  const field = page.locator('.form-field').filter({ hasText: label }).first()
  const input = field.locator('input, textarea').first()
  await input.fill(value)
}

async function createCompletedInterview(request: APIRequestContext) {
  const suffix = Date.now()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1r-job-${crypto.randomUUID()}` },
    data: {
      companyName: `重新打开复盘-${suffix}`,
      title: 'P1 复盘 reopen 岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-p1r-app-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: dateInputValue(new Date()),
      channel: 'E2E 渠道-reopen',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const transitionResponse = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-p1r-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(transitionResponse.ok(), `transition ${targetStatus} returned ${transitionResponse.status()}`).toBe(true)
    application = (await transitionResponse.json()) as { id: string; version: number }
  }

  const startsAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-p1r-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: 'P1 reopen 一面',
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)
  let interview = (await interviewResponse.json()) as { id: string; version: number }

  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-p1r-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok(), `complete returned ${completeResponse.status()}`).toBe(true)
  interview = (await completeResponse.json()) as { id: string; version: number }
  return interview
}

test('P1 review reopen keeps questions and allows continued editing', async ({ page, request }) => {
  test.setTimeout(120_000)
  const interview = await createCompletedInterview(request)

  // API 造草稿 + 问题 + 完成复盘，UI 从完成态开始
  const draftResponse = await request.put(`/api/interviews/${interview.id}/review`, {
    headers: { 'Idempotency-Key': `e2e-p1r-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(draftResponse.ok(), `PUT review returned ${draftResponse.status()}`).toBe(true)
  const review = (await draftResponse.json()) as { id: string }

  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-p1r-question-${crypto.randomUUID()}` },
    data: { content: 'reopen 前记录的问题', answerStatus: 'UNANSWERED' },
  })
  expect(questionResponse.ok(), `POST questions returned ${questionResponse.status()}`).toBe(true)

  const completeResponse = await request.post(`/api/reviews/${review.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-p1r-review-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(
        ((await (await request.get(`/api/interviews/${interview.id}/review`)).json()) as { version: number }).version,
      ),
    },
  })
  expect(completeResponse.ok(), `review complete returned ${completeResponse.status()}`).toBe(true)

  await page.goto(`/interviews/${interview.id}/review`)
  await expect(page.getByRole('heading', { name: '快速复盘' })).toBeVisible()
  await expect(page.getByText('复盘已完成，可以直接补充或修改')).toBeVisible()
  await expect(page.locator('dd').filter({ hasText: /^已完成$/ }).last()).toBeVisible()

  // 完成态可直接保存补充内容，并保持 COMPLETED。
  await fillField(page, '整体感受', '完成后直接补充的感受')
  const directEditResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/interviews/${interview.id}/review`) && response.request().method() === 'PUT',
  )
  await page.getByRole('button', { name: '保存复盘' }).click()
  expect((await directEditResponse).ok()).toBe(true)
  await expect(page.getByText('快速复盘已保存')).toBeVisible()
  await expect(page.locator('dd').filter({ hasText: /^已完成$/ }).last()).toBeVisible()

  // 重新打开：状态回草稿，编辑入口恢复
  await page.getByRole('button', { name: '重新打开' }).click()
  await expect(page.getByText('复盘已重新打开，可继续编辑')).toBeVisible()
  await expect(page.locator('dd').filter({ hasText: /^草稿$/ }).last()).toBeVisible()

  // 继续编辑：补充第二个问题并保存
  await page.locator('.form-field').filter({ hasText: '回答状态' }).first().locator('select').selectOption('PARTIALLY_ANSWERED')
  await fillField(page, '面试问题', 'reopen 后补充的问题')
  const createQuestionResponse = page.waitForResponse((response) =>
    response.url().includes('/api/reviews/') &&
    response.url().includes('/questions') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存复盘' }).click()
  expect((await createQuestionResponse).ok()).toBe(true)
  await expect(page.getByText('reopen 后补充的问题')).toBeVisible()
  await expect(page.getByText('reopen 前记录的问题')).toBeVisible()

  // 再次完成
  await page.getByRole('button', { name: '完成复盘' }).click()
  await expect(page.getByText('复盘已完成').first()).toBeVisible()
  await expect(page.locator('dd').filter({ hasText: /^已完成$/ }).last()).toBeVisible()

  const reviewAfter = await request.get(`/api/interviews/${interview.id}/review`)
  const reviewState = (await reviewAfter.json()) as { status: string; questions: unknown[] }
  expect(reviewState.status).toBe('COMPLETED')
  expect(reviewState.questions).toHaveLength(2)
})
