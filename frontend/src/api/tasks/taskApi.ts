import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type LearningTask = Schemas['LearningTask']
export type PageTask = Schemas['PageTask']
export type TaskStatus = Schemas['TaskStatus']
export type TaskSourceType = Schemas['TaskSourceType']
export type TaskPriority = NonNullable<LearningTask['priority']>
export type TaskCreateRequest = Schemas['TaskCreateRequest']
export type TaskUpdateRequest = Schemas['TaskUpdateRequest']
export type TaskTransitionRequest = Schemas['TaskTransitionRequest']
export type CreateTaskFromQuestionRequest = Schemas['CreateTaskFromQuestionRequest']

export interface TaskListParams {
  page?: number
  pageSize?: number
  status?: TaskStatus
  knowledgePointId?: string
  sourceType?: TaskSourceType
  dueAfter?: string
  dueBefore?: string
  jobId?: string
  interviewId?: string
}

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

export async function listTasks(params: TaskListParams): Promise<PageTask> {
  const res = await apiClient.get<PageTask>('/tasks', { params })
  return res.data
}

export async function getTask(taskId: string): Promise<LearningTask> {
  const res = await apiClient.get<LearningTask>(`/tasks/${taskId}`)
  return res.data
}

export async function createTask(body: TaskCreateRequest): Promise<LearningTask> {
  const res = await apiClient.post<LearningTask>('/tasks', body)
  return res.data
}

export async function updateTask(
  taskId: string,
  version: number,
  body: TaskUpdateRequest,
): Promise<LearningTask> {
  const res = await apiClient.put<LearningTask>(`/tasks/${taskId}`, body, {
    headers: ifMatchHeader(version),
  })
  return res.data
}

export async function transitionTask(
  taskId: string,
  version: number,
  body: TaskTransitionRequest,
): Promise<LearningTask> {
  const res = await apiClient.post<LearningTask>(
    `/tasks/${taskId}/transition`,
    body,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function createTaskFromQuestion(
  questionId: string,
  body: CreateTaskFromQuestionRequest,
): Promise<LearningTask> {
  const res = await apiClient.post<LearningTask>(
    `/interview-questions/${questionId}/create-task`,
    body,
  )
  return res.data
}

export type TaskEvidenceAttachRequest = Schemas['TaskEvidenceAttachRequest']
export type EvidenceReference = Schemas['EvidenceReference']

export async function listTaskEvidence(taskId: string): Promise<EvidenceReference[]> {
  const res = await apiClient.get<EvidenceReference[]>(`/tasks/${taskId}/evidence`)
  return res.data
}

export async function attachTaskEvidence(
  taskId: string,
  body: TaskEvidenceAttachRequest,
): Promise<EvidenceReference> {
  const res = await apiClient.post<EvidenceReference>(`/tasks/${taskId}/evidence`, body)
  return res.data
}

export async function detachTaskEvidence(
  taskId: string,
  evidenceId: string,
): Promise<void> {
  await apiClient.delete(`/tasks/${taskId}/evidence/${evidenceId}`)
}
