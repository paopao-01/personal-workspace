import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  mergeRequirements,
  type JobCreateRequest,
  type JobUpdateRequest,
  type RequirementUpdateRequest,
  archiveJob,
  createJob,
  extractRequirements,
  restoreJob,
  updateJob,
  updateRequirement,
} from '@/api/jobs/jobApi'
import type { Job, JobRequirement } from '@/api/jobs/jobApi'

/**
 * Job 模块 mutation hooks。
 *
 * If-Match-Version 回填策略：调用方从 useJob / useJobRequirements 的 data.version
 * 读取当前版本，作为 mutation 入参 version 传入；jobApi 函数写入 header。
 *
 * VERSION_CONFLICT 处理：onError 不自动覆盖用户输入，由 UI 决定是否刷新。
 */

export function useCreateJob() {
  const qc = useQueryClient()
  return useMutation<Job, Error, JobCreateRequest>({
    mutationFn: (body) => createJob(body),
    onSuccess: (job) => {
      qc.invalidateQueries({ queryKey: ['jobs'] })
      qc.setQueryData(['jobs', job.id], job)
    },
  })
}

export interface UpdateJobArgs {
  jobId: string
  version: number
  body: JobUpdateRequest
}

export function useUpdateJob() {
  const qc = useQueryClient()
  return useMutation<Job, Error, UpdateJobArgs>({
    mutationFn: ({ jobId, version, body }) => updateJob(jobId, version, body),
    onSuccess: (job, vars) => {
      qc.setQueryData(['jobs', vars.jobId], job)
      qc.invalidateQueries({ queryKey: ['jobs'] })
      // JD 变更可能触发要求回退，刷新 requirements 与 gap-list
      qc.invalidateQueries({ queryKey: ['jobs', vars.jobId, 'requirements'] })
      qc.invalidateQueries({ queryKey: ['jobs', vars.jobId, 'gap-list'] })
    },
  })
}

export function useArchiveJob() {
  const qc = useQueryClient()
  return useMutation<Job, Error, { jobId: string; version: number }>({
    mutationFn: ({ jobId, version }) => archiveJob(jobId, version),
    onSuccess: (job, vars) => {
      qc.setQueryData(['jobs', vars.jobId], job)
      qc.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export function useRestoreJob() {
  const qc = useQueryClient()
  return useMutation<Job, Error, { jobId: string; version: number }>({
    mutationFn: ({ jobId, version }) => restoreJob(jobId, version),
    onSuccess: (job, vars) => {
      qc.setQueryData(['jobs', vars.jobId], job)
      qc.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export function useExtractRequirements() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (jobId: string) => extractRequirements(jobId),
    onSuccess: (_data, jobId) => {
      qc.invalidateQueries({ queryKey: ['jobs', jobId, 'requirements'] })
      qc.invalidateQueries({ queryKey: ['jobs', jobId, 'gap-list'] })
    },
  })
}

export interface UpdateRequirementArgs {
  requirementId: string
  jobId: string
  version: number
  body: RequirementUpdateRequest
}

export function useMergeRequirements() {
  const qc = useQueryClient()
  return useMutation<JobRequirement, Error, { jobId: string; targetId: string; sourceIds: string[] }>({
    mutationFn: ({ targetId, sourceIds }) => mergeRequirements(targetId, sourceIds),
    onSuccess: (target, vars) => {
      qc.setQueryData<JobRequirement[]>(
        ['jobs', vars.jobId, 'requirements'],
        (prev) => prev?.map((r) => (r.id === target.id ? target : r)) ?? prev,
      )
    },
  })
}

export function useUpdateRequirement() {
  const qc = useQueryClient()
  return useMutation<JobRequirement, Error, UpdateRequirementArgs>({
    mutationFn: ({ requirementId, version, body }) =>
      updateRequirement(requirementId, version, body),
    onSuccess: (updated, vars) => {
      // 局部更新 requirements query，避免整列重拉
      qc.setQueryData<JobRequirement[]>(
        ['jobs', vars.jobId, 'requirements'],
        (prev) =>
          prev?.map((r) => (r.id === updated.id ? updated : r)) ?? prev,
      )
      // 差距状态变化（含 manualMatchStatus）需刷新 gap-list
      qc.invalidateQueries({ queryKey: ['jobs', vars.jobId, 'gap-list'] })
    },
  })
}
