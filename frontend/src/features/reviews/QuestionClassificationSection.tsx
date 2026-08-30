import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useAcceptAiJobItem,
  useAiJobsByQuestion,
  useCreateQuestionClassification,
  useRejectAiJobItem,
  useSingleAiJob,
} from '@/api/ai/useAiQueries'
import type { AiItemPayload } from '@/api/ai/aiApi'
import type { InterviewQuestion } from '@/api/reviews/reviewApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Select } from '@/components/ui/Form'

const CATEGORY_OPTIONS = [
  { value: 'TECHNICAL', label: '技术基础' },
  { value: 'PROJECT_EXPERIENCE', label: '项目经历' },
  { value: 'SYSTEM_DESIGN', label: '系统设计' },
  { value: 'BEHAVIORAL', label: '行为面试' },
  { value: 'DOMAIN', label: '业务场景' },
  { value: 'OTHER', label: '其他' },
] as const satisfies ReadonlyArray<{ value: AiItemPayload['type']; label: string }>

type QuestionCategory = (typeof CATEGORY_OPTIONS)[number]['value']

interface Props {
  question: InterviewQuestion
  disabled: boolean
  onChanged: () => Promise<unknown>
}

export function QuestionClassificationSection({ question, disabled, onChanged }: Props) {
  const historyQuery = useAiJobsByQuestion(question.id, 'QUESTION_CLASSIFICATION')
  const create = useCreateQuestionClassification()
  const accept = useAcceptAiJobItem()
  const reject = useRejectAiJobItem()
  const [activeJobId, setActiveJobId] = useState<string | undefined>()
  const [editedForItemId, setEditedForItemId] = useState<string | undefined>()
  const [editedType, setEditedType] = useState<QuestionCategory | ''>('')
  const trackedJobId = activeJobId ?? historyQuery.data?.[0]?.id
  const activeJobQuery = useSingleAiJob(trackedJobId)

  const job = activeJobQuery.data ?? historyQuery.data?.[0]
  const item = job?.items?.[0]
  const baseType = item?.payload?.type
  const categoryType = isQuestionCategory(baseType) ? baseType : undefined

  const selectedType = item?.id === editedForItemId && editedType ? editedType : categoryType

  const reportError = (error: unknown) => {
    if (isApiError(error) || isNetworkError(error)) {
      pushToast(error.message, 'error')
      return
    }
    pushToast('AI 分类操作失败，请稍后重试', 'error')
  }

  const startClassification = async () => {
    try {
      const created = await create.mutateAsync(question.id)
      setActiveJobId(created.id)
      setEditedForItemId(undefined)
      setEditedType('')
      pushToast('AI 分类任务已提交')
    } catch (error) {
      reportError(error)
    }
  }

  const acceptClassification = async () => {
    if (!item || !selectedType) return
    const payload: AiItemPayload = {
      ...item.payload,
      type: selectedType,
    }
    try {
      await accept.mutateAsync({ itemId: item.id, payload, questionVersion: question.version })
      await onChanged()
      pushToast('问题分类已采纳')
    } catch (error) {
      reportError(error)
    }
  }

  const rejectClassification = async () => {
    if (!item) return
    try {
      await reject.mutateAsync(item.id)
      pushToast('已拒绝该分类候选')
    } catch (error) {
      reportError(error)
    }
  }

  const pending = create.isPending || accept.isPending || reject.isPending
  const status = job?.status

  return (
    <div className="inline-edit" style={{ width: '100%' }}>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <strong>AI 问题分类</strong>
          {status ? <Badge variant={status === 'SUCCEEDED' ? 'success' : 'neutral'}>{status}</Badge> : null}
        </div>
        <Button
          size="sm"
          variant="ghost"
          type="button"
          disabled={disabled || pending || status === 'QUEUED' || status === 'RUNNING'}
          onClick={startClassification}
        >
          {create.isPending ? '提交中…' : status ? '重新分类' : '开始分类'}
        </Button>
      </div>
      {historyQuery.isLoading && !job ? <span className="muted">正在加载分类记录…</span> : null}
      {historyQuery.isError || activeJobQuery.isError ? (
        <span className="field-error-text">分类任务加载失败，请稍后重试。</span>
      ) : null}
      {status === 'QUEUED' || status === 'RUNNING' ? <span className="muted">正在生成候选分类…</span> : null}
      {status === 'FAILED' ? <span className="field-error-text">{job?.failureReason || '分类任务失败'}</span> : null}
      {status === 'SUCCEEDED' && item && categoryType && item.status === 'PROPOSED' ? (
        <div className="form-row" style={{ marginTop: 12 }}>
          <Field label="候选分类">
            <Select
              value={selectedType ?? ''}
              onChange={(event) => {
                setEditedForItemId(item.id)
                setEditedType(event.target.value as QuestionCategory)
              }}
              disabled={disabled || pending}
            >
              {CATEGORY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="分类理由">
            <span className="form-hint">{item.payload.rationale || '模型未提供理由'}</span>
          </Field>
          <div className="flex-row" style={{ alignItems: 'flex-end', paddingBottom: 8 }}>
            <Button size="sm" variant="primary" type="button" disabled={disabled || pending} onClick={acceptClassification}>
              {accept.isPending ? '采纳中…' : '采纳分类'}
            </Button>
            <Button size="sm" variant="danger" type="button" disabled={pending} onClick={rejectClassification}>
              {reject.isPending ? '拒绝中…' : '拒绝'}
            </Button>
          </div>
        </div>
      ) : null}
      {status === 'SUCCEEDED' && item?.status === 'ACCEPTED' ? <span className="muted">该分类候选已采纳。</span> : null}
      {status === 'SUCCEEDED' && item?.status === 'REJECTED' ? <span className="muted">该分类候选已拒绝。</span> : null}
    </div>
  )
}

function isQuestionCategory(value: AiItemPayload['type'] | undefined): value is QuestionCategory {
  return CATEGORY_OPTIONS.some((option) => option.value === value)
}
