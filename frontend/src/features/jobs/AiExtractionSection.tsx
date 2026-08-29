import { useState } from 'react'
import {
  useAcceptAiJobItem,
  useAiJobsByJob,
  useCancelAiJob,
  useCreateAiJob,
  useRejectAiJobItem,
  useRetryAiJob,
} from '@/api/ai/useAiQueries'
import type { AiJob, AiJobItem } from '@/api/ai/aiApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/Form'

function statusBadgeVariant(status: AiJob['status']) {
  if (status === 'SUCCEEDED') return 'success' as const
  if (status === 'FAILED') return 'danger' as const
  if (status === 'CANCELED') return 'neutral' as const
  return 'info' as const
}

const STATUS_LABELS: Record<AiJob['status'], string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
}

function ItemRow({ item }: { item: AiJobItem }) {
  const accept = useAcceptAiJobItem()
  const reject = useRejectAiJobItem()
  const [editing, setEditing] = useState(false)
  const [rawText, setRawText] = useState(item.payload?.rawText ?? '')
  const [type, setType] = useState<'MUST' | 'BONUS'>((item.payload?.type as 'MUST' | 'BONUS') ?? 'MUST')
  const payload = item.payload

  const runAccept = async (withEdit: boolean) => {
    try {
      await accept.mutateAsync({
        itemId: item.id,
        payload: withEdit
          ? { type, rawText: rawText.trim(), normalizedName: payload?.normalizedName, proficiencyText: payload?.proficiencyText ?? null }
          : undefined,
      })
      pushToast('已采纳为候选要求，请在下方要求确认区确认')
    } catch (caught) {
      pushToast((caught as Error).message, 'error')
    }
  }

  const runReject = async () => {
    try {
      await reject.mutateAsync(item.id)
      pushToast('已拒绝该候选')
    } catch (caught) {
      pushToast((caught as Error).message, 'error')
    }
  }

  if (item.status === 'PROPOSED') {
    return (
      <div className="requirement-row" style={{ flexWrap: 'wrap' }}>
        <div className="requirement-main" style={{ width: '100%' }}>
          <span className="requirement-raw">{payload?.rawText}</span>
          {editing ? (
            <div style={{ marginTop: 8 }}>
              <div className="form-row">
                <Field label="要求类型">
                  <Select value={type} onChange={(event) => setType(event.target.value as 'MUST' | 'BONUS')}>
                    <option value="MUST">必须要求</option>
                    <option value="BONUS">加分要求</option>
                  </Select>
                </Field>
              </div>
              <Field label="要求内容">
                <Input value={rawText} onChange={(event) => setRawText(event.target.value)} maxLength={2000} />
              </Field>
            </div>
          ) : null}
        </div>
        <div className="requirement-actions">
          <Badge variant="info">候选</Badge>
          {editing ? (
            <>
              <Button size="sm" variant="primary" type="button" disabled={accept.isPending || !rawText.trim()}
                onClick={() => runAccept(true)}>
                采纳修改后内容
              </Button>
              <Button size="sm" variant="ghost" type="button" onClick={() => setEditing(false)}>
                取消编辑
              </Button>
            </>
          ) : (
            <>
              <Button size="sm" variant="primary" type="button" disabled={accept.isPending} onClick={() => runAccept(false)}>
                采纳
              </Button>
              <Button size="sm" variant="ghost" type="button" onClick={() => setEditing(true)}>编辑后采纳</Button>
              <Button size="sm" variant="ghost" type="button" disabled={reject.isPending} onClick={runReject}>
                拒绝
              </Button>
            </>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="requirement-row" style={{ opacity: item.status === 'REJECTED' ? 0.55 : 1 }}>
      <div className="requirement-main">
        <span className="requirement-raw">{item.editedPayload?.rawText ?? payload?.rawText}</span>
        <span className="muted">
          {item.editedPayload ? '采纳时已编辑 · ' : ''}
          {item.status === 'ACCEPTED' ? '已采纳为候选要求' : '已拒绝'}
        </span>
      </div>
      <div className="requirement-actions">
        <Badge variant={item.status === 'ACCEPTED' ? 'success' : 'neutral'}>
          {item.status === 'ACCEPTED' ? '已采纳' : '已拒绝'}
        </Badge>
      </div>
    </div>
  )
}

function JobRow({ job }: { job: AiJob }) {
  const retry = useRetryAiJob()
  const cancel = useCancelAiJob()
  const [expanded, setExpanded] = useState(job.status === 'SUCCEEDED')

  const runRetry = async () => {
    try {
      await retry.mutateAsync(job.id)
      pushToast('任务已重新入队')
    } catch (caught) {
      pushToast((caught as Error).message, 'error')
    }
  }

  const runCancel = async () => {
    try {
      await cancel.mutateAsync(job.id)
      pushToast('任务已取消')
    } catch (caught) {
      pushToast((caught as Error).message, 'error')
    }
  }

  const actionable = job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'FAILED'

  return (
    <div className="card" style={{ boxShadow: 'none', border: '1px solid var(--border, #ddd)' }}>
      <div className="card-header">
        <h3 className="card-title" style={{ fontSize: 14 }}>
          <Badge variant={statusBadgeVariant(job.status)}>{STATUS_LABELS[job.status]}</Badge>
          <span style={{ marginLeft: 8 }}>{job.model}</span>
          <span className="muted" style={{ marginLeft: 8 }}>
            尝试 {job.attemptCount} 次 · {job.promptVersion}
          </span>
        </h3>
        <Button size="sm" variant="ghost" type="button" onClick={() => setExpanded((value) => !value)}>
          {expanded ? '收起' : '展开'}
        </Button>
      </div>
      <div className="card-body">
        {job.failureReason ? (
          <p className="muted" style={{ marginTop: 0 }}>失败原因：{job.failureReason}</p>
        ) : null}
        {actionable ? (
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            {job.status === 'FAILED' ? (
              <Button size="sm" variant="default" type="button" disabled={retry.isPending} onClick={runRetry}>
                重试
              </Button>
            ) : null}
            {job.status === 'QUEUED' || job.status === 'RUNNING' ? (
              <Button size="sm" variant="ghost" type="button" disabled={cancel.isPending} onClick={runCancel}>
                取消任务
              </Button>
            ) : null}
          </div>
        ) : null}
        {expanded && job.items.length > 0 ? (
          <div style={{ marginTop: 8 }}>
            {job.items.map((item) => (
              <ItemRow key={item.id} item={item} />
            ))}
          </div>
        ) : null}
        {expanded && job.items.length === 0 && job.status === 'SUCCEEDED' ? (
          <p className="muted">本次未产出候选。</p>
        ) : null}
      </div>
    </div>
  )
}

export function AiExtractionSection({ jobId }: { jobId: string }) {
  const jobsQuery = useAiJobsByJob(jobId)
  const create = useCreateAiJob()
  const jobs = jobsQuery.data ?? []
  const running = jobs.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING')

  const runExtraction = async () => {
    try {
      await create.mutateAsync({ jobType: 'JD_EXTRACTION', objectId: jobId })
      pushToast('AI 提取任务已入队，完成后在下方查看候选')
    } catch (caught) {
      pushToast((caught as Error).message, 'error')
    }
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">AI 提取要求</h2>
        <Button
          size="sm"
          variant="ghost"
          type="button"
          disabled={create.isPending || running}
          onClick={runExtraction}
        >
          {running ? '任务执行中…' : 'AI 提取要求'}
        </Button>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          由激活的 AI 供应商分析 JD 并产出候选要求；候选需逐项采纳后才进入要求确认区，AI 不做任何自动确认。
          重新生成不会影响已采纳或已拒绝的历史条目。
        </p>
        {jobs.length === 0 ? (
          <p className="muted">暂无 AI 提取任务。</p>
        ) : (
          <div style={{ display: 'grid', gap: 12 }}>
            {jobs.map((job) => (
              <JobRow key={job.id} job={job} />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
