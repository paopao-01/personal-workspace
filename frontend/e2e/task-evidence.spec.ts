import { expect, test, type APIRequestContext } from '@playwright/test'

async function createEvidence(request: APIRequestContext, title: string) {
  const response = await request.post('/api/evidence', {
    headers: { 'Idempotency-Key': `e2e-task-evidence-evidence-${crypto.randomUUID()}` },
    data: { type: 'GIT_REPOSITORY', title, urlOrPath: 'https://github.com/user/e2e-task-evidence' },
  })
  expect(response.ok(), `POST /api/evidence returned ${response.status()}`).toBe(true)
  const body = (await response.json()) as { id: string }
  return body.id
}

async function createTask(request: APIRequestContext, title: string) {
  const response = await request.post('/api/tasks', {
    headers: { 'Idempotency-Key': `e2e-task-evidence-task-${crypto.randomUUID()}` },
    data: { title },
  })
  expect(response.ok(), `POST /api/tasks returned ${response.status()}`).toBe(true)
  const body = (await response.json()) as { id: string }
  return body.id
}

async function attachEvidence(request: APIRequestContext, taskId: string, evidenceId: string) {
  const response = await request.post(`/api/tasks/${taskId}/evidence`, {
    headers: { 'Idempotency-Key': `e2e-task-evidence-attach-${crypto.randomUUID()}` },
    data: { evidenceId },
  })
  expect(response.ok(), `POST /api/tasks/{id}/evidence returned ${response.status()}`).toBe(true)
  return response
}

test('AT-65 task evidence association attach/list/detach/idempotent', async ({ page, request }) => {
  const suffix = Date.now()
  const evidenceTitle = `E2E 任务证据-${suffix}`
  const evidenceId = await createEvidence(request, evidenceTitle)
  const taskId = await createTask(request, `E2E 证据任务-${suffix}`)

  // 挂载证据，列表出现，刷新后仍在
  await attachEvidence(request, taskId, evidenceId)
  const listResponse = await request.get(`/api/tasks/${taskId}/evidence`)
  expect(listResponse.ok()).toBe(true)
  const list = (await listResponse.json()) as Array<{ id: string; title: string; trashed: boolean }>
  expect(list).toHaveLength(1)
  expect(list[0].id).toBe(evidenceId)
  expect(list[0].trashed).toBe(false)

  // 重复挂载幂等（不报错，仍只有一条）
  await attachEvidence(request, taskId, evidenceId)
  const listAfterReplay = (await (await request.get(`/api/tasks/${taskId}/evidence`)).json()) as Array<{ id: string }>
  expect(listAfterReplay).toHaveLength(1)

  // 详情接口回显 evidenceRefs
  const detailResponse = await request.get(`/api/tasks/${taskId}`)
  const detail = (await detailResponse.json()) as { evidenceRefs?: Array<{ id: string }> }
  expect(detail.evidenceRefs ?? []).toHaveLength(1)

  // UI：任务详情页「完成证据」区块可见且展示证据标题
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '完成证据' })).toBeVisible()
  await expect(page.getByText(evidenceTitle)).toBeVisible()

  // UI 卸载后列表减少
  await page.getByRole('button', { name: '卸载' }).click()
  await expect(page.getByText('证据已卸载')).toBeVisible()
  await expect(page.getByText('暂未挂载完成证据')).toBeVisible()

  // 再次卸载不存在的关联返回 404
  const detachAgain = await request.delete(`/api/tasks/${taskId}/evidence/${evidenceId}`, {
    headers: { 'Idempotency-Key': `e2e-task-evidence-detach-again-${crypto.randomUUID()}` },
  })
  expect(detachAgain.status()).toBe(404)
})

test('AT-65 task evidence unknown evidence/task returns 404', async ({ request }) => {
  const taskId = await createTask(request, `E2E 404 任务-${Date.now()}`)
  const unknownId = '99999999-9999-9999-9999-999999999999'

  const attachUnknownEvidence = await request.post(`/api/tasks/${taskId}/evidence`, {
    headers: { 'Idempotency-Key': `e2e-task-evidence-unknown-evidence-${crypto.randomUUID()}` },
    data: { evidenceId: unknownId },
  })
  expect(attachUnknownEvidence.status()).toBe(404)

  const attachUnknownTask = await request.post(`/api/tasks/${unknownId}/evidence`, {
    headers: { 'Idempotency-Key': `e2e-task-evidence-unknown-task-${crypto.randomUUID()}` },
    data: { evidenceId: unknownId },
  })
  expect(attachUnknownTask.status()).toBe(404)
})
