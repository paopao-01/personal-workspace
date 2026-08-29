import { useQuery } from '@tanstack/react-query'
import {
  listEvidence,
  listProjects,
  type Evidence,
  type ProjectCaseSummary,
} from '@/api/projects/projectApi'

export function useProjects() {
  return useQuery<ProjectCaseSummary[]>({
    queryKey: ['projects'],
    queryFn: listProjects,
  })
}

export function useEvidence() {
  return useQuery<Evidence[]>({
    queryKey: ['evidence'],
    queryFn: listEvidence,
  })
}
