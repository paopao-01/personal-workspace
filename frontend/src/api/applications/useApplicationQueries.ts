import { useQuery } from '@tanstack/react-query'
import {
  type ApplicationListParams,
  getApplicationDetail,
  listApplications,
  listStatusHistory,
} from '@/api/applications/applicationApi'
import type {
  ApplicationDetail,
  PageApplication,
  StatusLog,
} from '@/api/applications/applicationApi'

/**
 * Query key 约定：
 * ['applications', params]                         —— 投递列表
 * ['applications', applicationId]                   —— 单个投递详情（ApplicationDetail）
 * ['applications', applicationId, 'status-history'] —— 状态历史
 */

export function useApplicationList(params: ApplicationListParams) {
  return useQuery<PageApplication>({
    queryKey: ['applications', params],
    queryFn: () => listApplications(params),
    placeholderData: (prev) => prev,
  })
}

export function useApplicationDetail(applicationId: string | undefined) {
  return useQuery<ApplicationDetail>({
    queryKey: ['applications', applicationId],
    queryFn: () => getApplicationDetail(applicationId!),
    enabled: Boolean(applicationId),
  })
}

export function useApplicationStatusHistory(applicationId: string | undefined) {
  return useQuery<StatusLog[]>({
    queryKey: ['applications', applicationId, 'status-history'],
    queryFn: () => listStatusHistory(applicationId!),
    enabled: Boolean(applicationId),
  })
}
