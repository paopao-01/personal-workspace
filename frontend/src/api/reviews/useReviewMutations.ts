import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createReviewQuestion,
  saveReviewDraft,
  type InterviewQuestion,
  type InterviewReview,
  type QuestionCreateRequest,
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
