import { useQuery } from '@tanstack/react-query'
import { isNotFound } from '@/api/errors'
import {
  getInterviewReview,
  getReviewAnalysis,
  getWeakKnowledgePoints,
  listKnowledgePoints,
  type InterviewReview,
  type KnowledgePoint,
  type ReviewAnalysis,
  type WeakKnowledgePoint,
} from '@/api/reviews/reviewApi'

export function useInterviewReview(interviewId: string | undefined) {
  return useQuery<InterviewReview | null>({
    queryKey: ['reviews', 'interview', interviewId],
    queryFn: async () => {
      try {
        return await getInterviewReview(interviewId!)
      } catch (error) {
        if (isNotFound(error)) return null
        throw error
      }
    },
    enabled: Boolean(interviewId),
    retry: false,
  })
}

export function useKnowledgePoints(query: string) {
  return useQuery<KnowledgePoint[]>({
    queryKey: ['knowledge-points', query],
    queryFn: () => listKnowledgePoints(query.trim() || undefined),
  })
}

export function useWeakKnowledgePoints(params?: {
  from?: string
  to?: string
  jobId?: string
}) {
  return useQuery<WeakKnowledgePoint[]>({
    queryKey: ['knowledge-points', 'weak', params],
    queryFn: () => getWeakKnowledgePoints(params),
  })
}

export function useReviewAnalysis(params?: {
  from?: string
  to?: string
  jobId?: string
  compareFrom?: string
  compareTo?: string
}) {
  return useQuery<ReviewAnalysis>({
    queryKey: ['reviews', 'analysis', params],
    queryFn: () => getReviewAnalysis(params),
  })
}
