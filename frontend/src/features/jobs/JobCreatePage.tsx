import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  JobForm,
} from '@/features/jobs/components/JobForm'
import { toCreateRequest } from '@/features/jobs/components/jobFormValues'
import type { JobFormValues } from '@/features/jobs/components/jobFormValues'
import { useCreateJob } from '@/api/jobs/useJobMutations'
import {
  isApiError,
  isIdempotencyConflict,
  isNetworkError,
  type FieldError,
} from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'

export function JobCreatePage() {
  const navigate = useNavigate()
  const createMutation = useCreateJob()
  const [serverFieldErrors, setServerFieldErrors] = useState<FieldError[]>()

  const handleSubmit = (values: JobFormValues) => {
    setServerFieldErrors(undefined)
    createMutation.mutate(toCreateRequest(values), {
      onSuccess: (job) => {
        pushToast('岗位已创建')
        navigate(`/jobs/${job.id}`)
      },
      onError: (e) => {
        if (isIdempotencyConflict(e)) {
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
          <h1 className="page-title">粘贴 JD 创建岗位</h1>
          <p className="page-subtitle">
            填写必填字段后保存，将进入岗位详情确认要求与差距
          </p>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">岗位信息</h2>
        </div>
        <div className="card-body">
          <JobForm
            mode="create"
            fieldErrors={serverFieldErrors}
            submitting={createMutation.isPending}
            onSubmit={handleSubmit}
            onCancel={() => navigate('/jobs')}
          />
        </div>
      </div>
    </div>
  )
}
