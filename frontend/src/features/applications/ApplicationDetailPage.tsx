import { useParams, useNavigate } from 'react-router-dom'
import { useApplicationDetail } from '@/api/applications/useApplicationQueries'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { Button } from '@/components/ui/Button'
import { ApplicationSummarySection } from '@/features/applications/components/ApplicationSummarySection'
import { ApplicationStatusSection } from '@/features/applications/components/ApplicationStatusSection'
import { NextActionSection } from '@/features/applications/components/NextActionSection'
import { StatusTimelineSection } from '@/features/applications/components/StatusTimelineSection'
import { InterviewListSection } from '@/features/applications/components/InterviewListSection'

/**
 * P04 投递详情页。五区：摘要 / 当前状态 / 下一步行动 / 时间线 / 面试列表。
 */
export function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>()
  const navigate = useNavigate()
  const { data: detail, isLoading, error, refetch } = useApplicationDetail(applicationId)

  if (isLoading) {
    return <Spinner label="加载投递详情…" />
  }

  if (error || !detail) {
    return (
      <ErrorState
        error={error ?? new Error('投递不存在')}
        onRetry={() => refetch()}
        extraAction={
          <Button variant="ghost" size="sm" onClick={() => navigate('/jobs')}>
            返回岗位列表
          </Button>
        }
      />
    )
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">
            {detail.job?.title ?? '投递详情'}
          </h1>
          <p className="page-subtitle">
            {detail.job?.companyName ?? ''}
            {detail.job?.location ? ` · ${detail.job.location}` : ''}
          </p>
        </div>
      </div>

      <div className="section-grid">
        <ApplicationSummarySection detail={detail} />
        <ApplicationStatusSection detail={detail} />
        <NextActionSection detail={detail} />
        <StatusTimelineSection statusHistory={detail.statusHistory ?? []} />
        <InterviewListSection interviews={detail.interviews ?? []} />
      </div>
    </div>
  )
}
