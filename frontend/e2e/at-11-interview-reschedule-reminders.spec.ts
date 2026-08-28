import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

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

function dateAtMinute(offsetMs: number) {
  const date = new Date(Date.now() + offsetMs)
  date.setSeconds(0, 0)
  return date
}

function instantMs(value: string) {
  return new Date(value).getTime()
}

async function createJob(request: APIRequestContext, suffix: number) {
  const response = await request.post('/api/jobs', {
    headers: { 'Idempotency-Key': `e2e-at11-job-${crypto.randomUUID()}` },
    data: {
      companyName: `改期提醒科技-${suffix}`,
      title: 'AT11 面试改期岗位',
      jdRawText: '负责 Java 后端服务开发，要求熟悉 Spring Boot、MySQL 和 Redis。',
      source: 'E2E AT-11',
      location: '上海',
    },
  })
  expect(response.ok(), `POST /api/jobs returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string }
}

async function createApplication(request: APIRequestContext, jobId: string) {
  const response = await request.post('/api/applications', {
    headers: { 'Idempotency-Key': `e2e-at11-application-${crypto.randomUUID()}` },
    data: {
      jobId,
      appliedAt: dateInputValue(new Date()),
      channel: 'E2E 渠道-改期提醒',
      nextAction: '准备改期后的技术面',
    },
  })
  expect(response.ok(), `POST /api/applications returned ${response.status()}`).toBe(true)
  return (await response.json()) as { id: string; version: number }
}

async function transitionApplication(
  request: APIRequestContext,
  application: { id: string; version: number },
  targetStatus: 'APPLIED' | 'RESUME_PASSED',
) {
  const response = await request.post(`/api/applications/${application.id}/transition`, {
    headers: {
      'Idempotency-Key': `e2e-at11-transition-${targetStatus}-${crypto.randomUUID()}`,
      'If-Match-Version': String(application.version),
    },
    data: { targetStatus },
  })
  expect(
    response.ok(),
    `POST /api/applications/${application.id}/transition returned ${response.status()}`,
  ).toBe(true)
  return (await response.json()) as { id: string; version: number }
}

async function createInterviewViaUi(page: Page, applicationId: string, startsAt: Date) {
  await page.goto(`/applications/${applicationId}`)
  await page.getByRole('button', { name: '安排面试' }).click()
  const createForm = page.locator('form').filter({ hasText: '面试轮次' }).first()
  await fillField(page, '面试轮次', 'AT11 技术一面')
  await fillField(page, '开始时间', dateTimeLocalValue(startsAt))
  await fillField(page, '事件时区', 'Asia/Shanghai')
  await fillField(page, '准备事项', '确认会议链接\n复习项目案例')

  const createResponse = page.waitForResponse((response) =>
    response.url().includes('/api/interviews') &&
    response.request().method() === 'POST',
  )
  await createForm.getByRole('button', { name: '安排面试' }).click()
  const response = await createResponse
  expect(response.ok(), `POST /api/interviews returned ${response.status()}`).toBe(true)
  await page.waitForURL(/\/interviews\/[^/]+$/)
  return (await response.json()) as { id: string; version: number }
}

test('AT-11 interview reschedule replaces pending reminders and preserves sent history', async ({
  page,
  request,
}) => {
  const suffix = Date.now()
  const originalStartsAt = dateAtMinute(14 * 24 * 60 * 60 * 1000)
  const rescheduledStartsAt = dateAtMinute(15 * 24 * 60 * 60 * 1000 + 2 * 60 * 60 * 1000)

  const job = await createJob(request, suffix)
  let application = await createApplication(request, job.id)
  application = await transitionApplication(request, application, 'APPLIED')
  application = await transitionApplication(request, application, 'RESUME_PASSED')

  const interview = await createInterviewViaUi(page, application.id, originalStartsAt)

  let remindersResponse = await request.get(`/api/interviews/${interview.id}/reminders`)
  expect(remindersResponse.ok(), `GET reminders returned ${remindersResponse.status()}`).toBe(true)
  const originalReminders = (await remindersResponse.json()) as Array<{
    id: string
    reminderType: string
    status: string
  }>
  expect(originalReminders).toHaveLength(3)
  expect(originalReminders.map((reminder) => reminder.status)).toEqual([
    'PENDING',
    'PENDING',
    'PENDING',
  ])

  const sentReminder = originalReminders[0]
  const markSentResponse = await request.post(`/api/e2e/reminders/${sentReminder.id}/mark-sent`)
  expect(markSentResponse.ok(), `mark sent returned ${markSentResponse.status()}`).toBe(true)

  await page.reload()
  await expect(page.getByRole('heading', { name: 'AT11 技术一面' })).toBeVisible()
  await expect(page.locator('section.card').filter({ hasText: '提醒计划' }).getByText('已展示')).toBeVisible()

  await page.getByRole('button', { name: '改期' }).click()
  await fillField(page, '新的开始时间', dateTimeLocalValue(rescheduledStartsAt))
  await fillField(page, '事件时区', 'Asia/Tokyo')

  const rescheduleResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/interviews/${interview.id}/reschedule`) &&
    response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '确认改期' }).click()
  const response = await rescheduleResponse
  expect(response.ok(), `POST reschedule returned ${response.status()}`).toBe(true)
  await expect(page.getByText('面试已改期，提醒计划已重算')).toBeVisible()

  const summary = page.locator('section.card').filter({ hasText: '面试摘要' })
  await expect(summary).toContainText('Asia/Tokyo')

  remindersResponse = await request.get(`/api/interviews/${interview.id}/reminders`)
  expect(remindersResponse.ok(), `GET reminders after reschedule returned ${remindersResponse.status()}`).toBe(true)
  const reminders = (await remindersResponse.json()) as Array<{
    id: string
    reminderType: string
    scheduledAt: string
    status: string
  }>
  const sentReminders = reminders.filter((reminder) => reminder.status === 'SENT')
  const canceledReminders = reminders.filter((reminder) => reminder.status === 'CANCELED')
  const pendingReminders = reminders.filter((reminder) => reminder.status === 'PENDING')

  expect(sentReminders.map((reminder) => reminder.id)).toEqual([sentReminder.id])
  expect(canceledReminders).toHaveLength(2)
  expect(pendingReminders).toHaveLength(3)
  expect(pendingReminders.map((reminder) => instantMs(reminder.scheduledAt)).sort()).toEqual([
    rescheduledStartsAt.getTime() - 24 * 60 * 60 * 1000,
    rescheduledStartsAt.getTime() - 2 * 60 * 60 * 1000,
    rescheduledStartsAt.getTime() - 30 * 60 * 1000,
  ].sort())

  const detailResponse = await request.get(`/api/interviews/${interview.id}`)
  expect(detailResponse.ok(), `GET interview returned ${detailResponse.status()}`).toBe(true)
  const detail = (await detailResponse.json()) as { startsAt: string; eventTimeZone: string }
  expect(instantMs(detail.startsAt)).toBe(rescheduledStartsAt.getTime())
  expect(detail.eventTimeZone).toBe('Asia/Tokyo')

  const reminderSection = page.locator('section.card').filter({ hasText: '提醒计划' })
  await expect(reminderSection.getByText('已展示')).toBeVisible()
  await expect(reminderSection.getByText('已取消')).toHaveCount(2)
  await expect(reminderSection.getByText('待展示')).toHaveCount(3)
})
