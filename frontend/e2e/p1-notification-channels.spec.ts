import { expect, test } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

test('P1 notification channels deliver via browser and record email failures', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 1. 启用浏览器通知渠道；配置邮件渠道指向不可达 SMTP（验证失败记录 + 站内兜底）
  const browserPut = await request.put('/api/notification-channels/BROWSER', {
    headers: {
      'Idempotency-Key': `e2e-p1nc-browser-${crypto.randomUUID()}`,
      'If-Match-Version': '0',
    },
    data: { enabled: true },
  })
  expect(browserPut.ok(), `PUT BROWSER returned ${browserPut.status()}`).toBe(true)
  expect(((await browserPut.json()) as { enabled: boolean }).enabled).toBe(true)

  const emailPut = await request.put('/api/notification-channels/EMAIL', {
    headers: {
      'Idempotency-Key': `e2e-p1nc-email-${crypto.randomUUID()}`,
      'If-Match-Version': '0',
    },
    data: {
      enabled: true,
      config: {
        smtpHost: '127.0.0.1',
        smtpPort: 1,
        toAddress: `user-${suffix}@test.local`,
        fromAddress: 'jobhub@test.local',
      },
    },
  })
  expect(emailPut.ok(), `PUT EMAIL returned ${emailPut.status()}`).toBe(true)

  // 先打开页面（让浏览器通知 hook 记录初始批次），再造数触发新通知。
  // headless Chromium 中 Notification.permission 恒为 denied —— 恰好验证 PRD 9.3
  // “权限被拒时保留站内提醒”：前端跳过浏览器渠道，不产生回执。
  await page.goto('/dashboard')
  const pagePermission = await page.evaluate(() =>
    'Notification' in window ? Notification.permission : 'unsupported',
  )
  expect(pagePermission).toBe('denied')

  // 2. 造数：岗位 + 投递 + 一场 10 分钟后开始的面试（三条默认提醒全部到期）
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1nc-job-${crypto.randomUUID()}` },
    data: {
      companyName: `渠道验证-${suffix}`,
      title: 'P1 渠道岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-p1nc-app-${crypto.randomUUID()}` },
    data: {
      jobId: job.id,
      appliedAt: `${new Date().getFullYear()}-${pad2(new Date().getMonth() + 1)}-${pad2(new Date().getDate())}`,
      channel: 'E2E 渠道',
    },
  })
  expect(applicationResponse.ok(), `POST /api/applications returned ${applicationResponse.status()}`).toBe(true)
  const application = (await applicationResponse.json()) as { id: string; version: number }

  for (const [index, status] of ['APPLIED', 'RESUME_PASSED'].entries()) {
    const transition = await request.post(`/api/applications/${application.id}/transition`, {
      headers: {
        'Idempotency-Key': `e2e-p1nc-transition-${status}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version + index),
      },
      data: { targetStatus: status },
    })
    expect(transition.ok(), `transition ${status} returned ${transition.status()}`).toBe(true)
  }

  const startsAt = new Date(Date.now() + 10 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-p1nc-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `渠道一面-${suffix}`,
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)

  // 3. 等待提醒调度生成通知（每条通知带 BROWSER 与 EMAIL 两条渠道投递记录）
  interface Delivery {
    channelType: string
    status: string
  }
  interface Item {
    id: string
    title: string
    deliveries: Delivery[]
  }
  await expect
    .poll(
      async () => {
        const list = await request.get('/api/notifications')
        const items = (await list.json()) as Item[]
        return items.filter(
          (item) => item.title.includes(`渠道一面-${suffix}`) && (item.deliveries?.length ?? 0) >= 2,
        ).length
      },
      { timeout: 30_000, intervals: [1_000, 2_000, 5_000] },
    )
    .toBe(3)

  // 4. 浏览器渠道：权限被拒 → 前端不回执，BROWSER 投递保持 PENDING，站内通知保留
  const pendingBrowser = await request.get('/api/notifications')
  const pendingItems = (await pendingBrowser.json()) as Item[]
  expect(
    pendingItems.filter(
      (item) =>
        item.title.includes(`渠道一面-${suffix}`) &&
        item.deliveries.some((delivery) => delivery.channelType === 'BROWSER' && delivery.status === 'PENDING'),
    ).length,
  ).toBe(3)

  // 模拟已授权浏览器展示后的回执：ack 幂等置 SENT
  for (const item of pendingItems) {
    const ack = await request.post(
      `/api/notifications/${item.id}/channel-deliveries/BROWSER/ack`,
      { headers: { 'Idempotency-Key': `e2e-p1nc-ack-${crypto.randomUUID()}` }, data: {} },
    )
    expect(ack.status(), `ack returned ${ack.status()}`).toBe(204)
  }
  const ackedItems = (await (await request.get('/api/notifications')).json()) as Item[]
  expect(
    ackedItems.filter(
      (item) =>
        item.title.includes(`渠道一面-${suffix}`) &&
        item.deliveries.some((delivery) => delivery.channelType === 'BROWSER' && delivery.status === 'SENT'),
    ).length,
  ).toBe(3)

  // 5. 邮件渠道：SMTP 不可达 → 重试耗尽后 FAILED 且记录失败原因
  await expect
    .poll(
      async () => {
        const list = await request.get('/api/notifications')
        const items = (await list.json()) as Item[]
        return items.filter(
          (item) =>
            item.title.includes(`渠道一面-${suffix}`) &&
            item.deliveries.some(
              (delivery) =>
                delivery.channelType === 'EMAIL' && delivery.status === 'FAILED' && !!delivery.failureReason,
            ),
        ).length
      },
      { timeout: 60_000, intervals: [1_000, 2_000, 5_000] },
    )
    .toBe(3)

  // 6. 站内通知始终保留（PRD 9.3 兜底）：通知页可见
  await page.goto('/notifications')
  await expect(page.getByText(`渠道一面-${suffix}`).first()).toBeVisible()

  // 7. 收尾：全部标记已读，避免共享库残留影响 p1-notifications 的未读角标断言
  const allItems = (await (await request.get('/api/notifications')).json()) as Item[]
  for (const item of allItems) {
    if (!item.title.includes(`渠道一面-${suffix}`)) continue
    const read = await request.post(`/api/notifications/${item.id}/read`, {
      headers: { 'Idempotency-Key': `e2e-p1nc-read-${crypto.randomUUID()}` },
      data: {},
    })
    expect(read.ok(), `mark read returned ${read.status()}`).toBe(true)
  }
})
