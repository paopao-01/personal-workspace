import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { ConflictBanner } from '@/components/feedback/ConflictBanner'
import {
  useUpdateRequirement,
} from '@/api/jobs/useJobMutations'
import type {
  GapStatus,
  JobRequirement,
  RequirementType,
} from '@/api/jobs/jobApi'
import {
  confirmationLabel,
  confirmationVariant,
  gapStatusLabel,
  requirementTypeLabel,
} from '@/features/jobs/statusLabels'
import {
  isApiError,
  isVersionConflict,
} from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'

interface Props {
  requirement: JobRequirement
  jobId: string
}

const GAP_OPTIONS: GapStatus[] = [
  'SATISFIED_WITH_EVIDENCE',
  'SELF_REPORTED_NO_EVIDENCE',
  'NOT_MET',
  'INSUFFICIENT_INFO',
  'PENDING_CONFIRMATION',
]

const TYPE_OPTIONS: RequirementType[] = [
  'MUST',
  'BONUS',
  'RESPONSIBILITY',
  'EXPERIENCE',
  'DOMAIN',
  'TO_CONFIRM',
]

export function RequirementRow({ requirement: req, jobId }: Props) {
  const [editing, setEditing] = useState(false)
  const [rawText, setRawText] = useState(req.rawText)
  const [normalizedName, setNormalizedName] = useState(req.normalizedName ?? '')
  const [type, setType] = useState<RequirementType>(req.type)
  const [proficiencyText, setProficiencyText] = useState(
    req.proficiencyText ?? '',
  )

  const updateMutation = useUpdateRequirement()

  const reset = () => {
    setRawText(req.rawText)
    setNormalizedName(req.normalizedName ?? '')
    setType(req.type)
    setProficiencyText(req.proficiencyText ?? '')
  }

  const doUpdate = (
    body: Parameters<typeof updateMutation.mutate>[0]['body'],
    successMsg: string,
  ) => {
    updateMutation.mutate(
      { requirementId: req.id, jobId, version: req.version, body },
      {
        onSuccess: () => {
          pushToast(successMsg)
          setEditing(false)
          reset()
        },
        onError: (e) => {
          if (isApiError(e) && !isVersionConflict(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  const handleConfirm = () =>
    doUpdate({ confirmationStatus: 'CONFIRMED' }, '要求已确认')

  const handleIgnore = () =>
    doUpdate({ confirmationStatus: 'IGNORED' }, '要求已忽略')

  const handleRestore = () =>
    doUpdate({ confirmationStatus: 'PENDING' }, '要求已恢复待确认')

  const handleSaveEdit = () => {
    doUpdate(
      {
        confirmationStatus: req.confirmationStatus,
        rawText,
        normalizedName: normalizedName || undefined,
        type,
        proficiencyText: proficiencyText || undefined,
      },
      '要求已更新',
    )
  }

  return (
    <div className="requirement-row">
      <div className="requirement-main">
        {editing ? (
          <div className="inline-edit">
            <Field label="原始片段">
              <Textarea
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
                rows={2}
                maxLength={2000}
              />
            </Field>
            <div className="form-row">
              <Field label="标准技能名">
                <Input
                  value={normalizedName}
                  onChange={(e) => setNormalizedName(e.target.value)}
                  maxLength={200}
                />
              </Field>
              <Field label="类型">
                <Select
                  value={type}
                  onChange={(e) => setType(e.target.value as RequirementType)}
                >
                  {TYPE_OPTIONS.map((t) => (
                    <option key={t} value={t}>
                      {requirementTypeLabel[t]}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <Field label="熟练度描述" hint="可选">
              <Input
                value={proficiencyText}
                onChange={(e) => setProficiencyText(e.target.value)}
                maxLength={500}
              />
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setEditing(false)
                  reset()
                }}
              >
                取消
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={handleSaveEdit}
                disabled={updateMutation.isPending}
              >
                保存
              </Button>
            </div>
          </div>
        ) : (
          <>
            <span className="requirement-raw">{req.rawText}</span>
            <div className="requirement-meta">
              <Badge variant="neutral">{requirementTypeLabel[req.type]}</Badge>
              <Badge variant={confirmationVariant[req.confirmationStatus]}>
                {confirmationLabel[req.confirmationStatus]}
              </Badge>
              {req.normalizedName ? (
                <span className="muted">· {req.normalizedName}</span>
              ) : null}
              {req.proficiencyText ? (
                <span className="muted">· {req.proficiencyText}</span>
              ) : null}
              <span className="muted">· 来源 {req.source}</span>
            </div>
          </>
        )}
      </div>

      {!editing ? (
        <div className="requirement-actions">
          {req.confirmationStatus === 'PENDING' ? (
            <>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleConfirm}
                disabled={updateMutation.isPending}
              >
                确认
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleIgnore}
                disabled={updateMutation.isPending}
              >
                忽略
              </Button>
            </>
          ) : null}
          {req.confirmationStatus === 'IGNORED' ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRestore}
              disabled={updateMutation.isPending}
            >
              恢复
            </Button>
          ) : null}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setEditing(true)}
            disabled={updateMutation.isPending}
          >
            编辑
          </Button>
        </div>
      ) : null}

      {updateMutation.isError && isVersionConflict(updateMutation.error) ? (
        <div style={{ width: '100%', marginTop: 8 }}>
          <ConflictBanner
            message="该要求已被修改"
            actionLabel="刷新列表"
            onAction={() => window.location.reload()}
          />
        </div>
      ) : null}
    </div>
  )
}

export { TYPE_OPTIONS, GAP_OPTIONS }
export function ManualMatchInline({
  jobId,
  req,
}: {
  jobId: string
  req: JobRequirement
}) {
  const [manualMatchStatus, setManualMatchStatus] = useState<GapStatus | ''>('')
  const [reason, setReason] = useState('')
  const updateMutation = useUpdateRequirement()

  const submit = () => {
    updateMutation.mutate(
      {
        requirementId: req.id,
        jobId,
        version: req.version,
        body: {
          confirmationStatus: req.confirmationStatus,
          manualMatchStatus: manualMatchStatus || undefined,
          reason: reason || undefined,
        },
      },
      {
        onSuccess: () => {
          pushToast('匹配状态已修正')
          setManualMatchStatus('')
          setReason('')
        },
        onError: (e) => {
          if (isApiError(e) && !isVersionConflict(e)) pushToast(e.message, 'error')
        },
      },
    )
  }

  return (
    <div className="inline-edit">
      <div className="form-row">
        <Field label="人工修正匹配状态" hint="AT-04：覆盖自动计算的差距结论">
          <Select
            value={manualMatchStatus}
            onChange={(e) => setManualMatchStatus(e.target.value as GapStatus | '')}
          >
            <option value="">不修正</option>
            {GAP_OPTIONS.map((g) => (
              <option key={g} value={g}>
                {gapStatusLabel[g]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="修正原因" hint="可选，与 manualMatchStatus 配合">
          <Input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={1000}
          />
        </Field>
      </div>
      <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
        <Button
          variant="ghost"
          size="sm"
          onClick={submit}
          disabled={!manualMatchStatus || updateMutation.isPending}
        >
          保存修正
        </Button>
      </div>
    </div>
  )
}
