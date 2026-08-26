import type { ReactNode } from 'react'
import { isNetworkError } from '@/api/errors'

export function ErrorState({
  error,
  onRetry,
  retryLabel = '重试',
  extraAction,
}: {
  error: unknown
  onRetry?: () => void
  retryLabel?: string
  extraAction?: ReactNode
}) {
  const isNetwork = isNetworkError(error)
  const message = error instanceof Error ? error.message : '发生未知错误'
  return (
    <div className="error-state" role="alert">
      <div className="error-state-icon" aria-hidden="true">
        {isNetwork ? '🔌' : '⚠️'}
      </div>
      <div className="error-state-text">{message}</div>
      <div className="flex-row">
        {onRetry ? (
          <button className="btn btn-ghost btn-sm" onClick={onRetry}>
            {retryLabel}
          </button>
        ) : null}
        {extraAction}
      </div>
    </div>
  )
}
