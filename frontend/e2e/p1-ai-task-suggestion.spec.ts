import { expect, test, type APIRequestContext } from '@playwright/test'

async function createCompletedInterview(request: APIRequestContext, suffix: number) {
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-task-job-${suffix}-${crypto.randomUUID()}` },
    data: {
      companyName: `AI 任务建议-${suffix}`,
      title: 'P1 AI 任务建议岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉缓存一致性。',
      source: 'E2E P1 AI 任务建议',
      location: '上海',
    },
  })
  expect(jobResponse.ok()).toBe(true)
  const job = (await jobResponse.json()) as { id: string }
  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-task-app-${suffix}-${crypto.randomUUID()}` },
    data: { jobId: job.id, appliedAt: '2026-05-01', channel: 'E2E AI 任务建议', nextAction: '完成复盘' },
  })
  expect(applicationResponse.ok()).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }
  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const response = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-task-transition-${targetStatus}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version),
      },
      data: { targetStatus },
    })
    expect(response.ok()).toBe(true)
    application = (await response.json()) as { id: string; version: number }
  }
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-task-interview-${suffix}-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `AI 任务建议复盘-${suffix}`,
      startsAt: '2026-05-10T10:00:00Z',
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok()).toBe(true)
  const interview = (await interviewResponse.json()) as { id: string; version: number }
  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-task-complete-${suffix}-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok()).toBe(true)
  return interview.id
}

test('P1 task suggestion stays a candidate until accepted', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()
  const interviewId = await createCompletedInterview(request, suffix)
  const reviewResponse = await request.put(`/api/interviews/${interviewId}/review`, {
    headers: { 'Idempotency-Key': `e2e-task-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(reviewResponse.ok()).toBe(true)
  const review = (await reviewResponse.json()) as { id: string }
  const knowledgePointResponse = await request.post('/api/knowledge-points', {
    headers: { 'Idempotency-Key': `e2e-task-kp-${crypto.randomUUID()}` },
    data: { name: `E2E 任务建议知识点-${suffix}` },
  })
  expect(knowledgePointResponse.ok()).toBe(true)
  const knowledgePoint = (await knowledgePointResponse.json()) as { id: string }
  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-task-question-${crypto.randomUUID()}` },
    data: {
      content: '缓存一致性如何处理？（E2E 任务建议）',
      answerStatus: 'PARTIALLY_ANSWERED',
      knowledgePointIds: [knowledgePoint.id],
    },
  })
  expect(questionResponse.ok()).toBe(true)
  const question = (await questionResponse.json()) as { id: string; version: number }
  const providerResponse = await request.post('/api/ai-providers', {
    headers: { 'Idempotency-Key': `e2e-task-provider-${crypto.randomUUID()}` },
    data: {
      providerType: 'OPENAI_COMPATIBLE',
      name: `E2E 任务建议供应商-${suffix}`,
      baseUrl: 'http://127.0.0.1:18090/v1',
      model: 'fake-model',
      apiKey: 'sk-e2e-test',
    },
  })
  expect(providerResponse.ok()).toBe(true)

  await page.goto(`/interviews/${interviewId}/review`)
  const questionRow = page.locator('.requirement-row').filter({ hasText: 'E2E 任务建议' })
  await expect(questionRow.getByText('AI 学习任务建议')).toBeVisible()
  await questionRow.getByRole('button', { name: '生成建议' }).click()
  await expect(questionRow.getByText('正在生成可编辑任务候选…')).toBeVisible()
  const field = (label: string) => questionRow.locator('.form-field').filter({ has: page.locator('.form-label').filter({ hasText: label }) })
  await expect(field('任务名称').locator('input')).toBeVisible({ timeout: 30_000 })
  await field('任务名称').locator('input').fill('E2E 编辑后的学习任务')
  await field('验收标准').locator('textarea').fill('能完整说明机制、风险和一个边界场景。')
  await field('验证方式').locator('textarea').fill('口述演练并记录结果')
  await questionRow.getByRole('button', { name: '采纳并创建任务' }).click()
  await expect(page.getByText('已采纳建议并创建学习任务')).toBeVisible()

  const tasks = (await (await request.get('/api/tasks')).json()) as {
    total: number
    items: Array<{ title: string; status: string; knowledgePoints: Array<{ id: string }> }>
  }
  const createdTask = tasks.items.find((item) => item.title === 'E2E 编辑后的学习任务')
  expect(createdTask).toMatchObject({ title: 'E2E 编辑后的学习任务', status: 'TODO' })
  expect(createdTask?.knowledgePoints[0].id).toBe(knowledgePoint.id)
  const jobs = (await (await request.get(`/api/interview-questions/${question.id}/ai-jobs?jobType=TASK_SUGGESTION`)).json()) as Array<{
    items: Array<{ status: string; taskId: string }>
  }>
  expect(jobs[0].items[0].status).toBe('ACCEPTED')
  expect(jobs[0].items[0].taskId).toBeTruthy()
})
