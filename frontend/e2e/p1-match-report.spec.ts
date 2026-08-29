import { expect, test } from '@playwright/test'

test('P1 match report generates explainable scores and marks stale on input change', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 造数：岗位 + 提取（无提示词 JD 产出 MUST 候选）+ 确认两项并给出不同匹配状态
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1mr-job-${crypto.randomUUID()}` },
    data: {
      companyName: `匹配报告-${suffix}`,
      title: 'P1 匹配报告岗位',
      jdRawText: '岗位要求：熟悉 Java 与 JDK。熟悉 Spring Boot 框架。熟悉 MySQL 数据库。了解 Redis 缓存。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const extractResponse = await request.post(`/api/jobs/${job.id}/requirements/extract`, {
    headers: { 'Idempotency-Key': `e2e-p1mr-extract-${crypto.randomUUID()}` },
    data: {},
  })
  expect(extractResponse.ok(), `extract returned ${extractResponse.status()}`).toBe(true)
  const extraction = (await extractResponse.json()) as {
    candidates: Array<{ id: string; type: string; normalizedName?: string; version: number }>
  }
  const musts = extraction.candidates.filter(
    (item) => item.type === 'MUST' && item.normalizedName,
  )
  expect(musts.length).toBeGreaterThanOrEqual(2)
  const [first, second] = musts

  const confirmCases = [
    { candidate: first, manualMatchStatus: 'SATISFIED_WITH_EVIDENCE' },
    { candidate: second, manualMatchStatus: 'SELF_REPORTED_NO_EVIDENCE' },
  ]
  for (const { candidate, manualMatchStatus } of confirmCases) {
    const confirmResponse = await request.put(`/api/job-requirements/${candidate.id}`, {
      headers: { 'If-Match-Version': String(candidate.version) },
      data: {
        confirmationStatus: 'CONFIRMED',
        normalizedName: candidate.normalizedName,
        type: 'MUST',
        manualMatchStatus,
      },
    })
    expect(confirmResponse.ok(), `confirm returned ${confirmResponse.status()}`).toBe(true)
  }

  // UI：生成报告 → 建议与加权分数可见
  await page.goto(`/jobs/${job.id}`)
  const section = page.locator('section.card').filter({
    has: page.locator('h2.card-title', { hasText: '匹配报告' }),
  })
  await section.getByRole('button', { name: '生成匹配报告' }).click()
  await expect(section.getByText('部分匹配')).toBeVisible({ timeout: 20_000 })
  await expect(section.getByText('4.5 / 6')).toBeVisible()
  await expect(section.getByText('1 项必须要求有证据')).toBeVisible()

  // 输入变化 → stale=true 横幅出现
  const requirementsResponse = await request.get(`/api/jobs/${job.id}/requirements`)
  const requirements = (await requirementsResponse.json()) as Array<{
    id: string
    normalizedName?: string
    version: number
  }>
  const secondName = second.normalizedName!
  const secondRequirement = requirements.find((item) => item.normalizedName === secondName)
  expect(secondRequirement).toBeTruthy()
  const overrideResponse = await request.put(`/api/job-requirements/${secondRequirement!.id}`, {
    headers: { 'If-Match-Version': String(secondRequirement!.version) },
    data: {
      confirmationStatus: 'CONFIRMED',
      normalizedName: secondName,
      type: 'MUST',
      manualMatchStatus: 'SATISFIED_WITH_EVIDENCE',
      reason: '补充了项目证据',
    },
  })
  expect(overrideResponse.ok(), `override returned ${overrideResponse.status()}`).toBe(true)

  await page.reload()
  await expect(section.getByText('可能过期')).toBeVisible()

  // 重新生成 → 全部满足有证据 → 匹配度高，过期横幅消失
  await section.getByRole('button', { name: '重新生成' }).click()
  await expect(section.getByText('匹配度高')).toBeVisible({ timeout: 20_000 })
  await expect(section.getByText('可能过期')).toHaveCount(0)
})
