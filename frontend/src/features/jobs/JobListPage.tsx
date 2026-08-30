import { useNavigate, useSearchParams } from 'react-router-dom'
import { useJobList } from '@/api/jobs/useJobQueries'
import { Table } from '@/components/ui/Table'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { EmptyState } from '@/components/ui/EmptyState'
import { useArchiveJob, useRestoreJob } from '@/api/jobs/useJobMutations'
import {
  formatDateTime,
  jobDecisionLabel,
  jobDecisionVariant,
  jobStatusLabel,
} from '@/features/jobs/statusLabels'
import type {
  JobDecisionStatus,
  JobListItem,
  JobStatus,
} from '@/api/jobs/jobApi'
import { isApiError, isNetworkError, isVersionConflict } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'

const DECISION_OPTIONS: {
  value: '' | Exclude<JobDecisionStatus, null>
  label: string
}[] = [
  { value: '', label: '全部决定' },
  { value: 'TO_APPLY', label: jobDecisionLabel.TO_APPLY },
  { value: 'APPLY', label: jobDecisionLabel.APPLY },
  { value: 'DEFER', label: jobDecisionLabel.DEFER },
  { value: 'IGNORE', label: jobDecisionLabel.IGNORE },
]

const STATUS_OPTIONS: { value: '' | JobStatus; label: string }[] = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: jobStatusLabel.ACTIVE },
  { value: 'ARCHIVED', label: jobStatusLabel.ARCHIVED },
]

export function JobListPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const query = searchParams.get('query') ?? ''
  const decisionStatus = (searchParams.get('decisionStatus') ?? '') as string
  const jobStatus = (searchParams.get('jobStatus') ?? '') as string
  const location = searchParams.get('location') ?? ''
  const source = searchParams.get('source') ?? ''
  const pendingRequirements = searchParams.get('hasPendingRequirements') ?? ''
  const page = Number(searchParams.get('page') ?? '1') || 1

  const { data, isLoading, error, refetch } = useJobList({
    page,
    pageSize: 20,
    query: query || undefined,
    decisionStatus: (decisionStatus || undefined) as
      | Exclude<JobDecisionStatus, null>
      | undefined,
    jobStatus: (jobStatus || undefined) as JobStatus | undefined,
    location: location || undefined,
    source: source || undefined,
    hasPendingRequirements: pendingRequirements === '' ? undefined : pendingRequirements === 'true',
  })

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setSearchParams(next, { replace: false })
  }

  const archiveMutation = useArchiveJob()
  const restoreMutation = useRestoreJob()

  const handleArchive = (item: JobListItem) => {
    const job = item.job
    if (!confirm(`确认归档岗位「${job.title}」？归档后仍可恢复。`)) return
    archiveMutation.mutate(
      { jobId: job.id, version: job.version },
      {
        onSuccess: () => pushToast('岗位已归档'),
        onError: (e) => {
          if (isVersionConflict(e)) {
            pushToast('该岗位已被修改，正在刷新', 'error')
            refetch()
          } else if (isApiError(e)) {
            pushToast(e.message, 'error')
          } else if (isNetworkError(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  const handleRestore = (item: JobListItem) => {
    const job = item.job
    restoreMutation.mutate(
      { jobId: job.id, version: job.version },
      {
        onSuccess: () => pushToast('岗位已恢复'),
        onError: (e) => {
          if (isVersionConflict(e)) {
            pushToast('该岗位已被修改，正在刷新', 'error')
            refetch()
          } else if (isApiError(e)) {
            pushToast(e.message, 'error')
          } else if (isNetworkError(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  const jobs = data?.items ?? []
  const totalPages = data?.totalPages ?? 0
  const gotoPage = (p: number) => updateParam('page', String(p))

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">岗位收集箱</h1>
          <p className="page-subtitle">
            管理岗位、快速筛选下一份值得处理的 JD
          </p>
        </div>
        <Button variant="primary" onClick={() => navigate('/jobs/new')}>
          + 新增岗位
        </Button>
      </div>

      <div className="filter-bar card card-body">
        <Field label="关键词" hint="搜索公司 / 岗位名称">
          <Input
            value={query}
            onChange={(e) => updateParam('query', e.target.value)}
            placeholder="输入关键词"
            maxLength={100}
          />
        </Field>
        <Field label="投递决定">
          <Select
            value={decisionStatus}
            onChange={(e) => updateParam('decisionStatus', e.target.value)}
          >
            {DECISION_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="岗位状态">
          <Select
            value={jobStatus}
            onChange={(e) => updateParam('jobStatus', e.target.value)}
          >
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="地点">
          <Input value={location} onChange={(e) => updateParam('location', e.target.value)} placeholder="城市 / 地区" maxLength={100} />
        </Field>
        <Field label="来源">
          <Input value={source} onChange={(e) => updateParam('source', e.target.value)} placeholder="招聘平台 / 推荐" maxLength={100} />
        </Field>
        <Field label="待确认要求">
          <Select value={pendingRequirements} onChange={(e) => updateParam('hasPendingRequirements', e.target.value)}>
            <option value="">全部</option>
            <option value="true">有待确认项</option>
            <option value="false">无待确认项</option>
          </Select>
        </Field>
      </div>

      <div className="card">
        {isLoading ? (
          <Spinner label="加载岗位列表…" />
        ) : error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : jobs.length === 0 ? (
          <EmptyState
            text="还没有岗位。粘贴一份 JD 开始分析。"
            action={
              <Button variant="primary" onClick={() => navigate('/jobs/new')}>
                粘贴 JD 开始
              </Button>
            }
          />
        ) : (
          <>
            <Table
              headers={[
                '公司',
                '岗位',
                '地点',
                '决定',
                '要求 / 差距',
                '有效投递',
                '最近更新',
                '',
              ]}
            >
              {jobs.map((item) => {
                const job = item.job
                return (
                <tr
                  key={job.id}
                  style={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/jobs/${job.id}`)}
                >
                  <td>{job.companyName}</td>
                  <td>{job.title}</td>
                  <td className="muted">{job.location || '—'}</td>
                  <td>
                    {job.decisionStatus ? (
                      <Badge variant={jobDecisionVariant[job.decisionStatus]}>
                        {jobDecisionLabel[job.decisionStatus]}
                      </Badge>
                    ) : (
                      <Badge variant="subtle">未决定</Badge>
                    )}
                  </td>
                  <td className="muted">
                    已确认 {item.confirmedRequirementCount} 项 / 待确认 {item.pendingRequirementCount} 项<br />
                    {item.gapOverview}
                  </td>
                  <td>
                    <Badge variant={item.hasActiveApplication ? 'success' : 'subtle'}>
                      {item.hasActiveApplication ? '有' : '无'}
                    </Badge>
                  </td>
                  <td className="muted">{formatDateTime(job.updatedAt)}</td>
                  <td
                    className="table-row-actions"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {job.status === 'ACTIVE' ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleArchive(item)}
                        disabled={
                          archiveMutation.isPending ||
                          restoreMutation.isPending
                        }
                      >
                        归档
                      </Button>
                    ) : (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleRestore(item)}
                        disabled={
                          archiveMutation.isPending ||
                          restoreMutation.isPending
                        }
                      >
                        恢复
                      </Button>
                    )}
                  </td>
                </tr>
                )
              })}
            </Table>
            <div className="pagination">
              <span className="pagination-info">
                共 {data?.total ?? 0} 条 · 第 {page}/{Math.max(totalPages, 1)} 页
              </span>
              <div className="flex-row">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => gotoPage(page - 1)}
                  disabled={page <= 1}
                >
                  上一页
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => gotoPage(page + 1)}
                  disabled={page >= totalPages}
                >
                  下一页
                </Button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
