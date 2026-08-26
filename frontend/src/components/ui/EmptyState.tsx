import type { ReactNode } from 'react'

export function EmptyState({
  icon = '📋',
  text,
  action,
}: {
  icon?: string
  text: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon" aria-hidden="true">
        {icon}
      </div>
      <div className="empty-state-text">{text}</div>
      {action}
    </div>
  )
}
