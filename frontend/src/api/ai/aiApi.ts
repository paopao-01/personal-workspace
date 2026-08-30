import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type AiProvider = Schemas['AiProvider']
export type AiProviderUpsertRequest = Schemas['AiProviderCreateRequest']
export type AiProviderTestResult = Schemas['AiProviderTestResult']
export type AiJob = Schemas['AiJob']
export type AiJobItem = Schemas['AiJobItem']
export type AiItemPayload = Schemas['AiItemPayload']
export type AiJobType = Schemas['AiJob']['jobType']

const idem = () => ({ 'Idempotency-Key': crypto.randomUUID() })

export async function listAiProviders(): Promise<AiProvider[]> {
  const res = await apiClient.get<AiProvider[]>('/ai-providers')
  return res.data
}

export async function createAiProvider(body: AiProviderUpsertRequest): Promise<AiProvider> {
  const res = await apiClient.post<AiProvider>('/ai-providers', body, { headers: idem() })
  return res.data
}

export async function updateAiProvider(
  providerId: string,
  version: number,
  body: AiProviderUpsertRequest,
): Promise<AiProvider> {
  const res = await apiClient.put<AiProvider>(`/ai-providers/${providerId}`, body, {
    headers: { ...idem(), 'If-Match-Version': String(version) },
  })
  return res.data
}

export async function activateAiProvider(providerId: string): Promise<AiProvider> {
  const res = await apiClient.post<AiProvider>(`/ai-providers/${providerId}/activate`, {}, { headers: idem() })
  return res.data
}

export async function testAiProvider(providerId: string): Promise<AiProviderTestResult> {
  const res = await apiClient.post<AiProviderTestResult>(`/ai-providers/${providerId}/test`, {}, { headers: idem() })
  return res.data
}

export async function createAiJob(jobType: AiJobType, objectId: string, sourceText?: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>('/ai-jobs', { jobType, objectId, sourceText }, { headers: idem() })
  return res.data
}

export async function getAiJob(aiJobId: string): Promise<AiJob> {
  const res = await apiClient.get<AiJob>(`/ai-jobs/${aiJobId}`)
  return res.data
}

export async function createQuestionClassification(questionId: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>(
    `/interview-questions/${questionId}/ai-classification`,
    {},
    { headers: idem() },
  )
  return res.data
}

export async function createAnswerQualityAnalysis(questionId: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>(
    `/interview-questions/${questionId}/ai-answer-analysis`,
    {},
    { headers: idem() },
  )
  return res.data
}

export async function createTaskSuggestion(questionId: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>(
    `/interview-questions/${questionId}/ai-task-suggestion`,
    {},
    { headers: idem() },
  )
  return res.data
}

export type QuestionAiJobType = 'QUESTION_CLASSIFICATION' | 'ANSWER_QUALITY_ANALYSIS' | 'TASK_SUGGESTION'

export async function listAiJobsByQuestion(
  questionId: string,
  jobType: QuestionAiJobType,
): Promise<AiJob[]> {
  const res = await apiClient.get<AiJob[]>(`/interview-questions/${questionId}/ai-jobs`, {
    params: { jobType },
  })
  return res.data
}

export async function listAiJobsByJob(jobId: string): Promise<AiJob[]> {
  const res = await apiClient.get<AiJob[]>(`/jobs/${jobId}/ai-jobs`)
  return res.data
}

export async function retryAiJob(aiJobId: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>(`/ai-jobs/${aiJobId}/retry`, {}, { headers: idem() })
  return res.data
}

export async function cancelAiJob(aiJobId: string): Promise<AiJob> {
  const res = await apiClient.post<AiJob>(`/ai-jobs/${aiJobId}/cancel`, {}, { headers: idem() })
  return res.data
}

export async function acceptAiJobItem(
  itemId: string,
  payload?: AiItemPayload,
  questionVersion?: number,
): Promise<AiJobItem> {
  const res = await apiClient.post<AiJobItem>(
    `/ai-job-items/${itemId}/accept`,
    payload ? { payload } : {},
    {
      headers: {
        ...idem(),
        ...(questionVersion === undefined ? {} : { 'If-Match-Version': String(questionVersion) }),
      },
    },
  )
  return res.data
}

export async function rejectAiJobItem(itemId: string): Promise<AiJobItem> {
  const res = await apiClient.post<AiJobItem>(`/ai-job-items/${itemId}/reject`, {}, { headers: idem() })
  return res.data
}
