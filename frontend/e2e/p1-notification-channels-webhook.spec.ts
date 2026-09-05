import { expect, test } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

test('AT-34 webhook channel delivers via HTTP and records failures', async ({ page, request }) => {
  test.setTimeout(120_000)
  const suffix = Date.now()

  // 1. 配置并启用 WEBHOOK 渠道，URL 指向 fake-webhook-server（默认返回 200）
  const webhookPut = await request.put('/api/notification-channels/WEBHOOK', {
    headers: {
      'Idempotency-Key': `e2e-at34-webhook-${crypto.randomUUID()}`,
      'If-Match-Version': '0',
    },
    data: {
      enabled: true,
      webhookConfig: {
        url: 'http://127.0.0.1:18091/',
        secret: `wh-secret-${suffix}`,
        providerType: 'FEISHU',
      },
    },
  })
  expect(webhookPut.ok(), `PUT WEBHOOK returned ${webhookPut.status()}`).toBe(true)
  const webhook = (await webhookPut.json()) as { hasCredential: boolean; channelType: string }
  expect(webhook.hasCredential).toBe(true)
  expect(webhook.channelType).toBe('WEBHOOK')

  // 2. 造数：岗位 + 投递 + 一场 10 分钟后开始的面试（三条默认提醒全部到期）
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at34-job-${crypto.randomUUID()}` },
    data: {
      companyName: `Webhook验证-${suffix}`,
      title: 'AT-34 岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const applicationResponse = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-at34-app-${crypto.randomUUID()}` },
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
        'Idempotency-Key': `e2e-at34-transition-${status}-${crypto.randomUUID()}`,
        'If-Match-Version': String(application.version + index),
      },
      data: { targetStatus: status },
    })
    expect(transition.ok(), `transition ${status} returned ${transition.status()}`).toBe(true)
  }

  const startsAt = new Date(Date.now() + 10 * 60 * 1000)
  startsAt.setSeconds(0, 0)
  const interviewResponse = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `e2e-at34-interview-${crypto.randomUUID()}` },
    data: {
      applicationId: application.id,
      roundName: `Webhook一面-${suffix}`,
      startsAt: startsAt.toISOString(),
      eventTimeZone: 'Asia/Shanghai',
    },
  })
  expect(interviewResponse.ok(), `POST /api/interviews returned ${interviewResponse.status()}`).toBe(true)

  // 3. 等待提醒调度生成通知，每条带一条 channelType=WEBHOOK 的投递，fake-server 返回 200 → SENT
  interface Delivery {
    channelType: string
    status: string
    failureReason?: string | null
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
          (item) =>
            item.title.includes(`Webhook一面-${suffix}`) &&
            item.deliveries.some((delivery) => delivery.channelType === 'WEBHOOK' && delivery.status === 'SENT'),
        ).length
      },
      { timeout: 30_000, intervals: [1_000, 2_000, 5_000] },
    )
    .toBe(3)

  // 4. 站内通知始终保留
  await page.goto('/notifications')
  await expect(page.getByText(`Webhook一面-${suffix}`).first()).toBeVisible()

  // 5. WEBHOOK 渠道 ack → 422（仅 BROWSER 支持回执）
  const allItems = (await (await request.get('/api/notifications')).json()) as Item[]
  const target = allItems.find((item) => item.title.includes(`Webhook一面-${suffix}`))
  expect(target).toBeDefined()
  const ack = await request.post(
    `/api/notifications/${target!.id}/channel-deliveries/WEBHOOK/ack`,
    { headers: { 'Idempotency-Key': `e2e-at34-ack-${crypto.randomUUID()}` }, data: {} },
  )
  expect(ack.status()).toBe(422)

  // 6. 收尾：标记已读，避免共享库残留影响 p1-notifications 的未读角标断言
  for (const item of allItems) {
    if (!item.title.includes(`Webhook一面-${suffix}`)) continue
    await request.post(`/api/notifications/${item.id}/read`, {
      headers: { 'Idempotency-Key': `e2e-at34-read-${crypto.randomUUID()}` },
      data: {},
    })
  }

  // 7. 停用 WEBHOOK 渠道，避免影响后续用例的投递生成
  const persisted = (await (await request.get('/api/notification-channels/WEBHOOK')).json()) as { version: number }
  await request.put('/api/notification-channels/WEBHOOK', {
    headers: {
      'Idempotency-Key': `e2e-at34-disable-${crypto.randomUUID()}`,
      'If-Match-Version': String(persisted.version),
    },
    data: { enabled: false, webhookConfig: { url: 'http://127.0.0.1:18091/', secret: null, providerType: null } },
  })
})
