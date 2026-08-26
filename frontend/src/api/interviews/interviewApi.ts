import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type Interview = Schemas['Interview']
export type Reminder = Schemas['Reminder']
export type InterviewCreateRequest = Schemas['InterviewCreateRequest']

export async function getInterview(interviewId: string): Promise<Interview> {
  const res = await apiClient.get<Interview>(`/interviews/${interviewId}`)
  return res.data
}

export async function getInterviewReminders(
  interviewId: string,
): Promise<Reminder[]> {
  const res = await apiClient.get<Reminder[]>(
    `/interviews/${interviewId}/reminders`,
  )
  return res.data
}

export async function createInterview(
  body: InterviewCreateRequest,
): Promise<Interview> {
  const res = await apiClient.post<Interview>('/interviews', body)
  return res.data
}
