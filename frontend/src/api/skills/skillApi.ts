import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type SkillProfile = Schemas['SkillProfile']
export type SkillCreateRequest = Schemas['SkillCreateRequest']
export type SelfLevelUpdateRequest = Schemas['SelfLevelUpdateRequest']
export type SelfLevelHistoryEntry = Schemas['SelfLevelHistoryEntry']

export async function listSkillProfiles(): Promise<SkillProfile[]> {
  const res = await apiClient.get<SkillProfile[]>('/skills/profile')
  return res.data
}

export async function listSelfLevelHistory(
  skillId: string,
): Promise<SelfLevelHistoryEntry[]> {
  const res = await apiClient.get<SelfLevelHistoryEntry[]>(
    `/skills/${skillId}/self-level/history`,
  )
  return res.data
}

export async function updateSelfLevel(
  skillId: string,
  version: number,
  body: SelfLevelUpdateRequest,
): Promise<SkillProfile> {
  const res = await apiClient.put<SkillProfile>(`/skills/${skillId}/self-level`, body, {
    headers: { 'If-Match-Version': String(version) },
  })
  return res.data
}

export async function createSkill(body: SkillCreateRequest): Promise<SkillProfile> {
  const res = await apiClient.post<SkillProfile>('/skills', body)
  return res.data
}
