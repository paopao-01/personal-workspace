import { useMemo, useState } from 'react'
import { useAiJobsByJob, useCreateAiJob } from '@/api/ai/useAiQueries'
import type { AiJob } from '@/api/ai/aiApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'

const labels: Record<AiJob['status'], string> = {
  QUEUED: '排队中', RUNNING: '生成中', SUCCEEDED: '已生成', FAILED: '失败', CANCELED: '已取消',
}

export function ResumeDraftSection({ jobId }: { jobId: string }) {
  const jobs = useAiJobsByJob(jobId)
  const create = useCreateAiJob()
  const [sourceText, setSourceText] = useState('')
  const [draftOverride, setDraftOverride] = useState<string | undefined>()
  const resumeJobs = useMemo(() => (jobs.data ?? []).filter((job) => job.jobType === 'RESUME_DRAFT'), [jobs.data])
  const latest = resumeJobs[0]

  const generatedDraft = latest?.items?.find((candidate) => candidate.payload?.type === 'DRAFT')?.payload?.rawText ?? ''
  const draft = draftOverride ?? generatedDraft

  async function generate() {
    if (!sourceText.trim()) { pushToast('请先粘贴已确认的简历原文', 'error'); return }
    try {
      await create.mutateAsync({ jobType: 'RESUME_DRAFT', objectId: jobId, sourceText })
      pushToast('简历草稿任务已提交', 'success')
    } catch (error) { pushToast(error instanceof Error ? error.message : '提交失败', 'error') }
  }

  async function copyDraft() {
    await navigator.clipboard.writeText(draft)
    pushToast('草稿已复制，可在本地简历编辑器中继续修改', 'success')
  }

  return <section className="card">
    <div className="card-header"><h2>简历定制草稿</h2><Badge variant={latest ? (latest.status === 'SUCCEEDED' ? 'success' : latest.status === 'FAILED' ? 'danger' : 'info') : 'neutral'}>{latest ? labels[latest.status] : '未生成'}</Badge></div>
    <div className="card-body">
      <p className="muted">只基于你确认的简历事实和当前 JD 改写，不会覆盖原简历，也不会新增未经确认的经历或指标。</p>
      <label className="field-label" htmlFor="confirmed-resume">已确认的简历原文</label>
      <textarea id="confirmed-resume" className="textarea" rows={8} value={sourceText} onChange={(event) => setSourceText(event.target.value)} placeholder="粘贴你确认过的简历内容" />
      <div className="form-actions"><Button onClick={generate} disabled={create.isPending}>{create.isPending ? '提交中…' : '生成定制草稿'}</Button></div>
      {latest?.failureReason && <p className="error-text">失败原因：{latest.failureReason}</p>}
      {draft && <><label className="field-label" htmlFor="resume-draft">候选草稿（可编辑）</label><textarea id="resume-draft" className="textarea" rows={14} value={draft} onChange={(event) => setDraftOverride(event.target.value)} /><div className="form-actions"><Button variant="default" onClick={copyDraft}>复制草稿</Button></div></>}
    </div>
  </section>
}
