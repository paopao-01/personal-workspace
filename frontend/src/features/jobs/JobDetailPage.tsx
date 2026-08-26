import { useParams, useNavigate } from 'react-router-dom'
import { useJob } from '@/api/jobs/useJobQueries'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { Button } from '@/components/ui/Button'
import { JobSummarySection } from '@/features/jobs/components/JobSummarySection'
import { DecisionSection } from '@/features/jobs/components/DecisionSection'
import { RequirementConfirmationSection } from '@/features/jobs/components/RequirementConfirmationSection'
import { GapListSection } from '@/features/jobs/components/GapListSection'

export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>()
  const navigate = useNavigate()
  const { data: job, isLoading, error, refetch } = useJob(jobId)

  if (isLoading) {
    return <Spinner label="加载岗位详情…" />
  }

  if (error || !job) {
    return (
      <ErrorState
        error={error ?? new Error('岗位不存在')}
        onRetry={() => refetch()}
        extraAction={
          <Button variant="ghost" size="sm" onClick={() => navigate('/jobs')}>
            返回列表
          </Button>
        }
      />
    )
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">{job.title}</h1>
          <p className="page-subtitle">
            {job.companyName}
            {job.location ? ` · ${job.location}` : ''}
          </p>
        </div>
      </div>

      <div className="section-grid">
        <JobSummarySection job={job} />
        <DecisionSection job={job} />
        <RequirementConfirmationSection jobId={job.id} />
        <GapListSection jobId={job.id} />
      </div>
    </div>
  )
}
