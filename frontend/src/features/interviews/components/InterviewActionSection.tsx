import { useState, type FormEvent } from 'react'
import {
  useCancelInterview,
  useCompleteInterview,
  useMarkInterviewNoShow,
  useRescheduleInterview,
} from '@/api/interviews/useInterviewMutations'
import type { Interview } from '@/api/interviews/interviewApi'
import { isApiError, isVersionConflict } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/Form'
import { localDatetimeToIso, isoToLocalDatetime } from '@/features/applications/components/applicationFormValues'

type Panel = 'reschedule' | 'complete' | null

export function InterviewActionSection({ interview }: { interview: Interview }) {
  const [panel, setPanel] = useState<Panel>(null)
  const [startsAt, setStartsAt] = useState(() => isoToLocalDatetime(interview.startsAt))
  const [eventTimeZone, setEventTimeZone] = useState(interview.eventTimeZone)
  const [result, setResult] = useState(interview.result)
  const rescheduleMutation = useRescheduleInterview()
  const completeMutation = useCompleteInterview()
  const cancelMutation = useCancelInterview()
  const noShowMutation = useMarkInterviewNoShow()
  const pending = rescheduleMutation.isPending || completeMutation.isPending || cancelMutation.isPending || noShowMutation.isPending

  if (interview.scheduleStatus !== 'SCHEDULED') return null

  const reportError = (error: Error) => {
    if (isVersionConflict(error)) {
      pushToast('该面试已被修改，页面将刷新', 'error')
      return
    }
    pushToast(isApiError(error) ? error.message : '操作失败，请稍后重试', 'error')
  }

  const submitReschedule = (event: FormEvent) => {
    event.preventDefault()
    const iso = localDatetimeToIso(startsAt)
    if (!iso || !eventTimeZone.trim()) {
      pushToast('请填写新的开始时间和事件时区', 'error')
      return
    }
    rescheduleMutation.mutate(
      { interviewId: interview.id, version: interview.version, body: { startsAt: iso, eventTimeZone: eventTimeZone.trim() } },
      { onSuccess: () => { pushToast('面试已改期，提醒计划已重算'); setPanel(null) }, onError: reportError },
    )
  }

  const submitComplete = (event: FormEvent) => {
    event.preventDefault()
    completeMutation.mutate(
      { interviewId: interview.id, version: interview.version, body: { result } },
      { onSuccess: () => { pushToast('已标记面试完成'); setPanel(null) }, onError: reportError },
    )
  }

  const runCancel = () => {
    if (!confirm('确认取消这场面试？未触发的提醒会一并取消。')) return
    cancelMutation.mutate(
      { interviewId: interview.id, version: interview.version, body: undefined },
      { onSuccess: () => pushToast('面试已取消'), onError: reportError },
    )
  }

  const runNoShow = () => {
    if (!confirm('确认标记为未出席？未触发的提醒会一并取消。')) return
    noShowMutation.mutate(
      { interviewId: interview.id, version: interview.version, body: undefined },
      { onSuccess: () => pushToast('已标记为未出席'), onError: reportError },
    )
  }

  return (
    <section className="card">
      <div className="card-header"><h2 className="card-title">日程操作</h2></div>
      <div className="card-body">
        <div className="flex-row" style={{ gap: 8 }}>
          <Button variant="ghost" size="sm" onClick={() => setPanel(panel === 'reschedule' ? null : 'reschedule')} disabled={pending}>改期</Button>
          <Button variant="primary" size="sm" onClick={() => setPanel(panel === 'complete' ? null : 'complete')} disabled={pending}>标记完成</Button>
          <Button variant="ghost" size="sm" onClick={runCancel} disabled={pending}>取消</Button>
          <Button variant="danger" size="sm" onClick={runNoShow} disabled={pending}>标记未出席</Button>
        </div>

        {panel === 'reschedule' ? (
          <form onSubmit={submitReschedule} style={{ marginTop: 16 }}>
            <div className="form-row">
              <Field label="新的开始时间" required><Input type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></Field>
              <Field label="事件时区" required><Input value={eventTimeZone} onChange={(event) => setEventTimeZone(event.target.value)} /></Field>
            </div>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="ghost" type="button" onClick={() => setPanel(null)}>返回</Button>
              <Button variant="primary" type="submit" disabled={pending}>{pending ? '提交中…' : '确认改期'}</Button>
            </div>
          </form>
        ) : null}

        {panel === 'complete' ? (
          <form onSubmit={submitComplete} style={{ marginTop: 16 }}>
            <Field label="面试结果">
              <Select value={result} onChange={(event) => setResult(event.target.value as Interview['result'])}>
                <option value="PENDING">暂不确认</option>
                <option value="PASSED">通过</option>
                <option value="FAILED">未通过</option>
              </Select>
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="ghost" type="button" onClick={() => setPanel(null)}>返回</Button>
              <Button variant="primary" type="submit" disabled={pending}>{pending ? '提交中…' : '确认完成'}</Button>
            </div>
          </form>
        ) : null}
      </div>
    </section>
  )
}
