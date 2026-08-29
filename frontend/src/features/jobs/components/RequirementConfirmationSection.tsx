import { useState } from 'react'
import {
  RequirementRow,
  ManualMatchInline,
} from '@/features/jobs/components/RequirementRow'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { useJobRequirements } from '@/api/jobs/useJobQueries'
import {
  useExtractRequirements,
  useMergeRequirements,
} from '@/api/jobs/useJobMutations'
import { isApiError, isNetworkError } from '@/api/errors'
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
  const mergeMutation = useMergeRequirements()
  // 批量合并仅限同类候选（页面规格 P02），选中项须全部为待确认
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [actionError, setActionError] = useState<string | null>(null)

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

  const selectedItems = items.filter(
    (i) => selectedIds.includes(i.id) && i.confirmationStatus === 'PENDING',
  )
  const sameType =
    selectedItems.length < 2 ||
    selectedItems.every((i) => i.type === selectedItems[0].type)
  const canMerge = selectedItems.length >= 2 && sameType
  const mergeTarget = selectedItems[0]

  const toggleSelected = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id],
    )
  }

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '合并失败，请稍后重试'
    setActionError(message)
    pushToast(message, 'error')
  }

  const handleMerge = async () => {
    if (!mergeTarget) return
    const sources = selectedItems.slice(1)
    if (
      !window.confirm(
        `将把选中的 ${sources.length} 项候选要求合并到「${mergeTarget.normalizedName ?? mergeTarget.rawText}」，被合并项将移出列表且不再参与差距结论（原始记录保留）。确定合并？`,
      )
    ) {
      return
    }
    setActionError(null)
    try {
      await mergeMutation.mutateAsync({
        jobId,
        targetId: mergeTarget.id,
        sourceIds: sources.map((i) => i.id),
      })
      setSelectedIds([])
      refetch()
      pushToast('候选要求已合并')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

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
      {actionError ? (
        <div className="conflict-banner">
          <span>{actionError}</span>
        </div>
      ) : null}
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
            {selectedItems.length > 0 ? (
              <div className="flex-row" style={{ gap: 8, marginBottom: 12 }}>
                <span className="muted">
                  已选择 {selectedItems.length} 项
                  {!sameType ? '（仅同类候选可合并）' : ''}，合并到{' '}
                  {mergeTarget ? mergeTarget.normalizedName ?? mergeTarget.rawText : ''}
                </span>
                <Button
                  size="sm"
                  variant="primary"
                  type="button"
                  disabled={!canMerge || mergeMutation.isPending}
                  onClick={handleMerge}
                >
                  {mergeMutation.isPending ? '合并中…' : '合并所选'}
                </Button>
                <Button
                  size="sm"
                  variant="default"
                  type="button"
                  onClick={() => setSelectedIds([])}
                >
                  取消选择
                </Button>
              </div>
            ) : null}
            {groups.map((g) => (
              <div key={g.type} className="requirement-group">
                <h3 className="requirement-group-title">
                  {requirementTypeLabel[g.type]}（{g.items.length}）
                </h3>
                {g.items.map((req) => {
                  const selectable = req.confirmationStatus === 'PENDING'
                  return (
                    <div key={req.id} style={{ display: 'flex', gap: 8 }}>
                      {selectable ? (
                        <input
                          type="checkbox"
                          aria-label={`选择候选 ${req.normalizedName ?? req.rawText}`}
                          checked={selectedIds.includes(req.id)}
                          onChange={() => toggleSelected(req.id)}
                          style={{ marginTop: 6 }}
                        />
                      ) : null}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <RequirementRow requirement={req} jobId={jobId} />
                        {req.confirmationStatus === 'CONFIRMED' ? (
                          <ManualMatchInline jobId={jobId} req={req} />
                        ) : null}
                      </div>
                    </div>
                  )
                })}
              </div>
            ))}
          </>
        )}
      </div>
    </section>
  )
}
