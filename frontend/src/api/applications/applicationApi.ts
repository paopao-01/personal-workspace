import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

/**
 * Application 模块 API 调用函数（6 个端点）。
 * 类型来自 OpenAPI 生成产物（src/api/generated/types.ts），禁止手写枚举。
 *
 * 后端实际行为适配：
 * - updateApplication/transitionApplication 需传 If-Match-Version（缺失返回空 body 400），
 *   version 为必填参数，由调用方从 useApplicationDetail 的 data.version 回填。
 * - transitionApplication 使用稳定 Idempotency-Key `transition:${applicationId}:${targetStatus}`，
 *   覆盖 client.ts 的自动注入：相同转换的网络重试复用同一 key 走后端回放（AT-07），
 *   不同 targetStatus 视为新请求。POST createApplication 的 key 由 client.ts 自动注入。
 */

type Schemas = components['schemas']
export type Application = Schemas['Application']
export type ApplicationDetail = Schemas['ApplicationDetail']
export type ApplicationStatus = Schemas['ApplicationStatus']
export type ApplicationCreateRequest = Schemas['ApplicationCreateRequest']
export type ApplicationUpdateRequest = Schemas['ApplicationUpdateRequest']
export type ApplicationTransitionRequest = Schemas['ApplicationTransitionRequest']
export type PageApplication = Schemas['PageApplication']
export type StatusLog = Schemas['StatusLog']

export interface ApplicationListParams {
  page?: number
  pageSize?: number
  status?: ApplicationStatus
  overdueActionOnly?: boolean
}

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

/** transition 稳定幂等键：相同投递+相同目标状态复用，不同目标状态视为新请求。 */
const transitionIdempotencyKey = (
  applicationId: string,
  targetStatus: ApplicationStatus,
) => `transition:${applicationId}:${targetStatus}`

export async function listApplications(
  params: ApplicationListParams,
): Promise<PageApplication> {
  const res = await apiClient.get<PageApplication>('/applications', {
    params: {
      page: params.page,
      pageSize: params.pageSize,
      status: params.status ?? undefined,
      overdueActionOnly:
        params.overdueActionOnly === undefined
          ? undefined
          : params.overdueActionOnly,
    },
  })
  return res.data
}

export async function getApplicationDetail(
  applicationId: string,
): Promise<ApplicationDetail> {
  const res = await apiClient.get<ApplicationDetail>(
    `/applications/${applicationId}`,
  )
  return res.data
}

export async function createApplication(
  body: ApplicationCreateRequest,
): Promise<Application> {
  const res = await apiClient.post<Application>('/applications', body)
  return res.data
}

export async function updateApplication(
  applicationId: string,
  version: number,
  body: ApplicationUpdateRequest,
): Promise<Application> {
  const res = await apiClient.put<Application>(
    `/applications/${applicationId}`,
    body,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function transitionApplication(
  applicationId: string,
  version: number,
  body: ApplicationTransitionRequest,
): Promise<Application> {
  const res = await apiClient.post<Application>(
    `/applications/${applicationId}/transition`,
    body,
    {
      headers: {
        ...ifMatchHeader(version),
        'Idempotency-Key': transitionIdempotencyKey(
          applicationId,
          body.targetStatus,
        ),
      },
    },
  )
  return res.data
}

export async function listStatusHistory(
  applicationId: string,
): Promise<StatusLog[]> {
  const res = await apiClient.get<StatusLog[]>(
    `/applications/${applicationId}/status-history`,
  )
  return res.data
}
