import { expect, test } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

test('P1 notifications generated from due reminders and markable as read', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 造数：岗位 + 投递 + 一场 10 分钟后开始的面试（三条默认提醒全部已到期）
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1n-job-${crypto.randomUUID()}` },
    data: {
      companyName: `通知验证-${suffix}`,
      title: 'P1 通知岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-p1n-app-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: `${new Date().getFullYear()}-${pad2(new Date().getMonth() + 1)}-${pad2(new Date().getDate())}`,
      channel: 'E2E 通知渠道',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  const application = (await applicationResponse.json()) as { id: string; version: number }

  for (const [index, status] of ['APPLIED', 'RESUME_PASSED'].entries()) {
    const transition = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-p1n-transition-${status}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version + index),
      },
      data: { targetStatus: status },
    })
    expect(transition.ok(), `transition ${status} returned ${transition.status()}`).toBe(true)
  }

  const startsAt = new Date(Date.now() + 10 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-p1n-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: 'P1 通知一面',
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)

  // 等待调度扫描（e2e profile 间隔 1s）生成 3 条通知
  await expect
    .poll(
      async () => {
        const list = await request.get('/api/notifications')
        const items = (await list.json()) as unknown[]
        return items.length
      },
      { timeout: 30_000, intervals: [1_000, 2_000, 5_000] },
    )
    .toBe(3)

  // TopBar 未读角标 → 通知页
  await page.goto('/dashboard')
  await expect(page.getByLabel('未读通知 3 条')).toBeVisible({ timeout: 20_000 })
  await page.getByRole('button', { name: '站内通知' }).click()
  await page.waitForURL(/\/notifications$/)

  const rows = page.locator('.requirement-row')
  await expect(rows).toHaveCount(3)
  await expect(page.getByText('面试提醒：P1 通知一面').first()).toBeVisible()
  await expect(page.getByText('未读', { exact: true })).toHaveCount(3)

  // 标记第一条已读：未读减少，TopBar 角标同步
  await rows.first().getByRole('button', { name: '标记已读' }).click()
  await expect(page.getByText('未读', { exact: true })).toHaveCount(2)
  await expect(page.getByLabel('未读通知 2 条')).toBeVisible()

  const listAfter = await request.get('/api/notifications')
  const itemsAfter = (await listAfter.json()) as Array<{ id: string; readAt: string | null }>
  expect(itemsAfter.filter((item) => item.readAt !== null)).toHaveLength(1)
})
