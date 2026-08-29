import type { TrashItem } from '@/api/settings/trashApi'

export const exportStatusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '导出中',
  SUCCEEDED: '导出完成',
  FAILED: '导出失败',
}

export const exportStatusVariants: Record<string, 'success' | 'info' | 'subtle' | 'danger'> = {
  QUEUED: 'subtle',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

export function exportStatusLabel(status: string): string {
  return exportStatusLabels[status] ?? status
}

export function exportStatusVariant(status: string): 'success' | 'info' | 'subtle' | 'danger' {
  return exportStatusVariants[status] ?? 'subtle'
}

export const trashResourceTypeLabels: Record<string, string> = {
  PROJECT_CASE: '项目案例',
  EVIDENCE: '证据引用',
  INTERVIEW_QUESTION: '面试问题',
}

export function trashResourceTypeLabel(type: string): string {
  return trashResourceTypeLabels[type] ?? type
}

export function trashExpiryLabel(item: TrashItem, now: Date): string {
  const expiresAt = new Date(item.expiresAt)
  if (expiresAt.getTime() <= now.getTime()) {
    return '已过 30 天保留期，建议永久删除'
  }
  const days = Math.ceil((expiresAt.getTime() - now.getTime()) / (24 * 60 * 60 * 1000))
  return `剩余 ${days} 天可恢复`
}
