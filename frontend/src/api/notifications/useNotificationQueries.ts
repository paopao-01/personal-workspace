import { useQuery } from '@tanstack/react-query'
import {
  listNotifications,
  type Notification,
} from '@/api/notifications/notificationApi'

/**
 * 通知列表轮询刷新：到期提醒由后端调度生成，客户端无法感知写入时机。
 */
export function useNotifications() {
  return useQuery<Notification[]>({
    queryKey: ['notifications'],
    queryFn: listNotifications,
    refetchInterval: 5000,
  })
}
