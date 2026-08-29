import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  completeReview,
  createKnowledgePoint,
  createReviewQuestion,
  reopenReview,
  saveReviewDraft,
  updateReviewQuestion,
  type InterviewQuestion,
  type InterviewReview,
  type KnowledgePoint,
  type KnowledgePointCreateRequest,
  type QuestionCreateRequest,
  type QuestionUpdateRequest,
  type ReviewUpsertRequest,
} from '@/api/reviews/reviewApi'

export function useSaveReviewDraft() {
  const queryClient = useQueryClient()
  return useMutation<
    InterviewReview,
    Error,
    { interviewId: string; version?: number; body: ReviewUpsertRequest }
  >({
    mutationFn: ({ interviewId, version, body }) =>
      saveReviewDraft(interviewId, version, body),
    onSuccess: (review) => {
      queryClient.setQueryData(['reviews', 'interview', review.interviewId], review)
      queryClient.invalidateQueries({
        queryKey: ['reviews', 'interview', review.interviewId],
      })
    },
  })
}

export function useUpdateReviewQuestion() {
  const queryClient = useQueryClient()
  return useMutation<
    InterviewQuestion,
    Error,
    { questionId: string; interviewId: string; version: number; body: QuestionUpdateRequest }
  >({
    mutationFn: ({ questionId, version, body }) =>
      updateReviewQuestion(questionId, version, body),
    onSuccess: (_question, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['reviews', 'interview', variables.interviewId],
      })
      queryClient.invalidateQueries({ queryKey: ['knowledge-points', 'weak'] })
    },
  })
}

export function useCreateKnowledgePoint() {
  const queryClient = useQueryClient()
  return useMutation<KnowledgePoint, Error, KnowledgePointCreateRequest>({
    mutationFn: (body) => createKnowledgePoint(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['knowledge-points'] })
    },
  })
}

export function useCreateReviewQuestion() {
  const queryClient = useQueryClient()
  return useMutation<
    InterviewQuestion,
    Error,
    { reviewId: string; interviewId: string; body: QuestionCreateRequest }
  >({
    mutationFn: ({ reviewId, body }) => createReviewQuestion(reviewId, body),
    onSuccess: (_question, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['reviews', 'interview', variables.interviewId],
      })
    },
  })
}

export function useCompleteReview() {
  const queryClient = useQueryClient()
  return useMutation<
    InterviewReview,
    Error,
    { reviewId: string; interviewId: string; version: number }
  >({
    mutationFn: ({ reviewId, version }) => completeReview(reviewId, version),
    onSuccess: (review) => {
      queryClient.setQueryData(['reviews', 'interview', review.interviewId], review)
      queryClient.invalidateQueries({
        queryKey: ['reviews', 'interview', review.interviewId],
      })
    },
  })
}

export function useReopenReview() {
  const queryClient = useQueryClient()
  return useMutation<
    InterviewReview,
    Error,
    { reviewId: string; version: number }
  >({
    mutationFn: ({ reviewId, version }) => reopenReview(reviewId, version),
    onSuccess: (review) => {
      queryClient.setQueryData(['reviews', 'interview', review.interviewId], review)
      queryClient.invalidateQueries({
        queryKey: ['reviews', 'interview', review.interviewId],
      })
    },
  })
}
