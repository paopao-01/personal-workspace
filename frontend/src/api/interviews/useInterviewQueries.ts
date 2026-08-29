import { useQuery } from '@tanstack/react-query'
import {
  getInterview,
  getInterviewReminders,
  getPreparationPack,
  listInterviews,
  type InterviewListParams,
  type Interview,
  type InterviewListItem,
  type PreparationPack,
  type Reminder,
} from '@/api/interviews/interviewApi'

export function useInterviewList(params: InterviewListParams) {
  return useQuery<InterviewListItem[]>({
    queryKey: ['interviews', 'list', params],
    queryFn: () => listInterviews(params),
  })
}

export function useInterview(interviewId: string | undefined) {
  return useQuery<Interview>({
    queryKey: ['interviews', interviewId],
    queryFn: () => getInterview(interviewId!),
    enabled: Boolean(interviewId),
  })
}

export function usePreparationPack(interviewId: string | undefined) {
  return useQuery<PreparationPack>({
    queryKey: ['interviews', interviewId, 'preparation'],
    queryFn: () => getPreparationPack(interviewId!),
    enabled: Boolean(interviewId),
  })
}

export function useInterviewReminders(interviewId: string | undefined) {
  return useQuery<Reminder[]>({
    queryKey: ['interviews', interviewId, 'reminders'],
    queryFn: () => getInterviewReminders(interviewId!),
    enabled: Boolean(interviewId),
  })
}
