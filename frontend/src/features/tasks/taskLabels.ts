import type { TaskPriority, TaskStatus } from '@/api/tasks/taskApi'

type BadgeVariant = 'neutral' | 'primary' | 'success' | 'warning' | 'info' | 'danger' | 'subtle'

export const taskStatusLabel: Record<TaskStatus, string> = {
  TODO: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  ABANDONED: '已放弃',
}

export const taskStatusVariant: Record<TaskStatus, BadgeVariant> = {
  TODO: 'neutral',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  ABANDONED: 'subtle',
}

export const taskPriorityLabel: Record<TaskPriority, string> = {
  LOW: '低优先级',
  MEDIUM: '中优先级',
  HIGH: '高优先级',
  URGENT: '紧急',
}
