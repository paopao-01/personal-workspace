import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { Field, Textarea } from '@/components/ui/Form'
import { ConflictBanner } from '@/components/feedback/ConflictBanner'
import { useUpdateJob } from '@/api/jobs/useJobMutations'
import type { Job, JobDecisionStatus } from '@/api/jobs/jobApi'
import { isApiError, isVersionConflict } from '@/api/errors'
import { jobDecisionLabel } from '@/features/jobs/statusLabels'
import { pushToast } from '@/components/feedback/toastStore'

const OPTIONS: NonNullable<JobDecisionStatus>[] = [
  'TO_APPLY',
  'APPLY',
  'DEFER',
  'IGNORE',
]

interface Props {
  job: Job
}

export function DecisionSection({ job }: Props) {
  // 用 key 重建内部状态，避免在 effect 中同步 set-state（oxlint set-state-in-effect）。
  // 每当 job.version 变化（服务器刷新后），组件以新 key 重新挂载，state 从 job 派生。
  return <DecisionSectionInner key={job.id + ':' + job.version} job={job} />
}

function DecisionSectionInner({ job }: Props) {
  const [selected, setSelected] = useState<JobDecisionStatus | null>(
    job.decisionStatus ?? null,
  )
  const [reason, setReason] = useState(job.decisionReason ?? '')
  const updateMutation = useUpdateJob()
  const dirty =
    selected !== (job.decisionStatus ?? null) ||
    reason !== (job.decisionReason ?? '')

  const handleSave = () => {
    updateMutation.mutate(
      {
        jobId: job.id,
        version: job.version,
        // 以当前 job 全字段作 base（JobUpdateRequest 是 JobCreateRequest 超集），仅覆盖决定字段
        body: {
          companyName: job.companyName,
          title: job.title,
          jdRawText: job.jdRawText,
          source: job.source ?? undefined,
          sourceUrl: job.sourceUrl ?? undefined,
          location: job.location ?? undefined,
          salaryRange: job.salaryRange ?? undefined,
          notes: job.notes ?? undefined,
          decisionStatus: selected,
          decisionReason: reason || null,
        },
      },
      {
        onSuccess: () => pushToast('投递决定已保存'),
        onError: (e) => {
          if (isApiError(e) && !isVersionConflict(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">投递决定</h2>
        <Button
          variant="primary"
          size="sm"
          onClick={handleSave}
          disabled={!dirty || updateMutation.isPending}
        >
          {updateMutation.isPending ? '保存中…' : '保存决定'}
        </Button>
      </div>
      <div className="card-body">
        {updateMutation.isError && isVersionConflict(updateMutation.error) ? (
          <ConflictBanner
            message="该岗位已被修改，请加载最新版本后再保存"
            actionLabel="稍后刷新"
            onAction={() => window.location.reload()}
          />
        ) : null}

        <div className="decision-radios" role="radiogroup" aria-label="投递决定">
          {OPTIONS.map((opt) => (
            <label
              key={opt}
              className="decision-radio"
              title={jobDecisionLabel[opt]}
            >
              <input
                type="radio"
                name="decision"
                value={opt}
                checked={selected === opt}
                onChange={() => setSelected(opt)}
              />
              {jobDecisionLabel[opt]}
            </label>
          ))}
        </div>

        {selected === 'APPLY' ? (
          <p className="form-hint" style={{ marginTop: 12 }}>
            已决定投递？
            <NavLink
              to={`/applications/new?jobId=${job.id}`}
              className="btn btn-link"
              style={{ display: 'inline', padding: '0 4px' }}
            >
              创建投递记录
            </NavLink>
            ，记录渠道与下一步行动。
          </p>
        ) : null}

        <div style={{ marginTop: 16 }}>
          <Field label="决定理由" hint="可选">
            <Textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              maxLength={1000}
              placeholder="如：核心技术栈匹配，Redis 需重点准备"
            />
          </Field>
        </div>
      </div>
    </section>
  )
}
