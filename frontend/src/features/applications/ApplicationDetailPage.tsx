import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useApplicationDetail } from '@/api/applications/useApplicationQueries'
import { useDeleteApplication } from '@/api/applications/useApplicationMutations'
import { isApiError } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { Button } from '@/components/ui/Button'
import { ApplicationSummarySection } from '@/features/applications/components/ApplicationSummarySection'
import { ApplicationStatusSection } from '@/features/applications/components/ApplicationStatusSection'
import { NextActionSection } from '@/features/applications/components/NextActionSection'
import { StatusTimelineSection } from '@/features/applications/components/StatusTimelineSection'
import { InterviewListSection } from '@/features/applications/components/InterviewListSection'
import { InterviewCreateSection } from '@/features/interviews/components/InterviewCreateSection'

/**
 * P04 投递详情页。五区：摘要 / 当前状态 / 下一步行动 / 时间线 / 面试列表。
 */
export function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>()
  const navigate = useNavigate()
  const [creatingInterview, setCreatingInterview] = useState(false)
  const { data: detail, isLoading, error, refetch } = useApplicationDetail(applicationId)
  const deleteApplication = useDeleteApplication()

  const remove = () => {
    if (!detail || !confirm('确认删除此投递？可在最近删除中恢复。')) return
    deleteApplication.mutate({ applicationId: detail.id, version: detail.version }, {
      onSuccess: () => { pushToast('投递已移至最近删除'); navigate('/applications') },
      onError: (caught) => pushToast(isApiError(caught) ? caught.message : '删除失败，请稍后重试', 'error'),
    })
  }

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
        <Button variant="ghost" size="sm" disabled={deleteApplication.isPending} onClick={remove}>
          删除投递
        </Button>
      </div>

      <div className="section-grid">
        <ApplicationSummarySection detail={detail} />
        <ApplicationStatusSection detail={detail} />
        <NextActionSection detail={detail} />
        <StatusTimelineSection statusHistory={detail.statusHistory ?? []} />
        {creatingInterview ? (
          <InterviewCreateSection
            applicationId={detail.id}
            onCancel={() => setCreatingInterview(false)}
          />
        ) : null}
        <InterviewListSection
          interviews={detail.interviews ?? []}
          canCreate={
            detail.status === 'RESUME_PASSED' || detail.status === 'INTERVIEWING'
          }
          onCreate={() => setCreatingInterview(true)}
        />
      </div>
    </div>
  )
}
