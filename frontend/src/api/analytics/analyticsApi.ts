import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type ChannelEffectivenessReport = Schemas['ChannelEffectivenessReport']
export type ChannelEffectivenessGroup = Schemas['ChannelEffectivenessGroup']
export type ResumeVersionEffectivenessGroup = Schemas['ResumeVersionEffectivenessGroup']
export type FunnelBucket = Schemas['FunnelBucket']

/** 时间序列分组粒度。 */
export type FunnelGranularity = 'day' | 'hour'

export async function getChannelEffectiveness(params?: {
  from?: string
  to?: string
}): Promise<ChannelEffectivenessReport> {
  const res = await apiClient.get<ChannelEffectivenessReport>(
    '/analytics/channel-effectiveness',
    { params },
  )
  return res.data
}

export function useChannelEffectiveness(params: { from?: string; to?: string } | null) {
  return useQuery<ChannelEffectivenessReport>({
    queryKey: ['analytics', 'channel-effectiveness', params],
    queryFn: () => getChannelEffectiveness(params ?? undefined),
    enabled: params !== null,
  })
}

/** 拉取投递漏斗转化时间序列（只读，按 applied_at 与 granularity 分组，无需确认头/幂等键）。
 *  返回按 date 升序的桶数组，只含有投递记录的桶（不补 0）；空匹配返回 []。
 *  每桶 date（day→2026-09-07 / hour→2026-09-07T13:00:00Z）、total（含 DRAFT）、applied（非 DRAFT，漏斗顶部）、
 *  interviewed、offered、interviewRate（interviewed/applied）、offerRate（offered/applied，分母 0 兜底 0）。 */
export async function getFunnel(params?: {
  from?: string
  to?: string
  granularity?: FunnelGranularity
}): Promise<FunnelBucket[]> {
  const res = await apiClient.get<FunnelBucket[]>('/analytics/funnel', { params })
  return res.data
}

export function useFunnel(params: { from?: string; to?: string; granularity?: FunnelGranularity } | null) {
  return useQuery<FunnelBucket[]>({
    queryKey: ['analytics', 'funnel', params],
    queryFn: () => getFunnel(params ?? undefined),
    enabled: params !== null,
  })
}
