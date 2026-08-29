import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  markNotificationRead,
  type Notification,
} from '@/api/notifications/notificationApi'

export function useMarkNotificationRead() {
  const queryClient = useQueryClient()
  return useMutation<Notification, Error, string>({
    mutationFn: markNotificationRead,
    onSuccess: (notification) => {
      queryClient.setQueryData<Notification[]>(['notifications'], (prev) =>
        (prev ?? []).map((item) => (item.id === notification.id ? notification : item)),
      )
    },
  })
}
