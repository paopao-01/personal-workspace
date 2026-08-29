import { useQuery } from '@tanstack/react-query'
import {
  getNotificationChannel,
  type ChannelType,
  type NotificationChannel,
} from '@/api/notifications/channelApi'

export function useNotificationChannel(channelType: ChannelType) {
  return useQuery<NotificationChannel>({
    queryKey: ['notification-channels', channelType],
    queryFn: () => getNotificationChannel(channelType),
  })
}
