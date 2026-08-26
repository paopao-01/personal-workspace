import { useEffect, useState } from 'react'
import {
  dismissToast,
  subscribe,
  type Toast,
} from '@/components/feedback/toastStore'

export function ToastContainer() {
  const [list, setList] = useState<Toast[]>([])
  useEffect(() => subscribe(setList), [])

  return (
    <div className="toast-container" role="region" aria-label="通知">
      {list.map((t) => (
        <div
          key={t.id}
          className={`toast toast-${t.variant}`}
          role={t.variant === 'error' ? 'alert' : 'status'}
        >
          <div className="flex-row" style={{ justifyContent: 'space-between' }}>
            <span>{t.message}</span>
            <button
              className="btn-link btn-sm"
              onClick={() => dismissToast(t.id)}
              aria-label="关闭通知"
            >
              ×
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
