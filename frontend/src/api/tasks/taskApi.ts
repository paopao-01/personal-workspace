import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type LearningTask = Schemas['LearningTask']
export type PageTask = Schemas['PageTask']
export type TaskStatus = Schemas['TaskStatus']
export type TaskCreateRequest = Schemas['TaskCreateRequest']
export type TaskUpdateRequest = Schemas['TaskUpdateRequest']
export type TaskTransitionRequest = Schemas['TaskTransitionRequest']
export type CreateTaskFromQuestionRequest = Schemas['CreateTaskFromQuestionRequest']

export interface TaskListParams {
  page?: number
  pageSize?: number
  status?: TaskStatus
}

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

export async function listTasks(params: TaskListParams): Promise<PageTask> {
  const res = await apiClient.get<PageTask>('/tasks', { params })
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
