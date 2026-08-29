import type {
  GapStatus,
  JobDecisionStatus,
  JobStatus,
  RequirementConfirmationStatus,
  RequirementType,
} from '@/api/jobs/jobApi'
import { getDisplayTimeZone } from '@/api/settings/displayTimeZone'

/** UI 文案映射（非类型重复，集中管理）。颜色非传达状态的唯一方式。 */

export const jobDecisionLabel: Record<NonNullable<JobDecisionStatus>, string> = {
  TO_APPLY: '待投递',
  APPLY: '决定投递',
  DEFER: '暂缓',
  IGNORE: '忽略',
}

export const jobDecisionVariant: Record<
  NonNullable<JobDecisionStatus>,
  'primary' | 'success' | 'warning' | 'neutral'
> = {
  TO_APPLY: 'primary',
  APPLY: 'success',
  DEFER: 'warning',
  IGNORE: 'neutral',
}

export const jobStatusLabel: Record<JobStatus, string> = {
  ACTIVE: '活跃',
  ARCHIVED: '已归档',
}

export const requirementTypeLabel: Record<RequirementType, string> = {
  MUST: '必须',
  BONUS: '加分',
  RESPONSIBILITY: '职责',
  EXPERIENCE: '经验',
  DOMAIN: '业务领域',
  TO_CONFIRM: '待确认',
}

export const requirementTypeOrder: RequirementType[] = [
  'MUST',
  'BONUS',
  'EXPERIENCE',
  'RESPONSIBILITY',
  'DOMAIN',
  'TO_CONFIRM',
]

export const confirmationLabel: Record<RequirementConfirmationStatus, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  IGNORED: '已忽略',
}

export const confirmationVariant: Record<
  RequirementConfirmationStatus,
  'neutral' | 'success' | 'subtle'
> = {
  PENDING: 'subtle',
  CONFIRMED: 'success',
  IGNORED: 'neutral',
}

export const gapStatusLabel: Record<GapStatus, string> = {
  SATISFIED_WITH_EVIDENCE: '已满足且有证据',
  SELF_REPORTED_NO_EVIDENCE: '自评满足但缺少证据',
  NOT_MET: '不满足',
  INSUFFICIENT_INFO: '信息不足',
  PENDING_CONFIRMATION: '待确认',
}

export const gapStatusVariant: Record<
  GapStatus,
  'success' | 'warning' | 'danger' | 'info' | 'subtle'
> = {
  SATISFIED_WITH_EVIDENCE: 'success',
  SELF_REPORTED_NO_EVIDENCE: 'warning',
  NOT_MET: 'danger',
  INSUFFICIENT_INFO: 'info',
  PENDING_CONFIRMATION: 'subtle',
}

/** ISO-8601 UTC → 用户设置时区显示（原生 Intl，无新依赖）；未配置时按浏览器本地时区。 */
export function formatDateTime(isoUtc: string | null | undefined): string {
  if (!isoUtc) return '—'
  try {
    const dt = new Date(isoUtc)
    if (Number.isNaN(dt.getTime())) return '—'
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'short',
      timeStyle: 'short',
      timeZone: getDisplayTimeZone(),
    }).format(dt)
  } catch {
    return '—'
  }
}
