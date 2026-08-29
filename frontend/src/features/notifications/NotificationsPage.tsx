import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useMarkNotificationRead,
} from '@/api/notifications/useNotificationMutations'
import { useNotifications } from '@/api/notifications/useNotificationQueries'
import type { Notification } from '@/api/notifications/notificationApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime } from '@/features/jobs/statusLabels'

export function NotificationsPage() {
  const notificationsQuery = useNotifications()
  const markReadMutation = useMarkNotificationRead()
  const [error, setError] = useState<string | null>(null)
  const [operatingId, setOperatingId] = useState<string | null>(null)

  if (notificationsQuery.isLoading) {
    return <Spinner label="加载通知…" />
  }
  if (notificationsQuery.error) {
    return <ErrorState error={notificationsQuery.error} onRetry={() => notificationsQuery.refetch()} />
  }

  const notifications = notificationsQuery.data ?? []
  const unreadCount = notifications.filter((item) => !item.readAt).length

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const markRead = async (item: Notification) => {
    setError(null)
    setOperatingId(item.id)
    try {
      await markReadMutation.mutateAsync(item.id)
    } catch (caught) {
      reportError(caught as Error)
    } finally {
      setOperatingId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">通知</h1>
          <p className="page-subtitle">
            到期的面试提醒会自动出现在这里；{unreadCount > 0 ? `当前有 ${unreadCount} 条未读。` : '暂无未读。'}
          </p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">通知列表</h2>
        </div>
        <div className="card-body">
          {notifications.length === 0 ? (
            <EmptyState
              icon="🔔"
              text="暂无通知。面试的默认提醒（可自定义节点）到期后会自动出现在这里，无需手动创建。"
            />
          ) : (
            <div>
              {notifications.map((item) => (
                <div className="requirement-row" key={item.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{item.title}</span>
                    <p className="muted" style={{ margin: '4px 0 0' }}>{item.content}</p>
                    <p className="muted" style={{ margin: 0 }}>
                      通知时间：{formatDateTime(item.createdAt)}
                    </p>
                  </div>
                  <div className="requirement-actions">
                    {item.readAt ? (
                      <Badge variant="subtle">已读</Badge>
                    ) : (
                      <>
                        <Badge variant="warning">未读</Badge>
                        <Button
                          size="sm"
                          variant="default"
                          type="button"
                          disabled={markReadMutation.isPending || operatingId === item.id}
                          onClick={() => markRead(item)}
                        >
                          标记已读
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
