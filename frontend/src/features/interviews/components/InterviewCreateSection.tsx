import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateInterview } from '@/api/interviews/useInterviewMutations'
import type { InterviewCreateRequest } from '@/api/interviews/interviewApi'
import { isApiError, isNetworkError } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'

const defaultTimeZone =
  Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'

interface FormValues {
  roundName: string
  startsAt: string
  eventTimeZone: string
  mode: '' | 'ONLINE' | 'ONSITE' | 'PHONE'
  meetingUrlOrAddress: string
  contact: string
  preparationChecklist: string
  notes: string
}

const emptyValues: FormValues = {
  roundName: '',
  startsAt: '',
  eventTimeZone: defaultTimeZone,
  mode: '',
  meetingUrlOrAddress: '',
  contact: '',
  preparationChecklist: '',
  notes: '',
}

export function InterviewCreateSection({
  applicationId,
  onCancel,
}: {
  applicationId: string
  onCancel: () => void
}) {
  const navigate = useNavigate()
  const createMutation = useCreateInterview()
  const [values, setValues] = useState<FormValues>(emptyValues)
  const [errors, setErrors] = useState<Partial<Record<keyof FormValues, string>>>(
    {},
  )

  const update = (field: keyof FormValues, value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }))
    setErrors((previous) => ({ ...previous, [field]: undefined }))
  }

  const validate = () => {
    const next: Partial<Record<keyof FormValues, string>> = {}
    if (!values.roundName.trim()) next.roundName = '请填写面试轮次'
    if (!values.startsAt) next.startsAt = '请填写开始时间'
    if (!values.eventTimeZone.trim()) next.eventTimeZone = '请填写事件时区'
    if (values.roundName.length > 100) next.roundName = '不超过 100 字'
    if (values.meetingUrlOrAddress.length > 2000)
      next.meetingUrlOrAddress = '不超过 2000 字'
    if (values.contact.length > 200) next.contact = '不超过 200 字'
    if (values.notes.length > 5000) next.notes = '不超过 5000 字'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!validate()) return
    const checklist = values.preparationChecklist
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const body: InterviewCreateRequest = {
      applicationId,
      roundName: values.roundName.trim(),
      startsAt: new Date(values.startsAt).toISOString(),
      eventTimeZone: values.eventTimeZone.trim(),
      mode: values.mode || undefined,
      meetingUrlOrAddress: values.meetingUrlOrAddress.trim() || undefined,
      contact: values.contact.trim() || undefined,
      preparationChecklist: checklist.length > 0 ? checklist : undefined,
      notes: values.notes.trim() || undefined,
    }
    createMutation.mutate(body, {
      onSuccess: (interview) => {
        pushToast('面试已安排，默认提醒已生成')
        navigate(`/interviews/${interview.id}`)
      },
      onError: (error) => {
        if (isApiError(error) || isNetworkError(error)) {
          pushToast(error.message, 'error')
        }
      },
    })
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">安排面试</h2>
      </div>
      <div className="card-body">
        <form onSubmit={submit} noValidate>
          <div className="form-row">
            <Field label="面试轮次" required error={errors.roundName}>
              <Input
                value={values.roundName}
                onChange={(event) => update('roundName', event.target.value)}
                maxLength={100}
                placeholder="如：技术一面"
                aria-invalid={Boolean(errors.roundName)}
              />
            </Field>
            <Field label="开始时间" required error={errors.startsAt}>
              <Input
                type="datetime-local"
                value={values.startsAt}
                onChange={(event) => update('startsAt', event.target.value)}
                aria-invalid={Boolean(errors.startsAt)}
              />
            </Field>
          </div>
          <div className="form-row">
            <Field label="事件时区" required error={errors.eventTimeZone}>
              <Input
                value={values.eventTimeZone}
                onChange={(event) => update('eventTimeZone', event.target.value)}
                placeholder="如：Asia/Shanghai"
                aria-invalid={Boolean(errors.eventTimeZone)}
              />
            </Field>
            <Field label="面试方式">
              <Select
                value={values.mode}
                onChange={(event) => update('mode', event.target.value)}
              >
                <option value="">未填写</option>
                <option value="ONLINE">线上</option>
                <option value="ONSITE">现场</option>
                <option value="PHONE">电话</option>
              </Select>
            </Field>
          </div>
          <div className="form-row">
            <Field label="会议链接或地址" error={errors.meetingUrlOrAddress}>
              <Input
                value={values.meetingUrlOrAddress}
                onChange={(event) =>
                  update('meetingUrlOrAddress', event.target.value)
                }
                maxLength={2000}
              />
            </Field>
            <Field label="联系人" error={errors.contact}>
              <Input
                value={values.contact}
                onChange={(event) => update('contact', event.target.value)}
                maxLength={200}
              />
            </Field>
          </div>
          <Field label="准备事项" hint="每行一项">
            <Textarea
              value={values.preparationChecklist}
              onChange={(event) =>
                update('preparationChecklist', event.target.value)
              }
              rows={3}
              placeholder="准备项目案例\n复习岗位要求"
            />
          </Field>
          <Field label="备注" error={errors.notes}>
            <Textarea
              value={values.notes}
              onChange={(event) => update('notes', event.target.value)}
              maxLength={5000}
              rows={3}
            />
          </Field>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="ghost" type="button" onClick={onCancel}>
              取消
            </Button>
            <Button variant="primary" type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? '安排中…' : '安排面试'}
            </Button>
          </div>
        </form>
      </div>
    </section>
  )
}
