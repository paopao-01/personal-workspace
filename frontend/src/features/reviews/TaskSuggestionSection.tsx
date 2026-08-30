import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useAcceptAiJobItem,
  useAiJobsByQuestion,
  useCreateTaskSuggestion,
  useRejectAiJobItem,
  useSingleAiJob,
} from '@/api/ai/useAiQueries'
import type { AiItemPayload } from '@/api/ai/aiApi'
import type { InterviewQuestion } from '@/api/reviews/reviewApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'

interface Props {
  question: InterviewQuestion
  disabled: boolean
  onChanged: () => Promise<unknown>
}

type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

interface SuggestionDraft {
  itemId: string
  taskTitle: string
  priority: Priority
  estimatedMinutes: string
  learningGoal: string
  acceptanceCriteria: string
  verificationMethod: string
}

export function TaskSuggestionSection({ question, disabled, onChanged }: Props) {
  const historyQuery = useAiJobsByQuestion(question.id, 'TASK_SUGGESTION')
  const create = useCreateTaskSuggestion()
  const accept = useAcceptAiJobItem()
  const reject = useRejectAiJobItem()
  const [activeJobId, setActiveJobId] = useState<string | undefined>()
  const [editedDraft, setEditedDraft] = useState<SuggestionDraft | null>(null)
  const trackedJobId = activeJobId ?? historyQuery.data?.[0]?.id
  const activeJobQuery = useSingleAiJob(trackedJobId)
  const job = activeJobQuery.data ?? historyQuery.data?.[0]
  const item = job?.items?.[0]
  const draft = item
    ? editedDraft?.itemId === item.id
      ? editedDraft
      : {
          itemId: item.id,
          taskTitle: item.payload.taskTitle ?? item.payload.rawText,
          priority: isPriority(item.payload.priority) ? item.payload.priority : 'MEDIUM',
          estimatedMinutes: String(item.payload.estimatedMinutes ?? 60),
          learningGoal: item.payload.learningGoal ?? '',
          acceptanceCriteria: item.payload.acceptanceCriteria ?? '',
          verificationMethod: item.payload.verificationMethod ?? '',
        }
    : null

  const reportError = (error: unknown) => {
    if (isApiError(error) || isNetworkError(error)) {
      pushToast(error.message, 'error')
      return
    }
    pushToast('AI 学习任务建议操作失败，请稍后重试', 'error')
  }

  const startSuggestion = async () => {
    try {
      const created = await create.mutateAsync(question.id)
      setActiveJobId(created.id)
      setEditedDraft(null)
      pushToast('AI 学习任务建议已提交')
    } catch (error) {
      reportError(error)
    }
  }

  const updateDraft = (changes: Partial<SuggestionDraft>) => {
    if (draft) setEditedDraft({ ...draft, ...changes })
  }

  const acceptSuggestion = async () => {
    if (!item || !draft) return
    const payload: AiItemPayload = {
      ...item.payload,
      type: 'LEARNING_TASK',
      taskTitle: draft.taskTitle.trim(),
      priority: draft.priority,
      estimatedMinutes: Number(draft.estimatedMinutes),
      learningGoal: draft.learningGoal.trim(),
      acceptanceCriteria: draft.acceptanceCriteria.trim(),
      verificationMethod: draft.verificationMethod.trim(),
    }
    try {
      await accept.mutateAsync({ itemId: item.id, payload, questionVersion: question.version })
      await onChanged()
      pushToast('已采纳建议并创建学习任务')
    } catch (error) {
      reportError(error)
    }
  }

  const rejectSuggestion = async () => {
    if (!item) return
    try {
      await reject.mutateAsync(item.id)
      pushToast('已拒绝该学习任务建议')
    } catch (error) {
      reportError(error)
    }
  }

  const status = job?.status
  const pending = create.isPending || accept.isPending || reject.isPending
  const canStart = question.answerStatus === 'PARTIALLY_ANSWERED' || question.answerStatus === 'UNANSWERED'
  const candidateReady = Boolean(
    draft?.taskTitle.trim() &&
      draft.learningGoal.trim() &&
      draft.acceptanceCriteria.trim() &&
      draft.verificationMethod.trim() &&
      Number(draft.estimatedMinutes) > 0,
  )

  return (
    <div className="inline-edit" style={{ width: '100%' }}>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <strong>AI 学习任务建议</strong>
          {status ? <Badge variant={status === 'SUCCEEDED' ? 'success' : 'neutral'}>{status}</Badge> : null}
        </div>
        <Button
          size="sm"
          variant="ghost"
          type="button"
          disabled={disabled || !canStart || pending || status === 'QUEUED' || status === 'RUNNING'}
          onClick={startSuggestion}
        >
          {create.isPending ? '提交中…' : status ? '重新建议' : '生成建议'}
        </Button>
      </div>
      {!canStart ? <span className="muted">只有部分答出或未答出的问题可以生成学习任务建议。</span> : null}
      {historyQuery.isLoading && !job ? <span className="muted">正在加载任务建议记录…</span> : null}
      {historyQuery.isError || activeJobQuery.isError ? (
        <span className="field-error-text">任务建议加载失败，请稍后重试。</span>
      ) : null}
      {status === 'QUEUED' || status === 'RUNNING' ? <span className="muted">正在生成可编辑任务候选…</span> : null}
      {status === 'FAILED' ? <span className="field-error-text">{job?.failureReason || '任务建议生成失败'}</span> : null}
      {status === 'SUCCEEDED' && item?.status === 'PROPOSED' && draft ? (
        <div style={{ marginTop: 12 }}>
          <div className="form-row">
            <Field label="任务名称" required>
              <Input value={draft.taskTitle} onChange={(event) => updateDraft({ taskTitle: event.target.value })} maxLength={200} disabled={disabled || pending} />
            </Field>
            <Field label="优先级">
              <Select value={draft.priority} onChange={(event) => updateDraft({ priority: event.target.value as Priority })} disabled={disabled || pending}>
                <option value="LOW">低</option>
                <option value="MEDIUM">中</option>
                <option value="HIGH">高</option>
                <option value="URGENT">紧急</option>
              </Select>
            </Field>
            <Field label="预计分钟">
              <Input type="number" min={1} value={draft.estimatedMinutes} onChange={(event) => updateDraft({ estimatedMinutes: event.target.value })} disabled={disabled || pending} />
            </Field>
          </div>
          <Field label="学习目标" required>
            <Textarea value={draft.learningGoal} onChange={(event) => updateDraft({ learningGoal: event.target.value })} rows={2} maxLength={5000} disabled={disabled || pending} />
          </Field>
          <div className="form-row">
            <Field label="验收标准" required>
              <Textarea value={draft.acceptanceCriteria} onChange={(event) => updateDraft({ acceptanceCriteria: event.target.value })} rows={3} maxLength={5000} disabled={disabled || pending} />
            </Field>
            <Field label="验证方式" required>
              <Textarea value={draft.verificationMethod} onChange={(event) => updateDraft({ verificationMethod: event.target.value })} rows={3} maxLength={1000} disabled={disabled || pending} />
            </Field>
          </div>
          <p className="form-hint">关联知识点：{question.knowledgePoints?.map((point) => point.name).join('、') || '未关联知识点'}。{item.payload.rationale || '模型未提供建议依据'}</p>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button size="sm" variant="danger" type="button" disabled={pending} onClick={rejectSuggestion}>{reject.isPending ? '拒绝中…' : '拒绝'}</Button>
            <Button size="sm" variant="primary" type="button" disabled={disabled || pending || !candidateReady} onClick={acceptSuggestion}>{accept.isPending ? '采纳中…' : '采纳并创建任务'}</Button>
          </div>
        </div>
      ) : null}
      {status === 'SUCCEEDED' && item?.status === 'ACCEPTED' ? <span className="muted">已采纳并创建学习任务（任务 ID：{item.taskId}）。</span> : null}
      {status === 'SUCCEEDED' && item?.status === 'REJECTED' ? <span className="muted">该学习任务建议已拒绝。</span> : null}
    </div>
  )
}

function isPriority(value: AiItemPayload['priority']): value is Priority {
  return value === 'LOW' || value === 'MEDIUM' || value === 'HIGH' || value === 'URGENT'
}
