import { useQuery } from '@tanstack/react-query'
import {
  getInterview,
  getInterviewReminders,
  type Interview,
  type Reminder,
} from '@/api/interviews/interviewApi'

export function useInterview(interviewId: string | undefined) {
  return useQuery<Interview>({
    queryKey: ['interviews', interviewId],
    queryFn: () => getInterview(interviewId!),
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
