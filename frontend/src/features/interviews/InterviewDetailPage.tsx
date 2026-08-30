import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useInterview, useInterviewReminders } from '@/api/interviews/useInterviewQueries'
import { useDeleteInterview, useRetryReminder } from '@/api/interviews/useInterviewMutations'
import { ErrorState } from '@/components/ui/ErrorState'
import { Spinner } from '@/components/ui/Spinner'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { isApiError } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import { InterviewActionSection } from '@/features/interviews/components/InterviewActionSection'
import {
  formatInterviewTime,
  interviewResultLabel,
  interviewScheduleLabel,
  interviewScheduleVariant,
  reminderStatusLabel,
  reminderTypeLabel,
  interviewModeLabel,
} from '@/features/interviews/interviewLabels'

export function InterviewDetailPage() {
  const { interviewId } = useParams<{ interviewId: string }>()
  const navigate = useNavigate()
  const [openedAt] = useState(() => Date.now())
  const interviewQuery = useInterview(interviewId)
  const reminderQuery = useInterviewReminders(interviewId)
  const retryReminderMutation = useRetryReminder(interviewId)
  const deleteInterview = useDeleteInterview()

  if (interviewQuery.isLoading) return <Spinner label="加载面试详情…" />
  if (interviewQuery.error || !interviewQuery.data) {
    return (
      <ErrorState
        error={interviewQuery.error ?? new Error('面试不存在')}
        onRetry={() => interviewQuery.refetch()}
        extraAction={
          <Button variant="ghost" size="sm" onClick={() => navigate('/dashboard')}>
            返回首页
          </Button>
        }
      />
    )
  }

  const interview = interviewQuery.data
  const remove = () => {
    if (!confirm('确认删除此面试？可在最近删除中恢复。')) return
    deleteInterview.mutate({ interviewId: interview.id, version: interview.version }, {
      onSuccess: () => { pushToast('面试已移至最近删除'); navigate(`/applications/${interview.applicationId}`) },
      onError: (error) => pushToast(isApiError(error) ? error.message : '删除失败，请稍后重试', 'error'),
    })
  }
  const waitingForConfirmation =
    interview.scheduleStatus === 'SCHEDULED' &&
    new Date(interview.startsAt).getTime() < openedAt

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{interview.roundName}</h1>
          <p className="page-subtitle">面试详情与提醒计划</p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button
            variant="primary"
            size="sm"
            onClick={() => navigate(`/interviews/${interview.id}/preparation`)}
          >
            打开准备包
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(`/applications/${interview.applicationId}`)}
          >
            返回投递
          </Button>
          <Button variant="ghost" size="sm" disabled={deleteInterview.isPending} onClick={remove}>
            删除面试
          </Button>
        </div>
      </div>

      {waitingForConfirmation ? (
        <div className="conflict-banner">
          <span>该面试已到开始时间，等待你确认是否完成；系统不会自动完成。</span>
        </div>
      ) : null}

      <InterviewActionSection
        key={`${interview.id}:${interview.version}`}
        interview={interview}
      />

      {interview.scheduleStatus === 'COMPLETED' ? (
        <section className="card">
          <div className="card-header">
            <h2 className="card-title">复盘</h2>
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate(`/interviews/${interview.id}/review`)}
            >
              开始/继续复盘
            </Button>
          </div>
          <div className="card-body">
            <p className="muted" style={{ margin: 0 }}>
              记录面试结果、问题和回答状态，先保存草稿，后续可继续补充。
            </p>
          </div>
        </section>
      ) : null}

      <div className="section-grid">
        <section className="card detail-summary">
          <div className="card-header">
            <h2 className="card-title">面试摘要</h2>
          </div>
          <div className="card-body">
            <dl>
              <dt>关联投递</dt>
              <dd>
                <Link to={`/applications/${interview.applicationId}`}>查看投递记录</Link>
              </dd>
              <dt>开始时间</dt>
              <dd>{formatInterviewTime(interview.startsAt)}</dd>
              <dt>事件时区</dt>
              <dd>{interview.eventTimeZone}</dd>
              <dt>方式</dt>
              <dd>{interview.mode ? interviewModeLabel[interview.mode] : '—'}</dd>
              <dt>日程状态</dt>
              <dd>
                <Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>
                  {interviewScheduleLabel[interview.scheduleStatus]}
                </Badge>
              </dd>
              <dt>结果</dt>
              <dd>{interviewResultLabel[interview.result]}</dd>
              <dt>联系人</dt>
              <dd>{interview.contact ?? '—'}</dd>
              <dt>会议链接或地址</dt>
              <dd>{interview.meetingUrlOrAddress ?? '—'}</dd>
            </dl>
            {interview.notes ? (
              <p className="muted" style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                {interview.notes}
              </p>
            ) : null}
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2 className="card-title">准备事项</h2>
          </div>
          <div className="card-body">
            {(interview.preparationChecklist ?? []).length === 0 ? (
              <EmptyState icon="□" text="暂无准备事项" />
            ) : (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {interview.preparationChecklist?.map((item, index) => (
                  <li key={`${item}-${index}`}>{item}</li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2 className="card-title">提醒计划</h2>
          </div>
          <div className="card-body">
            {reminderQuery.isLoading ? (
              <Spinner label="加载提醒计划…" />
            ) : reminderQuery.error ? (
              <ErrorState error={reminderQuery.error} onRetry={() => reminderQuery.refetch()} />
            ) : (reminderQuery.data ?? []).length === 0 ? (
              <EmptyState icon="⏰" text="暂无提醒计划" />
            ) : (
              <table className="table">
                <thead><tr><th>节点</th><th>时间</th><th>状态</th><th>处理</th></tr></thead>
                <tbody>
                  {reminderQuery.data?.map((reminder) => (
                    <tr key={reminder.id}>
                      <td>{reminderTypeLabel[reminder.reminderType]}</td>
                      <td>{formatInterviewTime(reminder.scheduledAt)}</td>
                      <td>
                        <div>{reminderStatusLabel[reminder.status]}</div>
                        {reminder.status === 'FAILED' ? (
                          <div className="form-hint">
                            {reminder.failureReason ?? '未记录失败原因'} · 已尝试 {reminder.attemptCount} 次
                          </div>
                        ) : null}
                      </td>
                      <td>
                        {reminder.status === 'FAILED' ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={retryReminderMutation.isPending}
                            onClick={() => retryReminderMutation.mutate(
                              { reminderId: reminder.id, version: reminder.version },
                              {
                                onSuccess: () => pushToast('提醒已重新排队'),
                                onError: (error) => pushToast(
                                  isApiError(error) ? error.message : '重试失败，请稍后再试',
                                  'error',
                                ),
                              },
                            )}
                          >
                            {retryReminderMutation.isPending ? '排队中…' : '重试'}
                          </Button>
                        ) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="form-hint" style={{ margin: '12px 0 0' }}>
              提醒会在你打开应用后显示，不承诺系统级推送。
            </p>
          </div>
        </section>
      </div>
    </div>
  )
}
