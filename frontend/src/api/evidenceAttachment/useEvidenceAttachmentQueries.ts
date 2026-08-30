import { useQuery } from '@tanstack/react-query'
import { listEvidenceAttachments, type EvidenceAttachment } from './evidenceAttachmentApi'

export function useEvidenceAttachments(evidenceId?: string) {
  return useQuery<EvidenceAttachment[]>({
    queryKey: ['evidence-attachments', evidenceId ?? 'all'],
    queryFn: () => listEvidenceAttachments(evidenceId),
  })
}
