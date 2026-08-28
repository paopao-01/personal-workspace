import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type InterviewReview = Schemas['InterviewReview']
export type InterviewQuestion = Schemas['InterviewQuestion']
export type ReviewUpsertRequest = Schemas['ReviewUpsertRequest']
export type QuestionCreateRequest = Schemas['QuestionCreateRequest']
export type AnswerStatus = Schemas['AnswerStatus']

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
