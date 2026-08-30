import { useNavigate } from 'react-router-dom'
import { useDashboardOverview } from '@/api/dashboard/useDashboardQueries'
import type { ActionItem, DashboardOverview } from '@/api/dashboard/dashboardApi'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { formatDateTime } from '@/features/jobs/statusLabels'
import {
  applicationStatusLabel,
  applicationStatusVariant,
} from '@/features/applications/applicationStatusLabels'
import {
  formatInterviewTime,
  interviewModeLabel,
} from '@/features/interviews/interviewLabels'

/**
 * P01 首页工作台。让用户在 10 秒内知道今天应做什么。
 *
 * 后端按业务优先级聚合待复盘、投递行动和学习任务；所有行动都保留来源跳转。
 */
export function DashboardPage() {
  const navigate = useNavigate()
  const { data, isLoading, error, refetch } = useDashboardOverview()

  if (isLoading) {
    return <Spinner label="加载工作台…" />
  }

  if (error || !data) {
    return (
      <ErrorState
        error={error ?? new Error('加载工作台失败')}
        onRetry={() => refetch()}
      />
    )
  }

  const actionItems = data.actionItems ?? []
  const activeApplications = data.activeApplications ?? []
  const recentJobs = data.recentJobs ?? []
  const upcomingInterviews = data.upcomingInterviews ?? []

  // 全空：首次会话引导
  if (recentJobs.length === 0 && activeApplications.length === 0) {
    return (
      <div>
        <div className="page-header">
          <div>
            <h1 className="page-title">首页工作台</h1>
            <p className="page-subtitle">识别今天最该做的动作</p>
          </div>
        </div>
        <div className="card">
          <EmptyState
            icon="📋"
            text="还没有岗位。粘贴一份 JD 开始分析，做出投递判断。"
            action={
              <Button variant="primary" onClick={() => navigate('/jobs/new')}>
                粘贴 JD 开始
              </Button>
            }
          />
        </div>
      </div>
    )
  }

  // activeApplications 缺岗位标题：用 actionItems.sourceRef.label 关联
  const labelByAppId = new Map<string, string>()
  for (const item of actionItems) {
    labelByAppId.set(item.sourceRef.id, item.sourceRef.label)
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">首页工作台</h1>
          <p className="page-subtitle">识别今天最该做的动作</p>
        </div>
      </div>

      <div className="section-grid">
        <TodayActionsSection items={actionItems} />
        <ActiveApplicationsSection
          applications={activeApplications}
          labelByAppId={labelByAppId}
        />
        <UpcomingInterviewsSection interviews={upcomingInterviews} />
        <RecentJobsSection overview={data} />
      </div>
    </div>
  )
}

function UpcomingInterviewsSection({
  interviews,
}: {
  interviews: DashboardOverview['upcomingInterviews']
}) {
  const navigate = useNavigate()
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">即将面试</h2>
      </div>
      <div className="card-body">
        {interviews.length === 0 ? (
          <EmptyState icon="📅" text="暂无即将开始的面试" />
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {interviews.map((interview) => (
              <li
                key={interview.id}
                className="requirement-row"
                style={{ justifyContent: 'space-between', gap: 12, cursor: 'pointer' }}
                onClick={() => navigate(`/interviews/${interview.id}`)}
              >
                <div className="requirement-main">
                  <span className="requirement-raw">{interview.roundName}</span>
                  <span className="muted" style={{ fontSize: 12 }}>
                    {interview.mode ? interviewModeLabel[interview.mode] : '未填写方式'} · {interview.eventTimeZone}
                  </span>
                </div>
                <span className="muted" style={{ fontSize: 12, flexShrink: 0 }}>
                  {formatInterviewTime(interview.startsAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function actionVariant(
  priority: number,
): 'danger' | 'warning' | 'info' {
  if (priority === 2 || priority === 4) return 'danger'
  if (priority === 1 || priority === 3 || priority === 5) return 'warning'
  return 'info'
}

function actionTag(priority: number): string {
  if (priority === 1) return '待复盘'
  if (priority === 2 || priority === 4) return '逾期'
  if (priority === 3) return '缺失'
  if (priority === 5) return '即将到期'
  return '待办'
}

function TodayActionsSection({ items }: { items: ActionItem[] }) {
  const navigate = useNavigate()
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">今天应做什么</h2>
      </div>
      <div className="card-body">
        {items.length === 0 ? (
          <EmptyState icon="✅" text="暂无待办行动" />
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {items.map((item) => (
              <li
                key={item.id}
                className="requirement-row"
                style={{ justifyContent: 'space-between', gap: 12, cursor: 'pointer' }}
                onClick={() => {
                  const path = item.sourceRef.type === 'JOB'
                    ? `/jobs/${item.sourceRef.id}`
                    : item.sourceRef.type === 'INTERVIEW'
                      ? `/interviews/${item.sourceRef.id}/review`
                      : item.sourceRef.type === 'TASK'
                        ? '/tasks'
                        : `/applications/${item.sourceRef.id}`
                  navigate(path)
                }}
              >
                <div className="requirement-main">
                  <div className="requirement-meta">
                    <Badge variant={actionVariant(item.priority)}>
                      {actionTag(item.priority)}
                    </Badge>
                  </div>
                  <span className="requirement-raw">{item.title}</span>
                </div>
                <span className="muted" style={{ fontSize: 12, flexShrink: 0 }}>
                  {item.dueAt ? formatDateTime(item.dueAt) : '—'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function ActiveApplicationsSection({
  applications,
  labelByAppId,
}: {
  applications: DashboardOverview['activeApplications']
  labelByAppId: Map<string, string>
}) {
  const navigate = useNavigate()
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">进行中投递</h2>
      </div>
      <div className="card-body">
        {applications.length === 0 ? (
          <EmptyState
            icon="📨"
            text="暂无进行中投递，从岗位做出投递决定开始"
          />
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {applications.map((app) => (
              <li
                key={app.id}
                className="requirement-row"
                style={{ justifyContent: 'space-between', gap: 12, cursor: 'pointer' }}
                onClick={() => navigate(`/applications/${app.id}`)}
              >
                <div className="requirement-main">
                  <div className="requirement-meta">
                    <Badge variant={applicationStatusVariant[app.status]}>
                      {applicationStatusLabel[app.status]}
                    </Badge>
                  </div>
                  <span className="requirement-raw">
                    {labelByAppId.get(app.id) ?? '查看详情'}
                  </span>
                  {app.nextAction ? (
                    <span className="muted" style={{ fontSize: 12 }}>
                      下一步：{app.nextAction}
                    </span>
                  ) : null}
                </div>
                <span className="muted" style={{ fontSize: 12, flexShrink: 0 }}>
                  {app.nextActionDueAt ? formatDateTime(app.nextActionDueAt) : '—'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function RecentJobsSection({ overview }: { overview: DashboardOverview }) {
  const navigate = useNavigate()
  const jobs = overview.recentJobs ?? []
  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">最近岗位</h2>
      </div>
      <div className="card-body">
        {jobs.length === 0 ? (
          <EmptyState icon="🗂️" text="暂无岗位" />
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {jobs.map((job) => (
              <li
                key={job.id}
                className="requirement-row"
                style={{ justifyContent: 'space-between', gap: 12, cursor: 'pointer' }}
                onClick={() => navigate(`/jobs/${job.id}`)}
              >
                <div className="requirement-main">
                  <span className="requirement-raw">
                    {job.companyName} · {job.title}
                  </span>
                </div>
                <span className="muted" style={{ fontSize: 12, flexShrink: 0 }}>
                  {formatDateTime(job.updatedAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
