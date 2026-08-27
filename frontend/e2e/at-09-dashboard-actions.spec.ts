import { expect, test, type Page } from '@playwright/test'

function formField(page: Page, label: string) {
  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return page
    .locator('.form-field')
    .filter({
      has: page.locator('.form-label').filter({
        hasText: new RegExp(`^${escapedLabel}\\*?$`),
      }),
    })
    .first()
}

async function fillField(page: Page, label: string, value: string) {
  const field = formField(page, label)
  const input = field.locator('input, textarea').first()
  await input.fill(value)
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function dateInputValue(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function dateTimeLocalValue(date: Date) {
  return `${dateInputValue(date)}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

async function createJob(page: Page, companyName: string, title: string) {
  const response = await page.request.post('/api/jobs', {
    headers: {
      'Idempotency-Key': `e2e-at09-job-${crypto.randomUUID()}`,
    },
    data: {
      companyName,
      title,
      jdRawText:
        '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis，能够跟进投递与面试安排。',
      source: 'E2E AT-09',
      location: '上海',
    },
  })
  expect(response.ok(), `POST /api/jobs returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string }
}

async function createApplicationViaUi(
  page: Page,
  jobId: string,
  values: {
    channel: string
    nextAction?: string
    nextActionDueAt?: string
  },
) {
  await page.goto(`/applications/new?jobId=${jobId}`)
  await fillField(page, '投递日期', dateInputValue(new Date()))
  await fillField(page, '投递渠道', values.channel)
  if (values.nextActionDueAt) {
    await fillField(page, '下一步行动截止时间', values.nextActionDueAt)
  }
  if (values.nextAction) {
    await fillField(page, '下一步行动', values.nextAction)
  }

  const createResponse = page.waitForResponse((response) =>
    response.url().includes('/api/applications') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '保存投递' }).click()
  const response = await createResponse
  expect(response.ok(), `POST /api/applications returned ${response.status()}`).toBe(true)
  await page.waitForURL(/\/applications\/[^/]+$/)
}

async function transitionApplication(
  page: Page,
  buttonName: string,
  expectedStatus: string,
) {
  const transitionResponse = page.waitForResponse((response) =>
    response.url().includes('/api/applications/') &&
    response.url().includes('/transition') &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: buttonName }).click()
  const response = await transitionResponse
  expect(response.ok(), `${buttonName} returned ${response.status()}`).toBe(true)

  const statusSection = page.locator('section.card').filter({
    has: page.getByRole('heading', { name: '当前状态' }),
  })
  await expect(statusSection.locator('.badge').filter({ hasText: expectedStatus })).toBeVisible()
}

test('AT-09 dashboard identifies missing and overdue application actions', async ({ page }) => {
  const suffix = Date.now()
  const missingJob = await createJob(page, `缺失行动科技-${suffix}`, 'AT09 缺失行动岗位')
  const overdueJob = await createJob(page, `逾期行动科技-${suffix}`, 'AT09 逾期行动岗位')
  const overdueDueAt = dateTimeLocalValue(new Date(Date.now() - 3 * 24 * 60 * 60 * 1000))

  await createApplicationViaUi(page, missingJob.id, {
    channel: 'E2E 渠道-缺失行动',
  })
  await transitionApplication(page, '提交投递', '已投递')

  await createApplicationViaUi(page, overdueJob.id, {
    channel: 'E2E 渠道-逾期行动',
    nextAction: '跟进 HR 面试反馈',
    nextActionDueAt: overdueDueAt,
  })
  await transitionApplication(page, '提交投递', '已投递')
  await transitionApplication(page, '简历通过', '简历通过')
  await transitionApplication(page, '开始面试', '面试中')

  await page.goto('/dashboard')

  const todayActions = page.locator('section.card').filter({
    has: page.getByRole('heading', { name: '今天应做什么' }),
  })
  const actionRows = todayActions.locator('.requirement-row')

  await expect(
    actionRows.filter({ hasText: '补充「AT09 缺失行动岗位」的下一步行动' }),
  ).toBeVisible()
  await expect(
    actionRows.filter({ hasText: /跟进 HR 面试反馈（已逾期 \d+ 天）/ }),
  ).toBeVisible()
  await expect(actionRows.first()).toContainText('跟进 HR 面试反馈')
  await expect(actionRows.first()).toContainText(/已逾期 \d+ 天/)

  const activeApplications = page.locator('section.card').filter({
    has: page.getByRole('heading', { name: '进行中投递' }),
  })
  await expect(activeApplications.getByText('已投递')).toBeVisible()
  await expect(activeApplications.getByText('面试中')).toBeVisible()
})
