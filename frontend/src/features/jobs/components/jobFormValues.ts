import type { Job, JobCreateRequest, JobUpdateRequest } from '@/api/jobs/jobApi'

export interface JobFormValues {
  companyName: string
  title: string
  jdRawText: string
  source: string
  sourceUrl: string
  location: string
  salaryRange: string
  notes: string
}

export const EMPTY_JOB_VALUES: JobFormValues = {
  companyName: '',
  title: '',
  jdRawText: '',
  source: '',
  sourceUrl: '',
  location: '',
  salaryRange: '',
  notes: '',
}

export function jobToValues(job: Job): JobFormValues {
  return {
    companyName: job.companyName ?? '',
    title: job.title ?? '',
    jdRawText: job.jdRawText ?? '',
    source: job.source ?? '',
    sourceUrl: job.sourceUrl ?? '',
    location: job.location ?? '',
    salaryRange: job.salaryRange ?? '',
    notes: job.notes ?? '',
  }
}

/** 将表单值转为创建请求体。 */
export function toCreateRequest(v: JobFormValues): JobCreateRequest {
  return {
    companyName: v.companyName.trim(),
    title: v.title.trim(),
    jdRawText: v.jdRawText,
    source: v.source || undefined,
    sourceUrl: v.sourceUrl || undefined,
    location: v.location || undefined,
    salaryRange: v.salaryRange || undefined,
    notes: v.notes || undefined,
  }
}

/**
 * 将表单值 + 决定字段转为更新请求体。
 * JobUpdateRequest 是 JobCreateRequest 超集（OpenAPI allOf）。
 */
export function toUpdateRequest(
  v: JobFormValues,
  decisionStatus: Job['decisionStatus'],
  decisionReason: string | null | undefined,
): JobUpdateRequest {
  return {
    ...toCreateRequest(v),
    decisionStatus: decisionStatus ?? null,
    decisionReason: decisionReason ?? null,
  }
}
