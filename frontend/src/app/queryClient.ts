import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '@/api/errors'

/**
 * QueryClient 配置：
 * - staleTime 30s：本地单用户，数据足够新鲜
 * - refetchOnWindowFocus false：避免误触发 refetch 覆盖编辑中表单
 * - queries.retry：业务错误不重试，网络错误重试 2 次
 * - mutations.retry 0：写操作不自动重试（幂等键允许安全重试，由 UI 显式触发）
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
      retry: (count, error) => {
        if (isApiError(error)) return false
        return count < 2
      },
    },
    mutations: {
      retry: 0,
    },
  },
})
