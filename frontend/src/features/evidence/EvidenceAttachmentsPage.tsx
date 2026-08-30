import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useCreateEvidenceAttachment,
  useDeleteEvidenceAttachment,
  useUpdateEvidenceAttachment,
} from '@/api/evidenceAttachment/useEvidenceAttachmentMutations'
import type { EvidenceAttachment } from '@/api/evidenceAttachment/evidenceAttachmentApi'
import { useEvidenceAttachments } from '@/api/evidenceAttachment/useEvidenceAttachmentQueries'
import { useEvidence } from '@/api/projects/useProjectQueries'
import { pushToast } from '@/components/feedback/toastStore'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { evidenceTypeLabels } from '@/features/projects/projectLabels'

type SourceType = 'LOCAL_PATH' | 'EXTERNAL_URL'

const sourceTypeLabels: Record<SourceType, string> = {
  LOCAL_PATH: '本地路径',
  EXTERNAL_URL: '外部链接',
}

function formatBytes(sizeBytes?: number | null) {
  if (sizeBytes == null) return '未填写大小'
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`
  if (sizeBytes < 1024 * 1024 * 1024) return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(sizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export function EvidenceAttachmentsPage() {
  const evidenceQuery = useEvidence()
  const attachmentQuery = useEvidenceAttachments()
  const createAttachment = useCreateEvidenceAttachment()
  const updateAttachment = useUpdateEvidenceAttachment()
  const deleteAttachment = useDeleteEvidenceAttachment()
  const [selectedEvidenceId, setSelectedEvidenceId] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [sourceType, setSourceType] = useState<SourceType>('LOCAL_PATH')
  const [location, setLocation] = useState('')
  const [mediaType, setMediaType] = useState('')
  const [sizeBytes, setSizeBytes] = useState('')
  const [description, setDescription] = useState('')
  const [editing, setEditing] = useState<EvidenceAttachment | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (evidenceQuery.isLoading || attachmentQuery.isLoading) return <Spinner label="加载附件引用…" />
  if (evidenceQuery.error) return <ErrorState error={evidenceQuery.error} onRetry={() => evidenceQuery.refetch()} />
  if (attachmentQuery.error) return <ErrorState error={attachmentQuery.error} onRetry={() => attachmentQuery.refetch()} />

  const evidence = evidenceQuery.data ?? []
  const attachments = attachmentQuery.data ?? []
  const pending = createAttachment.isPending || updateAttachment.isPending || deleteAttachment.isPending

  const reportError = (caught: Error) => {
    const message = isApiError(caught) || isNetworkError(caught) ? caught.message : '操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const resetForm = () => {
    setEditing(null)
    setSelectedEvidenceId('')
    setDisplayName('')
    setSourceType('LOCAL_PATH')
    setLocation('')
    setMediaType('')
    setSizeBytes('')
    setDescription('')
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const parsedSize = sizeBytes.trim() === '' ? undefined : Number(sizeBytes)
    const body = {
      displayName,
      sourceType,
      location,
      mediaType: mediaType.trim() || undefined,
      sizeBytes: parsedSize,
      description: description.trim() || undefined,
    }
    try {
      if (editing) {
        await updateAttachment.mutateAsync({ attachmentId: editing.id, version: editing.version, body })
        pushToast('附件引用已更新')
      } else {
        if (!selectedEvidenceId) {
          setError('请选择所属证据')
          return
        }
        await createAttachment.mutateAsync({ evidenceId: selectedEvidenceId, body })
        pushToast('附件引用已登记')
      }
      resetForm()
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const startEdit = (attachment: EvidenceAttachment) => {
    setEditing(attachment)
    setSelectedEvidenceId(attachment.evidenceId)
    setDisplayName(attachment.displayName)
    setSourceType(attachment.sourceType)
    setLocation(attachment.location)
    setMediaType(attachment.mediaType ?? '')
    setSizeBytes(attachment.sizeBytes == null ? '' : String(attachment.sizeBytes))
    setDescription(attachment.description ?? '')
  }

  const remove = async (attachment: EvidenceAttachment) => {
    if (!window.confirm(`删除附件引用「${attachment.displayName}」？引用记录将在最近删除保留 30 天，原位置内容不受影响。`)) return
    setError(null)
    try {
      await deleteAttachment.mutateAsync({ attachmentId: attachment.id, version: attachment.version })
      if (editing?.id === attachment.id) resetForm()
      pushToast('附件引用已进入最近删除')
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">附件引用库</h1>
          <p className="page-subtitle">集中维护证据对应的本地路径和外部链接，支持一条证据关联多条引用。</p>
        </div>
      </div>

      {error ? <div className="conflict-banner"><span>{error}</span></div> : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">{editing ? '编辑附件引用' : '登记附件引用'}</h2>
        </div>
        <div className="card-body">
          <p className="form-hint" style={{ marginTop: 0 }}>
            这里只保存你填写的引用和可选元数据。应用不会读取、扫描、上传、下载或校验路径/链接指向的文件。
          </p>
          <form onSubmit={submit} noValidate>
            <Field label="所属证据" required>
              <Select aria-label="所属证据" value={selectedEvidenceId} onChange={(event) => setSelectedEvidenceId(event.target.value)} disabled={Boolean(editing)} required>
                <option value="">请选择证据</option>
                {evidence.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
              </Select>
            </Field>
            <div className="form-row">
              <Field label="显示名称" required><Input aria-label="显示名称" value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={200} required /></Field>
              <Field label="来源类型" required><Select aria-label="来源类型" value={sourceType} onChange={(event) => setSourceType(event.target.value as SourceType)}><option value="LOCAL_PATH">本地路径</option><option value="EXTERNAL_URL">外部链接</option></Select></Field>
            </div>
            <Field label={sourceType === 'LOCAL_PATH' ? '本地路径' : '外部链接'} required>
              <Input aria-label={sourceType === 'LOCAL_PATH' ? '本地路径' : '外部链接'} value={location} onChange={(event) => setLocation(event.target.value)} maxLength={2000} placeholder={sourceType === 'LOCAL_PATH' ? '例如：D:\\docs\\cache-report.pdf' : '例如：https://example.com/report'} required />
            </Field>
            <div className="form-row">
              <Field label="MIME 类型（可选）"><Input aria-label="MIME 类型（可选）" value={mediaType} onChange={(event) => setMediaType(event.target.value)} maxLength={100} placeholder="例如：application/pdf" /></Field>
              <Field label="大小（字节，可选）"><Input aria-label="大小（字节，可选）" type="number" min="0" max="2199023255552" value={sizeBytes} onChange={(event) => setSizeBytes(event.target.value)} /></Field>
            </div>
            <Field label="说明（可选）"><Textarea aria-label="说明（可选）" value={description} onChange={(event) => setDescription(event.target.value)} rows={3} maxLength={1000} placeholder="例如：面试前重点复习的压测结论" /></Field>
            <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
              <Button variant="primary" type="submit" disabled={pending}>{pending ? '保存中…' : editing ? '保存修改' : '登记引用'}</Button>
              {editing ? <Button type="button" onClick={resetForm} disabled={pending}>取消编辑</Button> : null}
            </div>
          </form>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">已登记引用</h2>
          <span className="muted">{attachments.length} 条</span>
        </div>
        <div className="card-body">
          {attachments.length === 0 ? (
            <EmptyState icon="📎" text={evidence.length === 0 ? <span>请先在<Link to="/projects">项目与证据</Link>中创建证据。</span> : '还没有登记附件引用。'} />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>名称</th><th>所属证据</th><th>来源</th><th>人工元数据</th><th>操作</th></tr></thead>
                <tbody>
                  {attachments.map((attachment) => (
                    <tr key={attachment.id}>
                      <td><strong>{attachment.displayName}</strong>{attachment.description ? <div className="muted">{attachment.description}</div> : null}</td>
                      <td><div>{attachment.evidenceTitle}</div><div className="muted">{(() => { const item = evidence.find((candidate) => candidate.id === attachment.evidenceId); return item ? evidenceTypeLabels[item.type] : '证据' })()}</div></td>
                      <td><div><span className="badge badge-subtle">{sourceTypeLabels[attachment.sourceType]}</span></div><code className="attachment-location">{attachment.location}</code></td>
                      <td><div>{attachment.mediaType ?? '未填写类型'}</div><div className="muted">{formatBytes(attachment.sizeBytes)}</div></td>
                      <td><div className="table-row-actions"><Button size="sm" type="button" onClick={() => startEdit(attachment)} disabled={pending}>编辑</Button><Button size="sm" variant="danger" type="button" onClick={() => remove(attachment)} disabled={pending}>删除</Button></div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
