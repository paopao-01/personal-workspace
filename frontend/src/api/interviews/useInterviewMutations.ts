import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createInterview,
  deleteInterview,
  cancelInterview,
  completeInterview,
  markInterviewNoShow,
  rescheduleInterview,
  retryReminder,
  type Interview,
  type InterviewCompleteRequest,
  type InterviewCreateRequest,
  type InterviewRescheduleRequest,
} from '@/api/interviews/interviewApi'

interface InterviewCommandArgs<TBody = undefined> {
  interviewId: string
  version: number
  body: TBody
}

function invalidateInterviewViews(
  queryClient: ReturnType<typeof useQueryClient>,
  interview: Interview,
) {
  queryClient.setQueryData(['interviews', interview.id], interview)
  queryClient.invalidateQueries({ queryKey: ['interviews', 'list'] })
  queryClient.invalidateQueries({
    queryKey: ['interviews', interview.id, 'reminders'],
  })
  queryClient.invalidateQueries({
    queryKey: ['applications', interview.applicationId],
  })
  queryClient.invalidateQueries({ queryKey: ['applications'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

export function useCreateInterview() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCreateRequest>({
    mutationFn: createInterview,
    onSuccess: (interview) => {
      invalidateInterviewViews(queryClient, interview)
    },
  })
}

export function useRescheduleInterview() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCommandArgs<InterviewRescheduleRequest>>({
    mutationFn: ({ interviewId, version, body }) =>
      rescheduleInterview(interviewId, version, body),
    onSuccess: (interview) => invalidateInterviewViews(queryClient, interview),
  })
}

export function useCompleteInterview() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCommandArgs<InterviewCompleteRequest>>({
    mutationFn: ({ interviewId, version, body }) =>
      completeInterview(interviewId, version, body),
    onSuccess: (interview) => invalidateInterviewViews(queryClient, interview),
  })
}

export function useCancelInterview() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCommandArgs>({
    mutationFn: ({ interviewId, version }) => cancelInterview(interviewId, version),
    onSuccess: (interview) => invalidateInterviewViews(queryClient, interview),
  })
}

export function useMarkInterviewNoShow() {
  const queryClient = useQueryClient()
  return useMutation<Interview, Error, InterviewCommandArgs>({
    mutationFn: ({ interviewId, version }) => markInterviewNoShow(interviewId, version),
    onSuccess: (interview) => invalidateInterviewViews(queryClient, interview),
  })
}

export function useRetryReminder(interviewId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ reminderId, version }: { reminderId: string; version: number }) =>
      retryReminder(reminderId, version),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['interviews', interviewId, 'reminders'],
      })
    },
  })
}

export function useDeleteInterview() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, { interviewId: string; version: number }>({
    mutationFn: ({ interviewId, version }) => deleteInterview(interviewId, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['interviews'] })
      queryClient.invalidateQueries({ queryKey: ['applications'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['trash'] })
    },
  })
}
