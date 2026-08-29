import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type Notification = Schemas['Notification']

export async function listNotifications(): Promise<Notification[]> {
  const res = await apiClient.get<Notification[]>('/notifications')
  return res.data
}

export async function markNotificationRead(notificationId: string): Promise<Notification> {
  const res = await apiClient.post<Notification>(`/notifications/${notificationId}/read`)
  return res.data
}
