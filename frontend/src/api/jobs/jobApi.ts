import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

/**
 * Job 模块 API 调用函数（10 个端点）。
 * 类型来自 OpenAPI 生成产物（src/api/generated/types.ts），禁止手写枚举。
 *
 * 后端实际行为适配：
 * - updateJob/archiveJob/restoreJob/updateRequirement 需传 If-Match-Version
 *   （缺失返回空 body 400，故此处 version 为必填参数，由调用方从 query data 回填）。
 */

type Schemas = components['schemas']
export type Job = Schemas['Job']
export type PageJob = Schemas['PageJob']
export type JobCreateRequest = Schemas['JobCreateRequest']
export type JobUpdateRequest = Schemas['JobUpdateRequest']
export type JobRequirement = Schemas['JobRequirement']
export type RequirementUpdateRequest = Schemas['RequirementUpdateRequest']
export type RequirementExtractionResult = Schemas['RequirementExtractionResult']
export type GapItem = Schemas['GapItem']
export type JobDecisionStatus = Schemas['JobDecisionStatus']
export type JobStatus = Schemas['JobStatus']
export type RequirementType = Schemas['RequirementType']
export type RequirementConfirmationStatus = Schemas['RequirementConfirmationStatus']
export type GapStatus = Schemas['GapStatus']
export type RequirementSource = Schemas['JobRequirement']['source']

export interface JobListParams {
  page?: number
  pageSize?: number
  decisionStatus?: JobDecisionStatus | null
  jobStatus?: JobStatus | null
  query?: string
}

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

export async function listJobs(params: JobListParams): Promise<PageJob> {
  const res = await apiClient.get<PageJob>('/jobs', {
    params: {
      page: params.page,
      pageSize: params.pageSize,
      decisionStatus: params.decisionStatus ?? undefined,
      jobStatus: params.jobStatus ?? undefined,
      query: params.query || undefined,
    },
  })
  return res.data
}

export async function getJob(jobId: string): Promise<Job> {
  const res = await apiClient.get<Job>(`/jobs/${jobId}`)
  return res.data
}

export async function createJob(body: JobCreateRequest): Promise<Job> {
  const res = await apiClient.post<Job>('/jobs', body)
  return res.data
}

export async function updateJob(
  jobId: string,
  version: number,
  body: JobUpdateRequest,
): Promise<Job> {
  const res = await apiClient.put<Job>(`/jobs/${jobId}`, body, {
    headers: ifMatchHeader(version),
  })
  return res.data
}

export async function archiveJob(jobId: string, version: number): Promise<Job> {
  const res = await apiClient.post<Job>(
    `/jobs/${jobId}/archive`,
    undefined,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function restoreJob(jobId: string, version: number): Promise<Job> {
  const res = await apiClient.post<Job>(
    `/jobs/${jobId}/restore`,
    undefined,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function extractRequirements(
  jobId: string,
): Promise<RequirementExtractionResult> {
  const res = await apiClient.post<RequirementExtractionResult>(
    `/jobs/${jobId}/requirements/extract`,
    undefined,
  )
  return res.data
}

export async function listRequirements(jobId: string): Promise<JobRequirement[]> {
  const res = await apiClient.get<JobRequirement[]>(`/jobs/${jobId}/requirements`)
  return res.data
}

export async function updateRequirement(
  requirementId: string,
  version: number,
  body: RequirementUpdateRequest,
): Promise<JobRequirement> {
  const res = await apiClient.put<JobRequirement>(
    `/job-requirements/${requirementId}`,
    body,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function getGapList(jobId: string): Promise<GapItem[]> {
  const res = await apiClient.get<GapItem[]>(`/jobs/${jobId}/gap-list`)
  return res.data
}
