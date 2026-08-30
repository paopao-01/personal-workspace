import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

function formField(page: Page, label: string) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return page.locator('.form-field').filter({ has: page.locator('.form-label').filter({ hasText: new RegExp(`^${escaped}\\*?$`) }) }).first()
}

async function createInterviewWithChecklist(request: APIRequestContext, suffix: number) {
  const job = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `gap-job-${suffix}-${crypto.randomUUID()}` },
    data: { companyName: `缺口验证科技-${suffix}`, title: '后端工程师', jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot。', source: 'E2E gap closures' },
  })
  expect(job.ok()).toBe(true)
  const jobBody = (await job.json()) as { id: string }
  const application = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `gap-app-${suffix}-${crypto.randomUUID()}` },
    data: { jobId: jobBody.id, appliedAt: '2026-08-01', channel: 'E2E', nextAction: '准备面试' },
  })
  expect(application.ok()).toBe(true)
  let applicationBody = (await application.json()) as { id: string; version: number }
  for (const targetStatus of ['APPLIED', 'RESUME_PASSED'] as const) {
    const response = await request.post(`/api/applications/${applicationBody.id}/transition`, {
      headers: { 'Idempotency-Key': `gap-transition-${targetStatus}-${suffix}-${crypto.randomUUID()}`, 'If-Match-Version': String(applicationBody.version) },
      data: { targetStatus },
    })
    expect(response.ok()).toBe(true)
    applicationBody = (await response.json()) as typeof applicationBody
  }
  const interview = await request.post('/api/interviews', {
    headers: { 'Idempotency-Key': `gap-interview-${suffix}-${crypto.randomUUID()}` },
    data: { applicationId: applicationBody.id, roundName: `缺口验证面试-${suffix}`, startsAt: '2099-12-01T10:00:00Z', eventTimeZone: 'Asia/Shanghai', preparationChecklist: [`准备事项-${suffix}`] },
  })
  expect(interview.ok()).toBe(true)
  return (await interview.json()) as { id: string; version: number }
}

test('P1 gap closures expose checklist, task detail and evidence skill association', async ({ page, request }) => {
  const suffix = Date.now()
  const interview = await createInterviewWithChecklist(request, suffix)

  await page.goto(`/interviews/${interview.id}/preparation`)
  const checklist = page.getByRole('checkbox').first()
  await expect(checklist).not.toBeChecked()
  const checklistUpdate = page.waitForResponse((response) => response.url().includes('/checklist/') && response.request().method() === 'PUT')
  await checklist.click()
  expect((await checklistUpdate).status()).toBe(200)
  await expect(checklist).toBeChecked()
  await expect(page.getByText('准备事项已完成')).toBeVisible()

  const knowledgePoint = await request.post('/api/knowledge-points', {
    headers: { 'Idempotency-Key': `gap-kp-${suffix}-${crypto.randomUUID()}` },
    data: { name: `缺口知识点-${suffix}` },
  })
  expect(knowledgePoint.ok()).toBe(true)
  const knowledgePointBody = (await knowledgePoint.json()) as { id: string }
  const task = await request.post('/api/tasks', {
    headers: { 'Idempotency-Key': `gap-task-${suffix}-${crypto.randomUUID()}` },
    data: { title: `缺口任务-${suffix}`, type: '知识点巩固', knowledgePointIds: [knowledgePointBody.id], learningGoal: '完成一轮演练', acceptanceCriteria: '能够解释核心方案', verificationMethod: '口述演练', estimatedMinutes: 30 },
  })
  expect(task.ok()).toBe(true)
  const taskBody = (await task.json()) as { id: string }
  await page.goto('/tasks')
  await page.getByRole('link', { name: `缺口任务-${suffix}` }).click()
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(formField(page, '任务类型').locator('input')).toHaveValue('知识点巩固')
  await fillField(page, '学习目标', '完成两轮演练并记录卡点')
  await page.getByRole('button', { name: '保存修改' }).click()
  await expect(page.getByText('任务已保存')).toBeVisible()
  const savedTask = await (await request.get(`/api/tasks/${taskBody.id}`)).json() as { learningGoal: string }
  expect(savedTask.learningGoal).toBe('完成两轮演练并记录卡点')

  const skill = await request.post('/api/skills', { data: { name: `缺口技能-${suffix}` } })
  expect(skill.ok()).toBe(true)
  const skillBody = (await skill.json()) as { skillId: string }
  await page.goto('/projects')
  await fillField(page, '证据名称', `缺口证据-${suffix}`)
  await fillField(page, '链接或本地路径', 'https://example.com/evidence')
  await page.getByRole('checkbox', { name: `缺口技能-${suffix}` }).check()
  await page.getByRole('button', { name: '创建证据引用' }).click()
  await expect(page.getByText(`缺口证据-${suffix}`, { exact: true })).toBeVisible()
  const evidence = (await (await request.get('/api/evidence')).json()) as Array<{ title: string; skillIds: string[] }>
  expect(evidence.find((item) => item.title === `缺口证据-${suffix}`)?.skillIds).toContain(skillBody.skillId)
})

async function fillField(page: Page, label: string, value: string) {
  await formField(page, label).locator('input, textarea').first().fill(value)
}
