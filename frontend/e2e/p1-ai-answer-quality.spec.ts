import { expect, test, type APIRequestContext } from '@playwright/test'

async function createCompletedInterview(request: APIRequestContext, suffix: number) {
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-answer-job-${suffix}-${crypto.randomUUID()}` },
    data: {
      companyName: `AI 回答分析-${suffix}`,
      title: 'P1 AI 回答分析岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉缓存一致性。',
      source: 'E2E P1 AI 回答分析',
      location: '上海',
    },
  })
  expect(jobResponse.ok()).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-answer-app-${suffix}-${crypto.randomUUID()}` },
    data: { jobId: job.id, appliedAt: '2026-05-01', channel: 'E2E AI 回答分析', nextAction: '完成复盘' },
  })
  expect(applicationResponse.ok()).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }

  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const response = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-answer-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(response.ok()).toBe(true)
    application = (await response.json()) as { id: string; version: number }
  }

  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-answer-interview-${suffix}-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `AI 回答分析复盘-${suffix}`,
      startsAt: '2026-05-10T10:00:00Z',
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok()).toBe(true)
  const interview = (await interviewResponse.json()) as { id: string; version: number }
  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-answer-complete-${suffix}-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok()).toBe(true)
  return interview.id
}

test('P1 answer quality analysis stays editable and preserves the user answer', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()
  const interviewId = await createCompletedInterview(request, suffix)
  const reviewResponse = await request.put(`/api/interviews/${interviewId}/review`, {
    headers: { 'Idempotency-Key': `e2e-answer-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(reviewResponse.ok()).toBe(true)
  const review = (await reviewResponse.json()) as { id: string }
  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-answer-question-${crypto.randomUUID()}` },
    data: {
      content: '缓存一致性如何处理？（E2E 回答分析）',
      answerStatus: 'UNANSWERED',
      type: '自定义类型',
    },
  })
  expect(questionResponse.ok()).toBe(true)
  const question = (await questionResponse.json()) as { id: string; version: number }
  const originalAnswer = '我会先更新数据库，再删除缓存。'
  const detailResponse = await request.put(`/api/interview-questions/${question.id}`, {
    headers: {
      'Idempotency-Key': `e2e-answer-detail-${crypto.randomUUID()}`,
      'If-Match-Version': String(question.version),
    },
    data: {
      content: '缓存一致性如何处理？（E2E 回答分析）',
      answerStatus: 'UNANSWERED',
      type: '自定义类型',
      knowledgePointIds: [],
      myAnswer: originalAnswer,
      difficulty: 4,
    },
  })
  expect(detailResponse.ok()).toBe(true)

  const providerResponse = await request.post('/api/ai-providers', {
    headers: { 'Idempotency-Key': `e2e-answer-provider-${crypto.randomUUID()}` },
    data: {
      providerType: 'OPENAI_COMPATIBLE',
      name: `E2E 回答分析供应商-${suffix}`,
      baseUrl: 'http://127.0.0.1:18090/v1',
      model: 'fake-model',
      apiKey: 'sk-e2e-test',
    },
  })
  expect(providerResponse.ok()).toBe(true)

  await page.goto(`/interviews/${interviewId}/review`)
  const questionRow = page.locator('.requirement-row').filter({ hasText: 'E2E 回答分析' })
  await expect(questionRow.getByText('AI 回答质量分析')).toBeVisible()
  await questionRow.getByRole('button', { name: '开始分析' }).click()
  await expect(questionRow.getByText('正在生成回答分析候选…')).toBeVisible()

  const statusField = questionRow
    .locator('.form-field')
    .filter({ has: page.locator('.form-label').filter({ hasText: '建议回答状态' }) })
  await expect(statusField.locator('select')).toBeVisible({ timeout: 30_000 })
  await statusField.locator('select').selectOption('FULLY_ANSWERED')

  const referenceField = questionRow
    .locator('.form-field')
    .filter({ has: page.locator('.form-label').filter({ hasText: '候选参考答案' }) })
  await referenceField.locator('textarea').fill('E2E 用户编辑后的参考答案')
  await questionRow.getByRole('button', { name: '采纳分析' }).click()
  await expect(page.getByText('回答质量分析已采纳')).toBeVisible()

  const updatedReview = (await (await request.get(`/api/interviews/${interviewId}/review`)).json()) as {
    questions: Array<{
      id: string
      type: string
      myAnswer: string
      answerStatus: string
      referenceAnswer: string
      difficulty: number
    }>
  }
  expect(updatedReview.questions.find((item) => item.id === question.id)).toMatchObject({
    type: '自定义类型',
    myAnswer: originalAnswer,
    answerStatus: 'FULLY_ANSWERED',
    referenceAnswer: 'E2E 用户编辑后的参考答案',
    difficulty: 4,
  })
})
