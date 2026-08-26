import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApplicationForm } from '@/features/applications/components/ApplicationForm'
import {
  toCreateRequest,
  type ApplicationFormValues,
} from '@/features/applications/components/applicationFormValues'
import { useCreateApplication } from '@/api/applications/useApplicationMutations'
import { useJob } from '@/api/jobs/useJobQueries'
import { Spinner } from '@/components/ui/Spinner'
import { ErrorState } from '@/components/ui/ErrorState'
import { isApiError, isNetworkError, type FieldError } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'

export function ApplicationCreatePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const jobId = searchParams.get('jobId') ?? undefined

  const { data: job, isLoading: jobLoading, error: jobError } = useJob(jobId)
  const createMutation = useCreateApplication()
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldError[]>()

  // 缺岗位 ID：无法创建投递
  if (!jobId) {
    return (
      <ErrorState
        error={new Error('缺少岗位 ID，无法创建投递')}
        extraAction={
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => navigate('/jobs')}
          >
            返回岗位列表
          </button>
        }
      />
    )
  }

  const handleSubmit = (values: ApplicationFormValues) => {
    setServerFieldErrors(undefined)
    createMutation.mutate(toCreateRequest(jobId, values), {
      onSuccess: (app) => {
        pushToast('投递已创建')
        navigate(`/applications/${app.id}`)
      },
      onError: (e) => {
        if (isApiError(e) && e.code === 'DUPLICATE_APPLICATION') {
          pushToast('该岗位已存在活动投递，无法重复创建', 'error')
        } else if (isApiError(e) && e.code === 'IDEMPOTENCY_CONFLICT') {
          pushToast('检测到重复提交（幂等键冲突），请勿重复点击', 'error')
        } else if (isApiError(e) && e.fieldErrors) {
          setServerFieldErrors(e.fieldErrors)
        } else if (isApiError(e)) {
          pushToast(e.message, 'error')
        } else if (isNetworkError(e)) {
          pushToast(e.message, 'error')
        }
      },
    })
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">创建投递记录</h1>
          <p className="page-subtitle">
            记录投递渠道与下一步行动，默认状态为草稿
          </p>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">岗位信息</h2>
        </div>
        <div className="card-body">
          {jobLoading ? (
            <Spinner label="加载岗位…" />
          ) : jobError || !job ? (
            <ErrorState
              error={jobError ?? new Error('岗位不存在')}
              onRetry={() => navigate('/jobs')}
              retryLabel="返回岗位列表"
            />
          ) : (
            <div className="detail-summary" style={{ marginBottom: 16 }}>
              <dl>
                <dt>公司</dt>
                <dd>{job.companyName}</dd>
                <dt>岗位</dt>
                <dd>{job.title}</dd>
                <dt>地点</dt>
                <dd>{job.location || '—'}</dd>
              </dl>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">投递信息</h2>
        </div>
        <div className="card-body">
          <ApplicationForm
            mode="create"
            jobId={jobId}
            fieldErrors={serverFieldErrors}
            submitting={createMutation.isPending}
            onSubmit={handleSubmit}
            onCancel={() => navigate(jobId ? `/jobs/${jobId}` : '/jobs')}
          />
        </div>
      </div>
    </div>
  )
}
