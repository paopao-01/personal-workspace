import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import { useCreateExport } from '@/api/settings/useExportMutations'
import type { DataExport } from '@/api/settings/exportApi'
import {
  usePurgeTrashItem,
  useRestoreTrashItem,
} from '@/api/settings/useTrashMutations'
import { useTrash } from '@/api/settings/useTrashQueries'
import type { TrashItem } from '@/api/settings/trashApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime } from '@/features/jobs/statusLabels'
import { exportStatusLabel, exportStatusVariant } from '@/features/settings/settingsLabels'
import { trashExpiryLabel, trashResourceTypeLabel } from '@/features/settings/settingsLabels'

export function SettingsPage() {
  const trashQuery = useTrash()
  const restoreItem = useRestoreTrashItem()
  const purgeItem = usePurgeTrashItem()
  const createExport = useCreateExport()
  const [lastExport, setLastExport] = useState<DataExport | null>(null)
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

  const runExport = async () => {
    setError(null)
    try {
      const created = await createExport.mutateAsync()
      setLastExport(created)
      if (created.status === 'SUCCEEDED') {
        pushToast('导出完成，可点击下载保存 JSON 文件')
      } else {
        pushToast(created.failureReason ?? '导出失败', 'error')
      }
    } catch (caught) {
      reportError(caught as Error)
    }
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
          <p className="page-subtitle">管理数据导出与最近删除；时区与提醒节点设置将在后续切片开放。</p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">数据导出</h2>
        </div>
        <div className="card-body">
          <p className="muted" style={{ marginTop: 0 }}>
            数据范围：岗位与要求、投递、面试、复盘、问题、知识点、学习任务、技能、项目案例与证据的完整
            JSON 数据及关联 ID。
          </p>
          <p className="muted" style={{ marginTop: 0 }}>
            不包含：访问令牌、密钥、应用运行日志、幂等记录和未确认的 AI 输入输出。
          </p>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            <Button
              variant="primary"
              type="button"
              disabled={createExport.isPending}
              onClick={runExport}
            >
              {createExport.isPending ? '导出中…' : '创建 JSON 导出'}
            </Button>
          </div>
          {lastExport ? (
            <div className="plain-block" style={{ marginTop: 12 }}>
              <p style={{ margin: 0 }}>
                <Badge variant={exportStatusVariant(lastExport.status)}>
                  {exportStatusLabel(lastExport.status)}
                </Badge>
                <span className="muted" style={{ marginLeft: 8 }}>
                  {formatDateTime(lastExport.createdAt)}
                </span>
              </p>
              {lastExport.downloadUrl ? (
                <p style={{ margin: '8px 0 0' }}>
                  <a
                    className="btn btn-link"
                    href={lastExport.downloadUrl}
                    download={`jobhub-export-${lastExport.id}.json`}
                  >
                    下载导出文件
                  </a>
                </p>
              ) : null}
              {lastExport.failureReason ? (
                <p className="muted" style={{ margin: '8px 0 0' }}>
                  {lastExport.failureReason}
                </p>
              ) : null}
            </div>
          ) : null}
        </div>
      </section>

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
