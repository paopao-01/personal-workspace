import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type NotificationChannel = Schemas['NotificationChannel']
export type NotificationChannelConfig = Schemas['NotificationChannelConfig']
export type ChannelTestResult = Schemas['ChannelTestResult']
export type ChannelType = 'BROWSER' | 'EMAIL'

export async function getNotificationChannel(
  channelType: ChannelType,
): Promise<NotificationChannel> {
  const res = await apiClient.get<NotificationChannel>(
    `/notification-channels/${channelType}`,
  )
  return res.data
}

export async function updateNotificationChannel(
  channelType: ChannelType,
  version: number,
  body: { enabled: boolean; config?: NotificationChannelConfig },
): Promise<NotificationChannel> {
  const res = await apiClient.put<NotificationChannel>(
    `/notification-channels/${channelType}`,
    body,
    {
      headers: {
        'If-Match-Version': String(version),
        'Idempotency-Key': crypto.randomUUID(),
      },
    },
  )
  return res.data
}

export async function testNotificationChannel(
  channelType: ChannelType,
): Promise<ChannelTestResult> {
  const res = await apiClient.post<ChannelTestResult>(
    `/notification-channels/${channelType}/test`,
    {},
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
  return res.data
}

/** 浏览器通知展示后的投递回执（幂等）。 */
export async function ackBrowserDelivery(notificationId: string): Promise<void> {
  await apiClient.post(
    `/notifications/${notificationId}/channel-deliveries/BROWSER/ack`,
    {},
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
}
