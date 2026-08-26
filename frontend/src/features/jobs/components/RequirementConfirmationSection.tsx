import {
  RequirementRow,
  ManualMatchInline,
} from '@/features/jobs/components/RequirementRow'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { useJobRequirements } from '@/api/jobs/useJobQueries'
import { useExtractRequirements } from '@/api/jobs/useJobMutations'
import type { JobRequirement, RequirementType } from '@/api/jobs/jobApi'
import {
  requirementTypeLabel,
  requirementTypeOrder,
} from '@/features/jobs/statusLabels'
import { pushToast } from '@/components/feedback/toastStore'

interface Props {
  jobId: string
}

function groupByType(items: JobRequirement[]): {
  type: RequirementType
  items: JobRequirement[]
}[] {
  return requirementTypeOrder
    .map((type) => ({
      type,
      items: items.filter((i) => i.type === type),
    }))
    .filter((g) => g.items.length > 0)
}

export function RequirementConfirmationSection({ jobId }: Props) {
  const { data: requirements, isLoading, error, refetch } = useJobRequirements(jobId)
  const extractMutation = useExtractRequirements()

  const handleExtract = () => {
    extractMutation.mutate(jobId, {
      onSuccess: (res) =>
        pushToast(`提取完成，候选 ${res.candidates.length} 项`),
      onError: () => pushToast('提取失败', 'error'),
    })
  }

  const items = requirements ?? []
  const groups = groupByType(items)
  const hasPending = items.some((i) => i.confirmationStatus === 'PENDING')
  const hasAny = items.length > 0

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">候选要求确认</h2>
        <Button
          variant="primary"
          size="sm"
          onClick={handleExtract}
          disabled={extractMutation.isPending}
        >
          {extractMutation.isPending ? '提取中…' : '提取候选要求'}
        </Button>
      </div>
      <div className="card-body">
        {isLoading ? (
          <Spinner label="加载候选要求…" />
        ) : error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : !hasAny ? (
          <p className="muted">
            尚无候选要求。点击「提取候选要求」从 JD 规则提取（结果均为待确认，不会覆盖已确认项）。
          </p>
        ) : (
          <>
            {hasPending ? (
              <p className="form-hint" style={{ marginBottom: 12 }}>
                待确认候选项不参与差距结论。请确认后查看差距清单。
              </p>
            ) : null}
            {groups.map((g) => (
              <div key={g.type} className="requirement-group">
                <h3 className="requirement-group-title">
                  {requirementTypeLabel[g.type]}（{g.items.length}）
                </h3>
                {g.items.map((req) => (
                  <div key={req.id}>
                    <RequirementRow requirement={req} jobId={jobId} />
                    {req.confirmationStatus === 'CONFIRMED' ? (
                      <ManualMatchInline jobId={jobId} req={req} />
                    ) : null}
                  </div>
                ))}
              </div>
            ))}
          </>
        )}
      </div>
    </section>
  )
}
