import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Form'
import { InlineFieldError } from '@/components/feedback/InlineFieldError'
import type { Job } from '@/api/jobs/jobApi'
import type { FieldError } from '@/api/errors'
import {
  EMPTY_JOB_VALUES,
  jobToValues,
  type JobFormValues,
} from '@/features/jobs/components/jobFormValues'

type Mode = 'create' | 'edit'

interface JobFormProps {
  mode: Mode
  job?: Job
  fieldErrors?: FieldError[]
  submitting?: boolean
  onSubmit: (values: JobFormValues) => void
  onCancel: () => void
}

export function JobForm({
  mode,
  job,
  fieldErrors,
  submitting,
  onSubmit,
  onCancel,
}: JobFormProps) {
  const [values, setValues] = useState<JobFormValues>(
    job ? jobToValues(job) : EMPTY_JOB_VALUES,
  )
  const [clientErrors, setClientErrors] = useState<
    Partial<Record<keyof JobFormValues, string>>
  >({})

  const update = (field: keyof JobFormValues, value: string) => {
    setValues((v) => ({ ...v, [field]: value }))
    setClientErrors((e) => ({ ...e, [field]: undefined }))
  }

  const validate = (): boolean => {
    const e: Partial<Record<keyof JobFormValues, string>> = {}
    if (!values.companyName.trim()) e.companyName = '请填写公司名称'
    else if (values.companyName.length > 100) e.companyName = '不超过 100 字'
    if (!values.title.trim()) e.title = '请填写岗位名称'
    else if (values.title.length > 150) e.title = '不超过 150 字'
    if (values.jdRawText.trim().length < 20) e.jdRawText = 'JD 原文至少 20 字'
    else if (values.jdRawText.length > 50000) e.jdRawText = 'JD 原文不超过 50000 字'
    if (values.source.length > 100) e.source = '不超过 100 字'
    if (values.sourceUrl && values.sourceUrl.length > 2048)
      e.sourceUrl = '不超过 2048 字'
    if (values.sourceUrl) {
      try {
        new URL(values.sourceUrl)
      } catch {
        e.sourceUrl = '请填写有效的 URL'
      }
    }
    if (values.location.length > 100) e.location = '不超过 100 字'
    if (values.salaryRange.length > 100) e.salaryRange = '不超过 100 字'
    if (values.notes.length > 5000) e.notes = '不超过 5000 字'
    setClientErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = (ev: FormEvent) => {
    ev.preventDefault()
    if (!validate()) return
    onSubmit(values)
  }

  const err = (field: keyof JobFormValues): React.ReactNode =>
    clientErrors[field] ? (
      <span className="field-error-text">{clientErrors[field]}</span>
    ) : (
      <InlineFieldError field={field} fieldErrors={fieldErrors} />
    )

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="form-row">
        <Field label="公司名称" required error={err('companyName')}>
          <Input
            value={values.companyName}
            onChange={(e) => update('companyName', e.target.value)}
            maxLength={100}
            aria-invalid={Boolean(clientErrors.companyName)}
            placeholder="如：云仓科技"
          />
        </Field>
        <Field label="岗位名称" required error={err('title')}>
          <Input
            value={values.title}
            onChange={(e) => update('title', e.target.value)}
            maxLength={150}
            aria-invalid={Boolean(clientErrors.title)}
            placeholder="如：Java 后端开发工程师"
          />
        </Field>
      </div>

      <Field
        label="JD 原文"
        required
        hint="粘贴完整 JD，系统据此提取候选要求（≥20 字）"
        error={err('jdRawText')}
      >
        <Textarea
          value={values.jdRawText}
          onChange={(e) => update('jdRawText', e.target.value)}
          rows={10}
          aria-invalid={Boolean(clientErrors.jdRawText)}
          placeholder="粘贴岗位 JD 原文…"
        />
      </Field>

      <div className="form-row">
        <Field label="来源" error={err('source')}>
          <Input
            value={values.source}
            onChange={(e) => update('source', e.target.value)}
            maxLength={100}
            placeholder="如：招聘网站 / 内推"
          />
        </Field>
        <Field label="来源链接" error={err('sourceUrl')}>
          <Input
            value={values.sourceUrl}
            onChange={(e) => update('sourceUrl', e.target.value)}
            maxLength={2048}
            placeholder="https://…（可选）"
          />
        </Field>
      </div>

      <div className="form-row">
        <Field label="地点" error={err('location')}>
          <Input
            value={values.location}
            onChange={(e) => update('location', e.target.value)}
            maxLength={100}
            placeholder="如：上海"
          />
        </Field>
        <Field label="薪资范围" error={err('salaryRange')}>
          <Input
            value={values.salaryRange}
            onChange={(e) => update('salaryRange', e.target.value)}
            maxLength={100}
            placeholder="如：20-35K·14薪"
          />
        </Field>
      </div>

      <Field label="备注" error={err('notes')}>
        <Textarea
          value={values.notes}
          onChange={(e) => update('notes', e.target.value)}
          rows={3}
          maxLength={5000}
        />
      </Field>

      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
        <Button variant="ghost" type="button" onClick={onCancel}>
          取消
        </Button>
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting ? '保存中…' : mode === 'create' ? '保存岗位' : '保存修改'}
        </Button>
      </div>
    </form>
  )
}
