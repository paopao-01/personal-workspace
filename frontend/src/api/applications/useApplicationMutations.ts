import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  type Application,
  type ApplicationCreateRequest,
  type ApplicationDetail,
  type ApplicationTransitionRequest,
  type ApplicationUpdateRequest,
  createApplication,
  transitionApplication,
  updateApplication,
} from '@/api/applications/applicationApi'

/**
 * Application 模块 mutation hooks。
 *
 * If-Match-Version 回填策略：调用方从 useApplicationDetail 的 data.version
 * 读取当前版本，作为 mutation 入参 version 传入；applicationApi 函数写入 header。
 *
 * 局部缓存策略：update/transition 成功后先 setQueryData 合并 application 字段
 * 到 ApplicationDetail 缓存（立即反映 status 变化），再 invalidate 详情以拉取
 * 最新的 statusHistory（transition 会新增历史记录）与 dashboard（行动项变更）。
 * create 后 invalidate 列表与 dashboard（新投递可能产生行动项）。
 */

export function useCreateApplication() {
  const qc = useQueryClient()
  return useMutation<Application, Error, ApplicationCreateRequest>({
    mutationFn: (body) => createApplication(body),
    onSuccess: (app) => {
      qc.invalidateQueries({ queryKey: ['applications'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      // 不做 setQueryData：新 id 在缓存中尚不存在，由详情页首次加载建立
      void app
    },
  })
}

export interface UpdateApplicationArgs {
  applicationId: string
  version: number
  body: ApplicationUpdateRequest
}

export function useUpdateApplication() {
  const qc = useQueryClient()
  return useMutation<Application, Error, UpdateApplicationArgs>({
    mutationFn: ({ applicationId, version, body }) =>
      updateApplication(applicationId, version, body),
    onSuccess: (app, vars) => {
      // 局部合并 application 字段到 detail（job/statusHistory/interviews 保留）
      qc.setQueryData<ApplicationDetail>(
        ['applications', vars.applicationId],
        (prev) => (prev ? { ...prev, ...app } : prev),
      )
      qc.invalidateQueries({ queryKey: ['applications'] })
      // 下一步行动变化可能影响 dashboard 行动项
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export interface TransitionApplicationArgs {
  applicationId: string
  version: number
  body: ApplicationTransitionRequest
}

export function useTransitionApplication() {
  const qc = useQueryClient()
  return useMutation<Application, Error, TransitionApplicationArgs>({
    mutationFn: ({ applicationId, version, body }) =>
      transitionApplication(applicationId, version, body),
    onSuccess: (app, vars) => {
      // 先局部合并以立即反映 status 变化
      qc.setQueryData<ApplicationDetail>(
        ['applications', vars.applicationId],
        (prev) => (prev ? { ...prev, ...app } : prev),
      )
      // transition 新增 status_log，需 refetch 详情拿完整时间线
      qc.invalidateQueries({ queryKey: ['applications', vars.applicationId] })
      qc.invalidateQueries({ queryKey: ['applications'] })
      // 状态/行动变化影响 dashboard 行动项
      qc.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
