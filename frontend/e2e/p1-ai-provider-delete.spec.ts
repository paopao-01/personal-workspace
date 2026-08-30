import { expect, test, type APIRequestContext } from '@playwright/test'

async function createProvider(request: APIRequestContext, name: string, baseUrl = 'http://127.0.0.1:18090/v1') {
  const response = await request.post('/api/ai-providers', {
    headers: { 'Idempotency-Key': `e2e-provider-delete-${crypto.randomUUID()}` },
    data: {
      providerType: 'OPENAI_COMPATIBLE',
      name,
      baseUrl,
      model: 'fake-model',
      apiKey: 'sk-e2e-test',
    },
  })
  expect(response.ok(), `POST /api/ai-providers returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string; version: number; isActive: boolean }
}

test('P1 AI provider deletion requires an inactive unreferenced provider', async ({ page, request }) => {
  const active = await createProvider(request, `E2E 删除供应商A-${Date.now()}`)
  const removable = await createProvider(request, `E2E 删除供应商B-${Date.now()}`)
  const activateOriginal = await request.post(`/api/ai-providers/${active.id}/activate`, {
    headers: { 'Idempotency-Key': `e2e-provider-delete-activate-original-${crypto.randomUUID()}` },
  })
  expect(activateOriginal.ok()).toBe(true)
  const activeDetail = (await (await request.get(`/api/ai-providers/${active.id}`)).json()) as { isActive: boolean }
  expect(activeDetail.isActive).toBe(true)
  const removableDetail = (await (await request.get(`/api/ai-providers/${removable.id}`)).json()) as { isActive: boolean }
  expect(removableDetail.isActive).toBe(false)

  await page.goto('/settings')
  const removableRow = page.locator('.requirement-row').filter({ hasText: `E2E 删除供应商B-` })
  await expect(removableRow.getByRole('button', { name: '删除' })).toBeEnabled()
  page.once('dialog', (dialog) => dialog.accept())
  await removableRow.getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('AI 供应商已删除')).toBeVisible()
  await expect(page.getByText(`E2E 删除供应商B-`)).toHaveCount(0)

  const activeRow = page.locator('.requirement-row').filter({ hasText: `E2E 删除供应商A-` })
  await expect(activeRow.getByRole('button', { name: '删除' })).toBeDisabled()

  const referenced = await createProvider(request, `E2E 已引用供应商-${Date.now()}`, 'http://127.0.0.1:18090/v1')
  const activateReferenced = await request.post(`/api/ai-providers/${referenced.id}/activate`, {
    headers: { 'Idempotency-Key': `e2e-provider-delete-activate-${crypto.randomUUID()}` },
  })
  expect(activateReferenced.ok()).toBe(true)
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-provider-delete-job-${crypto.randomUUID()}` },
    data: {
      companyName: 'AI 删除测试公司',
      title: 'AI 删除测试岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 与 Redis。',
    },
  })
  expect(jobResponse.ok()).toBe(true)
  const job = (await jobResponse.json()) as { id: string }
  const aiJobResponse = await request.post('/api/ai-jobs', {
    headers: { 'Idempotency-Key': `e2e-provider-delete-ai-job-${crypto.randomUUID()}` },
    data: { jobType: 'JD_EXTRACTION', objectId: job.id },
  })
  expect(aiJobResponse.ok()).toBe(true)
  const reactivateOriginal = await request.post(`/api/ai-providers/${active.id}/activate`, {
    headers: { 'Idempotency-Key': `e2e-provider-delete-reactivate-${crypto.randomUUID()}` },
  })
  expect(reactivateOriginal.ok()).toBe(true)
  const referencedDetail = (await (await request.get(`/api/ai-providers/${referenced.id}`)).json()) as { version: number }
  const deleteReferenced = await request.delete(`/api/ai-providers/${referenced.id}`, {
    headers: {
      'Idempotency-Key': `e2e-provider-delete-referenced-${crypto.randomUUID()}`,
      'If-Match-Version': String(referencedDetail.version),
    },
  })
  expect(deleteReferenced.status()).toBe(422)
})
