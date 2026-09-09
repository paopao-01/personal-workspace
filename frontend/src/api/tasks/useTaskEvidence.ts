import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  attachTaskEvidence,
  detachTaskEvidence,
  listTaskEvidence,
  type EvidenceReference,
} from '@/api/tasks/taskApi'

export function useTaskEvidence(taskId: string | undefined) {
  return useQuery<EvidenceReference[]>({
    queryKey: ['tasks', taskId, 'evidence'],
    queryFn: () => listTaskEvidence(taskId!),
    enabled: Boolean(taskId),
  })
}

export function useAttachTaskEvidence() {
  const queryClient = useQueryClient()
  return useMutation<EvidenceReference, Error, { taskId: string; evidenceId: string }>({
    mutationFn: ({ taskId, evidenceId }) => attachTaskEvidence(taskId, { evidenceId }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}

export function useDetachTaskEvidence() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, { taskId: string; evidenceId: string }>({
    mutationFn: ({ taskId, evidenceId }) => detachTaskEvidence(taskId, evidenceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] }),
  })
}
