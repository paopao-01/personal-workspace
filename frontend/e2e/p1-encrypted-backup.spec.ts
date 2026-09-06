import { expect, test } from '@playwright/test'

/**
 * AT-36 加密备份：UI 输入 passphrase 触发生成、列表展示与下载链接。
 * 复用后端标准数据包导出，AES-256-GCM 加密落盘；passphrase 不回显。
 */
test('AT-36 encrypted backup creates, lists and offers download', async ({ page, request }) => {
  test.setTimeout(90_000)
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有可导出数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at36-job-${crypto.randomUUID()}` },
    data: {
      companyName: `备份验证-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok(), `POST /api/jobs returned ${jobRes.status()}`).toBe(true)

  // UI：进入设置页加密备份区
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()

  const passphraseInput = page.getByLabel('备份 passphrase')
  await passphraseInput.fill(`secret-${suffix}`)

  // passphrase 不足 8 位时按钮 disabled
  await passphraseInput.fill('short')
  await expect(page.getByRole('button', { name: '立即加密备份' })).toBeDisabled()

  // 重新填入有效 passphrase 并提交
  await passphraseInput.fill(`secret-${suffix}`)
  await page.getByRole('button', { name: '立即加密备份' }).click()

  // 提交后 passphrase 输入清空
  await expect(passphraseInput).toHaveValue('')

  // 历史备份列表出现新记录
  const createdRow = page.locator('.requirement-row', {
    has: page.getByText('.enc'),
  })
  await expect(createdRow.first()).toBeVisible({ timeout: 20_000 })
  await expect(createdRow.first()).toContainText('AES_256_GCM_PBKDF2')

  // 下载按钮存在
  await expect(createdRow.first().getByRole('button', { name: '下载' })).toBeVisible()

  // API 直查：响应不含 passphrase
  const listRes = await request.get('/api/backups')
  expect(listRes.ok()).toBe(true)
  const listBody = JSON.stringify(await listRes.json())
  expect(listBody).not.toContain('passphrase')
})
