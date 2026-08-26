import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createInterview,
  type Interview,
  type InterviewCreateRequest,
} from '@/api/interviews/interviewApi'

export function useCreateInterview() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCreateRequest>({
    mutationFn: createInterview,
    onSuccess: (interview) => {
      queryClient.setQueryData(['interviews', interview.id], interview)
      queryClient.invalidateQueries({
        queryKey: ['applications', interview.applicationId],
      })
      queryClient.invalidateQueries({ queryKey: ['applications'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
