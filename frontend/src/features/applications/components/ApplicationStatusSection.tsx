import { useState } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Form'
import { ConflictBanner } from '@/components/feedback/ConflictBanner'
import type {
  ApplicationDetail,
  ApplicationStatus,
} from '@/api/applications/applicationApi'
import { useTransitionApplication } from '@/api/applications/useApplicationMutations'
import {
  ALLOWED_TRANSITIONS,
  applicationStatusLabel,
  applicationStatusVariant,
  transitionTargetLabel,
} from '@/features/applications/applicationStatusLabels'
import {
  isApiError,
  isIdempotencyConflict,
  isIllegalTransition,
  isVersionConflict,
} from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'

/**
 * 区2：当前状态。展示状态 Badge + 合法目标转换按钮 + reason 输入。
 * - ON_HOLD：走 previousActiveStatus 特殊恢复分支（转换矩阵为空）。
 * - OFFER 目标：展示逃生舱 checkbox（allowOfferWithoutCompletedInterview），
 *   因面试模块未实现，无法真正校验 COMPLETED 面试。
 * - 非法转换（422）：ConflictBanner 展示 currentState/targetState/reason（AT-06）。
 */
export function ApplicationStatusSection({
  detail,
}: {
  detail: ApplicationDetail
}) {
  // 用 key 重建内部状态，避免在 effect 中同步 set-state（与 DecisionSection 一致）。
  return (
    <ApplicationStatusSectionInner key={detail.id + ':' + detail.version} detail={detail} />
  )
}

function ApplicationStatusSectionInner({ detail }: { detail: ApplicationDetail }) {
  const [reason, setReason] = useState('')
  const [offerEscape, setOfferEscape] = useState(false)
  const transitionMutation = useTransitionApplication()

  const allowed = ALLOWED_TRANSITIONS[detail.status]
  const onHoldResumeTarget = detail.previousActiveStatus

  const handleTransition = (
    target: ApplicationStatus,
    allowOfferWithoutCompletedInterview = false,
  ) => {
    transitionMutation.mutate(
      {
        applicationId: detail.id,
        version: detail.version,
        body: {
          targetStatus: target,
          reason: reason.trim() || undefined,
          allowOfferWithoutCompletedInterview,
        },
      },
      {
        onSuccess: () => {
          pushToast('状态已更新')
          setReason('')
          setOfferEscape(false)
        },
        onError: (e) => {
          // 非法转换（422）由下方 ConflictBanner 展示 currentState/targetState/reason
          if (isVersionConflict(e)) {
            pushToast('该投递已被修改，请刷新后重试', 'error')
          } else if (isIdempotencyConflict(e)) {
            pushToast('检测到幂等冲突，请勿重复操作', 'error')
          } else if (isApiError(e) && !isIllegalTransition(e)) {
            pushToast(e.message, 'error')
          }
        },
      },
    )
  }

  const illegalApiError =
    transitionMutation.isError &&
    isApiError(transitionMutation.error) &&
    isIllegalTransition(transitionMutation.error)
      ? transitionMutation.error
      : null

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">当前状态</h2>
      </div>
      <div className="card-body">
        <div className="flex-row" style={{ marginBottom: 16, gap: 12 }}>
          <Badge variant={applicationStatusVariant[detail.status]}>
            {applicationStatusLabel[detail.status]}
          </Badge>
          {detail.status === 'ON_HOLD' && detail.previousActiveStatus ? (
            <span className="muted">
              原状态：{applicationStatusLabel[detail.previousActiveStatus]}
            </span>
          ) : null}
        </div>

        {illegalApiError ? (
          <ConflictBanner
            message="非法状态转换"
            detail={
              <span>
                {illegalApiError.currentState ?? detail.status} →{' '}
                {illegalApiError.targetState ?? '目标状态'}
                {illegalApiError.reason ? `（${illegalApiError.reason}）` : ''}
              </span>
            }
          />
        ) : null}

        {allowed.length === 0 && detail.status !== 'ON_HOLD' ? (
          <p className="muted">该投递已处于终止状态，不可再转换。</p>
        ) : null}

        <div className="decision-radios" style={{ gap: 8 }}>
          {detail.status === 'ON_HOLD' ? (
            onHoldResumeTarget ? (
              <Button
                variant="primary"
                size="sm"
                onClick={() => handleTransition(onHoldResumeTarget)}
                disabled={transitionMutation.isPending}
              >
                {`恢复（至${applicationStatusLabel[onHoldResumeTarget]}）`}
              </Button>
            ) : (
              <p className="muted">暂停中，缺少原活动状态，无法恢复。</p>
            )
          ) : (
            allowed.map((target) => {
              const isOffer = target === 'OFFER'
              return (
                <div
                  key={target}
                  style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                >
                  <Button
                    variant={isOffer ? 'primary' : 'ghost'}
                    size="sm"
                    onClick={() =>
                      handleTransition(target, isOffer ? offerEscape : false)
                    }
                    disabled={
                      transitionMutation.isPending ||
                      (isOffer && !offerEscape)
                    }
                  >
                    {transitionTargetLabel[target]}
                  </Button>
                  {isOffer ? (
                    <label
                      className="form-hint"
                      style={{
                        display: 'inline-flex',
                        gap: 4,
                        alignItems: 'center',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={offerEscape}
                        onChange={(e) => setOfferEscape(e.target.checked)}
                      />
                      确认无完成面试记录仍进入 OFFER
                    </label>
                  ) : null}
                </div>
              )
            })
          )}
        </div>

        <div style={{ marginTop: 16 }}>
          <Field label="转换原因" hint="可选">
            <Input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={1000}
              placeholder="如：HR 通知简历通过"
            />
          </Field>
        </div>
      </div>
    </section>
  )
}
