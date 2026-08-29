import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type TrashItem = Schemas['TrashItem']

export async function listTrash(): Promise<TrashItem[]> {
  const res = await apiClient.get<TrashItem[]>('/trash')
  return res.data
}

export async function restoreTrashItem(trashId: string): Promise<TrashItem> {
  const res = await apiClient.post<TrashItem>(`/trash/${trashId}/restore`)
  return res.data
}

/**
 * 永久删除需要显式确认头，缺失或非 true 时后端直接拒绝。
 */
export async function purgeTrashItem(trashId: string): Promise<void> {
  await apiClient.delete(`/trash/${trashId}/permanent`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
}
