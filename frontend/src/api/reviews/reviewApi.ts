import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type InterviewReview = Schemas['InterviewReview']
export type InterviewQuestion = Schemas['InterviewQuestion']
export type KnowledgePoint = Schemas['KnowledgePoint']
export type WeakKnowledgePoint = Schemas['WeakKnowledgePoint']
export type ReviewUpsertRequest = Schemas['ReviewUpsertRequest']
export type QuestionCreateRequest = Schemas['QuestionCreateRequest']
export type QuestionUpdateRequest = Schemas['QuestionUpdateRequest']
export type KnowledgePointCreateRequest = Schemas['KnowledgePointCreateRequest']
export type AnswerStatus = Schemas['AnswerStatus']
export type ReviewAnalysis = Schemas['ReviewAnalysis']

const ifMatchHeader = (version: number | undefined) =>
  version === undefined ? undefined : { 'If-Match-Version': String(version) }

export async function getInterviewReview(
  interviewId: string,
): Promise<InterviewReview> {
  const res = await apiClient.get<InterviewReview>(
    `/interviews/${interviewId}/review`,
  )
  return res.data
}

export async function saveReviewDraft(
  interviewId: string,
  version: number | undefined,
  body: ReviewUpsertRequest,
): Promise<InterviewReview> {
  const res = await apiClient.put<InterviewReview>(
    `/interviews/${interviewId}/review`,
    body,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function createReviewQuestion(
  reviewId: string,
  body: QuestionCreateRequest,
): Promise<InterviewQuestion> {
  const res = await apiClient.post<InterviewQuestion>(
    `/reviews/${reviewId}/questions`,
    body,
  )
  return res.data
}

export async function updateReviewQuestion(
  questionId: string,
  version: number,
  body: QuestionUpdateRequest,
): Promise<InterviewQuestion> {
  const res = await apiClient.put<InterviewQuestion>(
    `/interview-questions/${questionId}`,
    body,
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function listKnowledgePoints(query?: string): Promise<KnowledgePoint[]> {
  const res = await apiClient.get<KnowledgePoint[]>('/knowledge-points', {
    params: query ? { query } : undefined,
  })
  return res.data
}

export async function createKnowledgePoint(
  body: KnowledgePointCreateRequest,
): Promise<KnowledgePoint> {
  const res = await apiClient.post<KnowledgePoint>('/knowledge-points', body)
  return res.data
}

export async function getWeakKnowledgePoints(params?: {
  from?: string
  to?: string
  jobId?: string
}): Promise<WeakKnowledgePoint[]> {
  const res = await apiClient.get<WeakKnowledgePoint[]>('/knowledge-points/weak', {
    params,
  })
  return res.data
}

export async function getReviewAnalysis(params?: {
  from?: string
  to?: string
  jobId?: string
  compareFrom?: string
  compareTo?: string
}): Promise<ReviewAnalysis> {
  const res = await apiClient.get<ReviewAnalysis>('/reviews/analysis', {
    params,
  })
  return res.data
}

export async function completeReview(
  reviewId: string,
  version: number,
): Promise<InterviewReview> {
  const res = await apiClient.post<InterviewReview>(
    `/reviews/${reviewId}/complete`,
    {},
    { headers: ifMatchHeader(version) },
  )
  return res.data
}

export async function reopenReview(
  reviewId: string,
  version: number,
): Promise<InterviewReview> {
  const res = await apiClient.post<InterviewReview>(
    `/reviews/${reviewId}/reopen`,
    {},
    { headers: ifMatchHeader(version) },
  )
  return res.data
}
