import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

/**
 * Dashboard 模块 API 调用函数。类型来自 OpenAPI 生成产物，禁止手写枚举。
 *
 * 注意：后端 DashboardOverview.upcomingInterviews / weakKnowledgePoints 当前
 * 返回空数组（面试/复盘模块未实现），前端按空数组处理，不访问元素属性。
 */

type Schemas = components['schemas']
export type DashboardOverview = Schemas['DashboardOverview']
export type ActionItem = Schemas['ActionItem']
export type SourceRef = Schemas['SourceRef']

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const res = await apiClient.get<DashboardOverview>('/dashboard')
  return res.data
}
