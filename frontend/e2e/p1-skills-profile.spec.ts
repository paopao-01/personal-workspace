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

  // AT-66：自评历史——首次 from=null→3（经 UI 保存，不带 reason），再次 PUT 5 → from=3→to=5，列表升序
  const firstHistory = await request.get(`/api/skills/${redis!.skillId}/self-level/history`)
  expect(firstHistory.ok(), `GET history returned ${firstHistory.status()}`).toBe(true)
  const firstEntries = (await firstHistory.json()) as Array<{
    fromLevel: number | null
    toLevel: number
    reason: string | null
  }>
  expect(firstEntries).toHaveLength(1)
  expect(firstEntries[0].fromLevel).toBeNull()
  expect(firstEntries[0].toLevel).toBe(3)
  expect(firstEntries[0].reason).toBeNull()

  // 再次自评 3 → 5（带 reason），历史追加第二行
  const secondPut = await request.put(`/api/skills/${redis!.skillId}/self-level`, {
    headers: {
      'Idempotency-Key': `e2e-at66-second-${crypto.randomUUID()}`,
      'If-Match-Version': '0',
    },
    data: { selfLevel: 5, reason: '项目实战熟练' },
  })
  expect(secondPut.ok(), `PUT self-level=5 returned ${secondPut.status()}`).toBe(true)

  const secondHistory = await request.get(
    `/api/skills/${redis!.skillId}/self-level/history`,
  )
  const secondEntries = (await secondHistory.json()) as Array<{
    fromLevel: number | null
    toLevel: number
    reason: string | null
    occurredAt: string
  }>
  expect(secondEntries).toHaveLength(2)
  // 升序：首条 from=null→3，次条 from=3→5
  expect(secondEntries[0].fromLevel).toBeNull()
  expect(secondEntries[0].toLevel).toBe(3)
  expect(secondEntries[1].fromLevel).toBe(3)
  expect(secondEntries[1].toLevel).toBe(5)
  expect(secondEntries[1].reason).toBe('项目实战熟练')
  expect(secondEntries[1].occurredAt).toBeTruthy()

  // 不存在的技能 → 404
  const unknownHistory = await request.get(
    `/api/skills/99999999-9999-9999-9999-999999999999/self-level/history`,
  )
  expect(unknownHistory.status()).toBe(404)

  // UI：展开自评历史区块可见轨迹与列表
  const historyRow = page.locator('.requirement-row').filter({ hasText: skillName })
  await historyRow.getByRole('button', { name: '查看自评历史' }).click()
  await expect(historyRow.getByText('自评历史')).toBeVisible()
  // 轨迹 span 存在（— → 3 / 5 → 5 / 5）
  await expect(historyRow.locator('span', { hasText: '— → 3 / 5' })).toBeVisible()
  await expect(historyRow.locator('span', { hasText: '3 / 5 → 5 / 5' })).toBeVisible()
})
