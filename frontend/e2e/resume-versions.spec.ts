import { expect, test } from '@playwright/test'

test('AT-33 compares two confirmed resume versions', async ({ page }) => {
  test.setTimeout(60_000)
  const suffix = Date.now()
  await page.goto('/resume-versions')
  await page.getByLabel('版本名称').fill(`基础版-${suffix}`)
  await page.getByLabel('已确认的简历原文').fill('Java\n旧项目描述')
  await page.getByRole('button', { name: '保存版本' }).click()
  // 等待首个版本写库且 onSuccess 清空表单后再填第二个版本，避免清空表单与第二个版本 fill 竞态导致「保存版本」按钮一直 disabled
  await expect(page.getByLabel('版本名称')).toHaveValue('')
  await page.getByLabel('版本名称').fill(`岗位版-${suffix}`)
  await page.getByLabel('已确认的简历原文').fill('Java\n新项目描述')
  await page.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByLabel('左侧版本')).toBeVisible()
  await page.getByLabel('左侧版本').selectOption({ label: `基础版-${suffix}` })
  await page.getByLabel('右侧版本').selectOption({ label: `岗位版-${suffix}` })
  await page.getByRole('button', { name: '比较版本' }).click()
  await expect(page.getByText('新增：新项目描述')).toBeVisible()
  await expect(page.getByText('删除：旧项目描述')).toBeVisible()
})
