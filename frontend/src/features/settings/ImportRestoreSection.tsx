import { useState, type ChangeEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  restoreImportPackage,
  validateImportPackage,
  type ImportResultReport,
  type ImportValidationReport,
} from '@/api/settings/importApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Form'

const ISSUE_TYPE_LABELS: Record<string, string> = {
  INVALID_PACKAGE: '数据包无效',
  UNKNOWN_TABLE: '跳过未知表',
  CONFLICT: '冲突（保留现状）',
  MISSING_PARENT: '缺少关联数据',
  ROW_FAILED: '插入失败',
}

function issueTypeLabel(type: string) {
  return ISSUE_TYPE_LABELS[type] ?? type
}

function issueBadgeVariant(type: string) {
  if (type === 'ROW_FAILED' || type === 'INVALID_PACKAGE') return 'danger' as const
  if (type === 'CONFLICT' || type === 'MISSING_PARENT') return 'warning' as const
  return 'neutral' as const
}

export function ImportRestoreSection() {
  const [fileName, setFileName] = useState('')
  const [pkg, setPkg] = useState<Record<string, unknown> | null>(null)
  const [validation, setValidation] = useState<ImportValidationReport | null>(null)
  const [result, setResult] = useState<ImportResultReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const validateMutation = useMutation<ImportValidationReport, Error, Record<string, unknown>>({
    mutationFn: validateImportPackage,
  })
  const restoreMutation = useMutation<ImportResultReport, Error, Record<string, unknown>>({
    mutationFn: restoreImportPackage,
  })
  const pending = validateMutation.isPending || restoreMutation.isPending

  const onFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    setError(null)
    setValidation(null)
    setResult(null)
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      setFileName('')
      setPkg(null)
      return
    }
    setFileName(file.name)
    try {
      const parsed: unknown = JSON.parse(await file.text())
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('文件内容必须是 JSON 对象')
      }
      setPkg(parsed as Record<string, unknown>)
    } catch (caught) {
      setPkg(null)
      const message = caught instanceof Error ? caught.message : String(caught)
      setError(`无法读取数据包「${file.name}」：${message}`)
      pushToast('数据包读取失败', 'error')
    }
  }

  const runValidate = async () => {
    if (!pkg) return
    setError(null)
    setResult(null)
    try {
      const report = await validateMutation.mutateAsync(pkg)
      setValidation(report)
      pushToast(
        report.valid ? '预检完成，可查看恢复预览' : '数据包存在问题，请查看预检结果',
        report.valid ? 'success' : 'error',
      )
    } catch (caught) {
      setError(isApiError(caught) || isNetworkError(caught) ? caught.message : '预检失败，请稍后重试')
    }
  }

  const runRestore = async () => {
    if (!pkg || !validation) return
    const confirmed = window.confirm(
      `确认恢复「${fileName}」？将插入 ${validation.insertableRows} 行；重复、冲突或缺少关联数据的行会跳过，` +
        '不会修改任何已有数据。',
    )
    if (!confirmed) return
    setError(null)
    try {
      const report = await restoreMutation.mutateAsync(pkg)
      setResult(report)
      pushToast(`恢复完成：插入 ${report.inserted} 行，跳过 ${report.skippedIdentical + report.skippedConflict + report.skippedMissingParent} 行`)
    } catch (caught) {
      setError(isApiError(caught) || isNetworkError(caught) ? caught.message : '恢复失败，请稍后重试')
    }
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">数据导入与恢复</h2>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          选择应用导出的标准 JSON 数据包，先进行冲突预检并展示恢复预览（即影响范围），确认后执行恢复。
          恢复只插入本地缺失的行：同键重复、内容冲突或缺少关联数据的行会跳过，不会覆盖或删除任何已有数据。
        </p>
        <Field label="数据包文件">
          <input
            type="file"
            accept=".json,application/json"
            onChange={onFileChange}
            disabled={pending}
          />
        </Field>
        {error ? (
          <div className="conflict-banner">
            <span>{error}</span>
          </div>
        ) : null}
        <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
          <Button
            variant="default"
            type="button"
            disabled={!pkg || pending}
            onClick={runValidate}
          >
            {validateMutation.isPending ? '预检中…' : '预检并预览'}
          </Button>
          <Button
            variant="primary"
            type="button"
            disabled={!validation || !validation.valid || pending}
            onClick={runRestore}
          >
            {restoreMutation.isPending ? '恢复中…' : '确认恢复'}
          </Button>
        </div>

        {validation ? (
          <div className="plain-block" style={{ marginTop: 12 }}>
            <p style={{ margin: 0 }}>
              <Badge variant={validation.valid ? 'success' : 'warning'}>
                {validation.valid ? '预检通过' : '预检发现问题'}
              </Badge>
              <span className="muted" style={{ marginLeft: 8 }}>
                {fileName} · 数据包共 {validation.totalRows} 行，将插入 {validation.insertableRows} 行
              </span>
            </p>
            {validation.tablePreviews.filter((preview) => preview.packageRows > 0).length > 0 ? (
              <dl style={{ margin: '12px 0 0' }}>
                {validation.tablePreviews
                  .filter((preview) => preview.packageRows > 0)
                  .map((preview) => (
                    <div key={preview.tableName} style={{ marginBottom: 4 }}>
                      <dt style={{ fontWeight: 600 }}>{preview.tableName}</dt>
                      <dd style={{ margin: 0 }}>
                        共 {preview.packageRows} 行 · 将插入 {preview.toInsert} · 重复跳过{' '}
                        {preview.duplicateIdentical} · 冲突跳过 {preview.conflict} · 缺少关联跳过{' '}
                        {preview.missingParent}
                      </dd>
                    </div>
                  ))}
              </dl>
            ) : null}
            {validation.issues.length > 0 ? (
              <div style={{ marginTop: 8 }}>
                {validation.issues.slice(0, 20).map((issue, index) => (
                  <p className="muted" style={{ margin: '4px 0 0' }} key={`${issue.type}-${issue.tableName}-${issue.rowId ?? index}`}>
                    <Badge variant={issueBadgeVariant(issue.type)}>{issueTypeLabel(issue.type)}</Badge>{' '}
                    {issue.tableName ? `${issue.tableName}：` : ''}
                    {issue.detail}
                  </p>
                ))}
                {validation.issues.length > 20 ? (
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    ……其余 {validation.issues.length - 20} 条问题详见恢复结果报告。
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}

        {result ? (
          <div className="success-banner" style={{ marginTop: 12, flexDirection: 'column', alignItems: 'stretch' }}>
            <span>
              恢复完成：插入 {result.inserted} 行 · 重复跳过 {result.skippedIdentical} · 冲突跳过{' '}
              {result.skippedConflict} · 缺少关联跳过 {result.skippedMissingParent} · 失败{' '}
              {result.failed}
            </span>
            {result.issues.length > 0 ? (
              <div>
                {result.issues.slice(0, 20).map((issue, index) => (
                  <p className="muted" style={{ margin: '4px 0 0' }} key={`${issue.type}-${issue.tableName}-${issue.rowId ?? index}`}>
                    <Badge variant={issueBadgeVariant(issue.type)}>{issueTypeLabel(issue.type)}</Badge>{' '}
                    {issue.tableName ? `${issue.tableName}：` : ''}
                    {issue.detail}
                  </p>
                ))}
                {result.issues.length > 20 ? (
                  <p className="muted" style={{ margin: '4px 0 0' }}>
                    ……其余 {result.issues.length - 20} 条问题已记录。
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  )
}
