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

  // 拿到刚生成的备份 id 与文件名，构造下载 URL
  const backups = await listRes.json()
  const created = backups[0]
  expect(created).toBeDefined()
  expect(created.algorithm).toBe('AES_256_GCM_PBKDF2')

  // AT-37 恢复：下载 .enc 文件字节后，用正确 passphrase multipart 上传恢复
  const downloadRes = await request.get(`/api/backups/${created.id}/download`)
  expect(downloadRes.ok()).toBe(true)
  const encBytes = await downloadRes.body()

  // 用 multipart 上传 .enc + passphrase 恢复（岗位仍在库，将识别为重复跳过；
  // knowledge_point 等表因 DatabaseCleaner 不清，部分行也重复跳过，但导出中可能有缺失行被插入）
  const restoreRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at37-restore-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: `secret-${suffix}`,
    },
  })
  expect(restoreRes.ok(), `POST /backups/restore returned ${restoreRes.status()}`).toBe(true)
  const report = await restoreRes.json()
  expect(report.status).toMatch(/COMPLETED/)
  expect(report.skippedIdentical).toBeGreaterThanOrEqual(0)
  expect(JSON.stringify(report)).not.toContain('passphrase')

  // 再次恢复：幂等，inserted=0（无新增行）
  const restoreAgainRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at37-restore2-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: `secret-${suffix}`,
    },
  })
  expect(restoreAgainRes.ok()).toBe(true)
  const report2 = await restoreAgainRes.json()
  expect(report2.inserted).toBe(0)

  // 错误 passphrase 返回 422，不进行任何插入
  const badRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at37-bad-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: 'wrong-passphrase',
    },
  })
  expect(badRes.status()).toBe(422)

  // UI：恢复备份区可见，文件输入与恢复按钮存在，短 passphrase 时恢复按钮 disabled
  const restoreFileInput = page.getByLabel('选择加密备份文件')
  await expect(restoreFileInput).toBeVisible()
  const restorePassphraseInput = page.getByLabel('恢复 passphrase')
  await expect(restorePassphraseInput).toBeVisible()
  await expect(page.getByRole('button', { name: '恢复备份' })).toBeDisabled()
  // 选定文件并填入有效 passphrase 后按钮启用
  await restoreFileInput.setInputFiles({
    name: created.fileName,
    mimeType: 'application/octet-stream',
    buffer: encBytes,
  })
  await restorePassphraseInput.fill(`secret-${suffix}`)
  await expect(page.getByRole('button', { name: '恢复备份' })).toBeEnabled()
  // 提交后 passphrase 清空，恢复完成 toast 出现
  await page.getByRole('button', { name: '恢复备份' }).click()
  await expect(restorePassphraseInput).toHaveValue('')
  await expect(page.getByText(/恢复完成：插入 \d+ 行/).first()).toBeVisible()
})
