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
  useBackups,
  useCreateBackup,
} from '@/api/backup/backupApi'

/**
 * 设置页「加密备份」区块：输入 passphrase 手动触发生成加密备份，
 * 列表展示历史备份与下载。passphrase 仅写入不回显，提交后清空。
 * 本切片不提供恢复入口与定时调度。
 */
export function EncryptedBackupSection() {
  const backupsQuery = useBackups()
  const createBackup = useCreateBackup()
  const [passphrase, setPassphrase] = useState('')

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

  if (backupsQuery.isLoading) {
    return <Spinner label="加载备份记录…" />
  }
  if (backupsQuery.error) {
    return <ErrorState error={backupsQuery.error} onRetry={() => backupsQuery.refetch()} />
  }

  const backups = backupsQuery.data ?? []

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
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
        <p className="muted" style={{ marginTop: 12 }}>
          下载得到的是加密文件，需配合 passphrase 在未来恢复切片中解密；当前不提供恢复入口。
        </p>
      </div>
    </section>
  )
}
