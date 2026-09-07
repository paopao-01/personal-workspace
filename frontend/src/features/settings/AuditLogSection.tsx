import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { Table } from '@/components/ui/Table'
import { formatDateTime } from '@/features/jobs/statusLabels'
import {
  AUDIT_ACTIONS,
  AUDIT_RESOURCE_TYPES,
  useAuditLogs,
} from '@/api/audit/auditLogApi'

const AUDIT_PAGE_SIZE = 20

/**
 * 设置页「审计日志」区块：只读分页查询全量 audit_log（跨域通用入口，
 * 承接「孤儿清理审计日志」子区块仅 BACKUP_ORPHAN_CLEANED 的备份域便捷入口）。
 * 提供 action 与 resourceType 两个可选下拉过滤；响应不含 passphrase、省略恒 null 的快照字段。
 */
export function AuditLogSection() {
  const [page, setPage] = useState(1)
  const [action, setAction] = useState('')
  const [resourceType, setResourceType] = useState('')

  const query = useAuditLogs({
    page,
    pageSize: AUDIT_PAGE_SIZE,
    action: action || undefined,
    resourceType: resourceType || undefined,
  })

  const onFilterChange = (setter: (v: string) => void) => (event: React.ChangeEvent<HTMLSelectElement>) => {
    setter(event.target.value)
    setPage(1)
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">审计日志</h2>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          只读追溯关键用户确认与不可覆盖操作：二次投递确认、需求合并/编辑/删除、孤儿文件清理。
          可按动作类型与资源类型过滤；按时间倒序展示。审计为事后只读视图，不含 passphrase，
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
          <div className="flex-row" style={{ justifyContent: 'flex-start', alignSelf: 'flex-end' }}>
            <Button
              variant="default"
              type="button"
              onClick={() => query.refetch()}
              disabled={query.isFetching}
            >
              {query.isFetching ? '刷新中…' : '刷新'}
            </Button>
          </div>
        </div>

        {query.isLoading ? (
          <Spinner label="加载审计日志…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (query.data?.items ?? []).length === 0 ? (
          <EmptyState text="无匹配审计记录" />
        ) : (
          <>
            <Table headers={['时间', '资源类型', '动作', '资源 ID', '详情']}>
              {(query.data?.items ?? []).map((entry) => (
                <tr key={entry.id}>
                  <td>{formatDateTime(entry.occurredAt)}</td>
                  <td>{entry.resourceType}</td>
                  <td>{entry.action}</td>
                  <td>{entry.resourceId}</td>
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
