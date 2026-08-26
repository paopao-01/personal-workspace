import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useInterviewList } from '@/api/interviews/useInterviewQueries'
import type { ApplicationStatus } from '@/api/applications/applicationApi'
import type { Interview, InterviewScheduleStatus } from '@/api/interviews/interviewApi'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import {
  applicationStatusLabel,
  applicationStatusVariant,
} from '@/features/applications/applicationStatusLabels'
import {
  formatInterviewTime,
  interviewModeLabel,
  interviewResultLabel,
  interviewScheduleLabel,
  interviewScheduleVariant,
} from '@/features/interviews/interviewLabels'

function localDateValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const today = new Date()
const defaultFrom = localDateValue(today)
const nextWeek = new Date(today)
nextWeek.setDate(today.getDate() + 6)
const defaultTo = localDateValue(nextWeek)

const scheduleOptions: { value: '' | InterviewScheduleStatus; label: string }[] = [
  { value: '', label: '全部日程状态' },
  { value: 'SCHEDULED', label: interviewScheduleLabel.SCHEDULED },
  { value: 'COMPLETED', label: interviewScheduleLabel.COMPLETED },
  { value: 'CANCELED', label: interviewScheduleLabel.CANCELED },
  { value: 'NO_SHOW', label: interviewScheduleLabel.NO_SHOW },
]

const applicationStatusOptions: { value: '' | ApplicationStatus; label: string }[] = [
  { value: '', label: '全部投递状态' },
  { value: 'DRAFT', label: applicationStatusLabel.DRAFT },
  { value: 'APPLIED', label: applicationStatusLabel.APPLIED },
  { value: 'RESUME_PASSED', label: applicationStatusLabel.RESUME_PASSED },
  { value: 'INTERVIEWING', label: applicationStatusLabel.INTERVIEWING },
  { value: 'ON_HOLD', label: applicationStatusLabel.ON_HOLD },
  { value: 'OFFER', label: applicationStatusLabel.OFFER },
  { value: 'REJECTED', label: applicationStatusLabel.REJECTED },
  { value: 'WITHDRAWN', label: applicationStatusLabel.WITHDRAWN },
]

function startOfDate(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

function endOfDate(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(`${value}T23:59:59.999`)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

function localDayLabel(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
}

function timelineDayKey(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('sv-SE')
}

export function InterviewListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const from = searchParams.get('from') ?? defaultFrom
  const to = searchParams.get('to') ?? defaultTo
  const scheduleStatus = (searchParams.get('scheduleStatus') ?? '') as
    | ''
    | InterviewScheduleStatus
  const applicationStatus = (searchParams.get('applicationStatus') ?? '') as
    | ''
    | ApplicationStatus
  const mode = (searchParams.get('mode') ?? '') as '' | NonNullable<Interview['mode']>
  const [pageOpenedAt] = useState(() => Date.now())
  const { data, isLoading, error, refetch } = useInterviewList({
    from: startOfDate(from),
    to: endOfDate(to),
    scheduleStatus: scheduleStatus || undefined,
    applicationStatus: applicationStatus || undefined,
    mode: mode || undefined,
  })

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next, { replace: false })
  }

  const interviews = data ?? []

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">面试中心</h1>
          <p className="page-subtitle">默认展示未来 7 天的面试安排</p>
        </div>
      </div>

      <div className="filter-bar card card-body">
        <Field label="开始日期">
          <Input type="date" value={from} onChange={(event) => updateParam('from', event.target.value)} />
        </Field>
        <Field label="结束日期">
          <Input type="date" value={to} onChange={(event) => updateParam('to', event.target.value)} />
        </Field>
        <Field label="日程状态">
          <Select value={scheduleStatus} onChange={(event) => updateParam('scheduleStatus', event.target.value)}>
            {scheduleOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </Select>
        </Field>
        <Field label="投递状态">
          <Select value={applicationStatus} onChange={(event) => updateParam('applicationStatus', event.target.value)}>
            {applicationStatusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </Select>
        </Field>
        <Field label="面试方式">
          <Select value={mode} onChange={(event) => updateParam('mode', event.target.value)}>
            <option value="">全部方式</option>
            <option value="ONLINE">线上</option>
            <option value="ONSITE">现场</option>
            <option value="PHONE">电话</option>
          </Select>
        </Field>
      </div>

      <div className="card interview-timeline">
        {isLoading ? <Spinner label="加载面试列表…" /> : null}
        {!isLoading && error ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
        {!isLoading && !error && interviews.length === 0 ? (
          <EmptyState text="此范围内没有面试安排。" />
        ) : null}
        {!isLoading && !error && interviews.length > 0 ? (
          <div className="interview-timeline-list">
            {interviews.map((interview, index) => {
              const startsNewDay = index === 0 || timelineDayKey(interview.startsAt) !== timelineDayKey(interviews[index - 1].startsAt)
              const needsConfirmation = interview.scheduleStatus === 'SCHEDULED' && new Date(interview.startsAt).getTime() < pageOpenedAt
              return (
                <div key={interview.id} className="interview-timeline-group">
                  {startsNewDay ? <h2 className="interview-timeline-day">{localDayLabel(interview.startsAt)}</h2> : null}
                  <article className="interview-timeline-item">
                    <div className="interview-timeline-rail" aria-hidden="true">
                      <span className="interview-timeline-dot" />
                    </div>
                    <div className="interview-timeline-time">
                      <strong>{formatInterviewTime(interview.startsAt)}</strong>
                      <span>{interview.eventTimeZone}</span>
                    </div>
                    <div className="interview-timeline-main">
                      <div className="interview-timeline-heading">
                        <div>
                          <h3>{interview.application.companyName}</h3>
                          <p>{interview.application.jobTitle} · {interview.roundName}</p>
                        </div>
                        <Button variant="ghost" size="sm" onClick={() => navigate(`/interviews/${interview.id}`)}>查看详情</Button>
                      </div>
                      <div className="interview-timeline-meta">
                        <span>{interview.mode ? interviewModeLabel[interview.mode] : '未填写方式'}</span>
                        <Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>{interviewScheduleLabel[interview.scheduleStatus]}</Badge>
                        <Badge variant={applicationStatusVariant[interview.application.status]}>{applicationStatusLabel[interview.application.status]}</Badge>
                        <span>准备事项 {(interview.preparationChecklist ?? []).length}</span>
                        {interview.result !== 'PENDING' ? <span>结果：{interviewResultLabel[interview.result]}</span> : null}
                        {needsConfirmation ? <span className="interview-confirmation">等待确认是否完成</span> : null}
                      </div>
                    </div>
                  </article>
                </div>
              )
            })}
          </div>
        ) : null}
      </div>
    </div>
  )
}
