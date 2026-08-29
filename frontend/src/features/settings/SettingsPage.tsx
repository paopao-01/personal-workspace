import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  usePurgeTrashItem,
  useRestoreTrashItem,
} from '@/api/settings/useTrashMutations'
import { useTrash } from '@/api/settings/useTrashQueries'
import type { TrashItem } from '@/api/settings/trashApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime } from '@/features/jobs/statusLabels'
import { trashExpiryLabel, trashResourceTypeLabel } from '@/features/settings/settingsLabels'

export function SettingsPage() {
  const trashQuery = useTrash()
  const restoreItem = useRestoreTrashItem()
  const purgeItem = usePurgeTrashItem()
  const [error, setError] = useState<string | null>(null)
  const [operatingId, setOperatingId] = useState<string | null>(null)

  if (trashQuery.isLoading) {
    return <Spinner label="加载设置…" />
  }
  if (trashQuery.error) {
    return <ErrorState error={trashQuery.error} onRetry={() => trashQuery.refetch()} />
  }

  const trash = trashQuery.data ?? []
  const pending = restoreItem.isPending || purgeItem.isPending

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const restore = async (item: TrashItem) => {
    setError(null)
    setOperatingId(item.id)
    try {
      await restoreItem.mutateAsync(item.id)
      pushToast(`已恢复「${item.displayName ?? item.resourceType}」，引用关系保留`)
    } catch (caught) {
      reportError(caught as Error)
    } finally {
      setOperatingId(null)
    }
  }

  const purge = async (item: TrashItem) => {
    if (
      !window.confirm(
        `永久删除「${item.displayName ?? item.resourceType}」？此操作不可恢复，完成后无法再找回。`,
      )
    ) {
      return
    }
    setError(null)
    setOperatingId(item.id)
    try {
      await purgeItem.mutateAsync(item.id)
      pushToast('已永久删除')
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
          <h1 className="page-title">设置</h1>
          <p className="page-subtitle">时区、提醒节点与数据导出将在后续切片开放，当前提供最近删除管理。</p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">最近删除</h2>
        </div>
        <div className="card-body">
          {trash.length === 0 ? (
            <EmptyState
              icon="🗑"
              text="最近删除为空。删除的项目案例、证据或面试问题会在这里保留 30 天，期间可随时恢复。"
            />
          ) : (
            <div>
              {trash.map((item) => (
                <div className="requirement-row" key={item.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">
                      {item.displayName ?? trashResourceTypeLabel(item.resourceType)}
                    </span>
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      类型：{trashResourceTypeLabel(item.resourceType)} · 删除于{' '}
                      {formatDateTime(item.deletedAt)} · {trashExpiryLabel(item, new Date())}
                    </p>
                    {(item.impactSummary ?? []).length > 0 ? (
                      <p className="muted" style={{ margin: 0 }}>
                        影响：{(item.impactSummary ?? []).join('；')}（恢复后自动还原）
                      </p>
                    ) : null}
                  </div>
                  <div className="requirement-actions">
                    <Button
                      size="sm"
                      variant="primary"
                      type="button"
                      disabled={pending || operatingId === item.id}
                      onClick={() => restore(item)}
                    >
                      恢复
                    </Button>
                    <Button
                      size="sm"
                      variant="default"
                      type="button"
                      disabled={pending || operatingId === item.id}
                      onClick={() => purge(item)}
                    >
                      永久删除
                    </Button>
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
