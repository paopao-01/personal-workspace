import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { JobForm } from '@/features/jobs/components/JobForm'
import {
  toUpdateRequest,
  type JobFormValues,
} from '@/features/jobs/components/jobFormValues'
import {
  formatDateTime,
  jobDecisionLabel,
  jobStatusLabel,
} from '@/features/jobs/statusLabels'
import type { Job } from '@/api/jobs/jobApi'
import {
  isApiError,
  isVersionConflict,
  type FieldError,
} from '@/api/errors'
import { ConflictBanner } from '@/components/feedback/ConflictBanner'
import { useUpdateJob, useArchiveJob, useRestoreJob } from '@/api/jobs/useJobMutations'
import { pushToast } from '@/components/feedback/toastStore'

interface Props {
  job: Job
}

export function JobSummarySection({ job }: Props) {
  const [editing, setEditing] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>()

  const updateMutation = useUpdateJob()
  const archiveMutation = useArchiveJob()
  const restoreMutation = useRestoreJob()

  const handleSubmit = (values: JobFormValues) => {
    setFieldErrors(undefined)
    updateMutation.mutate(
      {
        jobId: job.id,
        version: job.version,
        body: toUpdateRequest(values, job.decisionStatus, job.decisionReason),
      },
      {
        onSuccess: () => {
          pushToast('岗位信息已更新')
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

  const handleArchive = () => {
    if (!confirm(`确认归档岗位「${job.title}」？归档后仍可恢复。`)) return
    archiveMutation.mutate(
      { jobId: job.id, version: job.version },
      {
        onSuccess: () => pushToast('岗位已归档'),
        onError: (e) =>
          pushToast(isApiError(e) || isVersionConflict(e) ? e.message : '操作失败', 'error'),
      },
    )
  }

  const handleRestore = () => {
    restoreMutation.mutate(
      { jobId: job.id, version: job.version },
      {
        onSuccess: () => pushToast('岗位已恢复'),
        onError: (e) =>
          pushToast(isApiError(e) ? e.message : '操作失败', 'error'),
      },
    )
  }

  if (editing) {
    return (
      <section className="card detail-summary">
        <div className="card-header">
          <h2 className="card-title">编辑岗位</h2>
        </div>
        <div className="card-body">
          {updateMutation.isError && isVersionConflict(updateMutation.error) ? (
            <ConflictBanner
              message="该岗位已被修改"
              detail="请加载最新版本后再编辑"
              actionLabel="加载最新"
              onAction={() => setEditing(false)}
            />
          ) : null}
          <JobForm
            mode="edit"
            job={job}
            fieldErrors={fieldErrors}
            submitting={updateMutation.isPending}
            onSubmit={handleSubmit}
            onCancel={() => setEditing(false)}
          />
        </div>
      </section>
    )
  }

  return (
    <section className="card detail-summary">
      <div className="card-header">
        <h2 className="card-title">岗位摘要</h2>
        <div className="flex-row">
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
            编辑
          </Button>
          {job.status === 'ACTIVE' ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleArchive}
              disabled={archiveMutation.isPending}
            >
              归档
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRestore}
              disabled={restoreMutation.isPending}
            >
              恢复
            </Button>
          )}
        </div>
      </div>
      <div className="card-body">
        <dl>
          <dt>公司</dt>
          <dd>{job.companyName}</dd>
          <dt>岗位</dt>
          <dd>{job.title}</dd>
          <dt>地点</dt>
          <dd className="muted">{job.location || '—'}</dd>
          <dt>来源</dt>
          <dd className="muted">{job.source || '—'}</dd>
          <dt>薪资</dt>
          <dd className="muted">{job.salaryRange || '—'}</dd>
          <dt>来源链接</dt>
          <dd>
            {job.sourceUrl ? (
              <a
                href={job.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                查看链接
              </a>
            ) : (
              <span className="muted">—</span>
            )}
          </dd>
          <dt>岗位状态</dt>
          <dd>
            <Badge variant={job.status === 'ARCHIVED' ? 'neutral' : 'primary'}>
              {jobStatusLabel[job.status]}
            </Badge>
          </dd>
          <dt>投递决定</dt>
          <dd>
            {job.decisionStatus ? (
              <Badge variant="info">{jobDecisionLabel[job.decisionStatus]}</Badge>
            ) : (
              <Badge variant="subtle">未决定</Badge>
            )}
          </dd>
          <dt>最近更新</dt>
          <dd className="muted">{formatDateTime(job.updatedAt)}</dd>
        </dl>
        <div style={{ marginTop: 16 }}>
          <div className="form-label" style={{ marginBottom: 6 }}>
            JD 原文
          </div>
          <div className="jd-text">{job.jdRawText}</div>
        </div>
        {job.decisionReason ? (
          <div style={{ marginTop: 12 }}>
            <div className="form-label" style={{ marginBottom: 4 }}>
              决定理由
            </div>
            <p className="muted" style={{ margin: 0 }}>
              {job.decisionReason}
            </p>
          </div>
        ) : null}
      </div>
    </section>
  )
}
