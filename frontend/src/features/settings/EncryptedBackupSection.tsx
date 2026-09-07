import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { Table } from '@/components/ui/Table'
import { formatDateTime } from '@/features/jobs/statusLabels'
import { pushToast } from '@/components/feedback/toastStore'
import {
  backupErrorMessage,
  downloadBackup,
  formatBytes,
  useArmBackupSchedule,
  useBackups,
  useBackupOrphanAudit,
  useBackupSchedule,
  useCleanOrphanFiles,
  useCreateBackup,
  useDeleteBackup,
  useKeepLastBackups,
  usePurgeOldBackups,
  useRestoreBackup,
  useRotateBackupKey,
  useUpdateBackupSchedule,
} from '@/api/backup/backupApi'
import { PassphraseStrengthMeter } from './PassphraseStrengthMeter'

const AUDIT_PAGE_SIZE = 20

/** 从审计 reason 文本（含 freedBytes=N 子串）解析释放字节数，失败回退到 null。 */
function parseFreedBytes(reason: string | undefined): number | null {
  if (!reason) return null
  const match = reason.match(/freedBytes=(\d+)/)
  return match ? Number(match[1]) : null
}

const LAST_RUN_STATUS_LABEL: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED_DISARMED: '跳过（未武装）',
}

/**
 * 设置页「加密备份」区块：输入 passphrase 手动触发生成加密备份，
 * 列表展示历史备份与下载，并提供上传 .enc 文件 + passphrase 的行级幂等恢复。
 * passphrase 仅写入不回显，提交后清空。
 */
export function EncryptedBackupSection() {
  const backupsQuery = useBackups()
  const createBackup = useCreateBackup()
  const restoreBackup = useRestoreBackup()
  const deleteBackup = useDeleteBackup()
  const rotateKey = useRotateBackupKey()
  const scheduleQuery = useBackupSchedule()
  const updateSchedule = useUpdateBackupSchedule()
  const armSchedule = useArmBackupSchedule()
  const purgeOldBackups = usePurgeOldBackups()
  const keepLastBackups = useKeepLastBackups()
  const cleanOrphanFiles = useCleanOrphanFiles()
  const [auditPage, setAuditPage] = useState(1)
  const auditQuery = useBackupOrphanAudit(auditPage, AUDIT_PAGE_SIZE)
  const [passphrase, setPassphrase] = useState('')
  const [restorePassphrase, setRestorePassphrase] = useState('')
  const [restoreFile, setRestoreFile] = useState<File | null>(null)
  const [cron, setCron] = useState('0 3 * * *')
  const [enabled, setEnabled] = useState(false)
  const [armPassphrase, setArmPassphrase] = useState('')
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null)
  const [rotatingId, setRotatingId] = useState<string | null>(null)
  const [rotateOldPass, setRotateOldPass] = useState('')
  const [rotateNewPass, setRotateNewPass] = useState('')
  const [purgeDays, setPurgeDays] = useState('30')
  const [confirmingPurge, setConfirmingPurge] = useState(false)
  const [keepLast, setKeepLast] = useState('5')
  const [confirmingKeepLast, setConfirmingKeepLast] = useState(false)
  const [confirmingOrphanClean, setConfirmingOrphanClean] = useState(false)

  const submit = async () => {
    if (passphrase.trim().length < 8) {
      pushToast('passphrase 至少 8 位', 'error')
      return
    }
    try {
      const created = await createBackup.mutateAsync(passphrase)
      setPassphrase('')
      pushToast(`已生成加密备份 ${created.fileName}（${formatBytes(created.sizeBytes)}）`)
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
    }
  }

  const submitRestore = async () => {
    if (!restoreFile) {
      pushToast('请选择 .enc 备份文件', 'error')
      return
    }
    if (restorePassphrase.trim().length < 8) {
      pushToast('passphrase 至少 8 位', 'error')
      return
    }
    try {
      const report = await restoreBackup.mutateAsync({
        file: restoreFile,
        passphrase: restorePassphrase,
      })
      setRestorePassphrase('')
      setRestoreFile(null)
      const orphan = report.orphanCleanSummary
      const orphanSuffix =
        orphan && orphan.deletedFiles > 0
          ? `｜同时清理 ${orphan.deletedFiles} 个孤儿文件（释放 ${formatBytes(orphan.freedBytes)}`
            + (orphan.skippedFiles > 0 ? `，跳过 ${orphan.skippedFiles} 个非备份文件` : '')
            + '）'
          : ''
      const weakSuffix = report.passphraseResetRecommended
        ? '｜此备份口令未达强，建议重新创建备份时设置更强口令'
        : ''
      pushToast(
        `恢复完成：插入 ${report.inserted} 行，重复跳过 ${report.skippedIdentical}，`
        + `冲突 ${report.skippedConflict}，缺父级 ${report.skippedMissingParent}，失败 ${report.failed}`
        + orphanSuffix
        + weakSuffix,
      )
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setRestorePassphrase('')
    }
  }

  const submitSchedule = async () => {
    const schedule = scheduleQuery.data
    if (!schedule) return
    if (!cron.trim()) {
      pushToast('cron 表达式不能为空', 'error')
      return
    }
    try {
      await updateSchedule.mutateAsync({ cronExpression: cron.trim(), enabled, version: schedule.version })
      pushToast(enabled ? '定时备份已启用' : '定时备份已停用')
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
    }
  }

  const submitArm = async () => {
    if (armPassphrase.trim().length < 8) {
      pushToast('passphrase 至少 8 位', 'error')
      return
    }
    try {
      await armSchedule.mutateAsync(armPassphrase)
      setArmPassphrase('')
      pushToast('调度器已武装，应用重启后需重新武装')
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setArmPassphrase('')
    }
  }

  const submitDelete = async (id: string, fileName: string) => {
    try {
      await deleteBackup.mutateAsync(id)
      setConfirmingDeleteId(null)
      pushToast(`已删除 ${fileName}（不可恢复）`)
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setConfirmingDeleteId(null)
    }
  }

  const submitRotate = async (id: string, fileName: string) => {
    if (rotateOldPass.trim().length < 8 || rotateNewPass.trim().length < 8) {
      pushToast('新旧 passphrase 均至少 8 位', 'error')
      return
    }
    try {
      const updated = await rotateKey.mutateAsync({
        id,
        oldPassphrase: rotateOldPass,
        newPassphrase: rotateNewPass,
      })
      setRotatingId(null)
      setRotateOldPass('')
      setRotateNewPass('')
      pushToast(`已轮换 ${fileName} 的保护口令（${formatBytes(updated.sizeBytes)}）`)
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setRotateOldPass('')
      setRotateNewPass('')
    }
  }

  const submitPurge = async () => {
    const days = Number(purgeDays)
    if (!Number.isInteger(days) || days < 1) {
      pushToast('阈值天数需为 ≥1 的整数', 'error')
      return
    }
    try {
      const summary = await purgeOldBackups.mutateAsync(days)
      setConfirmingPurge(false)
      pushToast(
        `已清理 ${summary.deletedCount} 条备份（${summary.filesCleaned} 个文件`
        + `${summary.lastBackupIdCleared ? '，已置空最近备份引用' : ''}，不可恢复）`,
      )
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setConfirmingPurge(false)
    }
  }

  const submitKeepLast = async () => {
    const n = Number(keepLast)
    if (!Number.isInteger(n) || n < 1) {
      pushToast('保留条数需为 ≥1 的整数', 'error')
      return
    }
    try {
      const summary = await keepLastBackups.mutateAsync(n)
      setConfirmingKeepLast(false)
      pushToast(
        `已保留最近 ${n} 条，清理 ${summary.deletedCount} 条备份（${summary.filesCleaned} 个文件`
        + `${summary.lastBackupIdCleared ? '，已置空最近备份引用' : ''}，不可恢复）`,
      )
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setConfirmingKeepLast(false)
    }
  }

  const submitOrphanClean = async () => {
    try {
      const summary = await cleanOrphanFiles.mutateAsync()
      setConfirmingOrphanClean(false)
      pushToast(
        `已清理 ${summary.deletedFiles} 个孤儿文件（释放 ${formatBytes(summary.freedBytes)}`
        + `${summary.skippedFiles > 0 ? `，跳过 ${summary.skippedFiles} 个非备份文件` : ''}，不可恢复）`,
      )
    } catch (caught) {
      pushToast(backupErrorMessage(caught as Error), 'error')
      setConfirmingOrphanClean(false)
    }
  }

  if (backupsQuery.isLoading) {
    return <Spinner label="加载备份记录…" />
  }
  if (backupsQuery.error) {
    return <ErrorState error={backupsQuery.error} onRetry={() => backupsQuery.refetch()} />
  }

  const backups = backupsQuery.data ?? []
  const schedule = scheduleQuery.data

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">加密备份</h2>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          输入 passphrase 后一键生成加密备份：复用标准数据包导出，用 PBKDF2 派生 AES-256-GCM
          密钥加密落盘。passphrase 仅本次使用、不回显、不存储，请妥善保管以备未来恢复。
        </p>
        <Field label="passphrase" required>
          <Input
            type="password"
            value={passphrase}
            onChange={(event) => setPassphrase(event.target.value)}
            placeholder="至少 8 位"
            maxLength={256}
            aria-label="备份 passphrase"
            autoComplete="new-password"
          />
          <p className="form-hint">用于派生加密密钥；提交后立即清空，服务端不保存。</p>
          <PassphraseStrengthMeter passphrase={passphrase} />
        </Field>
        <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
          <Button
            variant="primary"
            type="button"
            disabled={createBackup.isPending || passphrase.trim().length < 8}
            onClick={submit}
          >
            {createBackup.isPending ? '加密中…' : '立即加密备份'}
          </Button>
        </div>

        <div style={{ marginTop: 16 }}>
          <h3 className="card-subtitle">历史备份</h3>
          {backups.length === 0 ? (
            <EmptyState icon="🔐" text="尚无加密备份记录。" />
          ) : (
            <div>
              {backups.map((record) => (
                <div className="requirement-row" key={record.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{record.fileName}</span>
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      创建于 {formatDateTime(record.createdAt)} · {formatBytes(record.sizeBytes)}
                      {' · 算法 '}
                      {record.algorithm} · {record.pbkdf2Iterations} 次迭代
                    </p>
                  </div>
                  <div className="requirement-actions">
                    <Button
                      size="sm"
                      variant="default"
                      type="button"
                      onClick={() => downloadBackup(record.id, record.fileName)}
                    >
                      下载
                    </Button>
                    <Button
                      size="sm"
                      variant="default"
                      type="button"
                      onClick={() => {
                        setRotatingId(record.id)
                        setRotateOldPass('')
                        setRotateNewPass('')
                      }}
                    >
                      密钥轮换
                    </Button>
                    {confirmingDeleteId === record.id ? (
                      <>
                        <Button
                          size="sm"
                          variant="danger"
                          type="button"
                          disabled={deleteBackup.isPending}
                          onClick={() => submitDelete(record.id, record.fileName)}
                        >
                          {deleteBackup.isPending ? '删除中…' : '确认删除'}
                        </Button>
                        <Button
                          size="sm"
                          variant="default"
                          type="button"
                          disabled={deleteBackup.isPending}
                          onClick={() => setConfirmingDeleteId(null)}
                        >
                          取消
                        </Button>
                      </>
                    ) : (
                      <Button
                        size="sm"
                        variant="danger"
                        type="button"
                        onClick={() => setConfirmingDeleteId(record.id)}
                      >
                        删除
                      </Button>
                    )}
                  </div>
                  {rotatingId === record.id ? (
                    <div className="requirement-row" style={{ width: '100%', marginTop: 8 }}>
                      <div className="requirement-main" style={{ flex: 1 }}>
                        <Field label="旧 passphrase" required>
                          <Input
                            type="password"
                            value={rotateOldPass}
                            onChange={(event) => setRotateOldPass(event.target.value)}
                            placeholder="至少 8 位（当前保护口令）"
                            maxLength={256}
                            aria-label="旧 passphrase"
                            autoComplete="new-password"
                          />
                        </Field>
                        <Field label="新 passphrase" required>
                          <Input
                            type="password"
                            value={rotateNewPass}
                            onChange={(event) => setRotateNewPass(event.target.value)}
                            placeholder="至少 8 位（新保护口令，需达强）"
                            maxLength={256}
                            aria-label="新 passphrase"
                            autoComplete="new-password"
                          />
                          <PassphraseStrengthMeter passphrase={rotateNewPass} />
                        </Field>
                      </div>
                      <div className="requirement-actions">
                        <Button
                          size="sm"
                          variant="primary"
                          type="button"
                          disabled={
                            rotateKey.isPending
                            || rotateOldPass.trim().length < 8
                            || rotateNewPass.trim().length < 8
                          }
                          onClick={() => submitRotate(record.id, record.fileName)}
                        >
                          {rotateKey.isPending ? '轮换中…' : '确认轮换'}
                        </Button>
                        <Button
                          size="sm"
                          variant="default"
                          type="button"
                          disabled={rotateKey.isPending}
                          onClick={() => setRotatingId(null)}
                        >
                          取消
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </div>

        <div style={{ marginTop: 12 }}>
          <h3 className="card-subtitle">按龄批量清理</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            物理删除创建时间早于阈值的全部备份及其 .enc 密文文件，不可恢复，不进入最近删除。
            若被删集合包含最近一次定时备份引用，将置空该软引用。
          </p>
          <Field label="清理阈值（天）" required hint="整数 ≥1，删除创建时间早于 N 天前的全部备份">
            <Input
              type="number"
              min={1}
              value={purgeDays}
              onChange={(event) => setPurgeDays(event.target.value)}
              placeholder="30"
              aria-label="清理阈值天数"
            />
          </Field>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            {confirmingPurge ? (
              <>
                <Button
                  variant="danger"
                  type="button"
                  disabled={purgeOldBackups.isPending}
                  onClick={submitPurge}
                >
                  {purgeOldBackups.isPending ? '清理中…' : `确认清理 ${purgeDays} 天前`}
                </Button>
                <Button
                  variant="default"
                  type="button"
                  disabled={purgeOldBackups.isPending}
                  onClick={() => setConfirmingPurge(false)}
                >
                  取消
                </Button>
              </>
            ) : (
              <Button
                variant="danger"
                type="button"
                onClick={() => setConfirmingPurge(true)}
              >
                清理
              </Button>
            )}
          </div>
        </div>

        <div style={{ marginTop: 12 }}>
          <h3 className="card-subtitle">按数量保留</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            保留最近 N 条备份（按创建时间倒序），物理删除其余全部备份及其 .enc 密文文件，不可恢复，
            不进入最近删除。若被删集合包含最近一次定时备份引用，将置空该软引用。
          </p>
          <Field label="保留条数（N）" required hint="整数 ≥1，保留最近 N 条并删除其余">
            <Input
              type="number"
              min={1}
              value={keepLast}
              onChange={(event) => setKeepLast(event.target.value)}
              placeholder="5"
              aria-label="保留条数"
            />
          </Field>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            {confirmingKeepLast ? (
              <>
                <Button
                  variant="danger"
                  type="button"
                  disabled={keepLastBackups.isPending}
                  onClick={submitKeepLast}
                >
                  {keepLastBackups.isPending ? '清理中…' : `确认保留最近 ${keepLast} 条`}
                </Button>
                <Button
                  variant="default"
                  type="button"
                  disabled={keepLastBackups.isPending}
                  onClick={() => setConfirmingKeepLast(false)}
                >
                  取消
                </Button>
              </>
            ) : (
              <Button
                variant="danger"
                type="button"
                onClick={() => setConfirmingKeepLast(true)}
              >
                保留最近 N 条
              </Button>
            )}
          </div>
        </div>

        <div style={{ marginTop: 12 }}>
          <h3 className="card-subtitle">孤儿文件清理</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            扫描备份目录下无对应记录的孤儿 .enc 密文文件并物理删除。孤儿来源：删除或按龄清理在进程
            崩溃后残留的文件。本操作不删记录、只删无对应记录的 .enc 文件；非备份命名规则的文件会跳过不删。
            不可恢复，不进入最近删除。
          </p>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            {confirmingOrphanClean ? (
              <>
                <Button
                  variant="danger"
                  type="button"
                  disabled={cleanOrphanFiles.isPending}
                  onClick={submitOrphanClean}
                >
                  {cleanOrphanFiles.isPending ? '清理中…' : '确认清理孤儿文件'}
                </Button>
                <Button
                  variant="default"
                  type="button"
                  disabled={cleanOrphanFiles.isPending}
                  onClick={() => setConfirmingOrphanClean(false)}
                >
                  取消
                </Button>
              </>
            ) : (
              <Button
                variant="danger"
                type="button"
                onClick={() => setConfirmingOrphanClean(true)}
              >
                清理孤儿文件
              </Button>
            )}
          </div>
        </div>

        <div style={{ marginTop: 12 }}>
          <h3 className="card-subtitle">孤儿清理审计日志</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            分页查询每次孤儿清理删除的文件记录（仅 BACKUP_ORPHAN_CLEANED，按时间倒序）。审计为
            best-effort：极端情况下文件已删但审计可能缺失。范围仅孤儿清理，不含投递确认或需求变更。
          </p>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            <Button
              variant="default"
              type="button"
              onClick={() => auditQuery.refetch()}
              disabled={auditQuery.isFetching}
            >
              {auditQuery.isFetching ? '刷新中…' : '刷新'}
            </Button>
          </div>
          {auditQuery.isLoading ? (
            <Spinner label="加载审计日志…" />
          ) : auditQuery.error ? (
            <ErrorState error={auditQuery.error} onRetry={() => auditQuery.refetch()} />
          ) : (auditQuery.data?.items ?? []).length === 0 ? (
            <EmptyState text="尚无孤儿清理审计记录" />
          ) : (
            <>
              <Table headers={['时间', '文件 ID', '释放字节', '详情']}>
                {(auditQuery.data?.items ?? []).map((entry) => {
                  const freed = parseFreedBytes(entry.reason)
                  return (
                    <tr key={entry.id}>
                      <td>{formatDateTime(entry.occurredAt)}</td>
                      <td>{entry.resourceId}</td>
                      <td>{freed != null ? formatBytes(freed) : '—'}</td>
                      <td>{entry.reason}</td>
                    </tr>
                  )
                })}
              </Table>
              <div className="pagination">
                <span className="pagination-info">
                  共 {auditQuery.data?.total ?? 0} 条 · 第 {auditPage}/
                  {auditQuery.data?.totalPages ?? 0} 页
                </span>
                <Button
                  type="button"
                  onClick={() => setAuditPage((p) => Math.max(1, p - 1))}
                  disabled={auditPage <= 1}
                >
                  上一页
                </Button>
                <Button
                  type="button"
                  onClick={() =>
                    setAuditPage((p) =>
                      p >= (auditQuery.data?.totalPages ?? 1) ? p : p + 1,
                    )
                  }
                  disabled={auditPage >= (auditQuery.data?.totalPages ?? 1)}
                >
                  下一页
                </Button>
              </div>
            </>
          )}
        </div>

        <div style={{ marginTop: 16 }}>
          <h3 className="card-subtitle">恢复备份</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            选择此前下载的 .enc 备份文件并输入生成时使用的 passphrase，解密后行级幂等恢复：
            只插入缺失行，重复/冲突/缺父级行跳过，不覆盖任何已有数据。数据库丢失后仍可凭文件与
            passphrase 恢复。
          </p>
          <Field label="备份文件" required>
            <input
              type="file"
              accept=".enc,application/octet-stream"
              onChange={(event) => setRestoreFile(event.target.files?.[0] ?? null)}
              aria-label="选择加密备份文件"
            />
            <p className="form-hint">
              {restoreFile ? `已选择 ${restoreFile.name}（${formatBytes(restoreFile.size)}）` : '仅接受 .enc 加密备份文件'}
            </p>
          </Field>
          <Field label="passphrase" required>
            <Input
              type="password"
              value={restorePassphrase}
              onChange={(event) => setRestorePassphrase(event.target.value)}
              placeholder="至少 8 位"
              maxLength={256}
              aria-label="恢复 passphrase"
              autoComplete="new-password"
            />
            <PassphraseStrengthMeter passphrase={restorePassphrase} />
          </Field>
          <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
            <Button
              variant="default"
              type="button"
              disabled={
                restoreBackup.isPending
                || !restoreFile
                || restorePassphrase.trim().length < 8
              }
              onClick={submitRestore}
            >
              {restoreBackup.isPending ? '恢复中…' : '恢复备份'}
            </Button>
          </div>
        </div>

        <div style={{ marginTop: 16 }}>
          <h3 className="card-subtitle">定时备份调度</h3>
          <p className="muted" style={{ marginTop: 0 }}>
            配置 cron 表达式与启用开关，并武装调度器：passphrase 仅存进程内存（应用重启后自动解除武装，
            需重新武装），到点自动复用加密备份生成。未武装时到点记跳过，不生成备份。passphrase 不回显、不存储。
          </p>
          {schedule ? (
            <>
              <Field label="cron 表达式" required hint="5/6 字段，如 0 3 * * * 表示每天 3 点（0 秒）">
                <Input
                  value={cron}
                  onChange={(event) => setCron(event.target.value)}
                  placeholder="0 3 * * *"
                  aria-label="cron 表达式"
                />
              </Field>
              <Field label="启用定时备份">
                <label className="flex-row" style={{ gap: 8, alignItems: 'center' }}>
                  <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(event) => setEnabled(event.target.checked)}
                    aria-label="启用定时备份"
                  />
                  <span>{enabled ? '已启用' : '未启用'}</span>
                </label>
              </Field>
              <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
                <Button
                  variant="default"
                  type="button"
                  disabled={updateSchedule.isPending || !cron.trim()}
                  onClick={submitSchedule}
                >
                  {updateSchedule.isPending ? '保存中…' : '保存调度配置'}
                </Button>
              </div>

              <div className="requirement-row" style={{ marginTop: 12 }}>
                <div className="requirement-main">
                  <span className="requirement-raw">
                    武装状态：{schedule.armed ? '已武装' : '未武装'}
                  </span>
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    {schedule.armed
                      ? '调度器已武装，到点将自动生成加密备份。应用重启后自动解除武装。'
                      : '调度器未武装，请输入 passphrase 武装后才能定时生成备份。'}
                  </p>
                  {schedule.lastRunAt ? (
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      上次运行：{formatDateTime(schedule.lastRunAt)}
                      {' · '}
                      {LAST_RUN_STATUS_LABEL[schedule.lastRunStatus ?? ''] ?? schedule.lastRunStatus}
                      {schedule.lastRunError ? ` · ${schedule.lastRunError}` : ''}
                    </p>
                  ) : null}
                </div>
                <div className="requirement-actions">
                  <Field label="passphrase" required>
                    <Input
                      type="password"
                      value={armPassphrase}
                      onChange={(event) => setArmPassphrase(event.target.value)}
                      placeholder="至少 8 位"
                      maxLength={256}
                      aria-label="武装 passphrase"
                      autoComplete="new-password"
                    />
                    <PassphraseStrengthMeter passphrase={armPassphrase} />
                  </Field>
                  <Button
                    size="sm"
                    variant={schedule.armed ? 'default' : 'primary'}
                    type="button"
                    disabled={armSchedule.isPending || armPassphrase.trim().length < 8}
                    onClick={submitArm}
                  >
                    {armSchedule.isPending ? '武装中…' : '武装调度器'}
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <Spinner label="加载调度配置…" />
          )}
        </div>
        <p className="muted" style={{ marginTop: 12 }}>
          下载得到的是加密文件，需配合 passphrase 在本区恢复；删除、按龄清理、按数量保留与孤儿清理均为物理删除，不可恢复；
          密钥轮换用旧口令解密并以新口令重新加密，备份 id 与数据不变。
        </p>
      </div>
    </section>
  )
}
