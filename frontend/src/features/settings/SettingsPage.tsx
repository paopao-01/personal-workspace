import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import { useCreateExport } from '@/api/settings/useExportMutations'
import type { DataExport } from '@/api/settings/exportApi'
import type { UserSettings } from '@/api/settings/settingsApi'
import { useUpdateSettings } from '@/api/settings/useSettingsMutations'
import { useSettings } from '@/api/settings/useSettingsQueries'
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
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime } from '@/features/jobs/statusLabels'
import { exportStatusLabel, exportStatusVariant } from '@/features/settings/settingsLabels'
import { trashExpiryLabel, trashResourceTypeLabel } from '@/features/settings/settingsLabels'

const PRESET_REMINDERS: Array<{ minutes: number; label: string }> = [
  { minutes: 1440, label: '提前 1 天' },
  { minutes: 120, label: '提前 2 小时' },
  { minutes: 30, label: '提前 30 分钟' },
]

export function SettingsPage() {
  const trashQuery = useTrash()
  const settingsQuery = useSettings()
  const restoreItem = useRestoreTrashItem()
  const purgeItem = usePurgeTrashItem()
  const createExport = useCreateExport()
  const [lastExport, setLastExport] = useState<DataExport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [operatingId, setOperatingId] = useState<string | null>(null)

  const settingsData = settingsQuery.data
  if (trashQuery.isLoading || settingsQuery.isLoading) {
    return <Spinner label="加载设置…" />
  }
  if (trashQuery.error) {
    return <ErrorState error={trashQuery.error} onRetry={() => trashQuery.refetch()} />
  }
  if (settingsQuery.error) {
    return <ErrorState error={settingsQuery.error} onRetry={() => settingsQuery.refetch()} />
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
          <p className="page-subtitle">管理时区、默认提醒节点、数据导出与最近删除。</p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">时区与提醒</h2>
        </div>
        <div className="card-body">
          {settingsData ? (
            <TimeZoneReminderForm key={settingsData.version} settings={settingsData} />
          ) : null}
        </div>
      </section>

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

/**
 * 时区与默认提醒节点表单。以 settings.version 作为 key 挂载，保存成功后服务端版本递增触发重挂载，
 * 表单随之回到服务端归一化后的值；无 effect 状态同步。
 */
function TimeZoneReminderForm({ settings }: { settings: UserSettings }) {
  const updateSettings = useUpdateSettings()
  const [timeZone, setTimeZone] = useState(settings.timeZone)
  const [offsets, setOffsets] = useState<number[]>(
    settings.defaultReminderOffsetsMinutes ?? [],
  )
  const [customMinutes, setCustomMinutes] = useState('')

  const toggleOffset = (minutes: number) => {
    setOffsets((prev) =>
      prev.includes(minutes)
        ? prev.filter((item) => item !== minutes)
        : [...prev, minutes].sort((a, b) => b - a),
    )
  }

  const addCustomOffset = () => {
    const value = Number(customMinutes)
    if (!Number.isFinite(value) || value < 1 || !Number.isInteger(value)) {
      pushToast('自定义提醒节点需为不小于 1 的整数分钟', 'error')
      return
    }
    if (!offsets.includes(value)) {
      setOffsets((prev) => [...prev, value].sort((a, b) => b - a))
    }
    setCustomMinutes('')
  }

  const save = async () => {
    try {
      const saved = await updateSettings.mutateAsync({
        version: settings.version,
        body: {
          timeZone: timeZone.trim(),
          defaultReminderOffsetsMinutes: offsets,
        },
      })
      pushToast('设置已保存，新建与改期的面试将按当前提醒节点生成提醒')
      if (saved.timeZone !== timeZone.trim()) {
        setTimeZone(saved.timeZone)
      }
    } catch (caught) {
      const message = isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试'
      pushToast(message, 'error')
    }
  }

  return (
    <form onSubmit={(event) => event.preventDefault()} noValidate>
      <Field label="显示时区" required>
        <Input
          value={timeZone}
          onChange={(event) => setTimeZone(event.target.value)}
          placeholder="Asia/Shanghai"
          maxLength={64}
          required
        />
        <p className="form-hint">IANA 时区名，如 Asia/Shanghai、UTC；应用内时间将按此时区显示。</p>
      </Field>
      <Field label="默认提醒节点">
        <div className="evidence-picker" role="group" aria-label="默认提醒节点">
          {PRESET_REMINDERS.map((preset) => (
            <label key={preset.minutes} className="evidence-picker-item">
              <input
                type="checkbox"
                checked={offsets.includes(preset.minutes)}
                onChange={() => toggleOffset(preset.minutes)}
              />
              <span>{preset.label}</span>
            </label>
          ))}
          {offsets
            .filter((minutes) => !PRESET_REMINDERS.some((preset) => preset.minutes === minutes))
            .map((minutes) => (
              <label key={minutes} className="evidence-picker-item">
                <input type="checkbox" checked onChange={() => toggleOffset(minutes)} />
                <span>提前 {minutes} 分钟</span>
              </label>
            ))}
        </div>
        <p className="form-hint">
          新建与改期面试时按勾选的节点生成站内提醒；全部取消表示不生成默认提醒。单个面试还可在详情页单独增删提醒。
        </p>
      </Field>
      <div className="flex-row" style={{ justifyContent: 'flex-start', gap: 8 }}>
        <div style={{ width: 180 }}>
          <Input
            value={customMinutes}
            onChange={(event) => setCustomMinutes(event.target.value)}
            placeholder="自定义节点（分钟）"
            aria-label="自定义提醒节点分钟数"
            inputMode="numeric"
          />
        </div>
        <Button variant="default" type="button" onClick={addCustomOffset}>
          添加节点
        </Button>
      </div>
      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
        <Button
          variant="primary"
          type="button"
          disabled={updateSettings.isPending || !timeZone.trim()}
          onClick={save}
        >
          {updateSettings.isPending ? '保存中…' : '保存设置'}
        </Button>
      </div>
    </form>
  )
}
