import type { ReactNode } from 'react'
import { Button } from '@/components/ui/Button'

/**
 * 冲突/错误横幅：用于 VERSION_CONFLICT / ILLEGAL_STATE_TRANSITION / IDEMPOTENCY_CONFLICT。
 */
export function ConflictBanner({
  message,
  detail,
  actionLabel,
  onAction,
}: {
  message: string
  detail?: ReactNode
  actionLabel?: string
  onAction?: () => void
}) {
  return (
    <div className="conflict-banner" role="alert">
      <div>
        <strong>{message}</strong>
        {detail ? <div className="muted" style={{ marginTop: 2 }}>{detail}</div> : null}
      </div>
      {actionLabel && onAction ? (
        <Button variant="danger" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </div>
  )
}
