import { expect, test, type Page } from '@playwright/test'

function formField(page: Page, label: string) {
  return page.locator('.form-field').filter({ hasText: label }).first()
}

async function fillField(page: Page, label: string, value: string) {
  const field = formField(page, label)
  const input = field.locator('input, textarea').first()
  await input.fill(value)
}

test('AT-01 first session completes job analysis and shows the next action entry', async ({ page }) => {
  await page.goto('/jobs/new')

  await fillField(page, '公司名称', `端到端科技-${Date.now()}`)
  await fillField(page, '岗位名称', 'Java 后端开发工程师')
  await fillField(
    page,
    'JD 原文',
    '负责订单与库存服务开发。要求熟悉 Java、Spring Boot、MySQL，了解 Redis 和消息队列，有后端项目经验优先。',
  )
  await fillField(page, '来源', 'E2E 演示')
  await fillField(page, '地点', '上海')

  const createJobResponse = page.waitForResponse((response) =>
    response.url().includes('/api/jobs') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存岗位' }).click()
  const response = await createJobResponse
  expect(response.ok(), `POST /api/jobs returned ${response.status()}`).toBe(true)
  await expect(page.getByRole('heading', { name: 'Java 后端开发工程师' })).toBeVisible()

  await page.getByRole('button', { name: '提取候选要求' }).click()
  await expect(page.getByText(/提取完成，候选/)).toBeVisible()

  for (let i = 0; i < 3; i += 1) {
    await page.getByRole('button', { name: '确认' }).first().click()
    await expect(page.getByText('要求已确认').nth(i)).toBeVisible()
  }

  await expect(page.locator('.gap-item').filter({ hasText: '信息不足' }).first()).toBeVisible()
  await expect(page.locator('.gap-item').filter({ hasText: '不满足' })).toHaveCount(0)

  await page.getByRole('radio', { name: '待投递' }).check()
  await page.getByRole('button', { name: '保存决定' }).click()
  await expect(page.getByText('投递决定已保存')).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByText('为该岗位创建投递或安排下一步行动').first()).toBeVisible()
  await expect(page.getByText('上传附件')).toHaveCount(0)
  await expect(page.getByText('创建技能')).toHaveCount(0)
})
