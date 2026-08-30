import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useAcceptAiJobItem,
  useAiJobsByQuestion,
  useCreateAnswerQualityAnalysis,
  useRejectAiJobItem,
  useSingleAiJob,
} from '@/api/ai/useAiQueries'
import type { AiItemPayload } from '@/api/ai/aiApi'
import type { AnswerStatus, InterviewQuestion } from '@/api/reviews/reviewApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Select, Textarea } from '@/components/ui/Form'
import { answerStatusLabel } from '@/features/reviews/reviewLabels'

interface Props {
  question: InterviewQuestion
  disabled: boolean
  onChanged: () => Promise<unknown>
}

interface AnalysisDraft {
  itemId: string
  answerStatus: AnswerStatus
  summary: string
  referenceAnswer: string
  errorReason: string
  improvementPlan: string
}

export function AnswerQualityAnalysisSection({ question, disabled, onChanged }: Props) {
  const historyQuery = useAiJobsByQuestion(question.id, 'ANSWER_QUALITY_ANALYSIS')
  const create = useCreateAnswerQualityAnalysis()
  const accept = useAcceptAiJobItem()
  const reject = useRejectAiJobItem()
  const [activeJobId, setActiveJobId] = useState<string | undefined>()
  const [editedDraft, setEditedDraft] = useState<AnalysisDraft | null>(null)
  const trackedJobId = activeJobId ?? historyQuery.data?.[0]?.id
  const activeJobQuery = useSingleAiJob(trackedJobId)

  const job = activeJobQuery.data ?? historyQuery.data?.[0]
  const item = job?.items?.[0]
  const baseStatus = item?.payload.answerStatus
  const candidateStatus = isAnswerStatus(baseStatus) ? baseStatus : 'PARTIALLY_ANSWERED'
  const draft = item
    ? editedDraft?.itemId === item.id
      ? editedDraft
      : {
          itemId: item.id,
          answerStatus: candidateStatus,
          summary: item.payload.rawText,
          referenceAnswer: item.payload.referenceAnswer ?? '',
          errorReason: item.payload.errorReason ?? '',
          improvementPlan: item.payload.improvementPlan ?? '',
        }
    : null

  const reportError = (error: unknown) => {
    if (isApiError(error) || isNetworkError(error)) {
      pushToast(error.message, 'error')
      return
    }
    pushToast('AI 回答分析操作失败，请稍后重试', 'error')
  }

  const startAnalysis = async () => {
    try {
      const created = await create.mutateAsync(question.id)
      setActiveJobId(created.id)
      setEditedDraft(null)
      pushToast('AI 回答分析任务已提交')
    } catch (error) {
      reportError(error)
    }
  }

  const updateDraft = (changes: Partial<AnalysisDraft>) => {
    if (!draft) return
    setEditedDraft({ ...draft, ...changes })
  }

  const acceptAnalysis = async () => {
    if (!item || !draft) return
    const payload: AiItemPayload = {
      ...item.payload,
      type: 'ANSWER_QUALITY',
      rawText: draft.summary,
      answerStatus: draft.answerStatus,
      referenceAnswer: draft.referenceAnswer,
      errorReason: draft.errorReason,
      improvementPlan: draft.improvementPlan,
    }
    try {
      await accept.mutateAsync({ itemId: item.id, payload, questionVersion: question.version })
      await onChanged()
      pushToast('回答质量分析已采纳')
    } catch (error) {
      reportError(error)
    }
  }

  const rejectAnalysis = async () => {
    if (!item) return
    try {
      await reject.mutateAsync(item.id)
      pushToast('已拒绝该回答分析候选')
    } catch (error) {
      reportError(error)
    }
  }

  const hasAnswer = Boolean(question.myAnswer?.trim())
  const pending = create.isPending || accept.isPending || reject.isPending
  const status = job?.status

  return (
    <div className="inline-edit" style={{ width: '100%' }}>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <strong>AI 回答质量分析</strong>
          {status ? <Badge variant={status === 'SUCCEEDED' ? 'success' : 'neutral'}>{status}</Badge> : null}
        </div>
        <Button
          size="sm"
          variant="ghost"
          type="button"
          disabled={disabled || !hasAnswer || pending || status === 'QUEUED' || status === 'RUNNING'}
          onClick={startAnalysis}
        >
          {create.isPending ? '提交中…' : status ? '重新分析' : '开始分析'}
        </Button>
      </div>
      {!hasAnswer ? <span className="muted">请先在问题详情中填写并保存“我的回答”。</span> : null}
      {historyQuery.isLoading && !job ? <span className="muted">正在加载分析记录…</span> : null}
      {historyQuery.isError || activeJobQuery.isError ? (
        <span className="field-error-text">回答分析任务加载失败，请稍后重试。</span>
      ) : null}
      {status === 'QUEUED' || status === 'RUNNING' ? <span className="muted">正在生成回答分析候选…</span> : null}
      {status === 'FAILED' ? <span className="field-error-text">{job?.failureReason || '回答分析任务失败'}</span> : null}
      {status === 'SUCCEEDED' && item?.status === 'PROPOSED' && draft ? (
        <div style={{ marginTop: 12 }}>
          <div className="form-row">
            <Field label="建议回答状态">
              <Select
                value={draft.answerStatus}
                onChange={(event) => updateDraft({ answerStatus: event.target.value as AnswerStatus })}
                disabled={disabled || pending}
              >
                <option value="UNANSWERED">{answerStatusLabel.UNANSWERED}</option>
                <option value="PARTIALLY_ANSWERED">{answerStatusLabel.PARTIALLY_ANSWERED}</option>
                <option value="FULLY_ANSWERED">{answerStatusLabel.FULLY_ANSWERED}</option>
              </Select>
            </Field>
            <Field label="总体评价">
              <Textarea
                value={draft.summary}
                onChange={(event) => updateDraft({ summary: event.target.value })}
                rows={3}
                maxLength={2000}
                disabled={disabled || pending}
              />
            </Field>
          </div>
          <Field label="候选参考答案">
            <Textarea
              value={draft.referenceAnswer}
              onChange={(event) => updateDraft({ referenceAnswer: event.target.value })}
              rows={4}
              maxLength={20000}
              disabled={disabled || pending}
            />
          </Field>
          <div className="form-row">
            <Field label="候选错误原因">
              <Textarea
                value={draft.errorReason}
                onChange={(event) => updateDraft({ errorReason: event.target.value })}
                rows={3}
                maxLength={5000}
                disabled={disabled || pending}
              />
            </Field>
            <Field label="候选改进方案">
              <Textarea
                value={draft.improvementPlan}
                onChange={(event) => updateDraft({ improvementPlan: event.target.value })}
                rows={3}
                maxLength={5000}
                disabled={disabled || pending}
              />
            </Field>
          </div>
          <p className="form-hint">判断依据：{item.payload.rationale || '模型未提供判断依据'}</p>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button size="sm" variant="danger" type="button" disabled={pending} onClick={rejectAnalysis}>
              {reject.isPending ? '拒绝中…' : '拒绝'}
            </Button>
            <Button
              size="sm"
              variant="primary"
              type="button"
              disabled={disabled || pending || !draft.summary.trim()}
              onClick={acceptAnalysis}
            >
              {accept.isPending ? '采纳中…' : '采纳分析'}
            </Button>
          </div>
        </div>
      ) : null}
      {status === 'SUCCEEDED' && item?.status === 'ACCEPTED' ? <span className="muted">该回答分析候选已采纳。</span> : null}
      {status === 'SUCCEEDED' && item?.status === 'REJECTED' ? <span className="muted">该回答分析候选已拒绝。</span> : null}
    </div>
  )
}

function isAnswerStatus(value: AiItemPayload['answerStatus']): value is AnswerStatus {
  return value === 'FULLY_ANSWERED' || value === 'PARTIALLY_ANSWERED' || value === 'UNANSWERED'
}
