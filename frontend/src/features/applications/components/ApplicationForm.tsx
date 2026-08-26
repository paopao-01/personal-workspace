import { useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Form'
import { InlineFieldError } from '@/components/feedback/InlineFieldError'
import type { Application } from '@/api/applications/applicationApi'
import type { FieldError } from '@/api/errors'
import {
  EMPTY_APPLICATION_VALUES,
  appToValues,
  type ApplicationFormValues,
} from '@/features/applications/components/applicationFormValues'

type Mode = 'create' | 'edit'

interface ApplicationFormProps {
  mode: Mode
  application?: Application
  /** 创建模式必填：锁定 jobId（只读展示）。 */
  jobId?: string
  fieldErrors?: FieldError[]
  submitting?: boolean
  onSubmit: (values: ApplicationFormValues) => void
  onCancel: () => void
}

export function ApplicationForm({
  mode,
  application,
  jobId,
  fieldErrors,
  submitting,
  onSubmit,
  onCancel,
}: ApplicationFormProps) {
  const [values, setValues] = useState<ApplicationFormValues>(
    application ? appToValues(application) : EMPTY_APPLICATION_VALUES,
  )
  const [clientErrors, setClientErrors] = useState<
    Partial<Record<keyof ApplicationFormValues, string>>
  >({})

  const update = (field: keyof ApplicationFormValues, value: string) => {
    setValues((v) => ({ ...v, [field]: value }))
    setClientErrors((e) => ({ ...e, [field]: undefined }))
  }

  const validate = (): boolean => {
    const e: Partial<Record<keyof ApplicationFormValues, string>> = {}
    if (!values.appliedAt) e.appliedAt = '请填写投递日期'
    if (!values.channel.trim()) e.channel = '请填写投递渠道'
    else if (values.channel.length > 100) e.channel = '不超过 100 字'
    if (values.resumeVersion.length > 200) e.resumeVersion = '不超过 200 字'
    if (values.expectedSalary.length > 100) e.expectedSalary = '不超过 100 字'
    if (values.contact.length > 200) e.contact = '不超过 200 字'
    if (values.nextAction.length > 500) e.nextAction = '不超过 500 字'
    if (values.rejectionReason.length > 1000)
      e.rejectionReason = '不超过 1000 字'
    if (values.notes.length > 5000) e.notes = '不超过 5000 字'
    setClientErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = (ev: FormEvent) => {
    ev.preventDefault()
    if (!validate()) return
    onSubmit(values)
  }

  const err = (field: keyof ApplicationFormValues): ReactNode =>
    clientErrors[field] ? (
      <span className="field-error-text">{clientErrors[field]}</span>
    ) : (
      <InlineFieldError field={field} fieldErrors={fieldErrors} />
    )

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="form-row">
        <Field label="投递日期" required error={err('appliedAt')}>
          <Input
            type="date"
            value={values.appliedAt}
            onChange={(e) => update('appliedAt', e.target.value)}
            aria-invalid={Boolean(clientErrors.appliedAt)}
          />
        </Field>
        <Field label="投递渠道" required error={err('channel')}>
          <Input
            value={values.channel}
            onChange={(e) => update('channel', e.target.value)}
            maxLength={100}
            aria-invalid={Boolean(clientErrors.channel)}
            placeholder="如：BOSS 直聘 / 内推"
          />
        </Field>
      </div>

      <div className="form-row">
        <Field label="简历版本" error={err('resumeVersion')}>
          <Input
            value={values.resumeVersion}
            onChange={(e) => update('resumeVersion', e.target.value)}
            maxLength={200}
            placeholder="如：简历-v3"
          />
        </Field>
        <Field label="期望薪资" error={err('expectedSalary')}>
          <Input
            value={values.expectedSalary}
            onChange={(e) => update('expectedSalary', e.target.value)}
            maxLength={100}
            placeholder="如：25-40K"
          />
        </Field>
      </div>

      <div className="form-row">
        <Field label="联系人" error={err('contact')}>
          <Input
            value={values.contact}
            onChange={(e) => update('contact', e.target.value)}
            maxLength={200}
            placeholder="可选"
          />
        </Field>
        <Field
          label="下一步行动截止时间"
          hint="本地时间；进入 APPLIED 后建议补充"
          error={err('nextActionDueAt')}
        >
          <Input
            type="datetime-local"
            value={values.nextActionDueAt}
            onChange={(e) => update('nextActionDueAt', e.target.value)}
          />
        </Field>
      </div>

      <Field
        label="下一步行动"
        hint="建议填写具体动作（如：跟进 HR / 准备一面技术问题）"
        error={err('nextAction')}
      >
        <Input
          value={values.nextAction}
          onChange={(e) => update('nextAction', e.target.value)}
          maxLength={500}
          placeholder="可选"
        />
      </Field>

      {mode === 'edit' ? (
        <Field label="拒绝原因" error={err('rejectionReason')}>
          <Textarea
            value={values.rejectionReason}
            onChange={(e) => update('rejectionReason', e.target.value)}
            rows={2}
            maxLength={1000}
            placeholder="可选；标记 REJECTED 时记录原因"
          />
        </Field>
      ) : null}

      <Field label="备注" error={err('notes')}>
        <Textarea
          value={values.notes}
          onChange={(e) => update('notes', e.target.value)}
          rows={3}
          maxLength={5000}
        />
      </Field>

      {mode === 'create' && jobId ? (
        <input type="hidden" name="jobId" value={jobId} readOnly />
      ) : null}

      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
        <Button variant="ghost" type="button" onClick={onCancel}>
          取消
        </Button>
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting
            ? '保存中…'
            : mode === 'create'
              ? '保存投递'
              : '保存修改'}
        </Button>
      </div>
    </form>
  )
}
