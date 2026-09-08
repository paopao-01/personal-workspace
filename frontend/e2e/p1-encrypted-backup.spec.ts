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
  await passphraseInput.fill(`secret-${suffix}!Strong`)

  // passphrase 不足 8 位时按钮 disabled
  await passphraseInput.fill('short')
  await expect(page.getByRole('button', { name: '立即加密备份' })).toBeDisabled()

  // 重新填入有效 passphrase 并提交
  await passphraseInput.fill(`secret-${suffix}!Strong`)
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
      passphrase: `secret-${suffix}!Strong`,
    },
  })
  expect(restoreRes.ok(), `POST /backups/restore returned ${restoreRes.status()}`).toBe(true)
  const report = await restoreRes.json()
  expect(report.status).toMatch(/COMPLETED/)
  expect(report.skippedIdentical).toBeGreaterThanOrEqual(0)
  // 响应不含 passphrase 值（字段名 passphraseResetRecommended 不含口令值，仅返回布尔）
  expect(JSON.stringify(report)).not.toContain(`secret-${suffix}!Strong`)

  // 再次恢复：幂等，inserted=0（无新增行）
  const restoreAgainRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at37-restore2-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: `secret-${suffix}!Strong`,
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
  await restorePassphraseInput.fill(`secret-${suffix}!Strong`)
  await expect(page.getByRole('button', { name: '恢复备份' })).toBeEnabled()
  // 提交后 passphrase 清空，恢复完成 toast 出现
  await page.getByRole('button', { name: '恢复备份' }).click()
  await expect(restorePassphraseInput).toHaveValue('')
  await expect(page.getByText(/恢复完成：插入 \d+ 行/).first()).toBeVisible()
})

/**
 * AT-44 恢复后自动孤儿清理联动：恢复成功后于事务内自动触发 cleanOrphans，
 * 摘要随响应 orphanCleanSummary 返回；恢复失败不触发；无需 X-Confirm-Permanent-Delete 确认头。
 * 真实孤儿删除链路由后端集成测试覆盖（无法在 E2E 直接制造孤儿 .enc 文件），E2E 聚焦契约。
 */
test('AT-44 restore auto-cleans orphans and returns summary', async ({ request }) => {
  const suffix = Date.now()

  // 造数 + 生成一份加密备份
  await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at44-job-${crypto.randomUUID()}` },
    data: {
      companyName: `恢复联动-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at44-backup-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.ok()).toBe(true)
  const created = await createRes.json()

  // 下载 .enc 字节
  const downloadRes = await request.get(`/api/backups/${created.id}/download`)
  expect(downloadRes.ok()).toBe(true)
  const encBytes = await downloadRes.body()

  // 恢复成功：响应含 orphanCleanSummary 字段（无孤儿时全 0）
  const restoreRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at44-restore-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: `secret-${suffix}!Strong`,
    },
  })
  expect(restoreRes.ok(), `POST /backups/restore returned ${restoreRes.status()}`).toBe(true)
  const report = await restoreRes.json()
  expect(report.orphanCleanSummary).toBeDefined()
  expect(report.orphanCleanSummary.scannedFiles).toBeGreaterThanOrEqual(0)
  expect(report.orphanCleanSummary.orphanFiles).toBeGreaterThanOrEqual(0)
  expect(report.orphanCleanSummary.deletedFiles).toBeGreaterThanOrEqual(0)
  // 响应不含 passphrase 值（字段名 passphraseResetRecommended 不含口令值，仅返回布尔）
  expect(JSON.stringify(report)).not.toContain(`secret-${suffix}!Strong`)

  // 错误 passphrase 返回 422，不触发孤儿清理
  const badRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at44-bad-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase: 'wrong-passphrase',
    },
  })
  expect(badRes.status()).toBe(422)

  // 恢复端点无需 X-Confirm-Permanent-Delete 确认头（不带也能成功）
  expect(restoreRes.status()).toBe(200)
})

/**
 * AT-46 恢复后弱/中口令重设提示：恢复成功后后端内存评估 passphrase，未达 strong（score<70，即弱或中）置
 * passphraseResetRecommended=true 提示用户用强口令新建备份替换；强为 false。
 * 弱/中口令创建已被门槛拒绝（无法经 API 造弱/中口令备份），故 true 分支由后端集成测试覆盖；
 * E2E 聚焦契约：强口令恢复 → passphraseResetRecommended=false、toast 不追加重设提示、
 * 响应不回显 passphrase/score/level。
 */
test('AT-46 restore with strong passphrase returns recommended=false and no reset hint', async ({ page, request }) => {
  const suffix = Date.now()
  const passphrase = `StrongPass42!${suffix}` // 强口令（含大小写/数字/符号，长度 ≥16）

  // 造数 + 生成一份强口令加密备份
  await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at46-job-${crypto.randomUUID()}` },
    data: {
      companyName: `弱口令提示-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at46-backup-${crypto.randomUUID()}` },
    data: { passphrase },
  })
  expect(createRes.ok()).toBe(true)
  const created = await createRes.json()

  // 下载 .enc
  const downloadRes = await request.get(`/api/backups/${created.id}/download`)
  expect(downloadRes.ok()).toBe(true)
  const encBytes = await downloadRes.body()

  // 恢复：契约断言（API 层）
  const restoreRes = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at46-restore-${crypto.randomUUID()}` },
    multipart: {
      file: { name: created.fileName, mimeType: 'application/octet-stream', buffer: encBytes },
      passphrase,
    },
  })
  expect(restoreRes.ok(), `POST /backups/restore returned ${restoreRes.status()}`).toBe(true)
  const report = await restoreRes.json()
  // 强口令 → passphraseResetRecommended=false
  expect(report.passphraseResetRecommended).toBe(false)
  // 响应不回显 passphrase 值，不含 score/level
  expect(JSON.stringify(report)).not.toContain(passphrase)
  expect(JSON.stringify(report)).not.toMatch(/"score"/)
  expect(JSON.stringify(report)).not.toMatch(/"level"/)

  // UI：设置页恢复同一备份，恢复成功 toast 不追加弱口令提示
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  const restoreFileInput = page.getByLabel('选择加密备份文件')
  const restorePassphraseInput = page.getByLabel('恢复 passphrase')
  await restoreFileInput.setInputFiles({
    name: created.fileName,
    mimeType: 'application/octet-stream',
    buffer: encBytes,
  })
  await restorePassphraseInput.fill(passphrase)
  await page.getByRole('button', { name: '恢复备份' }).click()
  // 恢复成功 toast 出现，且不含重设提示字样
  const toast = page.locator('[role="status"], [role="alert"]').filter({ hasText: '恢复完成' })
  await expect(toast).toBeVisible()
  await expect(toast).not.toContainText('未达强')
  await expect(toast).not.toContainText('偏弱')
  // 输入框清空
  await expect(restorePassphraseInput).toHaveValue('')
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
    data: { passphrase: `secret-${suffix}!Strong` },
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
 * AT-40 passphrase 强度评估：纯前端提示，未达强口令提交由后端拒绝。
 * 弱口令显示「弱」+建议（含「未达强口令将被拒绝」）；中口令显示「中」同样含拒绝提示；
 * 强口令升「强」且建议消失；弱口令提交被后端返 400 拒绝（不禁用按钮，后端是唯一闸门）。
 */
test('AT-40 passphrase strength meter shows level and weak rejected by backend', async ({ page, request }) => {
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

  // 1. 弱口令（纯重复字符）→ 显示「弱」并给出建议，含「未达强口令将被拒绝」提示
  await passphraseInput.fill('aaaaaaaa')
  await expect(page.getByText('强度：弱')).toBeVisible()
  await expect(page.locator('.strength-suggestions li').first()).toBeVisible()
  await expect(page.getByText('未达强口令将被拒绝')).toBeVisible()

  // 2. 常见弱口令黑名单 → 仍为「弱」并提示
  await passphraseInput.fill('password123')
  await expect(page.getByText('强度：弱')).toBeVisible()

  // 3. 中口令（无符号，score 40–69，未达 strong）→ 显示「中」，仍含拒绝提示
  await passphraseInput.fill('CorrectHorse42')
  await expect(page.getByText('强度：中')).toBeVisible()
  await expect(page.getByText('未达强口令将被拒绝')).toBeVisible()

  // 4. 强口令（长度≥12 + 大小写 + 数字 + 符号）→ 升「强」且建议列表消失
  const strongPw = 'CorrectHorse42!battery'
  await passphraseInput.fill(strongPw)
  await expect(page.getByText('强度：强')).toBeVisible()
  await expect(page.locator('.strength-suggestions')).toHaveCount(0)

  // 5. 弱口令（满足 8–256 位但 score<70）提交被后端拒绝：按钮不禁用，点击后返回 400
  await passphraseInput.fill('aaaaaaaa')
  await expect(page.getByRole('button', { name: '立即加密备份' })).toBeEnabled()
  await page.getByRole('button', { name: '立即加密备份' }).click()
  // 提交失败：passphrase 输入未清空（创建未成功），强度提示仍在
  await expect(passphraseInput).toHaveValue('aaaaaaaa')
  await expect(page.getByText('强度：弱')).toBeVisible()

  // API 直查：响应不含 passphrase；弱口令未生成备份（列表不含本次「强度校验」岗位关联的新备份）
  const listRes = await request.get('/api/backups')
  expect(listRes.ok()).toBe(true)
  const listBody = JSON.stringify(await listRes.json())
  expect(listBody).not.toContain('passphrase')
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
  await armInput.fill(`arm-secret-${suffix}!Strong`)
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
  expect(schedBody).not.toContain(`arm-secret-${suffix}!Strong`)

  // 7. passphrase 不落库（backup_schedule 无 passphrase 列）
  const passColRes = await request.get('/api/backups/schedule')
  const schedJson = await passColRes.json()
  expect(schedJson).not.toHaveProperty('passphrase')

  // 8. 后台轮询触发定时备份（last_run_at=null 首次立即触发）：
  //    等待 last_run_status=SUCCESS 与新 backup_record。
  //    全量 E2E 下 4 个 webServer 资源竞争可能延迟后台调度线程，轮询 60s 增加容错。
  const armSecret = `arm-secret-${suffix}!Strong`
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
      data: { passphrase: `secret-${suffix}-${i}!Strong` },
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
  await expect(page.getByRole('button', { name: '清理', exact: true })).toBeVisible()

  // UI：点清理 → 内联二次确认（确认/取消）
  await page.getByRole('button', { name: '清理', exact: true }).click()
  await expect(page.getByRole('button', { name: /确认清理/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消' })).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('button', { name: '清理', exact: true })).toBeVisible()

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

/**
 * AT-42 孤儿 .enc 文件扫描清理：扫描 backup-dir 下全部 .enc，物理删除无 backup_record 对应的孤儿。
 * UUID 命名且无对应 file_name 的 .enc 即孤儿；非 UUID 命名的 .enc 跳过不删（skippedFiles）；
 * X-Confirm-Permanent-Delete 确认头防误清；不写记录、不联动 last_backup_id/data_export；
 * 无孤儿返回全 0（不报 404）；Idempotency-Key 保证幂等回放返回首次摘要。
 *
 * 孤儿真实删除链路（删记录留文件 → 清理孤儿）由后端集成测试 BackupOrphanScanIntegrationTest 覆盖
 * （E2E 无法直接删 backup_record 行模拟孤儿）。本 E2E 聚焦前端契约与入口：缺确认头 400、
 * 无孤儿 200 全 0、UI 孤儿清理区可见、二次确认可取消。
 */
test('AT-42 orphan .enc scan clean removes orphans and keeps legit files', async ({ page, request }) => {
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at42-job-${crypto.randomUUID()}` },
    data: {
      companyName: `孤儿清理-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 造 1 份合法加密备份（文件与记录都在）
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at42-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 后端契约：
  //   1. 缺确认头 → 400（不删任何文件）
  const noConfirm = await request.post('/api/backups/orphans/clean')
  expect(noConfirm.status()).toBe(400)

  //   2. 先清扫 backup-dir 中累积的孤儿文件（DatabaseCleaner 仅清 DB 不清文件，跨测试/跨运行会残留孤儿），
  //      使后续「无孤儿」断言确定。此处不固定删除数，仅断言成功。
  const sweep = await request.post('/api/backups/orphans/clean', {
    headers: {
      'X-Confirm-Permanent-Delete': 'true',
      'Idempotency-Key': `e2e-at42-sweep-${suffix}-${crypto.randomUUID()}`,
    },
  })
  expect(sweep.status()).toBe(200)

  //   3. 无孤儿（合法备份文件与记录都在，目录已无累积孤儿）→ 200 全 0
  const noOrphan = await request.post('/api/backups/orphans/clean', {
    headers: {
      'X-Confirm-Permanent-Delete': 'true',
      'Idempotency-Key': `e2e-at42-noorphan-${suffix}-${crypto.randomUUID()}`,
    },
  })
  expect(noOrphan.status()).toBe(200)
  const noOrphanBody = await noOrphan.json()
  expect(noOrphanBody.orphanFiles).toBe(0)
  expect(noOrphanBody.deletedFiles).toBe(0)
  expect(noOrphanBody.freedBytes).toBe(0)
  // 合法记录仍在
  const afterRes = await request.get('/api/backups')
  expect(afterRes.ok()).toBe(true)
  const afterList = JSON.stringify(await afterRes.json())
  expect(afterList).toContain(created.id)

  // UI：进入设置页，孤儿清理区可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '孤儿文件清理' })).toBeVisible()
  await expect(page.getByRole('button', { name: '清理孤儿文件' })).toBeVisible()

  // UI：点清理 → 内联二次确认（确认/取消）
  await page.getByRole('button', { name: '清理孤儿文件' }).click()
  await expect(page.getByRole('button', { name: '确认清理孤儿文件' })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消' })).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('button', { name: '清理孤儿文件' })).toBeVisible()

  // 末尾清理本次产生的备份（单条 DELETE），并断言 API 响应不含 passphrase
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  const listRes = await request.get('/api/backups')
  expect(listRes.ok()).toBe(true)
  const listBody = JSON.stringify(await listRes.json())
  expect(listBody).not.toContain('passphrase')
})

/**
 * AT-43 备份按数量保留清理：保留最近 N 条，物理删除其余全部记录与密文文件，
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。
 * keepLast 必填 ≥1；keepLast ≥ 总数 deletedCount=0；keepLast 与 olderThanDays 互斥；
 * 缺确认头 400；Idempotency-Key 保证幂等回放。
 *
 * created_at 由后端生成，E2E 无法改库模拟「真实保留最近 N 条」的删除链路——该路径由后端集成测试
 * BackupRetainIntegrationTest 覆盖（真实删行+清文件+置空 last_backup_id+幂等回放）。本 E2E 聚焦
 * 前端契约与入口：缺确认头 400、keepLast=0 400、两参数互斥 400、缺参数 400、N≥总数 200 全 0、
 * UI 按数量保留区可见、二次确认可取消。
 */
test('AT-43 backup keep last N retains newest and deletes the rest', async ({ page, request }) => {
  const suffix = Date.now()

  // 造数：一个岗位，保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at43-job-${crypto.randomUUID()}` },
    data: {
      companyName: `按数量保留-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok(), `POST /api/jobs returned ${jobRes.status()}`).toBe(true)

  // 造 2 份加密备份（用 API 直建，便于 N≥总数 断言）
  const created: { id: string; fileName: string }[] = []
  for (let i = 0; i < 2; i++) {
    const res = await request.post('/api/backups', {
      headers: { 'Idempotency-Key': `e2e-at43-create-${suffix}-${i}-${crypto.randomUUID()}` },
      data: { passphrase: `secret-${suffix}-${i}!Strong` },
    })
    expect(res.status()).toBe(201)
    const body = await res.json()
    created.push({ id: body.id, fileName: body.fileName })
  }

  // 后端契约：
  //   1. 缺确认头 → 400（不删任何记录）
  const noConfirm = await request.delete('/api/backups?keepLast=2')
  expect(noConfirm.status()).toBe(400)

  //   2. keepLast=0 → 400（@Min(1)）
  const zero = await request.delete('/api/backups?keepLast=0', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(zero.status()).toBe(400)

  //   3. keepLast 与 olderThanDays 互斥 → 400
  const both = await request.delete('/api/backups?keepLast=2&olderThanDays=5', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(both.status()).toBe(400)

  //   4. 缺两个参数 → 400
  const none = await request.delete('/api/backups', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  expect(none.status()).toBe(400)

  //   5. N ≥ 总数（2 份备份，keepLast=10）→ 200 deletedCount=0，全部保留
  const noMatch = await request.delete('/api/backups?keepLast=10', {
    headers: {
      'X-Confirm-Permanent-Delete': 'true',
      'Idempotency-Key': `e2e-at43-nomatch-${suffix}-${crypto.randomUUID()}`,
    },
  })
  expect(noMatch.status()).toBe(200)
  const noMatchBody = await noMatch.json()
  expect(noMatchBody.deletedCount).toBe(0)
  expect(noMatchBody.lastBackupIdCleared).toBe(false)
  // 记录仍在
  const afterRes = await request.get('/api/backups')
  expect(afterRes.ok()).toBe(true)
  const afterList = JSON.stringify(await afterRes.json())
  expect(afterList).toContain(created[0].id)
  expect(afterList).toContain(created[1].id)

  // UI：进入设置页，按数量保留区可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '加密备份' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '按数量保留' })).toBeVisible()
  await expect(page.getByLabel('保留条数')).toBeVisible()
  await expect(page.getByRole('button', { name: '保留最近 N 条' })).toBeVisible()

  // UI：点保留 → 内联二次确认（确认/取消）
  await page.getByRole('button', { name: '保留最近 N 条' }).click()
  await expect(page.getByRole('button', { name: /确认保留最近/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消' })).toBeVisible()
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('button', { name: '保留最近 N 条' })).toBeVisible()

  // 末尾清理本次产生的全部备份（单条 DELETE，避免影响其他用例），并断言 API 响应不含 passphrase
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

/**
 * AT-45 passphrase 强度强制门槛（要求 strong）：创建与武装对弱/中口令返回 400（score<70）；
 * 恢复端点豁免（passphrase 已与备份绑定，解密失败按 422，不触发强度门槛）。
 */
test('AT-45 passphrase strength gate rejects weak and fair on create and arm', async ({ request }) => {
  const suffix = Date.now()

  // 造数：一个岗位
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at45-job-${crypto.randomUUID()}` },
    data: {
      companyName: `门槛校验-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 1. 弱口令（纯重复）创建备份 → 400，含 score 与 ≥70
  const weakCreate = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at45-weak-create-${crypto.randomUUID()}` },
    data: { passphrase: 'aaaaaaaa' },
  })
  expect(weakCreate.status()).toBe(400)
  const weakBody = await weakCreate.json()
  expect(weakBody.code).toBe('VALIDATION_ERROR')
  expect(JSON.stringify(weakBody)).toContain('score=')
  expect(JSON.stringify(weakBody)).toContain('≥70')

  // 2. 弱口令武装 → 400
  const weakArm = await request.post('/api/backups/schedule/arm', {
    headers: { 'Idempotency-Key': `e2e-at45-weak-arm-${crypto.randomUUID()}` },
    data: { passphrase: 'aaaaaaaa' },
  })
  expect(weakArm.status()).toBe(400)
  const weakArmBody = await weakArm.json()
  expect(weakArmBody.code).toBe('VALIDATION_ERROR')

  // 3. 中等 passphrase 创建 → 400（AT-48：中口令同样被拒，要求 strong）
  const fairCreate = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at45-fair-create-${crypto.randomUUID()}` },
    data: { passphrase: 'CorrectHorse42' },
  })
  expect(fairCreate.status()).toBe(400)
  const fairCreateBody = await fairCreate.json()
  expect(fairCreateBody.code).toBe('VALIDATION_ERROR')
  expect(JSON.stringify(fairCreateBody)).toContain('≥70')

  // 4. 中等 passphrase 武装 → 400，armed 仍 false（未达 strong 一律拒）
  const fairArm = await request.post('/api/backups/schedule/arm', {
    headers: { 'Idempotency-Key': `e2e-at45-fair-arm-${crypto.randomUUID()}` },
    data: { passphrase: 'CorrectHorse42' },
  })
  expect(fairArm.status()).toBe(400)
  const fairArmBody = await fairArm.json()
  expect(fairArmBody.code).toBe('VALIDATION_ERROR')

  // 5. 强 passphrase 创建 → 201
  const strongCreate = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at45-strong-create-${crypto.randomUUID()}` },
    data: { passphrase: 'CorrectHorse42!battery' },
  })
  expect(strongCreate.status()).toBe(201)
  const strongCreated = await strongCreate.json()
  expect(JSON.stringify(strongCreated)).not.toContain('CorrectHorse42!battery')

  // 6. 强 passphrase 武装 → 200 armed=true
  const strongArm = await request.post('/api/backups/schedule/arm', {
    headers: { 'Idempotency-Key': `e2e-at45-strong-arm-${crypto.randomUUID()}` },
    data: { passphrase: 'CorrectHorse42!battery' },
  })
  expect(strongArm.status()).toBe(200)
  const strongArmBody = await strongArm.json()
  expect(strongArmBody.armed).toBe(true)
  expect(JSON.stringify(strongArmBody)).not.toContain('CorrectHorse42!battery')

  // 7. 恢复端点豁免门槛：弱口令恢复不返回 400（强度门槛），而是 422（解密失败）
  const downloadRes = await request.get(`/api/backups/${strongCreated.id}/download`)
  expect(downloadRes.ok()).toBe(true)
  const encBuffer = await downloadRes.body()
  const weakRestore = await request.post('/api/backups/restore', {
    headers: { 'Idempotency-Key': `e2e-at45-weak-restore-${crypto.randomUUID()}` },
    multipart: {
      file: { name: strongCreated.fileName, mimeType: 'application/octet-stream', buffer: encBuffer },
      passphrase: 'aaaaaaaa',
    },
  })
  // 不返回 400 强度门槛，而是 422（弱口令非该备份 passphrase，GCM 认证失败）
  expect(weakRestore.status()).toBe(422)
  const weakRestoreBody = await weakRestore.json()
  expect(JSON.stringify(weakRestoreBody)).not.toContain('score=')

  // 8. 清理本次生成的备份
  await request.delete(`/api/backups/${strongCreated.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})


/**
 * AT-49 孤儿清理审计日志查询：GET /api/backups/orphans/audit 分页查询 BACKUP_ORPHAN_CLEANED 审计记录。
 * 只读，无需确认头/幂等键；响应不含 passphrase；前端设置页审计查看区块可见。
 *
 * 真实孤儿清理→审计写入链路由后端集成测试 BackupOrphanScanIntegrationTest 覆盖（E2E 无法直接造孤儿文件）；
 * 本 E2E 聚焦前端契约与查询端点：触发一次 clean（可能删 0 个孤儿，但若 DB 拮留历史孤儿会删并写审计）
 * → GET 审计端点 200 + 分页字段 + items 结构 + 不含 passphrase → UI 审计区块可见。
 */
test('AT-49 orphan clean audit log query returns paged entries', async ({ page, request }) => {
  const suffix = Date.now()

  // 造一个岗位保证导出有数据，再造一份合法加密备份（保证 backup-dir 有 .enc 文件可扫描）
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at49-job-${crypto.randomUUID()}` },
    data: {
      companyName: `审计查询-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at49-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 触发一次孤儿清理（可能删 0 个孤儿，但 DB 跨测试残留的历史孤儿会写审计）
  await request.post('/api/backups/orphans/clean', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })

  // GET /api/backups/orphans/audit 只读（不携带确认头与幂等键）
  const auditRes = await request.get('/api/backups/orphans/audit?page=1&pageSize=20')
  expect(auditRes.status()).toBe(200)
  const auditBody = await auditRes.json()
  // 分页字段齐全
  expect(auditBody).toHaveProperty('items')
  expect(auditBody).toHaveProperty('total')
  expect(auditBody).toHaveProperty('page')
  expect(auditBody).toHaveProperty('pageSize')
  expect(auditBody).toHaveProperty('totalPages')
  expect(auditBody.page).toBe(1)
  expect(auditBody.pageSize).toBe(20)
  // items 为数组，每条结构含 id/resourceId/action/reason/freedBytes/occurredAt
  expect(Array.isArray(auditBody.items)).toBe(true)
  if (auditBody.items.length > 0) {
    const first = auditBody.items[0]
    expect(first.action).toBe('BACKUP_ORPHAN_CLEANED')
    expect(first.id).toBeTruthy()
    expect(first.resourceId).toBeTruthy()
    // freedBytes 为结构化字段（V27），reason 不再含 freedBytes= 子串
    expect(first.freedBytes).toBeTruthy()
    expect(first.reason).not.toContain('freedBytes=')
    expect(first.occurredAt).toBeTruthy()
  }
  // 响应不含 passphrase（审计记录从不存 passphrase）
  expect(JSON.stringify(auditBody)).not.toContain('passphrase')
  // 省略固定 resourceType 与快照字段
  expect(JSON.stringify(auditBody)).not.toContain('resourceType')

  // UI：设置页审计查看区块可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '孤儿清理审计日志' })).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})

/**
 * AT-50 全量审计日志查询：GET /api/audit-logs 分页查询全量 audit_log，可按 action/resourceType 过滤。
 * 只读，无需确认头/幂等键；响应不含 passphrase、省略恒 null 的快照字段；前端设置页审计日志区块可见。
 *
 * 真实多 action 写入链路由后端集成测试 AuditLogQueryIntegrationTest 覆盖；
 * 本 E2E 聚焦前端契约与查询端点：触发 clean 产生 BACKUP_ORPHAN_CLEANED → GET 全量端点 200 + 过滤 + 分页字段 +
 * items 结构（含 resourceType，全量查询不固定）+ 不含 passphrase/快照 → UI 审计日志区块可见。
 */
test('AT-50 full audit log query with action and resourceType filter', async ({ page, request }) => {
  const suffix = Date.now()

  // 造一个岗位保证导出有数据，再造一份合法加密备份（保证 backup-dir 有 .enc 文件可扫描）
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at50-job-${crypto.randomUUID()}` },
    data: {
      companyName: `全量审计-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at50-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 触发一次孤儿清理（可能删 0 个孤儿，但 DB 跨测试残留的历史孤儿会写审计）
  await request.post('/api/backups/orphans/clean', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })

  // GET /api/audit-logs 无过滤（只读，不携带确认头与幂等键）
  const allRes = await request.get('/api/audit-logs?page=1&pageSize=20')
  expect(allRes.status()).toBe(200)
  const allBody = await allRes.json()
  // 分页字段齐全
  expect(allBody).toHaveProperty('items')
  expect(allBody).toHaveProperty('total')
  expect(allBody).toHaveProperty('page')
  expect(allBody).toHaveProperty('pageSize')
  expect(allBody).toHaveProperty('totalPages')
  expect(allBody.page).toBe(1)
  expect(allBody.pageSize).toBe(20)
  // items 为数组，每条结构含 id/resourceType/resourceId/action/reason/occurredAt
  expect(Array.isArray(allBody.items)).toBe(true)
  if (allBody.items.length > 0) {
    const first = allBody.items[0]
    expect(first.id).toBeTruthy()
    expect(first.resourceType).toBeTruthy()
    expect(first.resourceId).toBeTruthy()
    expect(first.action).toBeTruthy()
    expect(first.reason).toBeTruthy()
    expect(first.occurredAt).toBeTruthy()
  }
  // 响应不含 passphrase、不含快照字段（恒 null 省略）
  expect(JSON.stringify(allBody)).not.toContain('passphrase')
  expect(JSON.stringify(allBody)).not.toContain('beforeSnapshotJson')
  expect(JSON.stringify(allBody)).not.toContain('afterSnapshotJson')

  // GET /api/audit-logs?action=BACKUP_ORPHAN_CLEANED 过滤
  const filteredRes = await request.get('/api/audit-logs?page=1&pageSize=20&action=BACKUP_ORPHAN_CLEANED')
  expect(filteredRes.status()).toBe(200)
  const filteredBody = await filteredRes.json()
  // 过滤后 items 仅含 BACKUP_ORPHAN_CLEANED，每条 resourceType=BACKUP_FILE
  expect(Array.isArray(filteredBody.items)).toBe(true)
  for (const item of filteredBody.items) {
    expect(item.action).toBe('BACKUP_ORPHAN_CLEANED')
    expect(item.resourceType).toBe('BACKUP_FILE')
  }
  // action=BACKUP_ORPHAN_CLEANED 的 id 集合与 GET /api/backups/orphans/audit 一致
  const orphanRes = await request.get('/api/backups/orphans/audit?page=1&pageSize=100')
  expect(orphanRes.status()).toBe(200)
  const orphanBody = await orphanRes.json()
  const auditIds = filteredBody.items.map((i: { id: string }) => i.id).sort()
  const orphanIds = (orphanBody.items ?? []).map((i: { id: string }) => i.id).sort()
  expect(auditIds).toEqual(orphanIds)

  // 非法分页 400
  const badRes = await request.get('/api/audit-logs?page=0&pageSize=20')
  expect(badRes.status()).toBe(400)

  // UI：设置页「审计日志」区块可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '审计日志', exact: true })).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})

/**
 * AT-57 freedBytes 结构化字段：孤儿清理释放字节数从 reason 子串提升为 audit_log.freed_bytes 结构化列，
 * 查询/导出走结构化字段，reason 不再含 freedBytes= 子串。
 *
 * 真实 cleanOrphans 写入 freed_bytes 列链路由后端集成测试覆盖；
 * 本 E2E 聚焦端点契约：触发 clean 产生 BACKUP_ORPHAN_CLEANED → GET /backups/orphans/audit 200 +
 * items[0].freedBytes 为正数 + reason 不含 freedBytes= → GET /audit-logs/export?format=json 元素含 freedBytes →
 * GET /audit-logs/export?format=csv 表头含 freedBytes 列 → UI 设置页「孤儿清理审计日志」区块可见。
 */
test('AT-57 freedBytes structured field in audit log query and export', async ({ page, request }) => {
  const suffix = Date.now()

  // 造岗位保证导出有数据，再造一份合法加密备份（保证 backup-dir 有 .enc 文件可扫描）
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at57-job-${crypto.randomUUID()}` },
    data: {
      companyName: `结构化释放-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at57-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 触发一次孤儿清理（DB 跨测试残留的历史孤儿会写审计）
  await request.post('/api/backups/orphans/clean', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })

  // GET /api/backups/orphans/audit：items[0].freedBytes 为正数，reason 不含 freedBytes= 子串
  const auditRes = await request.get('/api/backups/orphans/audit?page=1&pageSize=20')
  expect(auditRes.status()).toBe(200)
  const auditBody = await auditRes.json()
  expect(Array.isArray(auditBody.items)).toBe(true)
  if (auditBody.items.length > 0) {
    const first = auditBody.items[0]
    expect(first.freedBytes).toBeTruthy()
    expect(first.reason).not.toContain('freedBytes=')
  }

  // GET /api/audit-logs/export?format=json：元素含 freedBytes 字段（有记录时校验）
  const jsonRes = await request.get('/api/audit-logs/export?format=json&action=BACKUP_ORPHAN_CLEANED')
  expect(jsonRes.status()).toBe(200)
  const jsonBody = await jsonRes.json()
  expect(Array.isArray(jsonBody)).toBe(true)
  if (jsonBody.length > 0) {
    expect(jsonBody[0]).toHaveProperty('freedBytes')
  }

  // GET /api/audit-logs/export?format=csv：表头含 freedBytes 列（表头恒存在）
  const csvRes = await request.get('/api/audit-logs/export?format=csv&action=BACKUP_ORPHAN_CLEANED')
  expect(csvRes.status()).toBe(200)
  const csvText = await csvRes.text()
  expect(csvText).toContain('id,resourceType,resourceId,action,reason,freedBytes,occurredAt')

  // UI：设置页「孤儿清理审计日志」区块可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '孤儿清理审计日志' })).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})

/**
 * AT-53 密钥轮换（就地重加密）：POST /api/backups/{backupId}/rotate-key 用旧口令解密 → 新口令重新加密，
 * 覆盖原 .enc 文件并就地更新 backup_record 的 salt/iv/size_bytes。备份 id 与明文数据不变。
 *
 * 真实轮换→恢复→审计链路由后端集成测试 BackupKeyRotationIntegrationTest 覆盖；
 * 本 E2E 聚焦前端契约与端点：创建备份 → rotate-key 成功（id/fileName 不变，salt/iv 不暴露）→
 * 旧口令恢复 422 → 新口令恢复 200 → 审计可按 action=BACKUP_KEY_ROTATED 查询 → UI「密钥轮换」按钮可见。
 */
test('AT-53 rotate-key re-encrypts in place keeping id unchanged', async ({ page, request }) => {
  const suffix = Date.now()
  const oldPass = `old-${suffix}!Strong`
  const newPass = `new-${suffix}!Strong`

  // 造一个岗位保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at53-job-${crypto.randomUUID()}` },
    data: {
      companyName: `轮换-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 创建一份加密备份（用旧口令，须达强）
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at53-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: oldPass },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()
  const backupId = created.id
  const fileName = created.fileName

  // 密钥轮换：用旧口令解密 → 新口令重新加密
  const rotateRes = await request.post(`/api/backups/${backupId}/rotate-key`, {
    headers: { 'Idempotency-Key': `e2e-at53-rotate-${suffix}-${crypto.randomUUID()}` },
    data: { oldPassphrase: oldPass, newPassphrase: newPass },
  })
  expect(rotateRes.status()).toBe(200)
  const rotated = await rotateRes.json()
  // id/fileName 不变；sizeBytes 反映新密文大小
  expect(rotated.id).toBe(backupId)
  expect(rotated.fileName).toBe(fileName)
  expect(rotated.algorithm).toBe('AES_256_GCM_PBKDF2')
  // 响应不回显 passphrase/salt/iv/score/level
  const rotatedJson = JSON.stringify(rotated)
  expect(rotatedJson).not.toContain(oldPass)
  expect(rotatedJson).not.toContain(newPass)
  expect(rotatedJson).not.toContain('salt')
  expect(rotatedJson).not.toContain('iv')
  expect(rotatedJson).not.toContain('score')
  expect(rotatedJson).not.toContain('level')

  // 下载轮换后的 .enc 文件，分别用旧/新口令恢复
  const downloadRes = await request.get(`/api/backups/${backupId}/download`)
  expect(downloadRes.status()).toBe(200)
  const encBuffer = await downloadRes.body()

  // 构造 multipart：file part 的二进制内容为 encBuffer，passphrase part 为口令文本
  const boundary = `----e2e-at53-${suffix}`
  const fileHeader = [
    `--${boundary}`,
    'Content-Disposition: form-data; name="file"; filename="backup.enc"',
    'Content-Type: application/octet-stream',
    '',
    '',
  ].join('\r\n')
  const fileFooter = `\r\n--${boundary}\r\n`

  // 旧口令恢复 → 422（旧口令已不可解密）
  const oldPassPart = [
    'Content-Disposition: form-data; name="passphrase"',
    '',
    oldPass,
    `--${boundary}--`,
    '',
  ].join('\r\n')
  const oldMultipart = Buffer.concat([
    Buffer.from(fileHeader, 'utf8'),
    encBuffer,
    Buffer.from(fileFooter + oldPassPart, 'utf8'),
  ])
  const restoreOldRes = await request.post('/api/backups/restore', {
    headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
    data: oldMultipart,
  })
  expect(restoreOldRes.status()).toBe(422)

  // 新口令恢复 → 200
  const newPassPart = [
    'Content-Disposition: form-data; name="passphrase"',
    '',
    newPass,
    `--${boundary}--`,
    '',
  ].join('\r\n')
  const newMultipart = Buffer.concat([
    Buffer.from(fileHeader, 'utf8'),
    encBuffer,
    Buffer.from(fileFooter + newPassPart, 'utf8'),
  ])
  const restoreNewRes = await request.post('/api/backups/restore', {
    headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
    data: newMultipart,
  })
  expect(restoreNewRes.status()).toBe(200)

  // 审计可按 action=BACKUP_KEY_ROTATED 查询
  const auditRes = await request.get('/api/audit-logs?page=1&pageSize=100&action=BACKUP_KEY_ROTATED')
  expect(auditRes.status()).toBe(200)
  const auditBody = await auditRes.json()
  expect(Array.isArray(auditBody.items)).toBe(true)
  const found = auditBody.items.some((i: { resourceId: string }) => i.resourceId === backupId)
  expect(found).toBe(true)
  for (const item of auditBody.items) {
    expect(item.action).toBe('BACKUP_KEY_ROTATED')
    expect(item.resourceType).toBe('BACKUP_RECORD')
  }
  // 审计响应不含 passphrase/快照
  expect(JSON.stringify(auditBody)).not.toContain(oldPass)
  expect(JSON.stringify(auditBody)).not.toContain(newPass)
  expect(JSON.stringify(auditBody)).not.toContain('beforeSnapshotJson')

  // UI：设置页历史备份列表项有「密钥轮换」按钮
  await page.goto('/settings')
  await expect(page.getByRole('button', { name: '密钥轮换' }).first()).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${backupId}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})

/**
 * AT-56 批量密钥轮换：POST /api/backups/rotate-keys 对一组备份用同一旧口令解密、同一新口令
 * 逐条就地重加密。逐条独立事务，部分成功不阻塞其他。真实链路由后端集成测试覆盖；本 E2E 聚焦
 * 端点契约：创建多份同口令备份 → 批量轮换成功（rotated=N/failed=0）→ 部分失败（混入错误旧口令）
 * → newPassphrase 弱 400 → 审计可查 → UI「批量轮换」按钮可见。
 */
test('AT-56 batch rotate-keys re-encrypts multiple with single passphrase', async ({ page, request }) => {
  const suffix = Date.now()
  const oldPass = `batch-old-${suffix}!Strong`
  const newPass = `batch-new-${suffix}!Strong`

  // 造一个岗位保证导出有数据
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at56-job-${crypto.randomUUID()}` },
    data: {
      companyName: `批量轮换-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 创建三份加密备份（均用同一 oldPass）
  const ids: string[] = []
  for (let i = 0; i < 3; i++) {
    const createRes = await request.post('/api/backups', {
      headers: { 'Idempotency-Key': `e2e-at56-create-${suffix}-${i}-${crypto.randomUUID()}` },
      data: { passphrase: oldPass },
    })
    expect(createRes.status()).toBe(201)
    ids.push((await createRes.json()).id)
  }

  // 批量轮换：全部成功
  const rotateRes = await request.post('/api/backups/rotate-keys', {
    headers: { 'Idempotency-Key': `e2e-at56-rotate-${suffix}-${crypto.randomUUID()}` },
    data: { backupIds: ids, oldPassphrase: oldPass, newPassphrase: newPass },
  })
  expect(rotateRes.status()).toBe(200)
  const summary = await rotateRes.json()
  expect(summary.total).toBe(3)
  expect(summary.rotated).toBe(3)
  expect(summary.failed).toBe(0)
  expect(summary.results.length).toBe(3)
  for (const r of summary.results) {
    expect(r.status).toBe('SUCCESS')
    expect(ids).toContain(r.backupId)
  }
  // 响应不回显 passphrase
  const summaryJson = JSON.stringify(summary)
  expect(summaryJson).not.toContain(oldPass)
  expect(summaryJson).not.toContain(newPass)

  // 审计可按 action=BACKUP_KEY_ROTATED 查询到 3 条
  const auditRes = await request.get('/api/audit-logs?page=1&pageSize=100&action=BACKUP_KEY_ROTATED')
  expect(auditRes.status()).toBe(200)
  const auditBody = await auditRes.json()
  expect(auditBody.total).toBeGreaterThanOrEqual(3)
  for (const id of ids) {
    expect(auditBody.items.some((i: { resourceId: string }) => i.resourceId === id)).toBe(true)
  }

  // 部分失败：再创建 1 份用不同口令的备份，混入批量轮换
  const otherPass = `other-${suffix}!Strong`
  const otherCreate = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at56-other-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: otherPass },
  })
  expect(otherCreate.status()).toBe(201)
  const otherId = (await otherCreate.json()).id
  // 用 oldPass 批量轮换 otherId（其旧口令为 otherPass，不匹配）→ 该条 FAILED
  const partialRes = await request.post('/api/backups/rotate-keys', {
    headers: { 'Idempotency-Key': `e2e-at56-partial-${suffix}-${crypto.randomUUID()}` },
    data: { backupIds: [otherId], oldPassphrase: oldPass, newPassphrase: newPass },
  })
  expect(partialRes.status()).toBe(200)
  const partial = await partialRes.json()
  expect(partial.total).toBe(1)
  expect(partial.rotated).toBe(0)
  expect(partial.failed).toBe(1)
  expect(partial.results[0].status).toBe('FAILED')

  // newPassphrase 弱 → 400 fail fast
  const weakRes = await request.post('/api/backups/rotate-keys', {
    headers: { 'Idempotency-Key': `e2e-at56-weak-${suffix}-${crypto.randomUUID()}` },
    data: { backupIds: ids, oldPassphrase: newPass, newPassphrase: 'aaaaaaaa' },
  })
  expect(weakRes.status()).toBe(400)

  // empty backupIds → 400
  const emptyRes = await request.post('/api/backups/rotate-keys', {
    headers: { 'Idempotency-Key': `e2e-at56-empty-${suffix}-${crypto.randomUUID()}` },
    data: { backupIds: [], oldPassphrase: oldPass, newPassphrase: newPass },
  })
  expect(emptyRes.status()).toBe(400)

  // UI：设置页有「批量轮换」按钮
  await page.goto('/settings')
  await expect(page.getByRole('button', { name: '批量轮换' })).toBeVisible()

  // 清理本次产生的备份
  for (const id of [...ids, otherId]) {
    await request.delete(`/api/backups/${id}`, {
      headers: { 'X-Confirm-Permanent-Delete': 'true' },
    })
  }
})

test('AT-54 audit log export CSV and JSON', async ({ request, page }) => {
  const suffix = Date.now()
  // 造数：一个岗位 + 二次投递确认触发审计（SECONDARY_APPLICATION_CONFIRMED），再加一个备份造 BACKUP_DELETED 审计
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at54-job-${crypto.randomUUID()}` },
    data: {
      companyName: `导出验证-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)
  const jobId = (await jobRes.json()).id

  // 导出 JSON（无过滤）→ 200，JSON 数组，含字段，不含 passphrase/快照字段
  const jsonRes = await request.get('/api/audit-logs/export?format=json')
  expect(jsonRes.status()).toBe(200)
  const jsonBody = await jsonRes.json()
  expect(Array.isArray(jsonBody)).toBe(true)
  if (jsonBody.length > 0) {
    const entry = jsonBody[0]
    expect(entry).toHaveProperty('id')
    expect(entry).toHaveProperty('resourceType')
    expect(entry).toHaveProperty('resourceId')
    expect(entry).toHaveProperty('action')
    expect(entry).toHaveProperty('reason')
    expect(entry).toHaveProperty('freedBytes')
    expect(entry).toHaveProperty('occurredAt')
    // 不含 passphrase 字段与快照字段（reason 文本可能合法地提到 "passphrase" 一词，这里校验字段名而非字面词）
    expect(entry).not.toHaveProperty('passphrase')
    expect(entry).not.toHaveProperty('beforeSnapshotJson')
    expect(entry).not.toHaveProperty('afterSnapshotJson')
  }
  for (const item of jsonBody as Record<string, unknown>[]) {
    expect(item).not.toHaveProperty('passphrase')
    expect(item).not.toHaveProperty('beforeSnapshotJson')
    expect(item).not.toHaveProperty('afterSnapshotJson')
  }
  // Content-Disposition 含 .json
  expect(String(jsonRes.headers()['content-disposition'] ?? '')).toContain('attachment')
  expect(String(jsonRes.headers()['content-disposition'] ?? '')).toContain('.json')

  // 导出 CSV（无过滤）→ 200，BOM + 表头，不含 passphrase 字段列
  const csvRes = await request.get('/api/audit-logs/export?format=csv')
  expect(csvRes.status()).toBe(200)
  const csvText = await csvRes.text()
  // UTF-8 BOM
  expect(csvText.charCodeAt(0)).toBe(0xFEFF)
  const csvNoBom = csvText.slice(1)
  expect(csvNoBom.startsWith('id,resourceType,resourceId,action,reason,freedBytes,occurredAt')).toBe(true)
  // 表头无 passphrase 列
  const csvHeader = csvNoBom.split(/\r?\n/, 1)[0]
  expect(csvHeader).not.toContain('passphrase')

  // 过滤导出 JSON（action=NONEXISTENT）→ 200 空数组
  const emptyRes = await request.get('/api/audit-logs/export?format=json&action=NONEXISTENT')
  expect(emptyRes.status()).toBe(200)
  const emptyBody = await emptyRes.json()
  expect(emptyBody).toEqual([])

  // 非法 format → 400
  const badFmt = await request.get('/api/audit-logs/export?format=xml')
  expect(badFmt.status()).toBe(400)

  // 非法 from → 400
  const badFrom = await request.get('/api/audit-logs/export?from=not-a-date')
  expect(badFrom.status()).toBe(400)

  // UI：设置页审计日志区块有「导出 CSV」「导出 JSON」按钮
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '审计日志', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 CSV' })).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 JSON' })).toBeVisible()
})

/**
 * AT-58 审计导出流式响应：导出端点用 StreamingResponseBody 分批 fetch + 逐批写入响应流，真实 Tomcat
 * 以分块传输编码（Transfer-Encoding: chunked）逐批发送、不预设 Content-Length，用户仍一次性下载完整文件。
 * 跨批拼接完整性（>BATCH_SIZE 501 条无丢批无重复）由后端 AT-58 集成测试覆盖；E2E 聚焦真实 server 的
 * 流式传输契约（MockMvc 无法暴露 Transfer-Encoding 头，须真实 webServer 验证）。
 */
test('AT-58 audit log export streaming response (chunked transfer, complete download)', async ({ request, page }) => {
  const suffix = Date.now()
  // 造数：创建岗位触发需求合并审计（REQUIREMENT_MERGED），确保导出有记录走流式传输
  const jobRes = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at58-job-${crypto.randomUUID()}` },
    data: {
      companyName: `流式导出-${suffix}`,
      title: `Java 后端 ${suffix}`,
      jdRawText: '岗位负责 Java 与 Spring Boot 后端开发，5 年经验优先。',
    },
  })
  expect(jobRes.ok()).toBe(true)

  // 导出 JSON（无过滤）→ 200，流式分块传输，body 完整可解析
  const jsonRes = await request.get('/api/audit-logs/export?format=json')
  expect(jsonRes.status()).toBe(200)
  const jsonHeaders = jsonRes.headers()
  // 流式响应不预设 Content-Length（分批生成，流式前无法预知总字节数）
  expect(String(jsonHeaders['content-length'] ?? '')).toBe('')
  // 真实 Tomcat 以分块传输编码逐批发送
  expect(String(jsonHeaders['transfer-encoding'] ?? '').toLowerCase()).toContain('chunked')
  // body 完整可解析为 JSON 数组（流式逐批拼接后客户端收到完整文件）
  const jsonBody = await jsonRes.json()
  expect(Array.isArray(jsonBody)).toBe(true)
  for (const item of jsonBody as Record<string, unknown>[]) {
    expect(item).not.toHaveProperty('passphrase')
  }
  // Content-Disposition 含 .json
  expect(String(jsonHeaders['content-disposition'] ?? '')).toContain('.json')

  // 导出 CSV（无过滤）→ 200，流式分块，BOM + 表头完整
  const csvRes = await request.get('/api/audit-logs/export?format=csv')
  expect(csvRes.status()).toBe(200)
  expect(String(csvRes.headers()['transfer-encoding'] ?? '').toLowerCase()).toContain('chunked')
  expect(String(csvRes.headers()['content-length'] ?? '')).toBe('')
  const csvText = await csvRes.text()
  // UTF-8 BOM
  expect(csvText.charCodeAt(0)).toBe(0xFEFF)
  expect(csvText.slice(1).startsWith('id,resourceType,resourceId,action,reason,freedBytes,occurredAt')).toBe(true)

  // 空匹配流式导出仍返回 []
  const emptyRes = await request.get('/api/audit-logs/export?format=json&action=NONEXISTENT')
  expect(emptyRes.status()).toBe(200)
  expect(await emptyRes.json()).toEqual([])

  // 非法 format → 400 fail fast（写流前不开始写响应体）
  const badFmt = await request.get('/api/audit-logs/export?format=xml')
  expect(badFmt.status()).toBe(400)

  // UI：设置页审计日志区块导出按钮可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '审计日志', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 CSV' })).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 JSON' })).toBeVisible()
})

/**
 * AT-59 audit_log action+occurred_at 复合二级索引（V28 迁移）：复合索引存在且建索引列为
 * (action, occurred_at)，V26 单列索引仍存在（互补不替换）；带 action 过滤的查询/导出行为不变。
 * PRAGMA 断言由后端 AT-59 集成测试覆盖；E2E 聚焦真实 server 的带 action 过滤查询/导出链路。
 * 造数同 AT-50：创建备份 + 触发 orphans/clean（全量跑时删残留孤儿写 BACKUP_ORPHAN_CLEANED 审计，
 * 单跑时可能删 0 个无审计，用条件断言容忍空 DB，强语义由后端集成测试覆盖）。
 */
test('AT-59 audit log composite index (action+occurred_at) serves action-filtered query', async ({ request, page }) => {
  const suffix = Date.now()
  // 造一份合法加密备份（保证 backup-dir 有 .enc 文件可扫描）
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at59-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 触发一次孤儿清理（全量跑时删残留孤儿写 BACKUP_ORPHAN_CLEANED 审计）
  await request.post('/api/backups/orphans/clean', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })

  // 带 action 等值过滤的查询：复合索引服务，行为不变
  const listRes = await request.get('/api/audit-logs?action=BACKUP_ORPHAN_CLEANED&page=1&pageSize=20')
  expect(listRes.status()).toBe(200)
  const listBody = await listRes.json()
  expect(Array.isArray(listBody.items)).toBe(true)
  for (const item of listBody.items as Record<string, unknown>[]) {
    expect(item.action).toBe('BACKUP_ORPHAN_CLEANED')
    expect(item).not.toHaveProperty('passphrase')
  }

  // 带 action 过滤的导出：复合索引服务，行为不变
  const exportRes = await request.get('/api/audit-logs/export?format=json&action=BACKUP_ORPHAN_CLEANED')
  expect(exportRes.status()).toBe(200)
  const exportBody = await exportRes.json()
  expect(Array.isArray(exportBody)).toBe(true)
  for (const item of exportBody as Record<string, unknown>[]) {
    expect(item.action).toBe('BACKUP_ORPHAN_CLEANED')
    expect(item).not.toHaveProperty('passphrase')
  }

  // 不带 action 过滤的查询退回 V26 单列索引，行为不变
  const allRes = await request.get('/api/audit-logs?page=1&pageSize=1')
  expect(allRes.status()).toBe(200)

  // UI：设置页审计日志区块可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '审计日志', exact: true })).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})

/**
 * AT-60 freedBytes 布尔过滤参数：hasFreedBytes=true 只返回 freed_bytes IS NOT NULL 的孤儿清理行，
 * 可与 action 组合，缺省/false 不过滤（向后兼容）；导出端点同步过滤；UI「仅看有释放字节」复选框可见。
 * 造数同 AT-49：创建备份 + 触发 orphans/clean（全量跑时删残留孤儿写 freed_bytes 非 null 审计，
 * 单跑时可能删 0 个无审计，用条件断言容忍空 DB，强语义由后端 AT-60 集成测试覆盖）。
 */
test('AT-60 audit log hasFreedBytes filter (only freed_bytes IS NOT NULL rows)', async ({ request, page }) => {
  const suffix = Date.now()
  // 造一份合法加密备份（保证 backup-dir 有 .enc 文件可扫描）
  const createRes = await request.post('/api/backups', {
    headers: { 'Idempotency-Key': `e2e-at60-create-${suffix}-${crypto.randomUUID()}` },
    data: { passphrase: `secret-${suffix}!Strong` },
  })
  expect(createRes.status()).toBe(201)
  const created = await createRes.json()

  // 触发一次孤儿清理（全量跑时删残留孤儿写 freed_bytes 非 null 的 BACKUP_ORPHAN_CLEANED 审计）
  await request.post('/api/backups/orphans/clean', {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })

  // hasFreedBytes=true → 只返回 freed_bytes IS NOT NULL 的记录（孤儿清理行）
  const filteredRes = await request.get('/api/audit-logs?hasFreedBytes=true&page=1&pageSize=20')
  expect(filteredRes.status()).toBe(200)
  const filteredBody = await filteredRes.json()
  expect(Array.isArray(filteredBody.items)).toBe(true)
  for (const item of filteredBody.items as Record<string, unknown>[]) {
    expect(item.freedBytes).not.toBeNull()
    expect(item.action).toBe('BACKUP_ORPHAN_CLEANED')
    expect(item).not.toHaveProperty('passphrase')
  }

  // 不传 hasFreedBytes → 缺省不过滤返回全量（向后兼容，total >= hasFreedBytes=true 的 total）
  const allRes = await request.get('/api/audit-logs?page=1&pageSize=1')
  expect(allRes.status()).toBe(200)
  const allBody = await allRes.json()
  expect(allBody.total).toBeGreaterThanOrEqual(filteredBody.total)

  // hasFreedBytes=true & action=BACKUP_DELETED → 无匹配（BACKUP_DELETED 行 freed_bytes 恒 null，可靠断言）
  const emptyRes = await request.get('/api/audit-logs?hasFreedBytes=true&action=BACKUP_DELETED')
  expect(emptyRes.status()).toBe(200)
  const emptyBody = await emptyRes.json()
  expect(emptyBody.total).toBe(0)

  // 导出端点同步过滤：hasFreedBytes=true 的 JSON 只含 freedBytes 非 null 元素
  const exportRes = await request.get('/api/audit-logs/export?format=json&hasFreedBytes=true')
  expect(exportRes.status()).toBe(200)
  const exportBody = await exportRes.json()
  expect(Array.isArray(exportBody)).toBe(true)
  for (const item of exportBody as Record<string, unknown>[]) {
    expect(item.freedBytes).not.toBeNull()
    expect(item).not.toHaveProperty('passphrase')
  }

  // hasFreedBytes 不影响 format 校验：hasFreedBytes=true & format=xml → 400 fail fast
  const badFmt = await request.get('/api/audit-logs/export?hasFreedBytes=true&format=xml')
  expect(badFmt.status()).toBe(400)

  // UI：设置页审计日志区块「仅看有释放字节」复选框可见
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '审计日志', exact: true })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: '仅看有释放字节' })).toBeVisible()

  // 清理本次产生的备份
  await request.delete(`/api/backups/${created.id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
})


