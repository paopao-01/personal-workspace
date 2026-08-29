import { useMutation } from '@tanstack/react-query'
import { createExport, type DataExport, type ExportFormat } from '@/api/settings/exportApi'

export function useCreateExport() {
  return useMutation<DataExport, Error, ExportFormat>({
    mutationFn: createExport,
  })
}
