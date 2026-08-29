import { expect, test } from '@playwright/test'

test('AT-24 export contains business data and excludes operational records', async ({
  page,
  request,
}) => {
  test.setTimeout(120_000)
  const suffix = Date.now()
  const companyName = `导出验证-${suffix}`

  const jobResponse = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at24-job-${crypto.randomUUID()}` },
    data: {
      companyName,
      title: 'AT24 岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot 和 MySQL。',
      source: 'E2E AT-24',
    },
  })
  expect(jobResponse.ok(), `POST /api/jobs returned ${jobResponse.status()}`).toBe(true)

  await page.goto('/settings')
  await expect(page.getByText('数据范围：岗位与要求、投递、面试、复盘、问题、知识点、学习任务、技能、项目案例与证据的完整')).toBeVisible()
  await expect(page.getByText('不包含：访问令牌、密钥、应用运行日志、幂等记录和未确认的 AI 输入输出。')).toBeVisible()

  await page.getByRole('button', { name: '创建 JSON 导出' }).click()
  await expect(page.getByText('导出完成').first()).toBeVisible()
  await expect(page.getByText('导出失败')).toHaveCount(0)

  const downloadHref = await page
    .getByRole('link', { name: '下载导出文件' })
    .getAttribute('href')
  expect(downloadHref, 'downloadUrl should be present after export succeeds').toBeTruthy()

  const fileResponse = await request.get(downloadHref!)
  expect(fileResponse.ok(), `download returned ${fileResponse.status()}`).toBe(true)
  const payload = await fileResponse.text()
  expect(payload).toContain(companyName)
  expect(payload).toContain('job_posting')
  expect(payload).not.toContain('idempotency_record')
  expect(payload).not.toContain('"idempotency_key"')
  expect(payload).not.toContain('"audit_log"')
})
