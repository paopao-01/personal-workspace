import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import { useInterview } from '@/api/interviews/useInterviewQueries'
import { useCreateReviewQuestion, useSaveReviewDraft } from '@/api/reviews/useReviewMutations'
import { useInterviewReview } from '@/api/reviews/useReviewQueries'
import type { AnswerStatus } from '@/api/reviews/reviewApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Select, Textarea } from '@/components/ui/Form'
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
  const review = reviewQuery.data
  const [interviewResult, setInterviewResult] = useState<'PENDING' | 'PASSED' | 'FAILED' | ''>('')
  const [questionContent, setQuestionContent] = useState('')
  const [answerStatus, setAnswerStatus] = useState<AnswerStatus>('UNANSWERED')
  const [noQuestionsRecorded, setNoQuestionsRecorded] = useState<boolean | null>(null)
  const [overallFeeling, setOverallFeeling] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

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
  const pending = saveDraft.isPending || createQuestion.isPending
  const selectedInterviewResult = interviewResult || review?.interviewResult || 'FAILED'
  const selectedNoQuestionsRecorded = noQuestionsRecorded ?? review?.noQuestionsRecorded ?? false
  const selectedOverallFeeling = overallFeeling ?? review?.overallFeeling ?? ''

  const reportError = (caught: Error) => {
    if (isApiError(caught) || isNetworkError(caught)) {
      pushToast(caught.message, 'error')
      return
    }
    pushToast('保存复盘失败，请稍后重试', 'error')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
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
        await createQuestion.mutateAsync({
          reviewId: saved.id,
          interviewId: interview.id,
          body: { content, answerStatus },
        })
        setQuestionContent('')
      }
      await reviewQuery.refetch()
      pushToast('快速复盘已保存')
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
                  disabled={cannotReview}
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
                  disabled={cannotReview || selectedNoQuestionsRecorded}
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
                disabled={cannotReview || selectedNoQuestionsRecorded}
                aria-invalid={Boolean(error)}
              />
            </Field>
            <label className="decision-radio" style={{ marginBottom: 16 }}>
              <input
                type="checkbox"
                checked={selectedNoQuestionsRecorded}
                onChange={(event) => setNoQuestionsRecorded(event.target.checked)}
                disabled={cannotReview}
              />
              未记录到问题
            </label>
            <Field label="整体感受">
              <Textarea
                value={selectedOverallFeeling}
                onChange={(event) => setOverallFeeling(event.target.value)}
                rows={3}
                maxLength={5000}
                disabled={cannotReview}
              />
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="primary" type="submit" disabled={cannotReview || pending}>
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
                <div className="requirement-row" key={question.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{question.content}</span>
                    <span className="muted">{answerStatusLabel[question.answerStatus]}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
