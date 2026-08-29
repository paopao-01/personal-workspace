import { expect, test } from '@playwright/test'

test('P1 settings persist timezone and reminder offsets', async ({ page, request }) => {
  test.setTimeout(120_000)

  await page.goto('/settings')
  const timeZoneInput = page.locator('input[placeholder="Asia/Shanghai"]')
  await expect(timeZoneInput).toHaveValue('Asia/Shanghai')
  const presetGroup = page.getByRole('group', { name: '默认提醒节点' })
  await expect(presetGroup.locator('input')).toHaveCount(3)
  await expect(presetGroup.locator('input:checked')).toHaveCount(3)

  // 修改时区、关闭全部预设节点并添加自定义节点
  await timeZoneInput.fill('UTC')
  await page.getByText('提前 1 天').click()
  await page.getByText('提前 2 小时').click()
  await page.getByText('提前 30 分钟').click()
  await page.getByLabel('自定义提醒节点分钟数').fill('90')
  await page.getByRole('button', { name: '添加节点' }).click()
  await expect(presetGroup.getByText('提前 90 分钟')).toBeVisible()
  await page.getByRole('button', { name: '保存设置' }).click()

  // 刷新后表单与后端状态均为保存值：3 个预设保持渲染但未勾选，仅自定义节点选中
  await page.reload()
  await expect(timeZoneInput).toHaveValue('UTC')
  await expect(presetGroup.locator('input')).toHaveCount(4)
  await expect(presetGroup.locator('input:checked')).toHaveCount(1)
  await expect(presetGroup.getByText('提前 90 分钟')).toBeVisible()

  const settingsResponse = await request.get('/api/settings')
  expect(settingsResponse.ok()).toBe(true)
  const settings = (await settingsResponse.json()) as {
    timeZone: string
    defaultReminderOffsetsMinutes: number[]
    version: number
  }
  expect(settings.timeZone).toBe('UTC')
  expect(settings.defaultReminderOffsetsMinutes).toEqual([90])

  // 恢复种子设置，避免影响共享库中的其他用例
  await request.put('/api/settings', {
    headers: {
      'Idempotency-Key': `e2e-p1-restore-${crypto.randomUUID()}`,
      'If-Match-Version': String(settings.version),
    },
    data: { timeZone: 'Asia/Shanghai', defaultReminderOffsetsMinutes: [1440, 120, 30] },
  })
  await page.reload()
  await expect(timeZoneInput).toHaveValue('Asia/Shanghai')
  await expect(presetGroup.locator('input')).toHaveCount(3)
  await expect(presetGroup.locator('input:checked')).toHaveCount(3)
})
