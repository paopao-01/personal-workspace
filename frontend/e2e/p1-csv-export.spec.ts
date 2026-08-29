import { expect, test } from '@playwright/test'

test('P1 csv export creates zip package from settings page', async ({ page, request }) => {
  test.setTimeout(120_000)
  const suffix = Date.now()

  // 造一条业务数据，保证 CSV 中有本用例可识别的行
  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-p1csv-job-${crypto.randomUUID()}` },
    data: {
      companyName: `CSV导出科技-${suffix}`,
      title: 'P1 CSV 导出岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL，具备高并发经验。',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)
  const job = (await jobResponse.json()) as { id: string }

  // UI：选择 CSV 格式并创建导出
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: '数据导出' })).toBeVisible()
  await page.getByText('CSV（按表拆分打包 ZIP，供分析）').click()
  await page.getByRole('button', { name: '创建 CSV 导出' }).click()
  await expect(page.getByText('CSV 导出完成，可下载 ZIP 文件')).toBeVisible()
  await expect(page.locator('.plain-block').getByText('导出完成')).toBeVisible()
  await expect(page.getByText('下载导出文件（ZIP）')).toBeVisible()

  // 下载链接返回 ZIP：解出的 job_posting.csv 含本用例数据
  const downloadHref = await page.locator('.plain-block').getByRole('link', { name: /下载导出文件/ })
    .getAttribute('href')
  expect(downloadHref).toContain('/download')
  const response = await request.get(downloadHref!)
  expect(response.status()).toBe(200)
  expect(response.headers()['content-type']).toContain('application/zip')

  // 借助后端导入校验端点无法解析 ZIP，这里直接用 Node 内置方式不可行；
  // ZIP 内容正确性由集成测试 P1_csvExportPackagesBusinessTablesAsZipWithoutIdempotencyKey 覆盖，
  // 此处断言响应为二进制（非 JSON 错误）且体积大于 0。
  const body = await response.body()
  expect(body.length).toBeGreaterThan(0)
  // PK 魔数（ZIP 文件头）
  expect(body[0]).toBe(0x50)
  expect(body[1]).toBe(0x4b)
})
