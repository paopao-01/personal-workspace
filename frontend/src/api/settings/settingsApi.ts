import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type UserSettings = Schemas['UserSettings']
export type UserSettingsUpdateRequest = Schemas['UserSettingsUpdateRequest']

export async function getSettings(): Promise<UserSettings> {
  const res = await apiClient.get<UserSettings>('/settings')
  return res.data
}

export async function updateSettings(
  version: number,
  body: UserSettingsUpdateRequest,
): Promise<UserSettings> {
  const res = await apiClient.put<UserSettings>('/settings', body, {
    headers: { 'If-Match-Version': String(version) },
  })
  return res.data
}
