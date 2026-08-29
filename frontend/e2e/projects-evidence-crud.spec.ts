import { expect, test, type Page } from '@playwright/test'

async function fillField(page: Page, label: string, value: string) {
  const field = page.locator('.form-field').filter({ hasText: label }).first()
  const input = field.locator('input, textarea').first()
  await input.fill(value)
}

test('P10 projects page maintains evidence and project cases', async ({ page }) => {
  const suffix = Date.now()
  await page.goto('/projects')
  // 全量回归共享同一临时库，其他用例可能已写入项目，因此不断言全局空状态

  // 先创建证据引用：urlOrPath 仅作为文本保存，界面提示不读取文件
  await fillField(page, '证据名称', `缓存改造代码仓库-${suffix}`)
  await fillField(page, '链接或本地路径', 'https://github.com/user/cache-refactor')
  await expect(page.getByText('应用不会自动读取、扫描或上传被引用的文件')).toBeVisible()
  await page.getByRole('button', { name: '创建证据引用' }).click()
  const evidenceRow = page
    .locator('.requirement-row')
    .filter({ hasText: `缓存改造代码仓库-${suffix}` })
    .filter({ hasText: '引用（仅文本）' })
  await expect(evidenceRow).toBeVisible()
  await expect(page.getByText('引用（仅文本）：https://github.com/user/cache-refactor')).toBeVisible()

  // 再创建项目案例并关联证据
  await fillField(page, '项目名称', `缓存服务改造-${suffix}`)
  await fillField(page, '使用场景', '高峰期缓存与数据库双写不一致，订单读取命中旧值。')
  await fillField(page, '采取方案', '引入 Cache Aside 与延迟双删，补充监控告警。')
  await fillField(page, '解决问题', '将缓存不一致窗口从分钟级降到秒级。')
  await page
    .locator('.evidence-picker-item')
    .filter({ hasText: `缓存改造代码仓库-${suffix}` })
    .locator('input')
    .check()
  await page.getByRole('button', { name: '创建项目案例' }).click()
  const projectRow = page.locator('.requirement-row').filter({ hasText: `缓存服务改造-${suffix}` })
  await expect(projectRow).toBeVisible()
  await expect(projectRow.getByText(`证据引用：缓存改造代码仓库-${suffix}`)).toBeVisible()

  // 编辑证据名称后，项目案例的关联引用随查询刷新
  await evidenceRow.getByRole('button', { name: '编辑' }).click()
  await fillField(page, '证据名称', `缓存改造代码仓库V2-${suffix}`)
  await page.getByRole('button', { name: '保存修改' }).click()
  const updatedEvidenceRow = page
    .locator('.requirement-row')
    .filter({ hasText: `缓存改造代码仓库V2-${suffix}` })
    .filter({ hasText: '引用（仅文本）' })
  await expect(updatedEvidenceRow).toBeVisible()
  await expect(projectRow.getByText(`证据引用：缓存改造代码仓库V2-${suffix}`)).toBeVisible()
})
