import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type AuditLogEntry = Schemas['AuditLogEntry']
export type PageAuditLogEntry = Schemas['PageAuditLogEntry']

const AUDIT_LOG_KEY = ['audit-logs'] as const

/** 已知 action 取值（全量查询只读暴露既有写入值，不新增 action）。 */
export const AUDIT_ACTIONS = [
  'BACKUP_ORPHAN_CLEANED',
  'SECONDARY_APPLICATION_CONFIRMED',
  'REQUIREMENT_MERGED',
  'REQUIREMENT_UPDATED',
  'REQUIREMENT_DELETED',
] as const

/** 已知 resourceType 取值。 */
export const AUDIT_RESOURCE_TYPES = [
  'BACKUP_FILE',
  'APPLICATION',
  'JOB_REQUIREMENT',
] as const

export interface AuditLogQuery {
  page: number
  pageSize: number
  action?: string
  resourceType?: string
}

/** 分页查询全量审计日志（只读，无需确认头/幂等键）。action/resourceType 可选，空=不过滤返回全量。 */
export async function listAuditLogs(params: AuditLogQuery): Promise<PageAuditLogEntry> {
  const res = await apiClient.get<PageAuditLogEntry>('/audit-logs', { params })
  return res.data
}

export function useAuditLogs(query: AuditLogQuery) {
  return useQuery<PageAuditLogEntry, Error>({
    queryKey: [...AUDIT_LOG_KEY, query.page, query.pageSize, query.action ?? '', query.resourceType ?? ''],
    queryFn: () => listAuditLogs(query),
    placeholderData: (prev) => prev,
  })
}
