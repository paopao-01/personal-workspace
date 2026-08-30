import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createEvidenceAttachment,
  deleteEvidenceAttachment,
  updateEvidenceAttachment,
  type EvidenceAttachment,
  type EvidenceAttachmentCreateRequest,
} from './evidenceAttachmentApi'

const invalidate = (queryClient: ReturnType<typeof useQueryClient>) => {
  queryClient.invalidateQueries({ queryKey: ['evidence-attachments'] })
  queryClient.invalidateQueries({ queryKey: ['evidence'] })
  queryClient.invalidateQueries({ queryKey: ['trash'] })
}

export function useCreateEvidenceAttachment() {
  const queryClient = useQueryClient()
  return useMutation<EvidenceAttachment, Error, { evidenceId: string; body: EvidenceAttachmentCreateRequest }>({
    mutationFn: ({ evidenceId, body }) => createEvidenceAttachment(evidenceId, body),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useUpdateEvidenceAttachment() {
  const queryClient = useQueryClient()
  return useMutation<
    EvidenceAttachment,
    Error,
    { attachmentId: string; version: number; body: EvidenceAttachmentCreateRequest }
  >({
    mutationFn: ({ attachmentId, version, body }) => updateEvidenceAttachment(attachmentId, version, body),
    onSuccess: () => invalidate(queryClient),
  })
}

export function useDeleteEvidenceAttachment() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, { attachmentId: string; version: number }>({
    mutationFn: ({ attachmentId, version }) => deleteEvidenceAttachment(attachmentId, version),
    onSuccess: () => invalidate(queryClient),
  })
}
