import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createEvidence,
  createProject,
  deleteEvidence,
  deleteProject,
  updateEvidence,
  updateProject,
  type Evidence,
  type EvidenceCreateRequest,
  type ProjectCaseCreateRequest,
  type ProjectCaseSummary,
} from '@/api/projects/projectApi'

const invalidate = (queryClient: ReturnType<typeof useQueryClient>) => {
  queryClient.invalidateQueries({ queryKey: ['projects'] })
  queryClient.invalidateQueries({ queryKey: ['evidence'] })
  queryClient.invalidateQueries({ queryKey: ['interviews'] })
}

export function useCreateProject() {
  const queryClient = useQueryClient()
  return useMutation<ProjectCaseSummary, Error, ProjectCaseCreateRequest>({
    mutationFn: createProject,
    onSuccess: () => invalidate(queryClient),
  })
}

export function useUpdateProject() {
  const queryClient = useQueryClient()
  return useMutation<
    ProjectCaseSummary,
    Error,
    { projectId: string; version: number; body: ProjectCaseCreateRequest }
  >({
    mutationFn: ({ projectId, version, body }) =>
      updateProject(projectId, version, body),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useCreateEvidence() {
  const queryClient = useQueryClient()
  return useMutation<Evidence, Error, EvidenceCreateRequest>({
    mutationFn: createEvidence,
    onSuccess: () => invalidate(queryClient),
  })
}

export function useUpdateEvidence() {
  const queryClient = useQueryClient()
  return useMutation<
    Evidence,
    Error,
    { evidenceId: string; version: number; body: EvidenceCreateRequest }
  >({
    mutationFn: ({ evidenceId, version, body }) =>
      updateEvidence(evidenceId, version, body),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useDeleteProject() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, { projectId: string; version: number }>({
    mutationFn: ({ projectId, version }) => deleteProject(projectId, version),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useDeleteEvidence() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, { evidenceId: string; version: number }>({
    mutationFn: ({ evidenceId, version }) => deleteEvidence(evidenceId, version),
    onSuccess: () => invalidate(queryClient),
  })
}
