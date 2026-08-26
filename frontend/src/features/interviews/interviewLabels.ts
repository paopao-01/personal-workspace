import type { Interview, Reminder } from '@/api/interviews/interviewApi'

type BadgeVariant =
  | 'neutral'
  | 'primary'
  | 'success'
  | 'warning'
  | 'info'
  | 'danger'
  | 'subtle'

export const interviewScheduleLabel: Record<
  Interview['scheduleStatus'],
  string
> = {
  SCHEDULED: '已安排',
  COMPLETED: '已完成',
  CANCELED: '已取消',
  NO_SHOW: '未出席',
}

export const interviewScheduleVariant: Record<
  Interview['scheduleStatus'],
  BadgeVariant
> = {
  SCHEDULED: 'info',
  COMPLETED: 'success',
  CANCELED: 'neutral',
  NO_SHOW: 'warning',
}

export const interviewResultLabel: Record<Interview['result'], string> = {
  PENDING: '待确认',
  PASSED: '通过',
  FAILED: '未通过',
}

export const interviewModeLabel: Record<
  NonNullable<Interview['mode']>,
  string
> = {
  ONLINE: '线上',
  ONSITE: '现场',
  PHONE: '电话',
}

export const reminderTypeLabel: Record<Reminder['reminderType'], string> = {
  ONE_DAY: '提前 1 天',
  TWO_HOURS: '提前 2 小时',
  THIRTY_MINUTES: '提前 30 分钟',
  CUSTOM: '自定义',
}

export const reminderStatusLabel: Record<Reminder['status'], string> = {
  PENDING: '待展示',
  PROCESSING: '展示中',
  SENT: '已展示',
  FAILED: '展示失败',
  CANCELED: '已取消',
}

export function formatInterviewTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
