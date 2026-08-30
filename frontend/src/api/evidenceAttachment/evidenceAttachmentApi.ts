import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type EvidenceAttachment = Schemas['EvidenceAttachment']
export type EvidenceAttachmentCreateRequest = Schemas['EvidenceAttachmentCreateRequest']

const ifMatchHeader = (version: number) => ({
  'If-Match-Version': String(version),
})

export async function listEvidenceAttachments(evidenceId?: string): Promise<EvidenceAttachment[]> {
  const res = await apiClient.get<EvidenceAttachment[]>('/evidence-attachments', {
    params: evidenceId ? { evidenceId } : undefined,
  })
  return res.data
}

export async function createEvidenceAttachment(
  evidenceId: string,
  body: EvidenceAttachmentCreateRequest,
): Promise<EvidenceAttachment> {
  const res = await apiClient.post<EvidenceAttachment>(`/evidence/${evidenceId}/attachments`, body)
  return res.data
}

export async function updateEvidenceAttachment(
  attachmentId: string,
  version: number,
  body: EvidenceAttachmentCreateRequest,
): Promise<EvidenceAttachment> {
  const res = await apiClient.put<EvidenceAttachment>(`/evidence-attachments/${attachmentId}`, body, {
    headers: ifMatchHeader(version),
  })
  return res.data
}

export async function deleteEvidenceAttachment(attachmentId: string, version: number): Promise<void> {
  await apiClient.delete(`/evidence-attachments/${attachmentId}`, { headers: ifMatchHeader(version) })
}
