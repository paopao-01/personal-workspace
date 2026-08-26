import { useNavigate, useSearchParams } from 'react-router-dom'
import { useInterviewList } from '@/api/interviews/useInterviewQueries'
import type { InterviewScheduleStatus } from '@/api/interviews/interviewApi'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { Table } from '@/components/ui/Table'
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

export function InterviewListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const from = searchParams.get('from') ?? defaultFrom
  const to = searchParams.get('to') ?? defaultTo
  const scheduleStatus = (searchParams.get('scheduleStatus') ?? '') as
    | ''
    | InterviewScheduleStatus
  const mode = searchParams.get('mode') ?? ''
  const { data, isLoading, error, refetch } = useInterviewList({
    from: startOfDate(from),
    to: endOfDate(to),
    scheduleStatus: scheduleStatus || undefined,
  })

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next, { replace: false })
  }

  const interviews = (data ?? []).filter(
    (interview) => !mode || interview.mode === mode,
  )

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
        <Field label="面试方式">
          <Select value={mode} onChange={(event) => updateParam('mode', event.target.value)}>
            <option value="">全部方式</option>
            <option value="ONLINE">线上</option>
            <option value="ONSITE">现场</option>
            <option value="PHONE">电话</option>
          </Select>
        </Field>
      </div>

      <div className="card">
        {isLoading ? <Spinner label="加载面试列表…" /> : null}
        {!isLoading && error ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
        {!isLoading && !error && interviews.length === 0 ? (
          <EmptyState text="此范围内没有面试安排。" />
        ) : null}
        {!isLoading && !error && interviews.length > 0 ? (
          <Table headers={['轮次', '开始时间', '方式', '日程状态', '结果', '']}>
            {interviews.map((interview) => (
              <tr key={interview.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/interviews/${interview.id}`)}>
                <td>{interview.roundName}</td>
                <td>{formatInterviewTime(interview.startsAt)}</td>
                <td>{interview.mode ? interviewModeLabel[interview.mode] : '—'}</td>
                <td><Badge variant={interviewScheduleVariant[interview.scheduleStatus]}>{interviewScheduleLabel[interview.scheduleStatus]}</Badge></td>
                <td>{interviewResultLabel[interview.result]}</td>
                <td className="table-row-actions" onClick={(event) => event.stopPropagation()}>
                  <Button variant="ghost" size="sm" onClick={() => navigate(`/interviews/${interview.id}`)}>查看</Button>
                </td>
              </tr>
            ))}
          </Table>
        ) : null}
      </div>
    </div>
  )
}
