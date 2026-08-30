import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  acceptAiJobItem,
  activateAiProvider,
  cancelAiJob,
  createAiJob,
  createAiProvider,
  getAiJob,
  listAiJobsByJob,
  listAiProviders,
  rejectAiJobItem,
  retryAiJob,
  testAiProvider,
  updateAiProvider,
  type AiItemPayload,
  type AiJob,
  type AiJobItem,
  type AiProvider,
  type AiProviderTestResult,
  type AiProviderUpsertRequest,
} from '@/api/ai/aiApi'

export function useAiProviders() {
  return useQuery<AiProvider[]>({
    queryKey: ['ai-providers'],
    queryFn: listAiProviders,
  })
}

export function useAiJobsByJob(jobId: string | undefined) {
  return useQuery<AiJob[]>({
    queryKey: ['ai-jobs', 'job', jobId],
    queryFn: () => listAiJobsByJob(jobId!),
    enabled: Boolean(jobId),
  })
}

export function useSingleAiJob(aiJobId: string | undefined) {
  return useQuery<AiJob>({
    queryKey: ['ai-jobs', 'single', aiJobId],
    queryFn: () => getAiJob(aiJobId!),
    enabled: Boolean(aiJobId),
  })
}

export function useCreateAiProvider() {
  const queryClient = useQueryClient()
  return useMutation<AiProvider, Error, AiProviderUpsertRequest>({
    mutationFn: createAiProvider,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-providers'] }),
  })
}

export function useUpdateAiProvider() {
  const queryClient = useQueryClient()
  return useMutation<AiProvider, Error, { providerId: string; version: number; body: AiProviderUpsertRequest }>({
    mutationFn: ({ providerId, version, body }) => updateAiProvider(providerId, version, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-providers'] }),
  })
}

export function useActivateAiProvider() {
  const queryClient = useQueryClient()
  return useMutation<AiProvider, Error, string>({
    mutationFn: activateAiProvider,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-providers'] }),
  })
}

export function useTestAiProvider() {
  return useMutation<AiProviderTestResult, Error, string>({
    mutationFn: testAiProvider,
  })
}

export function useCreateAiJob() {
  const queryClient = useQueryClient()
  return useMutation<AiJob, Error, { jobType: 'JD_EXTRACTION' | 'RESUME_DRAFT'; objectId: string; sourceText?: string }>({
    mutationFn: ({ jobType, objectId, sourceText }) => createAiJob(jobType, objectId, sourceText),
    onSuccess: (job) => queryClient.invalidateQueries({ queryKey: ['ai-jobs', 'job', job.objectId] }),
  })
}

export function useRetryAiJob() {
  const queryClient = useQueryClient()
  return useMutation<AiJob, Error, string>({
    mutationFn: retryAiJob,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-jobs'] }),
  })
}

export function useCancelAiJob() {
  const queryClient = useQueryClient()
  return useMutation<AiJob, Error, string>({
    mutationFn: cancelAiJob,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-jobs'] }),
  })
}

export function useAcceptAiJobItem() {
  const queryClient = useQueryClient()
  return useMutation<AiJobItem, Error, { itemId: string; payload?: AiItemPayload }>({
    mutationFn: ({ itemId, payload }) => acceptAiJobItem(itemId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-jobs'] })
      queryClient.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export function useRejectAiJobItem() {
  const queryClient = useQueryClient()
  return useMutation<AiJobItem, Error, string>({
    mutationFn: rejectAiJobItem,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ai-jobs'] }),
  })
}
