import { expect, test } from '@playwright/test'

test('P1 evidence attachment reference library records metadata only', async ({ page, request }) => {
  const suffix = Date.now()
  const evidenceResponse = await request.post('/api/evidence', {
    headers: { 'Idempotency-Key': `e2e-attachment-evidence-${crypto.randomUUID()}` },
    data: { type: 'ARTICLE', title: `E2E 附件证据-${suffix}` },
  })
  expect(evidenceResponse.ok()).toBe(true)
  const evidence = (await evidenceResponse.json()) as { id: string }

  await page.goto('/evidence-attachments')
  await expect(page.getByRole('heading', { name: '附件引用库' })).toBeVisible()
  await page.getByLabel('所属证据').selectOption(evidence.id)
  await page.getByLabel('显示名称').fill('E2E 复盘文档')
  await page.getByLabel('本地路径').fill('D:\\docs\\e2e-review.pdf')
  await page.getByLabel('MIME 类型（可选）').fill('application/pdf')
  await page.getByLabel('大小（字节，可选）').fill('4096')
  await page.getByLabel('说明（可选）').fill('只保存引用位置和人工元数据')
  await page.getByRole('button', { name: '登记引用' }).click()

  await expect(page.getByText('附件引用已登记')).toBeVisible()
  const row = page.locator('tbody tr').filter({ hasText: 'E2E 复盘文档' })
  await expect(row).toContainText('本地路径')
  await expect(row).toContainText('D:\\docs\\e2e-review.pdf')
  await expect(row).toContainText('application/pdf')
  await expect(page.getByText('应用不会读取、扫描、上传、下载或校验路径/链接指向的文件。')).toBeVisible()
})
