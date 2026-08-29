import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type ProjectCaseSummary = Schemas['ProjectCaseSummary']
export type ProjectCaseCreateRequest = Schemas['ProjectCaseCreateRequest']
export type Evidence = Schemas['Evidence']
export type EvidenceCreateRequest = Schemas['EvidenceCreateRequest']
export type EvidenceType = NonNullable<EvidenceCreateRequest['type']>

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

export async function listProjects(): Promise<ProjectCaseSummary[]> {
  const res = await apiClient.get<ProjectCaseSummary[]>('/projects')
  return res.data
}

export async function createProject(
  body: ProjectCaseCreateRequest,
): Promise<ProjectCaseSummary> {
  const res = await apiClient.post<ProjectCaseSummary>('/projects', body)
  return res.data
}

export async function updateProject(
  projectId: string,
  version: number,
  body: ProjectCaseCreateRequest,
): Promise<ProjectCaseSummary> {
  const res = await apiClient.put<ProjectCaseSummary>(`/projects/${projectId}`, body, {
    headers: ifMatchHeader(version),
  })
  return res.data
}

export async function listEvidence(): Promise<Evidence[]> {
  const res = await apiClient.get<Evidence[]>('/evidence')
  return res.data
}

export async function createEvidence(
  body: EvidenceCreateRequest,
): Promise<Evidence> {
  const res = await apiClient.post<Evidence>('/evidence', body)
  return res.data
}

export async function updateEvidence(
  evidenceId: string,
  version: number,
  body: EvidenceCreateRequest,
): Promise<Evidence> {
  const res = await apiClient.put<Evidence>(`/evidence/${evidenceId}`, body, {
    headers: ifMatchHeader(version),
  })
  return res.data
}

export async function deleteProject(projectId: string, version: number): Promise<void> {
  await apiClient.delete(`/projects/${projectId}`, { headers: ifMatchHeader(version) })
}

export async function deleteEvidence(evidenceId: string, version: number): Promise<void> {
  await apiClient.delete(`/evidence/${evidenceId}`, { headers: ifMatchHeader(version) })
}
