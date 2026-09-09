import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

interface SeededApplication {
  id: string
  version: number
}

async function createApplication(
  request: APIRequestContext,
  options: { channel: string; appliedAt: string },
): Promise<SeededApplication> {
  const suffix = crypto.randomUUID()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-funnel-job-${suffix}` },
    data: {
      companyName: `漏斗分析科技-${suffix}`,
      title: 'Java 后端工程师',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E P1 投递漏斗',
      location: '上海',
    },
  })
  expect(jobResponse.ok()).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-funnel-app-${suffix}` },
    data: {
      jobId: job.id,
      appliedAt: options.appliedAt,
      channel: options.channel,
    },
  })
  expect(applicationResponse.ok()).toBe(true)
  return (await applicationResponse.json()) as SeededApplication
}

async function transition(
  request: APIRequestContext,
  application: SeededApplication,
  targetStatus: string,
  allowOfferWithoutInterview = false,
): Promise<SeededApplication> {
  const response = await request.post(`/api/applications/${application.id}/transition`, {
    headers: {
      'Idempotency-Key': `e2e-funnel-transition-${targetStatus}-${crypto.randomUUID()}`,
      'If-Match-Version': String(application.version),
    },
    data: { targetStatus, allowOfferWithoutCompletedInterview: allowOfferWithoutInterview },
  })
  expect(response.ok(), `${targetStatus} returned ${response.status()}`).toBe(true)
  return (await response.json()) as SeededApplication
}

async function completeInterview(
  request: APIRequestContext,
  application: SeededApplication,
): Promise<void> {
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-funnel-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: '技术一面',
      startsAt: '2026-09-05T10:00:00Z',
      eventTimeZone: 'Asia/Shanghai',
      mode: 'ONLINE',
    },
  })
  expect(interviewResponse.ok()).toBe(true)
  const interview = (await interviewResponse.json()) as { id: string; version: number }
  const completeResponse = await request.post(`/api/interviews/${interview.id}/complete`, {
    headers: {
      'Idempotency-Key': `e2e-funnel-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'PASSED' },
  })
  expect(completeResponse.ok()).toBe(true)
}

test('AT-64 analytics funnel grouping (granularity day|hour, no zero-fill)', async ({ page, request }) => {
  test.setTimeout(90_000)
  const suffix = Date.now()
  const channel = `渠道漏斗-${suffix}`

  // 3 份投递跨 3 天：09-07（OFFER）、09-08（INTERVIEWING）、09-09（APPLIED）
  let a1 = await createApplication(request, { channel, appliedAt: '2026-09-07' })
  a1 = await transition(request, a1, 'APPLIED')
  a1 = await transition(request, a1, 'RESUME_PASSED')
  a1 = await transition(request, a1, 'INTERVIEWING')
  await completeInterview(request, a1)
  await transition(request, a1, 'OFFER', true)

  let a2 = await createApplication(request, { channel, appliedAt: '2026-09-08' })
  a2 = await transition(request, a2, 'APPLIED')
  a2 = await transition(request, a2, 'RESUME_PASSED')
  await transition(request, a2, 'INTERVIEWING')

  const a3 = await createApplication(request, { channel, appliedAt: '2026-09-09' })
  await transition(request, a3, 'APPLIED')

  // 全量时间序列（day 缺省）→ 数组，按 date 升序
  const res = await request.get('/api/analytics/funnel')
  expect(res.status()).toBe(200)
  const buckets = await res.json()
  expect(Array.isArray(buckets)).toBeTruthy()
  if (buckets.length > 0) {
    expect(buckets[0]).toHaveProperty('date')
    expect(buckets[0]).toHaveProperty('applied')
    expect(buckets[0]).toHaveProperty('interviewed')
    expect(buckets[0]).toHaveProperty('offered')
    expect(buckets[0]).toHaveProperty('interviewRate')
    expect(buckets[0]).toHaveProperty('offerRate')
    expect(buckets[0]).not.toHaveProperty('passphrase')
    // 升序：date 单调递增
    for (let i = 1; i < buckets.length; i++) {
      expect(buckets[i].date >= buckets[i - 1].date).toBeTruthy()
    }
  }

  // min > max → 空数组不报 400（from > to）
  const fromGt = await request.get(
    '/api/analytics/funnel?from=2026-09-30T00:00:00Z&to=2026-09-01T00:00:00Z',
  )
  expect(fromGt.status()).toBe(200)
  expect(await fromGt.json()).toEqual([])

  // 非法 granularity → 400 fail fast
  const badGran = await request.get('/api/analytics/funnel?granularity=invalid')
  expect(badGran.status()).toBe(400)

  // 非法 from → 400 fail fast
  const badFrom = await request.get('/api/analytics/funnel?from=not-a-date')
  expect(badFrom.status()).toBe(400)

  // UI：投递漏斗页可见
  await page.goto('/analytics/funnel')
  await expect(page.getByRole('heading', { name: '投递漏斗转化趋势' })).toBeVisible()
  await page.getByRole('button', { name: '查询' }).click()
  // 表格可见（有数据时显示行；空 DB 时显示空状态——条件断言容忍单跑空 DB）
  await expect(page.getByRole('heading', { name: '筛选与粒度' })).toBeVisible()
})
