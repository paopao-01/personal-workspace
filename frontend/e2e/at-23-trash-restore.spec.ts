import { expect, test, type Page } from '@playwright/test'

async function fillField(page: Page, label: string, value: string) {
  const field = page.locator('.form-field').filter({ hasText: label }).first()
  const input = field.locator('input, textarea').first()
  await input.fill(value)
}

test('AT-23 trash keeps references, restores with unchanged id and guards permanent delete', async ({
  page,
  request,
}) => {
  test.setTimeout(120_000)
  const suffix = Date.now()
  const evidenceTitle = `AT23 被引用证据-${suffix}`
  const standaloneTitle = `AT23 独立证据-${suffix}`

  // 准备：创建两条证据与一个引用证据 A 的项目案例
  await page.goto('/projects')
  await fillField(page, '证据名称', evidenceTitle)
  await fillField(page, '链接或本地路径', 'https://github.com/user/at23')
  await page.getByRole('button', { name: '创建证据引用' }).click()
  await expect(page.locator('.requirement-row').filter({ hasText: evidenceTitle })).toBeVisible()
  await fillField(page, '证据名称', standaloneTitle)
  await fillField(page, '链接或本地路径', 'https://github.com/user/at23-standalone')
  await page.getByRole('button', { name: '创建证据引用' }).click()
  await expect(page.locator('.requirement-row').filter({ hasText: standaloneTitle })).toBeVisible()
  await fillField(page, '项目名称', `AT23 项目-${suffix}`)
  await fillField(page, '使用场景', 'AT23 使用场景描述。')
  await fillField(page, '采取方案', 'AT23 采取方案描述。')
  await fillField(page, '解决问题', 'AT23 解决问题描述。')
  await page.locator('.evidence-picker-item').filter({ hasText: evidenceTitle }).locator('input').check()
  await page.getByRole('button', { name: '创建项目案例' }).click()
  const projectRow = page.locator('.requirement-row').filter({ hasText: `AT23 项目-${suffix}` })
  await expect(projectRow).toBeVisible()

  // 删除被引用证据：确认框展示直接影响
  let dialogMessage = ''
  page.on('dialog', (dialog) => {
    dialogMessage = dialog.message()
    void dialog.accept()
  })
  const evidenceRow = page
    .locator('.requirement-row')
    .filter({ hasText: evidenceTitle })
    .filter({ hasText: '引用（仅文本）' })
  await evidenceRow.getByRole('button', { name: '删除' }).click()
  await expect
    .poll(() => dialogMessage, { timeout: 5000 })
    .toContain('1 个项目案例的引用将显示“来源已删除”')
  await expect(
    page
      .locator('.requirement-row')
      .filter({ hasText: evidenceTitle })
      .filter({ hasText: '引用（仅文本）' }),
  ).toHaveCount(0)
  await expect(projectRow.getByText(`证据引用：${evidenceTitle}（来源已删除）`)).toBeVisible()

  // 最近删除中有该证据，恢复后引用还原且 ID 不变
  const trashResponse = await request.get('/api/trash')
  expect(trashResponse.ok()).toBe(true)
  const trash = (await trashResponse.json()) as Array<{
    id: string
    resourceId: string
    displayName: string
    impactSummary: string[]
  }>
  const trashedEvidence = trash.find((item) => item.displayName === evidenceTitle)
  expect(trashedEvidence).toBeTruthy()
  expect(trashedEvidence!.impactSummary).toContain('1 个项目案例引用')
  const originalEvidenceId = trashedEvidence!.resourceId

  await page.goto('/settings')
  const trashRow = page.locator('.requirement-row').filter({ hasText: evidenceTitle })
  await expect(trashRow.getByText('影响：1 个项目案例引用')).toBeVisible()
  await trashRow.getByRole('button', { name: '恢复' }).click()
  await expect(page.locator('.requirement-row').filter({ hasText: evidenceTitle })).toHaveCount(0)

  const evidenceListResponse = await request.get('/api/evidence')
  expect(evidenceListResponse.ok()).toBe(true)
  const evidenceList = (await evidenceListResponse.json()) as Array<{ id: string; title: string }>
  expect(evidenceList.some((item) => item.id === originalEvidenceId && item.title === evidenceTitle)).toBe(true)
  await page.goto('/projects')
  await expect(projectRow.getByText(`证据引用：${evidenceTitle}`)).toBeVisible()
  await expect(projectRow.getByText('来源已删除')).toHaveCount(0)

  // 未被引用的证据可以永久删除；被引用的证据再次删除后，永久删除被后端拒绝
  await page.goto('/projects')
  const standaloneRow = page
    .locator('.requirement-row')
    .filter({ hasText: standaloneTitle })
    .filter({ hasText: '引用（仅文本）' })
  await standaloneRow.getByRole('button', { name: '删除' }).click()
  await page.goto('/settings')
  const standaloneTrashRow = page.locator('.requirement-row').filter({ hasText: standaloneTitle })
  await expect(standaloneTrashRow).toBeVisible()
  await standaloneTrashRow.getByRole('button', { name: '永久删除' }).click()
  await expect(standaloneTrashRow).toHaveCount(0)

  await page.goto('/projects')
  const referencedEvidenceRow = page
    .locator('.requirement-row')
    .filter({ hasText: evidenceTitle })
    .filter({ hasText: '引用（仅文本）' })
  await referencedEvidenceRow.getByRole('button', { name: '删除' }).click()
  await page.goto('/settings')
  const referencedTrashRow = page.locator('.requirement-row').filter({ hasText: evidenceTitle })
  await expect(referencedTrashRow).toBeVisible()
  await referencedTrashRow.getByRole('button', { name: '永久删除' }).click()
  await expect(page.getByText('证据仍被项目案例或技能引用，不能永久删除').first()).toBeVisible()
})
