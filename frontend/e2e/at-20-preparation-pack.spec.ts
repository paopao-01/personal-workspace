import { expect, test, type APIRequestContext } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function dateInputValue(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

async function transitionApplication(
  request: APIRequestContext,
  application: { id: string; version: number },
  targetStatus: 'APPLIED' | 'RESUME_PASSED',
) {
  const response = await request.post(`/api/applications/${application.id}/transition`, {
    headers: {
      'Idempotency-Key': `e2e-at20-transition-${targetStatus}-${crypto.randomUUID()}`,
      'If-Match-Version': String(application.version),
    },
    data: { targetStatus },
  })
  expect(response.ok(), `${targetStatus} returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string; version: number }
}

async function createInterview(
  request: APIRequestContext,
  applicationId: string,
  roundName: string,
  offsetDays: number,
) {
  const startsAt = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const response = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-at20-interview-${crypto.randomUUID()}` },
    data: {
      applicationId,
      roundName,
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
      preparationChecklist: ['准备库存服务项目讲解'],
    },
  })
  expect(response.ok(), `POST /api/interviews returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string; version: number }
}

async function createPreparationFixture(request: APIRequestContext) {
  const suffix = Date.now()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at20-job-${crypto.randomUUID()}` },
    data: {
      companyName: `准备包科技-${suffix}`,
      title: 'AT20 Java 后端岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis，了解 Kafka 者优先。',
      source: 'E2E AT-20',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const extractResponse = await request.post(`/api/jobs/${job.id}/requirements/extract`, {
    headers: { 'Idempotency-Key': `e2e-at20-extract-${crypto.randomUUID()}` },
    data: {},
  })
  expect(extractResponse.ok(), `extract returned ${extractResponse.status()}`).toBe(true)
  const extraction = (await extractResponse.json()) as {
    candidates: Array<{ id: string; normalizedName?: string; version: number }>
  }
  const redisRequirement = extraction.candidates.find((item) => item.normalizedName === 'Redis')
  expect(redisRequirement).toBeTruthy()

  const confirmResponse = await request.put(`/api/job-requirements/${redisRequirement!.id}`, {
    headers: { 'If-Match-Version': String(redisRequirement!.version) },
    data: {
      confirmationStatus: 'CONFIRMED',
      normalizedName: 'Redis',
      type: 'MUST',
      manualMatchStatus: 'SELF_REPORTED_NO_EVIDENCE',
      reason: '需要补充项目证据',
    },
  })
  expect(confirmResponse.ok(), `confirm returned ${confirmResponse.status()}`).toBe(true)

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-at20-application-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: dateInputValue(new Date()),
      channel: 'E2E 渠道-准备包',
      nextAction: '准备 Redis 项目案例',
      nextActionDueAt: new Date(Date.now() + 6 * 24 * 60 * 60 * 1000).toISOString(),
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  let application = (await applicationResponse.json()) as { id: string; version: number }
  application = await transitionApplication(request, application, 'APPLIED')
  application = await transitionApplication(request, application, 'RESUME_PASSED')

  const historicalInterview = await createInterview(request, application.id, 'AT20 历史模拟面试', -10)
  const completeResponse = await request.post(`/api/interviews/${historicalInterview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-at20-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(historicalInterview.version),
    },
    data: { result: 'FAILED' },
  })
  expect(completeResponse.ok(), `complete returned ${completeResponse.status()}`).toBe(true)

  const reviewResponse = await request.put(`/api/interviews/${historicalInterview.id}/review`, {
    headers: { 'Idempotency-Key': `e2e-at20-review-${crypto.randomUUID()}` },
    data: { interviewResult: 'FAILED', noQuestionsRecorded: false },
  })
  expect(reviewResponse.ok(), `review returned ${reviewResponse.status()}`).toBe(true)
  const review = (await reviewResponse.json()) as { id: string }

  const knowledgePointResponse = await request.post('/api/knowledge-points', {
    headers: { 'Idempotency-Key': `e2e-at20-kp-${crypto.randomUUID()}` },
    data: { name: `Redis 缓存一致性-${suffix}`, category: 'Redis' },
  })
  expect(knowledgePointResponse.ok(), `knowledge point returned ${knowledgePointResponse.status()}`).toBe(true)
  const knowledgePoint = (await knowledgePointResponse.json()) as { id: string }

  const questionResponse = await request.post(`/api/reviews/${review.id}/questions`, {
    headers: { 'Idempotency-Key': `e2e-at20-question-${crypto.randomUUID()}` },
    data: {
      content: '缓存与数据库双写如何保证一致性？',
      answerStatus: 'UNANSWERED',
      knowledgePointIds: [knowledgePoint.id],
    },
  })
  expect(questionResponse.ok(), `question returned ${questionResponse.status()}`).toBe(true)
  const question = (await questionResponse.json()) as { id: string }

  const taskResponse = await request.post(`/api/interview-questions/${question.id}/create-task`, {
    headers: { 'Idempotency-Key': `e2e-at20-task-${crypto.randomUUID()}` },
    data: {
      mode: 'CREATE_NEW',
      title: '梳理 Redis 缓存一致性方案',
      acceptanceCriteria: '能说明 Cache Aside 更新顺序和异常补偿。',
      verificationMethod: '口述演练',
    },
  })
  expect(taskResponse.ok(), `create task returned ${taskResponse.status()}`).toBe(true)

  const seedResponse = await request.post(`/api/e2e/jobs/${job.id}/seed-project-evidence`)
  expect(seedResponse.ok(), `seed project returned ${seedResponse.status()}`).toBe(true)

  const futureInterview = await createInterview(request, application.id, 'AT20 技术一面', 7)
  return futureInterview
}

test('AT-20 preparation pack aggregates traceable interview prep data', async ({ page, request }) => {
  const interview = await createPreparationFixture(request)

  const packResponse = await request.get(`/api/interviews/${interview.id}/preparation`)
  expect(packResponse.ok(), `GET preparation returned ${packResponse.status()}`).toBe(true)
  const pack = (await packResponse.json()) as {
    prioritizedItems: Array<{ reasons: string[]; sourceRefs: unknown[] }>
    requirements: Array<{ requirement: { confirmationStatus: string } }>
  }
  expect(pack.requirements.map((item) => item.requirement.confirmationStatus)).toEqual(['CONFIRMED'])
  for (const item of pack.prioritizedItems) {
    expect(item.reasons.length).toBeGreaterThanOrEqual(1)
    expect(item.sourceRefs.length).toBeGreaterThanOrEqual(1)
  }

  await page.goto(`/interviews/${interview.id}`)
  await page.getByRole('button', { name: '打开准备包' }).click()
  await page.waitForURL(new RegExp(`/interviews/${interview.id}/preparation$`))

  await expect(page.getByRole('heading', { name: '面试准备包' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '本场优先准备项' })).toBeVisible()
  await expect(page.getByText('用户已确认岗位要求')).toBeVisible()
  const projectSection = page.locator('section.card').filter({ has: page.locator('h2.card-title', { hasText: '可讲项目案例' }) })
  const questionSection = page.locator('section.card').filter({ has: page.locator('h2.card-title', { hasText: '历史问题' }) })
  const taskSection = page.locator('section.card').filter({ has: page.locator('h2.card-title', { hasText: '未完成任务' }) })
  await expect(projectSection.getByText('库存服务缓存改造')).toBeVisible()
  await expect(questionSection.getByText('缓存与数据库双写如何保证一致性？')).toBeVisible()
  await expect(taskSection.getByText('梳理 Redis 缓存一致性方案')).toBeVisible()
  await expect(page.getByText('准备库存服务项目讲解').first()).toBeVisible()
  await expect(page.getByText('待确认要求不会作为确定性准备结论展示。')).toBeVisible()
})
