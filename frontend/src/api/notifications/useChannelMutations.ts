import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  updateNotificationChannel,
  testNotificationChannel,
  type ChannelTestResult,
  type ChannelType,
  type NotificationChannel,
  type NotificationChannelConfig,
} from '@/api/notifications/channelApi'

export function useUpdateNotificationChannel(channelType: ChannelType) {
  const queryClient = useQueryClient()
  return useMutation<NotificationChannel, Error, {
    version: number
    body: { enabled: boolean; config?: NotificationChannelConfig }
  }>({
    mutationFn: ({ version, body }) => updateNotificationChannel(channelType, version, body),
    onSuccess: (channel) => {
      queryClient.setQueryData<NotificationChannel>(
        ['notification-channels', channelType],
        channel,
      )
    },
  })
}

export function useTestNotificationChannel(channelType: ChannelType) {
  return useMutation<ChannelTestResult, Error, void>({
    mutationFn: () => testNotificationChannel(channelType),
  })
}
