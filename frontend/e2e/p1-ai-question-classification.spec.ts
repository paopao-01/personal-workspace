import { expect, test, type APIRequestContext } from '@playwright/test'

async function createCompletedInterview(request: APIRequestContext, suffix: number) {
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-job-${suffix}-${crypto.randomUUID()}` },
    data: {
      companyName: `AI 分类验证-${suffix}`,
      title: 'P1 AI 分类岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E P1 AI 分类',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-app-${suffix}-${crypto.randomUUID()}` },
    data: { jobId: job.id, appliedAt: '2026-05-01', channel: 'E2E AI 分类', nextAction: '完成复盘' },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const response = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-p1-ai-question-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(response.ok(), `${targetStatus} returned ${response.status()}`).toBe(true)
    application = (await response.json()) as { id: string; version: number }
  }

  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-interview-${suffix}-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `AI 分类复盘-${suffix}`,
      startsAt: '2026-05-10T10:00:00Z',
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)
  const interview = (await interviewResponse.json()) as { id: string; version: number }
  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-p1-ai-question-complete-${suffix}-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok(), `POST /api/interviews/${interview.id}/complete returned ${completeResponse.status()}`).toBe(true)
  return interview.id
}

test('P1 interview question classification stays a candidate until accepted', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()
  const interviewId = await createCompletedInterview(request, suffix)
  const reviewResponse = await request.put(`/api/interviews/${interviewId}/review`, {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(reviewResponse.ok()).toBe(true)
  const review = (await reviewResponse.json()) as { id: string }
  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-create-${crypto.randomUUID()}` },
    data: {
      content: 'Redis 持久化机制如何选择？（E2E 复盘问题）',
      answerStatus: 'UNANSWERED',
      type: '自定义类型',
    },
  })
  expect(questionResponse.ok()).toBe(true)
  const question = (await questionResponse.json()) as { id: string }

  const providerResponse = await request.post('/api/ai-providers', {
    headers: { 'Idempotency-Key': `e2e-p1-ai-question-provider-${crypto.randomUUID()}` },
    data: {
      providerType: 'OPENAI_COMPATIBLE',
      name: `E2E 分类供应商-${suffix}`,
      baseUrl: 'http://127.0.0.1:18090/v1',
      model: 'fake-model',
      apiKey: 'sk-e2e-test',
    },
  })
  expect(providerResponse.ok()).toBe(true)

  await page.goto(`/interviews/${interviewId}/review`)
  await expect(page.getByRole('heading', { name: '已记录问题' })).toBeVisible()
  const questionRow = page.locator('.requirement-row').filter({ hasText: 'E2E 复盘问题' })
  await expect(questionRow.getByText('AI 问题分类')).toBeVisible()
  await questionRow.getByRole('button', { name: '开始分类' }).click()
  await expect(questionRow.getByText('正在生成候选分类…')).toBeVisible()
  const classificationSelect = questionRow
    .locator('.form-field')
    .filter({ has: page.locator('.form-label').filter({ hasText: '候选分类' }) })
    .locator('select')
  await expect(classificationSelect).toBeVisible({ timeout: 30_000 })

  await classificationSelect.selectOption('SYSTEM_DESIGN')
  await questionRow.getByRole('button', { name: '采纳分类' }).click()
  await expect(page.getByText('问题分类已采纳')).toBeVisible()
  await expect(questionRow.getByText(/SYSTEM_DESIGN/)).toBeVisible()

  const updatedReview = (await (await request.get(`/api/interviews/${interviewId}/review`)).json()) as {
    questions: Array<{ id: string; type: string; answerStatus: string }>
  }
  expect(updatedReview.questions.find((item) => item.id === question.id)).toMatchObject({
    type: 'SYSTEM_DESIGN',
    answerStatus: 'UNANSWERED',
  })
})
