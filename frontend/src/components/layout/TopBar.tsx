import { useNavigate } from 'react-router-dom'
import { useNotifications } from '@/api/notifications/useNotificationQueries'
import { Button } from '@/components/ui/Button'

export function TopBar() {
  const navigate = useNavigate()
  const { data: notifications } = useNotifications()
  const unreadCount = (notifications ?? []).filter((item) => !item.readAt).length

  return (
    <header className="app-topbar">
      <div className="page-subtitle">Java 后端求职个人工作台</div>
      <div className="flex-row" style={{ gap: 8 }}>
        <Button variant="default" size="sm" onClick={() => navigate('/notifications')} aria-label="站内通知">
          通知
          {unreadCount > 0 ? (
            <span className="badge badge-danger" style={{ marginLeft: 6 }} aria-label={`未读通知 ${unreadCount} 条`}>
              {unreadCount}
            </span>
          ) : null}
        </Button>
        <Button variant="primary" size="sm" onClick={() => navigate('/jobs/new')}>
          + 新增岗位
        </Button>
      </div>
    </header>
  )
}
