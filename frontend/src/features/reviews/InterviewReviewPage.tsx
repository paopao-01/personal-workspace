import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import { useInterview } from '@/api/interviews/useInterviewQueries'
import {
  useCompleteReview,
  useCreateKnowledgePoint,
  useCreateReviewQuestion,
  useReopenReview,
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
import { QuestionClassificationSection } from '@/features/reviews/QuestionClassificationSection'

interface QuestionDetailDraft {
  questionId: string
  type: string
  myAnswer: string
  referenceAnswer: string
  difficulty: number | null
  errorReason: string
  improvementPlan: string
}

export function InterviewReviewPage() {
  const { interviewId } = useParams<{ interviewId: string }>()
  const navigate = useNavigate()
  const interviewQuery = useInterview(interviewId)
  const reviewQuery = useInterviewReview(interviewId)
  const saveDraft = useSaveReviewDraft()
  const createQuestion = useCreateReviewQuestion()
  const completeReview = useCompleteReview()
  const reopenReview = useReopenReview()
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
  const [interviewerFocus, setInterviewerFocus] = useState<string | null>(null)
  const [jobInterest, setJobInterest] = useState<string | null>(null)
  const [projectExpressRisk, setProjectExpressRisk] = useState<string | null>(null)
  const [showFullReview, setShowFullReview] = useState(false)
  const [questionDetailDraft, setQuestionDetailDraft] = useState<QuestionDetailDraft | null>(null)
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
    reopenReview.isPending ||
    createKnowledgePoint.isPending ||
    updateQuestion.isPending ||
    createTaskFromQuestion.isPending
  const selectedInterviewResult = interviewResult || review?.interviewResult || 'FAILED'
  const selectedNoQuestionsRecorded = noQuestionsRecorded ?? review?.noQuestionsRecorded ?? false
  const selectedOverallFeeling = overallFeeling ?? review?.overallFeeling ?? ''
  const selectedInterviewerFocus = interviewerFocus ?? review?.interviewerFocus ?? ''
  const selectedJobInterest = jobInterest ?? review?.jobInterest ?? ''
  const selectedProjectExpressRisk = projectExpressRisk ?? review?.projectExpressRisk ?? ''
  const fullReviewFieldsDisabled = cannotReview || isCompletedReview || pending

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
          interviewerFocus: selectedInterviewerFocus.trim() || undefined,
          jobInterest: selectedJobInterest.trim() || undefined,
          projectExpressRisk: selectedProjectExpressRisk.trim() || undefined,
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
    if (questionDetailDraft?.questionId === questionId) {
      setQuestionDetailDraft(null)
    }
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

  const openQuestionDetail = (question: InterviewQuestion) => {
    setQuestionDetailDraft({
      questionId: question.id,
      type: question.type ?? '',
      myAnswer: question.myAnswer ?? '',
      referenceAnswer: question.referenceAnswer ?? '',
      difficulty: question.difficulty ?? null,
      errorReason: question.errorReason ?? '',
      improvementPlan: question.improvementPlan ?? '',
    })
  }

  const submitQuestionDetail = async (question: InterviewQuestion) => {
    if (!questionDetailDraft || questionDetailDraft.questionId !== question.id) return
    setActionError(null)
    try {
      await updateQuestion.mutateAsync({
        questionId: question.id,
        interviewId: interview.id,
        version: question.version,
        body: {
          content: question.content,
          answerStatus: question.answerStatus,
          type: questionDetailDraft.type.trim() || undefined,
          knowledgePointIds: question.knowledgePoints?.map((point) => point.id) ?? [],
          myAnswer: questionDetailDraft.myAnswer.trim() || undefined,
          referenceAnswer: questionDetailDraft.referenceAnswer.trim() || undefined,
          difficulty: questionDetailDraft.difficulty ?? undefined,
          errorReason: questionDetailDraft.errorReason.trim() || undefined,
          improvementPlan: questionDetailDraft.improvementPlan.trim() || undefined,
        },
      })
      setQuestionDetailDraft(null)
      await reviewQuery.refetch()
      pushToast('问题详情已保存')
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

  const reopen = async () => {
    setError(null)
    setActionError(null)
    if (!review) {
      setActionError('请先保存复盘草稿')
      return
    }
    try {
      await reopenReview.mutateAsync({
        reviewId: review.id,
        version: review.version,
      })
      await reviewQuery.refetch()
      pushToast('复盘已重新打开，可继续编辑')
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
        <div className="success-banner" style={{ gap: 12 }}>
          <span>复盘已完成。如需补充或修改问题，可重新打开复盘（问题与任务关联会保留）。</span>
          <Button
            size="sm"
            variant="default"
            type="button"
            disabled={pending}
            onClick={reopen}
          >
            重新打开
          </Button>
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
          <Button
            size="sm"
            variant="ghost"
            type="button"
            disabled={cannotReview || isCompletedReview}
            onClick={() => setShowFullReview((value) => !value)}
          >
            {showFullReview ? '收起完整复盘字段' : '展开完整复盘字段'}
          </Button>
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
            {showFullReview ? (
              <>
                <Field label="面试官关注点">
                  <Textarea
                    value={selectedInterviewerFocus}
                    onChange={(event) => setInterviewerFocus(event.target.value)}
                    rows={3}
                    maxLength={5000}
                    disabled={fullReviewFieldsDisabled}
                  />
                </Field>
                <Field label="岗位意愿">
                  <Input
                    value={selectedJobInterest}
                    onChange={(event) => setJobInterest(event.target.value)}
                    maxLength={1000}
                    placeholder="例如：较高，团队方向与期望匹配"
                    disabled={fullReviewFieldsDisabled}
                  />
                </Field>
                <Field label="项目表达与真实性风险">
                  <Textarea
                    value={selectedProjectExpressRisk}
                    onChange={(event) => setProjectExpressRisk(event.target.value)}
                    rows={3}
                    maxLength={5000}
                    placeholder="例如：量化结果不足，追问实现细节时表达含糊"
                    disabled={fullReviewFieldsDisabled}
                  />
                </Field>
                <p className="muted" style={{ marginTop: -8 }}>
                  完整复盘字段可随时后补；逐题的我的回答、参考答案、难度、错误原因与改进方案在“已记录问题”中编辑。
                </p>
              </>
            ) : null}
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
                      {question.type ? `${question.type} · ` : ''}
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
                    <Button
                      size="sm"
                      variant="ghost"
                      type="button"
                      disabled={pending || isCompletedReview}
                      onClick={() =>
                        questionDetailDraft?.questionId === question.id
                          ? setQuestionDetailDraft(null)
                          : openQuestionDetail(question)
                      }
                    >
                      {questionDetailDraft?.questionId === question.id ? '收起详情' : '编辑详情'}
                    </Button>
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
                  {questionDetailDraft?.questionId === question.id ? (
                    <div className="inline-edit" style={{ width: '100%' }}>
                      <div className="form-row">
                        <Field label="问题类型">
                          <Input
                            value={questionDetailDraft.type}
                            onChange={(event) =>
                              setQuestionDetailDraft({ ...questionDetailDraft, type: event.target.value })
                            }
                            maxLength={100}
                            placeholder="例如：技术、项目、行为面试"
                          />
                        </Field>
                        <Field label="难度">
                          <Select
                            value={questionDetailDraft.difficulty ?? ''}
                            onChange={(event) =>
                              setQuestionDetailDraft({
                                ...questionDetailDraft,
                                difficulty: event.target.value === '' ? null : Number(event.target.value),
                              })
                            }
                          >
                            <option value="">未填写</option>
                            <option value="1">1（简单）</option>
                            <option value="2">2</option>
                            <option value="3">3（中等）</option>
                            <option value="4">4</option>
                            <option value="5">5（困难）</option>
                          </Select>
                        </Field>
                      </div>
                      <Field label="我的回答">
                        <Textarea
                          value={questionDetailDraft.myAnswer}
                          onChange={(event) =>
                            setQuestionDetailDraft({ ...questionDetailDraft, myAnswer: event.target.value })
                          }
                          rows={3}
                          maxLength={20000}
                        />
                      </Field>
                      <Field label="参考答案">
                        <Textarea
                          value={questionDetailDraft.referenceAnswer}
                          onChange={(event) =>
                            setQuestionDetailDraft({ ...questionDetailDraft, referenceAnswer: event.target.value })
                          }
                          rows={3}
                          maxLength={20000}
                        />
                      </Field>
                      <Field label="错误原因">
                        <Textarea
                          value={questionDetailDraft.errorReason}
                          onChange={(event) =>
                            setQuestionDetailDraft({ ...questionDetailDraft, errorReason: event.target.value })
                          }
                          rows={2}
                          maxLength={5000}
                        />
                      </Field>
                      <Field label="改进方案">
                        <Textarea
                          value={questionDetailDraft.improvementPlan}
                          onChange={(event) =>
                            setQuestionDetailDraft({ ...questionDetailDraft, improvementPlan: event.target.value })
                          }
                          rows={2}
                          maxLength={5000}
                        />
                      </Field>
                      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
                        <Button
                          type="button"
                          variant="ghost"
                          disabled={pending}
                          onClick={() => setQuestionDetailDraft(null)}
                        >
                          取消
                        </Button>
                        <Button
                          type="button"
                          variant="primary"
                          disabled={pending}
                          onClick={() => submitQuestionDetail(question)}
                        >
                          {updateQuestion.isPending ? '保存中…' : '保存问题详情'}
                        </Button>
                      </div>
                    </div>
                  ) : null}
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
                  <QuestionClassificationSection
                    question={question}
                    disabled={cannotReview || isCompletedReview || pending}
                    onChanged={() => reviewQuery.refetch()}
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
