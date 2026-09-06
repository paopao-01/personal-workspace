import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import { isApiError, isNetworkError } from '@/api/errors'
import { newIdempotencyKey } from '@/api/idempotency'
import type { components } from '@/api/generated/types'

type Schemas = components['schemas']
export type BackupRecord = Schemas['BackupRecord']
export type RestoreReport = Schemas['ImportResultReport']
export type BackupSchedule = Schemas['BackupSchedule']

const BACKUPS_KEY = ['backups'] as const
const BACKUP_SCHEDULE_KEY = ['backup-schedule'] as const

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

/**
 * 删除加密备份记录：物理删除记录行与落盘 .enc 文件，不可恢复，不进入最近删除。
 * 携带 X-Confirm-Permanent-Delete 确认头防误删；passphrase 不参与删除验证。
 * Idempotency-Key 由拦截器自动注入，支持安全重试。
 */
export async function deleteBackup(id: string): Promise<void> {
  await apiClient.delete(`/backups/${id}`, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
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

/** 删除加密备份；成功后刷新备份列表与调度配置（lastBackupId 可能变化）。 */
export function useDeleteBackup() {
  const queryClient = useQueryClient()
  return useMutation<void, Error, string>({
    mutationFn: deleteBackup,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUPS_KEY })
      void queryClient.invalidateQueries({ queryKey: BACKUP_SCHEDULE_KEY })
    },
  })
}

/**
 * 恢复加密备份：上传 .enc 文件 + passphrase，解密后行级幂等恢复。
 * 用 FormData 让浏览器设置 multipart boundary（勿手动设 Content-Type——
 * apiClient 请求拦截器对写操作默认注入 application/json，会覆盖 multipart boundary，
 * 此处显式删除 Content-Type 让浏览器/XHR 按 FormData 自动生成）。
 * passphrase 仅写入不回显、不持久化。恢复为幂等操作，重复恢复同一备份提示全部重复跳过。
 */
export async function restoreBackup(file: File, passphrase: string): Promise<RestoreReport> {
  const form = new FormData()
  form.append('file', file)
  form.append('passphrase', passphrase)
  const res = await apiClient.post<RestoreReport>('/backups/restore', form, {
    headers: {
      'Idempotency-Key': newIdempotencyKey(),
      // 删除默认注入的 application/json，让浏览器按 FormData 设 multipart/form-data; boundary=...
      'Content-Type': null as unknown as string,
    },
  })
  return res.data
}

export function useRestoreBackup() {
  return useMutation<RestoreReport, Error, { file: File; passphrase: string }>({
    mutationFn: ({ file, passphrase }) => restoreBackup(file, passphrase),
  })
}

/**
 * 按龄批量清理：物理删除早于 olderThanDays 天的全部备份与 .enc 文件。
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。
 * X-Confirm-Permanent-Delete 确认头防误清；Idempotency-Key 支持安全重试。
 */
export async function purgeOldBackups(olderThanDays: number): Promise<BackupPurgeSummary> {
  const res = await apiClient.delete<BackupPurgeSummary>('/backups', {
    params: { olderThanDays },
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  return res.data
}

export type BackupPurgeSummary = Schemas['BackupPurgeSummary']
export type BackupOrphanCleanSummary = Schemas['BackupOrphanCleanSummary']

/** 按龄批量清理；成功后刷新备份列表与调度配置（lastBackupId 可能变化）。 */
export function usePurgeOldBackups() {
  const queryClient = useQueryClient()
  return useMutation<BackupPurgeSummary, Error, number>({
    mutationFn: purgeOldBackups,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUPS_KEY })
      void queryClient.invalidateQueries({ queryKey: BACKUP_SCHEDULE_KEY })
    },
  })
}

/**
 * 按数量保留：保留最近 N 条（created_at DESC），物理删除其余全部备份与 .enc 文件。
 * 复用单条删除联动（删行 + 清文件 + 置空 last_backup_id 软引用）。
 * X-Confirm-Permanent-Delete 确认头防误清；Idempotency-Key 支持安全重试。
 */
export async function keepLastBackups(keepLast: number): Promise<BackupPurgeSummary> {
  const res = await apiClient.delete<BackupPurgeSummary>('/backups', {
    params: { keepLast },
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  return res.data
}

/** 按数量保留清理；成功后刷新备份列表与调度配置（lastBackupId 可能变化）。 */
export function useKeepLastBackups() {
  const queryClient = useQueryClient()
  return useMutation<BackupPurgeSummary, Error, number>({
    mutationFn: keepLastBackups,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUPS_KEY })
      void queryClient.invalidateQueries({ queryKey: BACKUP_SCHEDULE_KEY })
    },
  })
}

/**
 * 孤儿 .enc 文件扫描清理：扫描 backup-dir 下全部 .enc，物理删除无 backup_record 对应的孤儿。
 * 不写记录、不联动 last_backup_id/data_export；passphrase 不参与清理验证。
 * X-Confirm-Permanent-Delete 确认头防误清；Idempotency-Key 由拦截器自动注入，支持安全重试。
 */
export async function cleanOrphanFiles(): Promise<BackupOrphanCleanSummary> {
  const res = await apiClient.post<BackupOrphanCleanSummary>('/backups/orphans/clean', null, {
    headers: { 'X-Confirm-Permanent-Delete': 'true' },
  })
  return res.data
}

/** 孤儿文件清理；成功后刷新备份列表（孤儿清理不影响记录，但文件视图可同步）。 */
export function useCleanOrphanFiles() {
  const queryClient = useQueryClient()
  return useMutation<BackupOrphanCleanSummary, Error, void>({
    mutationFn: cleanOrphanFiles,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUPS_KEY })
    },
  })
}

/**
 * 定时备份调度配置。armed 反映进程内存武装状态，不回显 passphrase。
 * cron 接受 5/6 字段表达式；服务端归一化为 6 字段存储。
 */
export async function getBackupSchedule(): Promise<BackupSchedule> {
  const res = await apiClient.get<BackupSchedule>('/backups/schedule')
  return res.data
}

export function useBackupSchedule() {
  return useQuery<BackupSchedule, Error>({
    queryKey: BACKUP_SCHEDULE_KEY,
    queryFn: getBackupSchedule,
  })
}

export async function updateBackupSchedule(
  cronExpression: string,
  enabled: boolean,
  version: number,
): Promise<BackupSchedule> {
  const res = await apiClient.put<BackupSchedule>(
    '/backups/schedule',
    { cronExpression, enabled, version },
    { headers: { 'Idempotency-Key': newIdempotencyKey(), 'If-Match-Version': String(version) } },
  )
  return res.data
}

export function useUpdateBackupSchedule() {
  const queryClient = useQueryClient()
  return useMutation<BackupSchedule, Error, { cronExpression: string; enabled: boolean; version: number }>({
    mutationFn: ({ cronExpression, enabled, version }) =>
      updateBackupSchedule(cronExpression, enabled, version),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUP_SCHEDULE_KEY })
    },
  })
}

/** 武装调度器：passphrase 仅写入内存（volatile），应用重启后清空，不回显。 */
export async function armBackupSchedule(passphrase: string): Promise<BackupSchedule> {
  const res = await apiClient.post<BackupSchedule>(
    '/backups/schedule/arm',
    { passphrase },
    { headers: { 'Idempotency-Key': newIdempotencyKey() } },
  )
  return res.data
}

export function useArmBackupSchedule() {
  const queryClient = useQueryClient()
  return useMutation<BackupSchedule, Error, string>({
    mutationFn: armBackupSchedule,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: BACKUP_SCHEDULE_KEY })
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
