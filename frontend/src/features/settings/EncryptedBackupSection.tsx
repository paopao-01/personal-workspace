import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { formatDateTime } from '@/features/jobs/statusLabels'
import { pushToast } from '@/components/feedback/toastStore'
import {
  backupErrorMessage,
  downloadBackup,
  formatBytes,
  useArmBackupSchedule,
  useBackups,
  useBackupSchedule,
  useCreateBackup,
  useDeleteBackup,
  useRestoreBackup,
  useUpdateBackupSchedule,
} from '@/api/backup/backupApi'

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
  const scheduleQuery = useBackupSchedule()
  const updateSchedule = useUpdateBackupSchedule()
  const armSchedule = useArmBackupSchedule()
  const [passphrase, setPassphrase] = useState('')
  const [restorePassphrase, setRestorePassphrase] = useState('')
  const [restoreFile, setRestoreFile] = useState<File | null>(null)
  const [cron, setCron] = useState('0 3 * * *')
  const [enabled, setEnabled] = useState(false)
  const [armPassphrase, setArmPassphrase] = useState('')
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null)

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
      pushToast(
        `恢复完成：插入 ${report.inserted} 行，重复跳过 ${report.skippedIdentical}，`
        + `冲突 ${report.skippedConflict}，缺父级 ${report.skippedMissingParent}，失败 ${report.failed}`,
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
                </div>
              ))}
            </div>
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
          下载得到的是加密文件，需配合 passphrase 在本区恢复；删除为物理删除，不可恢复。
        </p>
      </div>
    </section>
  )
}
