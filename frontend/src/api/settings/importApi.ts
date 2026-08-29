import { apiClient } from '@/api/client'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type ImportValidationReport = Schemas['ImportValidationReport']
export type ImportResultReport = Schemas['ImportResultReport']

/**
 * 数据包为应用导出的标准 JSON，原样作为请求体回传（PRD 9.5）。
 */
export async function validateImportPackage(
  pkg: Record<string, unknown>,
): Promise<ImportValidationReport> {
  const res = await apiClient.post<ImportValidationReport>('/data-imports/validate', pkg)
  return res.data
}

export async function restoreImportPackage(
  pkg: Record<string, unknown>,
): Promise<ImportResultReport> {
  const res = await apiClient.post<ImportResultReport>('/data-imports/restore', pkg, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })
  return res.data
}
