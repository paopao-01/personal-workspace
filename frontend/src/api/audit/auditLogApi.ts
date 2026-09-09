import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type AuditLogEntry = Schemas['AuditLogEntry']
export type PageAuditLogEntry = Schemas['PageAuditLogEntry']
export type FreedBytesSummary = Schemas['FreedBytesSummary']
export type FreedBytesBucket = Schemas['FreedBytesBucket']

export const AUDIT_LOG_KEY = ['audit-logs'] as const

/** 已知 action 取值（全量查询只读暴露既有写入值，不新增 action）。 */
export const AUDIT_ACTIONS = [
  'BACKUP_ORPHAN_CLEANED',
  'SECONDARY_APPLICATION_CONFIRMED',
  'REQUIREMENT_MERGED',
  'REQUIREMENT_UPDATED',
  'REQUIREMENT_DELETED',
  'BACKUP_DELETED',
  'BACKUP_PURGED_BY_AGE',
  'BACKUP_PURGED_BY_COUNT',
  'BACKUP_KEY_ROTATED',
] as const

/** 已知 resourceType 取值。 */
export const AUDIT_RESOURCE_TYPES = [
  'BACKUP_FILE',
  'BACKUP_RECORD',
  'APPLICATION',
  'JOB_REQUIREMENT',
] as const

export interface AuditLogQuery {
  page: number
  pageSize: number
  action?: string
  resourceType?: string
  from?: string
  to?: string
  hasFreedBytes?: boolean
  freedBytesMin?: number
  freedBytesMax?: number
}

/** 分页查询全量审计日志（只读，无需确认头/幂等键）。action/resourceType/from/to/hasFreedBytes/freedBytesMin/
 *  freedBytesMax 可选，空/hasFreedBytes=false/缺省=不过滤返回全量。from/to 为 ISO-8601 UTC 字符串（含边界：
 *  from 起始 occurred_at >= from，to 结束 occurred_at <= to）。hasFreedBytes=true 只返回 freed_bytes IS NOT NULL
 *  的记录。freedBytesMin/freedBytesMax 按释放字节数范围过滤（闭区间，非负整数，NULL 行自动排除）。 */
export async function listAuditLogs(params: AuditLogQuery): Promise<PageAuditLogEntry> {
  const res = await apiClient.get<PageAuditLogEntry>('/audit-logs', { params })
  return res.data
}

export function useAuditLogs(query: AuditLogQuery) {
  return useQuery<PageAuditLogEntry, Error>({
    queryKey: [
      ...AUDIT_LOG_KEY,
      query.page,
      query.pageSize,
      query.action ?? '',
      query.resourceType ?? '',
      query.from ?? '',
      query.to ?? '',
      query.hasFreedBytes ?? false,
      query.freedBytesMin ?? '',
      query.freedBytesMax ?? '',
    ],
    queryFn: () => listAuditLogs(query),
    placeholderData: (prev) => prev,
  })
}

/** 导出审计日志为 CSV 或 JSON 文件下载（只读，即时下载，复用当前过滤条件）。
 *  浏览器侧完成下载，不落盘后端文件系统。导出失败（非法 format/from/to/freedBytesMin/freedBytesMax
 *  经后端 400）抛 ApiError。 */
export async function exportAuditLogs(
  format: 'csv' | 'json',
  filters: {
    action?: string
    resourceType?: string
    from?: string
    to?: string
    hasFreedBytes?: boolean
    freedBytesMin?: number
    freedBytesMax?: number
  },
): Promise<void> {
  const res = await apiClient.get<Blob>('/audit-logs/export', {
    params: { format, ...filters },
    responseType: 'blob',
  })
  const ext = format
  // 从 Content-Disposition 提取文件名，回退到默认名
  const disposition = String(res.headers['content-disposition'] ?? '')
  const match = /filename="?([^";]+)"?/i.exec(disposition)
  const filename = match ? match[1] : `audit-logs.${ext}`
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 聚合统计过滤条件（与查询/导出同源同过滤）。 */
type SummaryFilters = {
  action?: string
  resourceType?: string
  from?: string
  to?: string
  hasFreedBytes?: boolean
  freedBytesMin?: number
  freedBytesMax?: number
}

/** 拉取释放字节数聚合统计（只读，复用当前过滤条件，无需确认头/幂等键）。
 *  返回 totalCount（含 NULL 行）/totalFreedBytes（SUM，NULL 不计入）/avgFreedBytes（非空行均值）。 */
export async function fetchFreedBytesSummary(filters: SummaryFilters): Promise<FreedBytesSummary> {
  const res = await apiClient.get<FreedBytesSummary>('/audit-logs/freed-bytes-summary', { params: filters })
  return res.data
}

export function useFreedBytesSummary(filters: SummaryFilters) {
  return useQuery<FreedBytesSummary, Error>({
    queryKey: [...AUDIT_LOG_KEY, 'summary', filters.action ?? '', filters.resourceType ?? '',
      filters.from ?? '', filters.to ?? '', filters.hasFreedBytes ?? false,
      filters.freedBytesMin ?? '', filters.freedBytesMax ?? ''],
    queryFn: () => fetchFreedBytesSummary(filters),
    placeholderData: (prev) => prev,
  })
}

/** 时间序列分组粒度（只读，复用当前过滤条件 + granularity）。 */
export type TimeseriesGranularity = 'day' | 'hour'

/** 拉取释放字节数时间序列分组（只读，复用当前过滤条件 + granularity，无需确认头/幂等键）。
 *  返回按 date 升序的桶数组，只含有数据的桶（不补 0）；空匹配返回 []。
 *  每桶 date（day→2026-09-07 / hour→2026-09-07T13:00:00Z）、count（含 NULL 行）、
 *  totalFreedBytes（SUM，NULL 不计入）、avgFreedBytes（非空行均值，分母 0 兜底 0）。 */
export async function fetchFreedBytesTimeseries(
  filters: SummaryFilters,
  granularity: TimeseriesGranularity = 'day',
): Promise<FreedBytesBucket[]> {
  const res = await apiClient.get<FreedBytesBucket[]>('/audit-logs/freed-bytes-timeseries', {
    params: { granularity, ...filters },
  })
  return res.data
}

export function useFreedBytesTimeseries(filters: SummaryFilters, granularity: TimeseriesGranularity = 'day') {
  return useQuery<FreedBytesBucket[], Error>({
    queryKey: [...AUDIT_LOG_KEY, 'timeseries', granularity, filters.action ?? '', filters.resourceType ?? '',
      filters.from ?? '', filters.to ?? '', filters.hasFreedBytes ?? false,
      filters.freedBytesMin ?? '', filters.freedBytesMax ?? ''],
    queryFn: () => fetchFreedBytesTimeseries(filters, granularity),
    placeholderData: (prev) => prev,
  })
}
