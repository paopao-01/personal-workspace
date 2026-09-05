import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type ChannelEffectivenessReport = Schemas['ChannelEffectivenessReport']
export type ChannelEffectivenessGroup = Schemas['ChannelEffectivenessGroup']
export type ResumeVersionEffectivenessGroup = Schemas['ResumeVersionEffectivenessGroup']

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
