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

interface SeededApplication {
  id: string
  version: number
}

async function createApplication(
  request: APIRequestContext,
  options: { channel: string; resumeVersion?: string; appliedAt: string },
): Promise<SeededApplication> {
  const suffix = crypto.randomUUID()
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-channel-effectiveness-job-${suffix}` },
    data: {
      companyName: `效果对比科技-${suffix}`,
      title: 'Java 后端工程师',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E P1 效果对比',
      location: '上海',
    },
  })
  expect(jobResponse.ok()).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-channel-effectiveness-app-${suffix}` },
    data: {
      jobId: job.id,
      appliedAt: options.appliedAt,
      channel: options.channel,
      resumeVersion: options.resumeVersion,
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
      'Idempotency-Key': `e2e-channel-effectiveness-transition-${targetStatus}-${crypto.randomUUID()}`,
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
    headers: { 'Idempotency-Key': `e2e-p1-channel-effectiveness-interview-${crypto.randomUUID()}` },
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
      'Idempotency-Key': `e2e-p1-channel-effectiveness-complete-${crypto.randomUUID()}`,
      'If-Match-Version': String(interview.version),
    },
    data: { result: 'PASSED' },
  })
  expect(completeResponse.ok()).toBe(true)
}

test('AT-35 channel effectiveness aggregates raw counts', async ({ page, request }) => {
  test.setTimeout(90_000)
  const suffix = Date.now()
  const channelA = `渠道A-${suffix}`
  const channelB = `渠道B-${suffix}`
  const resumeX = `简历版本X-${suffix}`
  const resumeZ = `简历版本Z-${suffix}`

  // 渠道 A：3 份投递（1 OFFER、1 INTERVIEWING、1 APPLIED）
  let a1 = await createApplication(request, { channel: channelA, resumeVersion: resumeX, appliedAt: '2026-09-01' })
  a1 = await transition(request, a1, 'APPLIED')
  a1 = await transition(request, a1, 'RESUME_PASSED')
  a1 = await transition(request, a1, 'INTERVIEWING')
  await completeInterview(request, a1)
  await transition(request, a1, 'OFFER', true)

  let a2 = await createApplication(request, { channel: channelA, resumeVersion: resumeX, appliedAt: '2026-09-02' })
  a2 = await transition(request, a2, 'APPLIED')
  a2 = await transition(request, a2, 'RESUME_PASSED')
  await transition(request, a2, 'INTERVIEWING')

  const a3 = await createApplication(request, { channel: channelA, resumeVersion: resumeZ, appliedAt: '2026-09-03' })
  await transition(request, a3, 'APPLIED')

  // 渠道 B：1 份投递（简历版本 Z，仅 1 份 → offerRate 信息不足）
  const b1 = await createApplication(request, { channel: channelB, resumeVersion: resumeZ, appliedAt: '2026-09-04' })
  await transition(request, b1, 'APPLIED')

  // UI 打开效果对比页（首次加载会发起一次 GET）
  await page.goto('/analytics/channel-effectiveness')
  await expect(page.getByRole('heading', { name: '渠道与简历版本效果对比' })).toBeVisible()
  await page.getByRole('button', { name: '查询' }).click()

  // 渠道组：渠道 A 在前（3 份），渠道 B 在后（1 份）
  const channelRowA = page.locator('.requirement-row').filter({ hasText: channelA }).first()
  await expect(channelRowA).toContainText('投递 3 · 面试 2 · Offer 1')
  await expect(channelRowA).toContainText('Offer 率 33%')
  const channelRowB = page.locator('.requirement-row').filter({ hasText: channelB }).first()
  await expect(channelRowB).toContainText('投递 1 · 面试 0 · Offer 0')
  await expect(channelRowB).toContainText('Offer 率 信息不足')

  // 简历版本组：简历版本X（2 份，1 offer）+ 简历版本Z（2 份，0 offer）
  const resumeRowX = page.locator('.requirement-row').filter({ hasText: resumeX }).first()
  await expect(resumeRowX).toContainText('投递 2 · 面试 2 · Offer 1')
  await expect(resumeRowX).toContainText('Offer 率 50%')
  const resumeRowZ = page.locator('.requirement-row').filter({ hasText: resumeZ }).first()
  await expect(resumeRowZ).toContainText('投递 2 · 面试 0 · Offer 0')
  await expect(resumeRowZ).toContainText('Offer 率 0%')

  // 不出现趋势结论字样
  await expect(page.getByText('不推断趋势或行动')).toBeVisible()

  // 日期过滤：只取 2026-09-03 之后
  await fillField(page, '开始日期', '2026-09-03')
  await page.getByRole('button', { name: '查询' }).click()
  const channelRowAFiltered = page.locator('.requirement-row').filter({ hasText: channelA }).first()
  await expect(channelRowAFiltered).toContainText('投递 1 · 面试 0 · Offer 0')
  await expect(page.locator('.requirement-row').filter({ hasText: channelB })).toHaveCount(1)
})
