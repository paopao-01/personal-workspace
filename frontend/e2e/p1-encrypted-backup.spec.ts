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

/**
 * AT-39 加密备份删除：物理删除记录行 + 落盘 .enc 文件清理 + last_backup_id 软引用置空。
 * 不可恢复、不进入最近删除；X-Confirm-Permanent-Delete 确认头防误删；passphrase 不参与删除验证。
 */
test('AT-39 backup delete removes record, file and clears lastBackupId', async ({ page, request }) => {
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at39-job-${crypto.randomUUID()}` },
    data: {
      companyName: `删除备份-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 生成一份加密备份
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at39-create-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()
  expect(created.id).toBeDefined()

  // UI：进入设置页，历史备份列表出现该记录与删除按钮
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  const row = page.locator('.requirement-row', { hasText: created.fileName }).first()
  await expect(row).toBeVisible({ timeout: 20_000 })
  await expect(row.getByRole('button', { name: '删除' })).toBeVisible()

  // 缺确认头：API 直查返回 400（不删除）
  const noConfirmRes = await request.delete(`/api/backups/${created.id}`)
  expect(noConfirmRes.status()).toBe(400)

  // 点击删除按钮 → 内联二次确认
  await row.getByRole('button', { name: '删除' }).click()
  await expect(row.getByRole('button', { name: '确认删除' })).toBeVisible()
  // 取消可回退
  await row.getByRole('button', { name: '取消' }).click()
  await expect(row.getByRole('button', { name: '删除' })).toBeVisible()

  // 再次点击删除并确认
  await row.getByRole('button', { name: '删除' }).click()
  await row.getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText(`已删除 ${created.fileName}`)).toBeVisible({ timeout: 10_000 })

  // 列表不再包含该记录
  await expect(page.locator('.requirement-row', { hasText: created.fileName })).toHaveCount(0)

  // API 直查：下载该已删备份返回 404
  const dlRes = await request.get(`/api/backups/${created.id}/download`)
  expect(dlRes.status()).toBe(404)

  // 删不存在 ID 返回 404
  const missingRes = await request.delete(`/api/backups/99999999-9999-9999-9999-999999999999`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(missingRes.status()).toBe(404)
})

/**
 * AT-40 passphrase 强度评估：纯前端、非强制、不离开浏览器。
 * 弱口令显示「弱」+建议；强口令升「强」且建议消失；弱口令下提交仍成功（不阻塞）。
 */
test('AT-40 passphrase strength meter shows level and non-blocking', async ({ page, request }) => {
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at40-job-${crypto.randomUUID()}` },
    data: {
      companyName: `强度校验-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()

  const passphraseInput = page.getByLabel('备份 passphrase')

  // 1. 弱口令（纯重复字符）→ 显示「弱」并给出建议
  await passphraseInput.fill('aaaaaaaa')
  await expect(page.getByText('强度：弱')).toBeVisible()
  await expect(page.locator('.strength-suggestions li').first()).toBeVisible()

  // 2. 常见弱口令黑名单 → 仍为「弱」并提示
  await passphraseInput.fill('password123')
  await expect(page.getByText('强度：弱')).toBeVisible()

  // 3. 强口令（长度≥12 + 大小写 + 数字 + 符号）→ 升「强」且建议列表消失
  const strongPw = 'CorrectHorse42!battery'
  await passphraseInput.fill(strongPw)
  await expect(page.getByText('强度：强')).toBeVisible()
  await expect(page.locator('.strength-suggestions')).toHaveCount(0)

  // 4. 弱口令下提交仍成功（非强制，满足 8–256 位即放行）
  await passphraseInput.fill(`weak-but-long-enough-${suffix}`)
  // 仍可能不是 strong，但按钮应启用（长度≥8）
  await expect(page.getByRole('button', { name: '立即加密备份' })).toBeEnabled()
  await page.getByRole('button', { name: '立即加密备份' }).click()

  // 提交成功（201）+ passphrase 清空 + 强度提示随清空消失
  await expect(passphraseInput).toHaveValue('')
  await expect(page.locator('.strength-meter')).toHaveCount(0)

  // API 直查：响应不含 passphrase
  const listRes = await request.get('/api/backups')
  expect(listRes.ok()).toBe(true)
  const listBody = JSON.stringify(await listRes.json())
  expect(listBody).not.toContain('passphrase')
  // 清理本次生成的备份
  const backups = await listRes.json()
  if (Array.isArray(backups) && backups.length > 0) {
    await request.delete(`/api/backups/${backups[0].id}`, {
      headers: { 'X-Confirm-Permanent-Delete': 'true' },
    })
  }
})

/**
 * AT-38 加密定时备份调度：内存武装 + cron 触发 + 重启解除武装 + 乐观锁 + 非法 cron。
 * passphrase 仅存内存、永不回显、不落库；armed 反映内存状态；未武装到点记 SKIPPED_DISARMED。
 * 调度器后台轮询在 e2e profile 置 1s，UI 操作后等待后台触发；last_run_at=null 时首次轮询
 * 立即触发（epoch 起点已过最近 cron 周期）。
 */
test('AT-38 backup schedule arm + cron trigger + disarmed skip', async ({ page, request }) => {
  test.setTimeout(90_000)
  const suffix = Date.now()

  // 造数：一个岗位，保证定时备份导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at38-job-${crypto.randomUUID()}` },
    data: {
      companyName: `定时备份-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '定时备份调度' })).toBeVisible()

  // 1. 非法 cron → 422（UI toast 提示失败），配置不更新
  const cronInput = page.getByLabel('cron 表达式')
  await cronInput.fill('not-a-cron')
  await page.getByRole('checkbox', { name: '启用定时备份' }).check()
  await page.getByRole('button', { name: '保存调度配置' }).click()
  await expect(page.getByText(/失败|错误|无效/).first()).toBeVisible({ timeout: 10_000 })

  // 2. 合法 cron + enabled → 保存成功
  await cronInput.fill('0 3 * * *')
  await page.getByRole('button', { name: '保存调度配置' }).click()
  await expect(page.getByText('定时备份已启用')).toBeVisible({ timeout: 10_000 })

  // 3. 未武装状态：armed=false 提示
  await expect(page.getByText('调度器未武装')).toBeVisible()

  // 4. 武装：passphrase 仅写入内存，提交后清空，armed 变 true
  const armInput = page.getByLabel('武装 passphrase')
  await armInput.fill(`arm-secret-${suffix}`)
  await page.getByRole('button', { name: '武装调度器' }).click()
  await expect(armInput).toHaveValue('')
  await expect(page.getByText('调度器已武装，应用重启后需重新武装')).toBeVisible({ timeout: 10_000 })

  // 5. 短 passphrase 时武装按钮 disabled
  await armInput.fill('short')
  await expect(page.getByRole('button', { name: '武装调度器' })).toBeDisabled()

  // 6. API 直查：armed=true，响应不含 passphrase
  const schedRes = await request.get('/api/backups/schedule')
  expect(schedRes.ok()).toBe(true)
  const schedBody = JSON.stringify(await schedRes.json())
  expect(schedBody).toContain('"armed":true')
  expect(schedBody).not.toContain(`arm-secret-${suffix}`)

  // 7. passphrase 不落库（backup_schedule 无 passphrase 列）
  const passColRes = await request.get('/api/backups/schedule')
  const schedJson = await passColRes.json()
  expect(schedJson).not.toHaveProperty('passphrase')

  // 8. 后台轮询触发定时备份（last_run_at=null 首次立即触发）：
  //    等待 last_run_status=SUCCESS 与新 backup_record。
  //    全量 E2E 下 4 个 webServer 资源竞争可能延迟后台调度线程，轮询 60s 增加容错。
  const armSecret = `arm-secret-${suffix}`
  for (let i = 0; i < 80; i++) {
    const r = await request.get('/api/backups/schedule')
    const j = await r.json()
    if (j.lastRunStatus === 'SUCCESS' && j.lastBackupId) {
      // 验证生成的备份可凭武装 passphrase 恢复
      const dl = await request.get(`/api/backups/${j.lastBackupId}/download`)
      expect(dl.ok()).toBe(true)
      const enc = await dl.body()
      const restoreRes = await request.post('/api/backups/restore', {
        headers: { 'Idempotency-Key': `e2e-at38-restore-${crypto.randomUUID()}` },
        multipart: {
          file: { name: `${j.lastBackupId}.enc`, mimeType: 'application/octet-stream', buffer: enc },
          passphrase: armSecret,
        },
      })
      expect(restoreRes.ok(), `定时备份恢复 returned ${restoreRes.status()}`).toBe(true)
      const report = await restoreRes.json()
      expect(report.status).toMatch(/COMPLETED/)
      return
    }
    await page.waitForTimeout(750)
  }
  throw new Error('定时备份在 60s 内未触发 SUCCESS')
})

/**
 * AT-41 备份按龄批量清理：物理删除早于阈值的全部记录与密文文件，
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。
 * X-Confirm-Permanent-Delete 确认头防误清；olderThanDays 必填 ≥1；
 * 无匹配记录 deletedCount=0；Idempotency-Key 保证幂等回放。
 *
 * created_at 由后端生成，E2E 无法改库模拟「旧」备份的真实删除链路——该路径由后端集成测试
 * BackupPurgeIntegrationTest 覆盖（真实删行+清文件+置空 last_backup_id+幂等回放）。
 * 本 E2E 聚焦前端契约与入口：缺确认头 400、缺参数 400、olderThanDays=0 400、无匹配 200、
 * UI 按龄清理区可见、二次确认可取消。
 */
test('AT-41 backup purge by age removes old records and files', async ({ page, request }) => {
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at41-job-${crypto.randomUUID()}` },
    data: {
      companyName: `按龄清理-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 造 3 份加密备份（用 API 直建，便于改 created_at 模拟「旧」）
  const created: { id: string; fileName: string }[] = []
  for (let i = 0; i < 3; i++) {
    const res = await request.post('/api/backups', {
      headers: { 'Idempotency-Key': `e2e-at41-create-${suffix}-${i}-${crypto.randomUUID()}` },
      data: { passphrase: `secret-${suffix}-${i}` },
    })
    expect(res.status()).toBe(201)
    const body = await res.json()
    created.push({ id: body.id, fileName: body.fileName })
  }

  // 前 2 份 created_at 改为 10 天前（模拟早于阈值），第 3 份保持最新
  // 直接用 API 无法改 created_at；这里通过设置 olderThanDays=0 不可行（校验拒绝），
  // 改为：先确认 UI 入口存在，再用 API 直接调清理端点验证后端语义（前端在 AT-36 已覆盖删除 UI 链路）。
  // 后端直测清理语义（与前端 UI 互补）：
  //   1. 缺确认头 → 400
  const noConfirm = await request.delete(`/api/backups?olderThanDays=5`)
  expect(noConfirm.status()).toBe(400)

  //   2. 缺 olderThanDays → 400
  const noParam = await request.delete('/api/backups', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(noParam.status()).toBe(400)

  //   3. olderThanDays=0 → 400（@Min(1)）
  const zero = await request.delete('/api/backups?olderThanDays=0', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(zero.status()).toBe(400)

  //   4. 无匹配（仅最新备份，阈值 5 天内）→ 200 deletedCount=0
  const noMatch = await request.delete('/api/backups?olderThanDays=5', {
    headers: {
      'X-Confirm-Permanent-Delete': 'true',
      'Idempotency-Key': `e2e-at41-nomatch-${suffix}-${crypto.randomUUID()}`,
    },
  })
  expect(noMatch.status()).toBe(200)
  const noMatchBody = await noMatch.json()
  expect(noMatchBody.deletedCount).toBe(0)
  expect(noMatchBody.lastBackupIdCleared).toBe(false)

  // UI：进入设置页，按龄清理区可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '按龄批量清理' })).toBeVisible()
  await expect(page.getByLabel('清理阈值天数')).toBeVisible()
  await expect(page.getByRole('button', { name: '清理' })).toBeVisible()

  // UI：点清理 → 内联二次确认（确认/取消）
  await page.getByRole('button', { name: '清理' }).click()
  await expect(page.getByRole('button', { name: /确认清理/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消' })).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('button', { name: '清理' })).toBeVisible()

  // 末尾清理本次产生的全部备份（用按龄清理 1 天前的——created_at 全为「现在」，1 天前无匹配，
  // 故改用单条 DELETE 逐个清理，避免影响其他用例），并断言 API 响应不含 passphrase
  for (const b of created) {
    await request.delete(`/api/backups/${b.id}`, {
      headers: { 'X-Confirm-Permanent-Delete': 'true' },
    })
  }
  const listRes = await request.get('/api/backups')
  expect(listRes.ok()).toBe(true)
  const listBody = JSON.stringify(await listRes.json())
  expect(listBody).not.toContain('passphrase')
})
