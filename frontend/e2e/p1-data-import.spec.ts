import { expect, test, type APIRequestContext } from '@playwright/test'

const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi

async function createJob(request: APIRequestContext, suffix: number) {
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1-import-job-${crypto.randomUUID()}` },
    data: {
      companyName: `导入恢复科技-${suffix}`,
      title: 'P1 数据导入岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
      source: 'E2E P1 数据导入',
      location: '上海',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  return (await jobResponse.json()) as { id: string; title: string }
}

async function exportPackage(request: APIRequestContext): Promise<string> {
  const exportResponse = await request.post('/api/data-exports', {
    headers: { 'Idempotency-Key': `e2e-p1-import-export-${crypto.randomUUID()}` },
    data: { format: 'JSON' },
  })
  expect(exportResponse.ok(), `POST /api/data-exports returned ${exportResponse.status()}`).toBe(true)
  const created = (await exportResponse.json()) as { id: string }
  const downloadResponse = await request.get(`/api/data-exports/${created.id}/download`)
  expect(downloadResponse.ok(), `GET download returned ${downloadResponse.status()}`).toBe(true)
  return downloadResponse.text()
}

/**
 * 重映射全部 UUID（保持引用关系一致），并改写业务唯一键 normalized_name，
 * 使数据包相对当前库全部为“缺失行”，恢复时全部可插入。
 */
function remapPackage(packageJson: string): { json: string; mapping: Map<string, string> } {
  const mapping = new Map<string, string>()
  const remapped = packageJson.replace(UUID_RE, (uuid) => {
    if (!mapping.has(uuid)) mapping.set(uuid, crypto.randomUUID())
    return mapping.get(uuid)!
  })
  const pkg = JSON.parse(remapped) as {
    tables: Record<string, Array<Record<string, unknown>>>
  }
  for (const table of ['knowledge_point', 'skill']) {
    for (const row of pkg.tables[table] ?? []) {
      if (typeof row.normalized_name === 'string' && typeof row.id === 'string') {
        row.normalized_name = `${row.normalized_name}-导入副本-${row.id.slice(0, 8)}`
      }
    }
  }
  for (const row of pkg.tables.skill_alias ?? []) {
    if (typeof row.normalized_alias === 'string' && typeof row.id === 'string') {
      row.normalized_alias = `${row.normalized_alias}-导入副本-${row.id.slice(0, 8)}`
    }
  }
  return { json: JSON.stringify(pkg), mapping }
}

test('P1 data import validates, previews and restores an exported package', async ({ page, request }) => {
  const suffix = Date.now()
  const job = await createJob(request, suffix)
  const packageJson = await exportPackage(request)
  const { json, mapping } = remapPackage(packageJson)
  // 通过映射表精确定位本用例岗位的重映射 id（共享库中数据包可能含其他用例数据）
  const newJobId = mapping.get(job.id)
  expect(newJobId, '本用例岗位的重映射 id').toBeTruthy()

  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '数据导入与恢复' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles({
    name: 'jobhub-package.json',
    mimeType: 'application/json',
    buffer: Buffer.from(json, 'utf-8'),
  })

  await page.getByRole('button', { name: '预检并预览' }).click()
  await expect(page.getByText('预检通过')).toBeVisible()
  await expect(page.getByText(/数据包共 \d+ 行，将插入 \d+ 行/)).toBeVisible()

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '确认恢复' }).click()
  // toast 与结果横幅同时含“恢复完成”文案，取首个即可
  await expect(page.getByText(/恢复完成：插入 \d+ 行/).first()).toBeVisible()
  await expect(page.getByText('失败 0')).toBeVisible()

  // 恢复后的岗位可通过业务接口读取
  const restoredJob = await request.get(`/api/jobs/${newJobId}`)
  expect(restoredJob.status()).toBe(200)
  expect(((await restoredJob.json()) as { companyName: string }).companyName).toBe(`导入恢复科技-${suffix}`)

  // 重复恢复同一数据包 → 全部重复跳过，插入 0 行（幂等）
  await page.getByRole('button', { name: '预检并预览' }).click()
  await expect(page.getByText(/重复跳过 \d+/).first()).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '确认恢复' }).click()
  await expect(page.getByText(/恢复完成：插入 0 行/).first()).toBeVisible()
})
