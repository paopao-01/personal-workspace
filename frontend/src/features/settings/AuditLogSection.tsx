import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { Table } from '@/components/ui/Table'
import { pushToast } from '@/components/feedback/toastStore'
import { formatBytes } from '@/api/backup/backupApi'
import { formatDateTime } from '@/features/jobs/statusLabels'
import {
  AUDIT_ACTIONS,
  AUDIT_RESOURCE_TYPES,
  exportAuditLogs,
  useAuditLogs,
  useFreedBytesSummary,
  useFreedBytesTimeseries,
  type TimeseriesGranularity,
} from '@/api/audit/auditLogApi'

const AUDIT_PAGE_SIZE = 20

/** 将 datetime-local 本地时间值转为 ISO-8601 UTC 字符串（后端 from/to 参数用）。空返回空串。 */
function toUtcIso(localValue: string): string {
  if (!localValue) return ''
  // datetime-local 值如 "2026-09-03T10:00"，本地时区；转 Date 取 ISO UTC（Instant.parse 接受的格式）
  const d = new Date(localValue)
  if (Number.isNaN(d.getTime())) return ''
  return d.toISOString()
}

/**
 * 设置页「审计日志」区块：只读分页查询全量 audit_log（跨域通用入口，
 * 承接「孤儿清理审计日志」子区块仅 BACKUP_ORPHAN_CLEANED 的备份域便捷入口）。
 * 提供 action 与 resourceType 两个可选下拉过滤；响应不含 passphrase、省略恒 null 的快照字段。
 */
export function AuditLogSection() {
  const [page, setPage] = useState(1)
  const [action, setAction] = useState('')
  const [resourceType, setResourceType] = useState('')
  const [fromLocal, setFromLocal] = useState('')  // datetime-local 本地时间值
  const [toLocal, setToLocal] = useState('')      // datetime-local 本地时间值
  const [hasFreedBytes, setHasFreedBytes] = useState(false)
  const [freedBytesMin, setFreedBytesMin] = useState('')
  const [freedBytesMax, setFreedBytesMax] = useState('')
  const [exporting, setExporting] = useState<'' | 'csv' | 'json'>('')
  const [granularity, setGranularity] = useState<TimeseriesGranularity>('day')

  const freedBytesMinNum = freedBytesMin === '' ? undefined : Number(freedBytesMin)
  const freedBytesMaxNum = freedBytesMax === '' ? undefined : Number(freedBytesMax)

  const query = useAuditLogs({
    page,
    pageSize: AUDIT_PAGE_SIZE,
    action: action || undefined,
    resourceType: resourceType || undefined,
    from: fromLocal ? toUtcIso(fromLocal) : undefined,
    to: toLocal ? toUtcIso(toLocal) : undefined,
    hasFreedBytes: hasFreedBytes || undefined,
    freedBytesMin: freedBytesMinNum !== undefined && !Number.isNaN(freedBytesMinNum) ? freedBytesMinNum : undefined,
    freedBytesMax: freedBytesMaxNum !== undefined && !Number.isNaN(freedBytesMaxNum) ? freedBytesMaxNum : undefined,
  })

  // 释放字节数聚合统计（复用与查询同源的过滤条件）
  const summary = useFreedBytesSummary({
    action: action || undefined,
    resourceType: resourceType || undefined,
    from: fromLocal ? toUtcIso(fromLocal) : undefined,
    to: toLocal ? toUtcIso(toLocal) : undefined,
    hasFreedBytes: hasFreedBytes || undefined,
    freedBytesMin: freedBytesMinNum !== undefined && !Number.isNaN(freedBytesMinNum) ? freedBytesMinNum : undefined,
    freedBytesMax: freedBytesMaxNum !== undefined && !Number.isNaN(freedBytesMaxNum) ? freedBytesMaxNum : undefined,
  })

  // 释放字节数时间序列分组（复用与查询同源的过滤条件 + 粒度切换）
  const timeseries = useFreedBytesTimeseries({
    action: action || undefined,
    resourceType: resourceType || undefined,
    from: fromLocal ? toUtcIso(fromLocal) : undefined,
    to: toLocal ? toUtcIso(toLocal) : undefined,
    hasFreedBytes: hasFreedBytes || undefined,
    freedBytesMin: freedBytesMinNum !== undefined && !Number.isNaN(freedBytesMinNum) ? freedBytesMinNum : undefined,
    freedBytesMax: freedBytesMaxNum !== undefined && !Number.isNaN(freedBytesMaxNum) ? freedBytesMaxNum : undefined,
  }, granularity)

  // 当前生效的过滤条件（供导出复用，与查询一致）
  const exportFilters = {
    action: action || undefined,
    resourceType: resourceType || undefined,
    from: fromLocal ? toUtcIso(fromLocal) : undefined,
    to: toLocal ? toUtcIso(toLocal) : undefined,
    hasFreedBytes: hasFreedBytes || undefined,
    freedBytesMin: freedBytesMinNum !== undefined && !Number.isNaN(freedBytesMinNum) ? freedBytesMinNum : undefined,
    freedBytesMax: freedBytesMaxNum !== undefined && !Number.isNaN(freedBytesMaxNum) ? freedBytesMaxNum : undefined,
  }

  const onExport = async (format: 'csv' | 'json') => {
    setExporting(format)
    try {
      await exportAuditLogs(format, exportFilters)
    } catch (err) {
      pushToast(`导出失败：${err instanceof Error ? err.message : String(err)}`, 'error')
    } finally {
      setExporting('')
    }
  }

  const onFilterChange = (setter: (v: string) => void) => (event: React.ChangeEvent<HTMLSelectElement>) => {
    setter(event.target.value)
    setPage(1)
  }
  const onDateChange = (setter: (v: string) => void) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setter(event.target.value)
    setPage(1)
  }
  const onToggleFreedBytes = (event: React.ChangeEvent<HTMLInputElement>) => {
    setHasFreedBytes(event.target.checked)
    setPage(1)
  }
  const onFreedBytesRangeChange = (setter: (v: string) => void) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setter(event.target.value)
    setPage(1)
  }
  const onGranularityChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setGranularity(event.target.value as TimeseriesGranularity)
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">审计日志</h2>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          只读追溯关键用户确认与不可覆盖操作：二次投递确认、需求合并/编辑/删除、孤儿文件清理。
          可按动作类型、资源类型、时间范围与释放字节范围过滤；按时间倒序展示。审计为事后只读视图，不含 passphrase，
          省略恒为空的快照字段。
        </p>
        <div className="flex-row" style={{ justifyContent: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ minWidth: 200 }}>
            <Field label="动作类型">
              <Select
                value={action}
                onChange={onFilterChange(setAction)}
                aria-label="按动作类型过滤"
              >
                <option value="">全部</option>
                {AUDIT_ACTIONS.map((a) => (
                  <option key={a} value={a}>{a}</option>
                ))}
              </Select>
            </Field>
          </div>
          <div style={{ minWidth: 200 }}>
            <Field label="资源类型">
              <Select
                value={resourceType}
                onChange={onFilterChange(setResourceType)}
                aria-label="按资源类型过滤"
              >
                <option value="">全部</option>
                {AUDIT_RESOURCE_TYPES.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </Select>
            </Field>
          </div>
          <div style={{ minWidth: 200 }}>
            <Field label="起始时间">
              <input
                type="datetime-local"
                value={fromLocal}
                onChange={onDateChange(setFromLocal)}
                aria-label="按起始时间过滤（含边界）"
              />
            </Field>
          </div>
          <div style={{ minWidth: 200 }}>
            <Field label="结束时间">
              <input
                type="datetime-local"
                value={toLocal}
                onChange={onDateChange(setToLocal)}
                aria-label="按结束时间过滤（含边界）"
              />
            </Field>
          </div>
          <div style={{ alignSelf: 'flex-end', paddingBottom: 6 }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={hasFreedBytes}
                onChange={onToggleFreedBytes}
                aria-label="仅看有释放字节的记录"
              />
              仅看有释放字节
            </label>
          </div>
          <div style={{ minWidth: 120, alignSelf: 'flex-end' }}>
            <Field label="释放字节下界">
              <input
                type="number"
                min={0}
                value={freedBytesMin}
                onChange={onFreedBytesRangeChange(setFreedBytesMin)}
                placeholder="最小"
                aria-label="按释放字节下界过滤（含边界）"
              />
            </Field>
          </div>
          <div style={{ minWidth: 120, alignSelf: 'flex-end' }}>
            <Field label="释放字节上界">
              <input
                type="number"
                min={0}
                value={freedBytesMax}
                onChange={onFreedBytesRangeChange(setFreedBytesMax)}
                placeholder="最大"
                aria-label="按释放字节上界过滤（含边界）"
              />
            </Field>
          </div>
          <div className="flex-row" style={{ justifyContent: 'flex-start', alignSelf: 'flex-end' }}>
            <Button
              variant="default"
              type="button"
              onClick={() => query.refetch()}
              disabled={query.isFetching}
            >
              {query.isFetching ? '刷新中…' : '刷新'}
            </Button>
            <Button
              variant="default"
              type="button"
              onClick={() => onExport('csv')}
              disabled={exporting !== ''}
            >
              {exporting === 'csv' ? '导出中…' : '导出 CSV'}
            </Button>
            <Button
              variant="default"
              type="button"
              onClick={() => onExport('json')}
              disabled={exporting !== ''}
            >
              {exporting === 'json' ? '导出中…' : '导出 JSON'}
            </Button>
          </div>
        </div>

        <p className="muted" style={{ margin: '12px 0' }}>
          释放字节汇总：
          {summary.isLoading
            ? '统计中…'
            : summary.error
              ? '统计失败'
              : `共 ${summary.data?.totalCount ?? 0} 条 · 释放 ${formatBytes(summary.data?.totalFreedBytes ?? 0)}（均值 ${formatBytes(summary.data?.avgFreedBytes ? Math.round(summary.data.avgFreedBytes) : 0)}/条）`}
        </p>

        <div style={{ margin: '12px 0' }}>
          <div className="flex-row" style={{ justifyContent: 'flex-start', gap: 12, alignItems: 'center', marginBottom: 8 }}>
            <span className="muted" style={{ marginTop: 0 }}>释放字节趋势</span>
            <Select value={granularity} onChange={onGranularityChange} aria-label="趋势分组粒度">
              <option value="day">按天</option>
              <option value="hour">按小时</option>
            </Select>
          </div>
          {timeseries.isLoading ? (
            <p className="muted" style={{ margin: 0 }}>趋势统计中…</p>
          ) : timeseries.error ? (
            <p className="muted" style={{ margin: 0 }}>趋势统计失败</p>
          ) : (timeseries.data ?? []).length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>无趋势数据</p>
          ) : (
            <Table headers={['日期', '记录数', '释放字节', '均值/条']}>
              {(timeseries.data ?? []).map((bucket) => (
                <tr key={bucket.date}>
                  <td>{bucket.date}</td>
                  <td>{bucket.count}</td>
                  <td>{formatBytes(bucket.totalFreedBytes)}</td>
                  <td>{formatBytes(bucket.avgFreedBytes ? Math.round(bucket.avgFreedBytes) : 0)}</td>
                </tr>
              ))}
            </Table>
          )}
        </div>

        {query.isLoading ? (
          <Spinner label="加载审计日志…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (query.data?.items ?? []).length === 0 ? (
          <EmptyState text="无匹配审计记录" />
        ) : (
          <>
            <Table headers={['时间', '资源类型', '动作', '资源 ID', '释放字节', '详情']}>
              {(query.data?.items ?? []).map((entry) => (
                <tr key={entry.id}>
                  <td>{formatDateTime(entry.occurredAt)}</td>
                  <td>{entry.resourceType}</td>
                  <td>{entry.action}</td>
                  <td>{entry.resourceId}</td>
                  <td>{entry.freedBytes != null ? formatBytes(entry.freedBytes) : '—'}</td>
                  <td>{entry.reason}</td>
                </tr>
              ))}
            </Table>
            <div className="pagination">
              <span className="pagination-info">
                共 {query.data?.total ?? 0} 条 · 第 {page}/{query.data?.totalPages ?? 0} 页
              </span>
              <Button
                type="button"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page <= 1}
              >
                上一页
              </Button>
              <Button
                type="button"
                onClick={() =>
                  setPage((p) =>
                    p >= (query.data?.totalPages ?? 1) ? p : p + 1,
                  )
                }
                disabled={page >= (query.data?.totalPages ?? 1)}
              >
                下一页
              </Button>
            </div>
          </>
        )}
      </div>
    </section>
  )
}
