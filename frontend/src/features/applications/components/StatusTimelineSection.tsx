import { Badge } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'
import { formatDateTime } from '@/features/jobs/statusLabels'
import {
  applicationStatusLabel,
} from '@/features/applications/applicationStatusLabels'
import type { StatusLog } from '@/api/applications/applicationApi'

/**
 * 区4：状态时间线。展示 ApplicationDetail.statusHistory（按 occurredAt 倒序）。
 * 普通 PUT 不改历史，只有 transition 新增记录（AT-05）。
 */
export function StatusTimelineSection({
  statusHistory,
}: {
  statusHistory: StatusLog[]
}) {
  const items = [...statusHistory].sort((a, b) =>
    a.occurredAt < b.occurredAt ? 1 : -1,
  )

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">状态时间线</h2>
      </div>
      <div className="card-body">
        {items.length === 0 ? (
          <EmptyState icon="🕘" text="暂无状态变更记录" />
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {items.map((log) => (
              <li
                key={log.id}
                className="requirement-row"
                style={{ justifyContent: 'flex-start', gap: 12 }}
              >
                <div className="requirement-main">
                  <div className="requirement-meta">
                    <Badge variant="subtle">
                      {applicationStatusLabel[log.fromStatus]}
                    </Badge>
                    <span className="muted">→</span>
                    <Badge variant="info">
                      {applicationStatusLabel[log.toStatus]}
                    </Badge>
                  </div>
                  {log.reason ? (
                    <span className="requirement-raw muted" style={{ fontSize: 13 }}>
                      {log.reason}
                    </span>
                  ) : null}
                </div>
                <span className="muted" style={{ fontSize: 12, flexShrink: 0 }}>
                  {formatDateTime(log.occurredAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
