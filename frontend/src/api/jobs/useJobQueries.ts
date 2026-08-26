import { useQuery } from '@tanstack/react-query'
import {
  type JobListParams,
  getGapList,
  getJob,
  listJobs,
  listRequirements,
} from '@/api/jobs/jobApi'
import type { Job, JobRequirement, GapItem, PageJob } from '@/api/jobs/jobApi'

/**
 * Query key 约定：
 * ['jobs', params]           —— 岗位列表
 * ['jobs', jobId]            —— 单个岗位
 * ['jobs', jobId, 'requirements']
 * ['jobs', jobId, 'gap-list']
 */

export function useJobList(params: JobListParams) {
  return useQuery<PageJob>({
    queryKey: ['jobs', params],
    queryFn: () => listJobs(params),
    placeholderData: (prev) => prev,
  })
}

export function useJob(jobId: string | undefined) {
  return useQuery<Job>({
    queryKey: ['jobs', jobId],
    queryFn: () => getJob(jobId!),
    enabled: Boolean(jobId),
  })
}

export function useJobRequirements(jobId: string | undefined) {
  return useQuery<JobRequirement[]>({
    queryKey: ['jobs', jobId, 'requirements'],
    queryFn: () => listRequirements(jobId!),
    enabled: Boolean(jobId),
  })
}

export function useGapList(jobId: string | undefined) {
  return useQuery<GapItem[]>({
    queryKey: ['jobs', jobId, 'gap-list'],
    queryFn: () => getGapList(jobId!),
    enabled: Boolean(jobId),
  })
}
