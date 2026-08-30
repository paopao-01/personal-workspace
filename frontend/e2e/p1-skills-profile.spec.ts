import { expect, test } from '@playwright/test'

test('P1 skills profile shows unrated skills and supports self-level updates', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 造数：岗位 + JD 提取 + 确认 Redis 要求 + e2e 夹具创建带项目证据的技能
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1s-job-${crypto.randomUUID()}` },
    data: {
      companyName: `技能画像-${suffix}`,
      title: 'P1 技能岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis，了解 Kafka 者优先。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const extractResponse = await request.post(`/api/jobs/${job.id}/requirements/extract`, {
    headers: { 'Idempotency-Key': `e2e-p1s-extract-${crypto.randomUUID()}` },
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
    data: { confirmationStatus: 'CONFIRMED', normalizedName: 'Redis', type: 'MUST' },
  })
  expect(confirmResponse.ok(), `confirm returned ${confirmResponse.status()}`).toBe(true)

  const skillName = `Redis-${suffix}`
  const seedResponse = await request.post(`/api/e2e/jobs/${job.id}/seed-project-evidence`, {
    data: { skillName },
  })
  expect(seedResponse.ok(), `seed returned ${seedResponse.status()}`).toBe(true)

  const profileResponse = await request.get('/api/skills/profile')
  expect(profileResponse.ok(), `GET skills profile returned ${profileResponse.status()}`).toBe(true)
  const profiles = (await profileResponse.json()) as Array<{
    skillId: string
    skillName: string
    selfLevel: number | null
    evidenceStatus: string | null
    version: number
  }>
  const redis = profiles.find((item) => item.skillName === skillName)
  expect(redis).toBeTruthy()
  expect(redis!.selfLevel).toBeNull()
  expect(redis!.evidenceStatus).toBe('VALID')
  expect(redis!.version).toBe(0)

  // UI：未评估 → 首次自评 3 → 证据状态保持有效（三维度独立）
  await page.goto('/skills')
  const redisRow = page.locator('.requirement-row').filter({ hasText: skillName })
  await expect(redisRow.getByText('自评：未评估')).toBeVisible()
  await expect(redisRow.getByText('证据：证据有效')).toBeVisible()

  await redisRow.getByLabel(`选择 ${skillName} 的自评等级`).selectOption('3')
  await redisRow.getByRole('button', { name: '保存' }).click()
  await expect(redisRow.getByText('自评：3 / 5')).toBeVisible()
  await expect(redisRow.getByText('证据：证据有效')).toBeVisible()

  // 刷新后持久化
  await page.reload()
  const refreshedRow = page.locator('.requirement-row').filter({ hasText: skillName })
  await expect(refreshedRow.getByText('自评：3 / 5')).toBeVisible()

  const profileAfter = await request.get('/api/skills/profile')
  const profilesAfter = (await profileAfter.json()) as Array<{
    skillName: string
    selfLevel: number | null
    evidenceStatus: string | null
    version: number
  }>
  const redisAfter = profilesAfter.find((item) => item.skillName === skillName)
  expect(redisAfter!.selfLevel).toBe(3)
  expect(redisAfter!.evidenceStatus).toBe('VALID')
  // 首次自评创建 user_skill 后为初始版本 0
  expect(redisAfter!.version).toBe(0)
})
