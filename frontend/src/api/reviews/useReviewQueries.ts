import { useQuery } from '@tanstack/react-query'
import { isNotFound } from '@/api/errors'
import {
  getInterviewReview,
  type InterviewReview,
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
