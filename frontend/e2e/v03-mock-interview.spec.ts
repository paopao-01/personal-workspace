import { expect, test } from '@playwright/test'

test('V0.3 starts a project mock interview without changing the project', async ({ page, request }) => {
  test.setTimeout(90_000)
  const suffix = Date.now()
  const provider = await request.post('/api/ai-providers', { headers: { 'Idempotency-Key': crypto.randomUUID() }, data: { providerType: 'OPENAI_COMPATIBLE', name: `模拟面试供应商-${suffix}`, baseUrl: 'http://127.0.0.1:18090/v1', model: 'fake-model', apiKey: 'sk-e2e' } })
  expect(provider.ok()).toBe(true)
  const providerBody = await provider.json() as { id: string; isActive: boolean }
  if (!providerBody.isActive) await request.post(`/api/ai-providers/${providerBody.id}/activate`, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
  const project = await request.post('/api/projects', { headers: { 'Idempotency-Key': crypto.randomUUID() }, data: { title: `支付项目-${suffix}`, scenario: '高峰交易', approach: '异步削峰', problemSolved: '降低超时' } })
  expect(project.ok()).toBe(true)
  const projectBody = await project.json() as { id: string }
  await page.goto('/projects')
  await page.locator('.requirement-row').filter({ hasText: `支付项目-${suffix}` }).getByRole('button', { name: '模拟面试' }).click()
  await expect(page.getByRole('heading', { name: '项目模拟面试' })).toBeVisible()
  await expect(page.getByText('我会按项目场景、采取方案、解决问题和结果进行讲解。')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('请说明这个方案的关键取舍。')).toBeVisible()
  await page.getByRole('button', { name: '结束练习' }).click()
  await expect(page.getByText('会话已结束。')).toBeVisible()
  const projects = await (await request.get('/api/projects')).json() as Array<{ id: string; title: string }>
  expect(projects.find((item) => item.id === projectBody.id)?.title).toBe(`支付项目-${suffix}`)
})
