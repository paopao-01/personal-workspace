import { Badge } from '@/components/ui/Badge'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { EmptyState } from '@/components/ui/EmptyState'
import { useGapList } from '@/api/jobs/useJobQueries'
import { gapStatusLabel, gapStatusVariant } from '@/features/jobs/statusLabels'

interface Props {
  jobId: string
}

export function GapListSection({ jobId }: Props) {
  const { data: gaps, isLoading, error, refetch } = useGapList(jobId)
  const items = gaps ?? []

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">差距清单</h2>
      </div>
      <div className="card-body">
        {isLoading ? (
          <Spinner label="加载差距清单…" />
        ) : error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            text="确认候选要求后，这里会基于已确认要求生成差距清单。"
          />
        ) : (
          items.map((gap) => (
            <div key={gap.requirement.id} className="gap-item">
              <div className="requirement-main">
                <span className="requirement-raw">
                  {gap.requirement.rawText}
                </span>
                <div className="requirement-meta">
                  <Badge variant={gapStatusVariant[gap.status]}>
                    {gapStatusLabel[gap.status]}
                  </Badge>
                  {gap.requirement.normalizedName ? (
                    <span className="muted">
                      · {gap.requirement.normalizedName}
                    </span>
                  ) : null}
                </div>
                {gap.manualOverrideReason ? (
                  <span className="gap-item-reason">
                    人工修正：{gap.manualOverrideReason}
                  </span>
                ) : null}
                <span className="muted" style={{ fontSize: 12 }}>
                  证据：{gap.evidence.length > 0 ? '有' : '暂无'}
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  )
}
