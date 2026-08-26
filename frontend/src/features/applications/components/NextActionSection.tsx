import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { ConflictBanner } from '@/components/feedback/ConflictBanner'
import { ApplicationForm } from '@/features/applications/components/ApplicationForm'
import {
  toUpdateRequest,
  type ApplicationFormValues,
} from '@/features/applications/components/applicationFormValues'
import { useUpdateApplication } from '@/api/applications/useApplicationMutations'
import { formatDateTime } from '@/features/jobs/statusLabels'
import {
  isApiError,
  isVersionConflict,
  type FieldError,
} from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import type { ApplicationDetail } from '@/api/applications/applicationApi'

/**
 * 区3：下一步行动。展示行动文本/截止时间 + 行内编辑。
 *
 * PUT 全字段覆盖写：编辑 nextAction/nextActionDueAt 时，ApplicationForm 的
 * 初始值由 appToValues(detail) 回填全部字段，提交时 toUpdateRequest 包含全部
 * 8 字段，避免未传字段被清空。key 重建确保版本刷新后表单状态同步。
 *
 * AT-09：缺失行动醒目提示；逾期行动显示逾期天数。
 */
export function NextActionSection({
  detail,
}: {
  detail: ApplicationDetail
}) {
  const [editing, setEditing] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>()
  const updateMutation = useUpdateApplication()

  const handleSubmit = (values: ApplicationFormValues) => {
    setFieldErrors(undefined)
    updateMutation.mutate(
      {
        applicationId: detail.id,
        version: detail.version,
        body: toUpdateRequest(values),
      },
      {
        onSuccess: () => {
          pushToast('投递信息已更新')
          setEditing(false)
        },
        onError: (e) => {
          if (isVersionConflict(e)) {
            setEditing(false)
          } else if (isApiError(e) && e.fieldErrors) {
            setFieldErrors(e.fieldErrors)
          } else if (isApiError(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  // 逾期/缺失检测（AT-09）
  const overdue =
    detail.nextActionDueAt && new Date(detail.nextActionDueAt) < new Date()
  const missing = !detail.nextAction

  if (editing) {
    return (
      <section className="card detail-summary">
        <div className="card-header">
          <h2 className="card-title">编辑投递信息</h2>
        </div>
        <div className="card-body">
          {updateMutation.isError && isVersionConflict(updateMutation.error) ? (
            <ConflictBanner
              message="该投递已被修改"
              detail="请加载最新版本后再编辑"
              actionLabel="稍后刷新"
              onAction={() => setEditing(false)}
            />
          ) : null}
          <div key={detail.id + ':' + detail.version}>
            <ApplicationForm
              mode="edit"
              application={detail}
              fieldErrors={fieldErrors}
              submitting={updateMutation.isPending}
              onSubmit={handleSubmit}
              onCancel={() => setEditing(false)}
            />
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">下一步行动</h2>
        <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
          编辑
        </Button>
      </div>
      <div className="card-body">
        {missing ? (
          <div className="conflict-banner" style={{ marginBottom: 12 }}>
            <strong>该投递缺少下一步行动，建议补充</strong>
          </div>
        ) : null}
        <div className="detail-summary">
          <dl>
            <dt>行动</dt>
            <dd>{detail.nextAction || '—'}</dd>
            <dt>截止时间</dt>
            <dd>
              {detail.nextActionDueAt ? (
                <span className={overdue ? '' : 'muted'} style={overdue ? { color: 'var(--danger)' } : undefined}>
                  {formatDateTime(detail.nextActionDueAt)}
                </span>
              ) : (
                <span className="muted">—</span>
              )}
            </dd>
          </dl>
        </div>
        {overdue ? (
          <div style={{ marginTop: 8 }}>
            <Badge variant="danger">
              已逾期
            </Badge>
          </div>
        ) : null}
      </div>
    </section>
  )
}
