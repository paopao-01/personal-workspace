import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import { useInterview } from '@/api/interviews/useInterviewQueries'
import {
  useCompleteReview,
  useCreateKnowledgePoint,
  useCreateReviewQuestion,
  useSaveReviewDraft,
  useUpdateReviewQuestion,
} from '@/api/reviews/useReviewMutations'
import { useInterviewReview } from '@/api/reviews/useReviewQueries'
import type { AnswerStatus, InterviewQuestion } from '@/api/reviews/reviewApi'
import { useCreateTaskFromQuestion } from '@/api/tasks/useTaskMutations'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import {
  formatInterviewTime,
  interviewResultLabel,
  interviewScheduleLabel,
  interviewScheduleVariant,
} from '@/features/interviews/interviewLabels'
import { answerStatusLabel, reviewStatusLabel } from '@/features/reviews/reviewLabels'

export function InterviewReviewPage() {
  const { interviewId } = useParams<{ interviewId: string }>()
  const navigate = useNavigate()
  const interviewQuery = useInterview(interviewId)
  const reviewQuery = useInterviewReview(interviewId)
  const saveDraft = useSaveReviewDraft()
  const createQuestion = useCreateReviewQuestion()
  const completeReview = useCompleteReview()
  const createKnowledgePoint = useCreateKnowledgePoint()
  const updateQuestion = useUpdateReviewQuestion()
  const createTaskFromQuestion = useCreateTaskFromQuestion()
  const review = reviewQuery.data
  const [interviewResult, setInterviewResult] = useState<'PENDING' | 'PASSED' | 'FAILED' | ''>('')
  const [questionContent, setQuestionContent] = useState('')
  const [answerStatus, setAnswerStatus] = useState<AnswerStatus>('UNANSWERED')
  const [knowledgePointName, setKnowledgePointName] = useState('')
  const [noQuestionsRecorded, setNoQuestionsRecorded] = useState<boolean | null>(null)
  const [overallFeeling, setOverallFeeling] = useState<string | null>(null)
  const [taskDraftQuestionId, setTaskDraftQuestionId] = useState<string | null>(null)
  const [taskTitle, setTaskTitle] = useState('')
  const [taskAcceptanceCriteria, setTaskAcceptanceCriteria] = useState('')
  const [taskVerificationMethod, setTaskVerificationMethod] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (interviewQuery.isLoading || reviewQuery.isLoading) {
    return <Spinner label="加载复盘…" />
  }
  if (interviewQuery.error || !interviewQuery.data) {
    return (
      <ErrorState
        error={interviewQuery.error ?? new Error('面试不存在')}
        onRetry={() => interviewQuery.refetch()}
      />
    )
  }
  if (reviewQuery.error) {
    return <ErrorState error={reviewQuery.error} onRetry={() => reviewQuery.refetch()} />
  }

  const interview = interviewQuery.data
  const cannotReview = interview.scheduleStatus !== 'COMPLETED'
  const isCompletedReview = review?.status === 'COMPLETED'
  const pending =
    saveDraft.isPending ||
    createQuestion.isPending ||
    completeReview.isPending ||
    createKnowledgePoint.isPending ||
    updateQuestion.isPending ||
    createTaskFromQuestion.isPending
  const selectedInterviewResult = interviewResult || review?.interviewResult || 'FAILED'
  const selectedNoQuestionsRecorded = noQuestionsRecorded ?? review?.noQuestionsRecorded ?? false
  const selectedOverallFeeling = overallFeeling ?? review?.overallFeeling ?? ''

  const reportError = (caught: Error) => {
    if (isApiError(caught) || isNetworkError(caught)) {
      setActionError(caught.message)
      pushToast(caught.message, 'error')
      return
    }
    setActionError('保存复盘失败，请稍后重试')
    pushToast('保存复盘失败，请稍后重试', 'error')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setActionError(null)
    const content = questionContent.trim()
    if (!selectedNoQuestionsRecorded && !content) {
      setError('请填写至少一道问题，或勾选未记录到问题')
      return
    }
    try {
      const saved = await saveDraft.mutateAsync({
        interviewId: interview.id,
        version: review?.version,
        body: {
          interviewResult: selectedInterviewResult,
          noQuestionsRecorded: selectedNoQuestionsRecorded,
          overallFeeling: selectedOverallFeeling.trim() || undefined,
        },
      })
      if (content) {
        const knowledgePointIds = []
        const trimmedKnowledgePointName = knowledgePointName.trim()
        if (trimmedKnowledgePointName) {
          const knowledgePoint = await createKnowledgePoint.mutateAsync({
            name: trimmedKnowledgePointName,
          })
          knowledgePointIds.push(knowledgePoint.id)
        }
        await createQuestion.mutateAsync({
          reviewId: saved.id,
          interviewId: interview.id,
          body: { content, answerStatus, knowledgePointIds },
        })
        setQuestionContent('')
        setKnowledgePointName('')
      }
      await reviewQuery.refetch()
      pushToast('快速复盘已保存')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const changeQuestionAnswerStatus = async (
    questionId: string,
    version: number,
    nextAnswerStatus: AnswerStatus,
  ) => {
    const question = review?.questions?.find((item) => item.id === questionId)
    if (!question) return
    setActionError(null)
    try {
      await updateQuestion.mutateAsync({
        questionId,
        interviewId: interview.id,
        version,
        body: {
          content: question.content,
          answerStatus: nextAnswerStatus,
          type: question.type ?? undefined,
          knowledgePointIds: question.knowledgePoints?.map((point) => point.id) ?? [],
          myAnswer: question.myAnswer ?? undefined,
          referenceAnswer: question.referenceAnswer ?? undefined,
          difficulty: question.difficulty ?? undefined,
          errorReason: question.errorReason ?? undefined,
          improvementPlan: question.improvementPlan ?? undefined,
        },
      })
      await reviewQuery.refetch()
      pushToast('回答状态已更新')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const complete = async () => {
    setError(null)
    setActionError(null)
    if (!review) {
      setActionError('请先保存复盘草稿')
      return
    }
    try {
      await completeReview.mutateAsync({
        reviewId: review.id,
        interviewId: interview.id,
        version: review.version,
      })
      await reviewQuery.refetch()
      pushToast('复盘已完成')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const openTaskDraft = (question: InterviewQuestion) => {
    setTaskDraftQuestionId(question.id)
    setTaskTitle(`补齐：${question.content.slice(0, 60)}`)
    setTaskAcceptanceCriteria('能独立讲清核心思路、关键风险和一个项目例子。')
    setTaskVerificationMethod('口述演练并记录验证结果')
  }

  const submitTaskFromQuestion = async (questionId: string) => {
    setActionError(null)
    try {
      await createTaskFromQuestion.mutateAsync({
        questionId,
        body: {
          mode: 'CREATE_NEW',
          title: taskTitle,
          acceptanceCriteria: taskAcceptanceCriteria,
          verificationMethod: taskVerificationMethod,
        },
      })
      setTaskDraftQuestionId(null)
      setTaskTitle('')
      setTaskAcceptanceCriteria('')
      setTaskVerificationMethod('')
      pushToast('学习任务已创建')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">快速复盘</h1>
          <p className="page-subtitle">{interview.roundName} · {formatInterviewTime(interview.startsAt)}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => navigate(`/interviews/${interview.id}`)}>
          返回面试
        </Button>
      </div>

      <section className="card detail-summary">
        <div className="card-header">
          <h2 className="card-title">面试状态</h2>
        </div>
        <div className="card-body">
          <dl>
            <dt>日程状态</dt>
            <dd>
              <Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>
                {interviewScheduleLabel[interview.scheduleStatus]}
              </Badge>
            </dd>
            <dt>当前结果</dt>
            <dd>{interviewResultLabel[interview.result]}</dd>
            <dt>复盘状态</dt>
            <dd>{review ? reviewStatusLabel[review.status] : '未开始'}</dd>
          </dl>
        </div>
      </section>

      {cannotReview ? (
        <div className="conflict-banner">
          <span>只有已完成的面试可以保存复盘。</span>
        </div>
      ) : null}
      {isCompletedReview ? (
        <div className="success-banner">
          <span>复盘已完成，当前切片暂不支持重新打开后编辑。</span>
        </div>
      ) : null}
      {actionError ? (
        <div className="conflict-banner">
          <span>{actionError}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">最小复盘</h2>
        </div>
        <div className="card-body">
          <form onSubmit={submit} noValidate>
            <div className="form-row">
              <Field label="面试结果" required>
                <Select
                  value={selectedInterviewResult}
                  onChange={(event) =>
                    setInterviewResult(event.target.value as typeof interviewResult)
                  }
                  disabled={cannotReview || isCompletedReview}
                >
                  <option value="FAILED">未通过</option>
                  <option value="PASSED">通过</option>
                  <option value="PENDING">暂不确认</option>
                </Select>
              </Field>
              <Field label="回答状态" required>
                <Select
                  value={answerStatus}
                  onChange={(event) => setAnswerStatus(event.target.value as AnswerStatus)}
                  disabled={cannotReview || isCompletedReview || selectedNoQuestionsRecorded}
                >
                  <option value="UNANSWERED">{answerStatusLabel.UNANSWERED}</option>
                  <option value="PARTIALLY_ANSWERED">{answerStatusLabel.PARTIALLY_ANSWERED}</option>
                  <option value="FULLY_ANSWERED">{answerStatusLabel.FULLY_ANSWERED}</option>
                </Select>
              </Field>
            </div>
            <Field label="面试问题" required={!selectedNoQuestionsRecorded} error={error}>
              <Textarea
                value={questionContent}
                onChange={(event) => setQuestionContent(event.target.value)}
                rows={4}
                maxLength={10000}
                disabled={cannotReview || isCompletedReview || selectedNoQuestionsRecorded}
                aria-invalid={Boolean(error)}
              />
            </Field>
            <Field label="关联知识点">
              <Input
                value={knowledgePointName}
                onChange={(event) => setKnowledgePointName(event.target.value)}
                maxLength={100}
                placeholder="例如 Redis 缓存一致性"
                disabled={cannotReview || isCompletedReview || selectedNoQuestionsRecorded}
              />
            </Field>
            <label className="decision-radio" style={{ marginBottom: 16 }}>
              <input
                type="checkbox"
                checked={selectedNoQuestionsRecorded}
                onChange={(event) => setNoQuestionsRecorded(event.target.checked)}
                disabled={cannotReview || isCompletedReview}
              />
              未记录到问题
            </label>
            <Field label="整体感受">
              <Textarea
                value={selectedOverallFeeling}
                onChange={(event) => setOverallFeeling(event.target.value)}
                rows={3}
                maxLength={5000}
                disabled={cannotReview || isCompletedReview}
              />
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button
                variant="default"
                type="button"
                disabled={cannotReview || pending || !review || isCompletedReview}
                onClick={complete}
              >
                {completeReview.isPending ? '完成中…' : '完成复盘'}
              </Button>
              <Button
                variant="primary"
                type="submit"
                disabled={cannotReview || isCompletedReview || pending}
              >
                {pending ? '保存中…' : '保存复盘'}
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">已记录问题</h2>
        </div>
        <div className="card-body">
          {(review?.questions ?? []).length === 0 ? (
            <EmptyState icon="□" text="暂无问题记录" />
          ) : (
            <div>
              {review?.questions?.map((question) => (
                <div className="requirement-row" key={question.id} style={{ flexWrap: 'wrap' }}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{question.content}</span>
                    <span className="muted">
                      {question.knowledgePoints?.length
                        ? question.knowledgePoints.map((point) => point.name).join('、')
                        : '未关联知识点'}
                    </span>
                  </div>
                  <div className="requirement-actions">
                    <Select
                      value={question.answerStatus}
                      onChange={(event) =>
                        changeQuestionAnswerStatus(
                          question.id,
                          question.version,
                          event.target.value as AnswerStatus,
                        )
                      }
                      disabled={pending}
                      aria-label="更新回答状态"
                    >
                      <option value="UNANSWERED">{answerStatusLabel.UNANSWERED}</option>
                      <option value="PARTIALLY_ANSWERED">
                        {answerStatusLabel.PARTIALLY_ANSWERED}
                      </option>
                      <option value="FULLY_ANSWERED">{answerStatusLabel.FULLY_ANSWERED}</option>
                    </Select>
                    {question.answerStatus !== 'FULLY_ANSWERED' ? (
                      <Button
                        size="sm"
                        variant="primary"
                        type="button"
                        disabled={pending}
                        onClick={() => openTaskDraft(question)}
                      >
                        创建学习任务
                      </Button>
                    ) : null}
                  </div>
                  {taskDraftQuestionId === question.id ? (
                    <div className="inline-edit" style={{ width: '100%' }}>
                      <Field label="任务名称" required>
                        <Input
                          value={taskTitle}
                          onChange={(event) => setTaskTitle(event.target.value)}
                          maxLength={200}
                        />
                      </Field>
                      <div className="form-row">
                        <Field label="验收标准" required>
                          <Textarea
                            value={taskAcceptanceCriteria}
                            onChange={(event) => setTaskAcceptanceCriteria(event.target.value)}
                            rows={3}
                            maxLength={5000}
                          />
                        </Field>
                        <Field label="验证方式" required>
                          <Textarea
                            value={taskVerificationMethod}
                            onChange={(event) => setTaskVerificationMethod(event.target.value)}
                            rows={3}
                            maxLength={1000}
                          />
                        </Field>
                      </div>
                      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
                        <Button
                          type="button"
                          variant="ghost"
                          disabled={pending}
                          onClick={() => setTaskDraftQuestionId(null)}
                        >
                          取消
                        </Button>
                        <Button
                          type="button"
                          variant="primary"
                          disabled={
                            pending ||
                            !taskTitle.trim() ||
                            !taskAcceptanceCriteria.trim() ||
                            !taskVerificationMethod.trim()
                          }
                          onClick={() => submitTaskFromQuestion(question.id)}
                        >
                          {createTaskFromQuestion.isPending ? '创建中…' : '确认创建'}
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
