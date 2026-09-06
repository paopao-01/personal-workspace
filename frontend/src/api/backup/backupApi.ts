import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import { isApiError, isNetworkError } from '@/api/errors'
import { newIdempotencyKey } from '@/api/idempotency'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type BackupRecord = Schemas['BackupRecord']

const BACKUPS_KEY = ['backups'] as const

/** 生成加密备份：passphrase 仅写入不回显，由拦截器自动注入 Idempotency-Key。 */
export async function createBackup(passphrase: string): Promise<BackupRecord> {
  const res = await apiClient.post<BackupRecord>(
    '/backups',
    { passphrase },
    { headers: { 'Idempotency-Key': newIdempotencyKey() } },
  )
  return res.data
}

export async function listBackups(): Promise<BackupRecord[]> {
  const res = await apiClient.get<BackupRecord[]>('/backups')
  return res.data
}

/** 下载加密备份文件（触发浏览器保存）。 */
export function downloadBackup(id: string, fileName: string): void {
  // 用同源 a 标签触发下载，避免 axios 拉取二进制再构造 blob 的开销
  const link = document.createElement('a')
  link.href = `/api/backups/${id}/download`
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

export function useBackups() {
  return useQuery<BackupRecord[], Error>({
    queryKey: BACKUPS_KEY,
    queryFn: listBackups,
  })
}

export function useCreateBackup() {
  const queryClient = useQueryClient()
  return useMutation<BackupRecord, Error, string>({
    mutationFn: createBackup,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUPS_KEY })
    },
  })
}

/** 将字节数格式化为人类可读大小。 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** 统一错误信息提取。 */
export function backupErrorMessage(caught: Error): string {
  return isApiError(caught) || isNetworkError(caught)
    ? caught.message
    : '备份生成失败，请稍后重试'
}
