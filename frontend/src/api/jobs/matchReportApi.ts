import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type MatchReport = Schemas['MatchReport']

export async function generateMatchReport(jobId: string): Promise<MatchReport> {
  const res = await apiClient.post<MatchReport>(`/jobs/${jobId}/match-reports`)
  return res.data
}

export async function getLatestMatchReport(jobId: string): Promise<MatchReport> {
  const res = await apiClient.get<MatchReport>(`/jobs/${jobId}/match-reports/latest`)
  return res.data
}
