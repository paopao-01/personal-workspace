import { useMutation } from '@tanstack/react-query'
import { createExport, type DataExport } from '@/api/settings/exportApi'

export function useCreateExport() {
  return useMutation<DataExport, Error, void>({
    mutationFn: createExport,
  })
}
