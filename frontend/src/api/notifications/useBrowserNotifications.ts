import { useEffect } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ackBrowserDelivery } from '@/api/notifications/channelApi'
import { useNotificationChannel } from '@/api/notifications/useChannelQueries'
import type { Notification } from '@/api/notifications/notificationApi'

/**
 * 浏览器通知渠道（PRD 9.3）：渠道启用且浏览器授权（Notification.permission === 'granted'）时，
 * 对轮询新到达的未读站内通知调用系统 Notification 展示，并回执后端（BROWSER 投递标记 SENT）。
 * 授权被拒或展示失败仅跳过浏览器渠道，站内通知始终保留。
 * 首次加载只记录现有通知 id，不补发历史通知。
 */
const shownNotificationIds = new Set<string>()
let seenInitialBatch = false

export function useBrowserNotifications(notifications: Notification[] | undefined) {
  const channelQuery = useNotificationChannel('BROWSER')
  const enabled = channelQuery.data?.enabled === true
  const { mutate: ack } = useMutation<void, Error, string>({
    mutationFn: ackBrowserDelivery,
  })

  useEffect(() => {
    if (!enabled || !notifications) return
    const canNotify =
      typeof window !== 'undefined' &&
      'Notification' in window &&
      Notification.permission === 'granted'
    // 无论是否能弹系统通知，都推进已见标记，避免授权后一次性补发历史通知
    if (!seenInitialBatch) {
      seenInitialBatch = true
      for (const notification of notifications) {
        shownNotificationIds.add(notification.id)
      }
      return
    }
    if (!canNotify) return
    for (const notification of notifications) {
      if (shownNotificationIds.has(notification.id)) continue
      shownNotificationIds.add(notification.id)
      if (notification.readAt) continue
      try {
        const browserNotification = new Notification(notification.title, {
          body: notification.content,
        })
        browserNotification.onclick = () => {
          window.focus()
          browserNotification.close()
        }
      } catch {
        // 系统通知展示失败：跳过浏览器渠道，站内通知保留
      }
      ack(notification.id)
    }
  }, [enabled, notifications, ack])
}
