import { expect, test } from '@playwright/test'

test('P1 merge duplicate pending requirements into target', async ({ page, request }) => {
  test.setTimeout(180_000)
  const suffix = Date.now()

  // 造数：岗位 + JD 提取（样例 JD 稳定产出 ≥2 个 MUST 候选）
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1m-job-${crypto.randomUUID()}` },
    data: {
      companyName: `合并验证-${suffix}`,
      title: 'P1 合并岗位',
      // 无“负责/优先/年”提示词，确保关键词被判定为 MUST
      jdRawText: '岗位要求：熟悉 Java 与 JDK。熟悉 Spring Boot 框架。熟悉 MySQL 数据库。了解 Redis 缓存。熟悉 Kafka 消息队列。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  const extractResponse = await request.post(`/api/jobs/${job.id}/requirements/extract`, {
    headers: { 'Idempotency-Key': `e2e-p1m-extract-${crypto.randomUUID()}` },
    data: {},
  })
  expect(extractResponse.ok(), `extract returned ${extractResponse.status()}`).toBe(true)
  const extraction = (await extractResponse.json()) as {
    candidates: Array<{ id: string; type: string; normalizedName?: string }>
  }
  // 批量合并仅限同类候选：取第一个拥有 ≥2 个候选的类型分组
  const groups = new Map<string, Array<{ id: string; normalizedName?: string }>>()
  for (const item of extraction.candidates) {
    if (!item.normalizedName) continue
    const list = groups.get(item.type) ?? []
    list.push(item)
    groups.set(item.type, list)
  }
  const groupEntry = [...groups.entries()].find(([, list]) => list.length >= 2)
  expect(groupEntry, 'JD should produce at least two same-type candidates').toBeTruthy()
  const [sameType, group] = groupEntry!
  expect(sameType).toBe('MUST')
  const target = group[0]
  const source = group[1]
  const targetName = target.normalizedName!
  const sourceName = source.normalizedName!

  // UI：勾选两条同类候选 → 合并到第一项
  await page.goto(`/jobs/${job.id}`)
  await expect(page.getByRole('heading', { name: '候选要求确认' })).toBeVisible()

  const dialogHandled = new Promise<string>((resolve) => {
    page.on('dialog', (dialog) => {
      resolve(dialog.message())
      void dialog.accept()
    })
  })
  await page.getByLabel(`选择候选 ${sourceName}`).check()
  await page.getByLabel(`选择候选 ${targetName}`).check()
  await expect(page.getByText('已选择 2 项')).toBeVisible()
  await page.getByRole('button', { name: '合并所选' }).click()

  const message = await dialogHandled
  expect(message).toContain('合并')

  // 合并后选择清空（rawText 窗口可能互相包含关键词，行级文本断言不可靠，以 API 为准）
  await expect(page.getByText('候选要求已合并')).toBeVisible()
  await expect(page.getByText('已选择 2 项')).toHaveCount(0)

  // API 校验：列表不含来源
  const requirementsResponse = await request.get(`/api/jobs/${job.id}/requirements`)
  const requirements = (await requirementsResponse.json()) as Array<{ id: string }>
  expect(requirements.some((item) => item.id === source.id)).toBe(false)
  expect(requirements.some((item) => item.id === target.id)).toBe(true)
})
