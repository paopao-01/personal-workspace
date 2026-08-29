import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type DataExport = Schemas['DataExport']

export type ExportFormat = 'JSON' | 'CSV'

export async function createExport(format: ExportFormat): Promise<DataExport> {
  const res = await apiClient.post<DataExport>('/data-exports', { format })
  return res.data
}

export async function getExport(exportId: string): Promise<DataExport> {
  const res = await apiClient.get<DataExport>(`/data-exports/${exportId}`)
  return res.data
}
