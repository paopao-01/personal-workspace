import { useQuery } from '@tanstack/react-query'
import { getDashboardOverview } from '@/api/dashboard/dashboardApi'
import type { DashboardOverview } from '@/api/dashboard/dashboardApi'

/**
 * Query key 约定：
 * ['dashboard'] —— 首页工作台聚合视图
 *
 * staleTime 由 queryClient 默认控制；application mutation 成功后会
 * invalidate(['dashboard']) 以刷新行动项。
 */

export function useDashboardOverview() {
  return useQuery<DashboardOverview>({
    queryKey: ['dashboard'],
    queryFn: getDashboardOverview,
  })
}
