import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type Interview = Schemas['Interview']
export type InterviewListItem = Schemas['InterviewListItem']
export type PreparationPack = Schemas['PreparationPack']
export type Reminder = Schemas['Reminder']
export type InterviewCreateRequest = Schemas['InterviewCreateRequest']
export type InterviewCompleteRequest = Schemas['InterviewCompleteRequest']
export type InterviewRescheduleRequest = Schemas['InterviewRescheduleRequest']
export type InterviewScheduleStatus = Schemas['InterviewScheduleStatus']

export interface InterviewListParams {
  from?: string
  to?: string
  scheduleStatus?: InterviewScheduleStatus
  applicationStatus?: Schemas['ApplicationStatus']
  mode?: NonNullable<Interview['mode']>
}

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

const commandHeaders = (
  interviewId: string,
  version: number,
  command: string,
) => ({
  ...ifMatchHeader(version),
  'Idempotency-Key': `interview:${interviewId}:${command}:${version}`,
})

export async function listInterviews(
  params: InterviewListParams,
): Promise<InterviewListItem[]> {
  const res = await apiClient.get<InterviewListItem[]>('/interviews', { params })
  return res.data
}

export async function getInterview(interviewId: string): Promise<Interview> {
  const res = await apiClient.get<Interview>(`/interviews/${interviewId}`)
  return res.data
}

export async function getPreparationPack(
  interviewId: string,
): Promise<PreparationPack> {
  const res = await apiClient.get<PreparationPack>(
    `/interviews/${interviewId}/preparation`,
  )
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

export async function rescheduleInterview(
  interviewId: string,
  version: number,
  body: InterviewRescheduleRequest,
): Promise<Interview> {
  const res = await apiClient.post<Interview>(
    `/interviews/${interviewId}/reschedule`,
    body,
    { headers: commandHeaders(interviewId, version, 'reschedule') },
  )
  return res.data
}

export async function completeInterview(
  interviewId: string,
  version: number,
  body: InterviewCompleteRequest,
): Promise<Interview> {
  const res = await apiClient.post<Interview>(
    `/interviews/${interviewId}/complete`,
    body,
    { headers: commandHeaders(interviewId, version, 'complete') },
  )
  return res.data
}

export async function cancelInterview(
  interviewId: string,
  version: number,
): Promise<Interview> {
  const res = await apiClient.post<Interview>(
    `/interviews/${interviewId}/cancel`,
    {},
    { headers: commandHeaders(interviewId, version, 'cancel') },
  )
  return res.data
}

export async function markInterviewNoShow(
  interviewId: string,
  version: number,
): Promise<Interview> {
  const res = await apiClient.post<Interview>(
    `/interviews/${interviewId}/no-show`,
    {},
    { headers: commandHeaders(interviewId, version, 'no-show') },
  )
  return res.data
}

export async function retryReminder(
  reminderId: string,
  version: number,
): Promise<Reminder> {
  const res = await apiClient.post<Reminder>(
    `/reminders/${reminderId}/retry`,
    {},
    {
      headers: {
        'If-Match-Version': String(version),
        'Idempotency-Key': `reminder:${reminderId}:retry:${version}`,
      },
    },
  )
  return res.data
}

export async function deleteInterview(interviewId: string, version: number): Promise<void> {
  await apiClient.delete(`/interviews/${interviewId}`, { headers: ifMatchHeader(version) })
}
