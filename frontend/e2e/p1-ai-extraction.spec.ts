import { expect, test } from '@playwright/test'

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

test('P1 ai provider switchable and jd extraction candidates confirmable', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 造岗位（含中文 JD）
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1ai-job-${crypto.randomUUID()}` },
    data: {
      companyName: `AI验证-${suffix}`,
      title: 'P1 AI 岗位',
      jdRawText:
        '负责核心业务系统研发，要求熟悉 Spring Boot、MySQL 与 Redis，具备高并发系统经验；了解 K8s 与消息队列者优先。',
      source: 'E2E P1 AI',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  // 配置并激活假供应商（Playwright webServer 中的 Node 假 AI 服务）
  const createResponse = await request.post('/api/ai-providers', {
    headers: { 'Idempotency-Key': `e2e-p1ai-provider-${crypto.randomUUID()}` },
    data: {
      providerType: 'OPENAI_COMPATIBLE',
      name: `E2E 假供应商-${suffix}`,
      baseUrl: 'http://127.0.0.1:18090/v1',
      model: 'fake-model',
      apiKey: 'sk-e2e-test',
    },
  })
  expect(createResponse.ok(), `POST /api/ai-providers returned ${createResponse.status()}`).toBe(true)
  const provider = (await createResponse.json()) as { id: string; isActive: boolean; hasCredential: boolean }
  expect(provider.hasCredential).toBe(true)
  if (!provider.isActive) {
    const activateResponse = await request.post(`/api/ai-providers/${provider.id}/activate`, {
      headers: { 'Idempotency-Key': `e2e-p1ai-provider-activate-${crypto.randomUUID()}` },
    })
    expect(activateResponse.ok(), `POST /api/ai-providers/${provider.id}/activate returned ${activateResponse.status()}`).toBe(true)
  }

  // 创建提取任务
  const aiJobResponse = await request.post('/api/ai-jobs', {
    headers: { 'Idempotency-Key': `e2e-p1ai-extract-${crypto.randomUUID()}` },
    data: { jobType: 'JD_EXTRACTION', objectId: job.id },
  })
  expect(aiJobResponse.ok(), `POST /api/ai-jobs returned ${aiJobResponse.status()}`).toBe(true)
  const aiJob = (await aiJobResponse.json()) as { id: string; promptVersion: string }
  expect(aiJob.promptVersion).toBe('JD_EXTRACTION_V1')

  // 等待任务完成且生成候选
  await expect
    .poll(
      async () => {
        const detail = await request.get(`/api/ai-jobs/${aiJob.id}`)
        const body = (await detail.json()) as { status: string; items: unknown[] }
        return body.status === 'SUCCEEDED' ? body.items.length : -1
      },
      { timeout: 30_000, intervals: [500, 1_000, 2_000] },
    )
    .toBe(2)

  // UI：岗位详情页展示候选，采纳第一条（原文），拒绝第二条
  await page.goto(`/jobs/${job.id}`)
  await expect(page.getByRole('heading', { name: 'AI 提取要求' })).toBeVisible()
  const candidateRows = page.locator('.requirement-row').filter({ hasText: 'E2E 假供应商输出' })
  await expect(candidateRows).toHaveCount(2)

  await candidateRows.filter({ hasText: 'Spring Boot' }).getByRole('button', { name: '采纳', exact: true }).click()
  await expect(page.getByText('已采纳为候选要求，请在下方要求确认区确认')).toBeVisible()

  await candidateRows.filter({ hasText: 'Redis' }).getByRole('button', { name: '拒绝', exact: true }).click()
  await expect(page.getByText('已拒绝该候选')).toBeVisible()

  // 采纳生成 source=AI 的 PENDING 候选要求，进入既有确认区
  const requirementsResponse = await request.get(`/api/jobs/${job.id}/requirements`)
  const requirements = (await requirementsResponse.json()) as Array<{ source: string; confirmationStatus: string; rawText: string }>
  const aiRequirement = requirements.find((item) => item.source === 'AI' && item.rawText.includes('E2E 假供应商输出'))
  expect(aiRequirement, '采纳后生成 AI 来源候选要求').toBeTruthy()
  expect(aiRequirement?.confirmationStatus).toBe('PENDING')

  // 重新生成：新任务 SUCCEEDED，既有条目与确认状态不受影响
  const secondResponse = await request.post('/api/ai-jobs', {
    headers: { 'Idempotency-Key': `e2e-p1ai-extract2-${crypto.randomUUID()}` },
    data: { jobType: 'JD_EXTRACTION', objectId: job.id },
  })
  expect(secondResponse.ok(), `POST /api/ai-jobs (2nd) returned ${secondResponse.status()}`).toBe(true)
  const secondJob = (await secondResponse.json()) as { id: string }
  await expect
    .poll(
      async () => {
        const detail = await request.get(`/api/ai-jobs/${secondJob.id}`)
        return ((await detail.json()) as { status: string }).status
      },
      { timeout: 30_000, intervals: [500, 1_000, 2_000] },
    )
    .toBe('SUCCEEDED')
  const history = await (await request.get(`/api/jobs/${job.id}/ai-jobs`)).json()
  const jobs = history as Array<{ items: Array<{ status: string }> }>
  expect(jobs.length).toBe(2)
  expect(jobs[1].items.some((item) => item.status === 'ACCEPTED')).toBe(true)

  // 设置页：AI 供应商区块可见且显示激活徽章
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: 'AI 供应商' })).toBeVisible()
  await expect(page.getByText('激活中')).toBeVisible()
  await expect(page.getByText(`E2E 假供应商-${suffix}`)).toBeVisible()
})
