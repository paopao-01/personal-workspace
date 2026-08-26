import type {
  Application,
  ApplicationCreateRequest,
  ApplicationUpdateRequest,
} from '@/api/applications/applicationApi'

/**
 * 投递表单值与请求体转换。
 *
 * 关键约束（AGENTS.md）：PUT /applications 是全字段覆盖写——未传字段会被后端清空。
 * 因此 ApplicationFormValues 必须包含 ApplicationUpdateRequest 的全部字段，
 * toUpdateRequest 回填全部 8 字段（nextAction/nextActionDueAt/rejectionReason
 * 可空字段空值传 null；channel/resumeVersion/expectedSalary/contact/notes 传 trim 值）。
 *
 * nextActionDueAt 在 API 中为 ISO-8601 UTC（date-time），HTML datetime-local
 * input 需要本地时间无时区格式（YYYY-MM-DDTHH:mm），故需双向转换。
 */

export interface ApplicationFormValues {
  /** 创建用；PUT 不含此字段（appliedAt 不可改）。格式 yyyy-MM-dd */
  appliedAt: string
  channel: string
  resumeVersion: string
  expectedSalary: string
  contact: string
  nextAction: string
  /** datetime-local 本地格式 YYYY-MM-DDTHH:mm */
  nextActionDueAt: string
  rejectionReason: string
  notes: string
}

export const EMPTY_APPLICATION_VALUES: ApplicationFormValues = {
  appliedAt: '',
  channel: '',
  resumeVersion: '',
  expectedSalary: '',
  contact: '',
  nextAction: '',
  nextActionDueAt: '',
  rejectionReason: '',
  notes: '',
}

/** ISO-8601 UTC → datetime-local 本地格式；null/无效返回空串。 */
export function isoToLocalDatetime(
  iso: string | null | undefined,
): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`
}

/** datetime-local 本地格式 → ISO-8601 UTC；空返回 null。 */
export function localDatetimeToIso(local: string): string | null {
  if (!local) return null
  const d = new Date(local)
  if (Number.isNaN(d.getTime())) return null
  return d.toISOString()
}

/** 从 Application/ApplicationDetail 回填表单值。 */
export function appToValues(app: Application): ApplicationFormValues {
  return {
    appliedAt: app.appliedAt ?? '',
    channel: app.channel ?? '',
    resumeVersion: app.resumeVersion ?? '',
    expectedSalary: app.expectedSalary ?? '',
    contact: app.contact ?? '',
    nextAction: app.nextAction ?? '',
    nextActionDueAt: isoToLocalDatetime(app.nextActionDueAt),
    rejectionReason: app.rejectionReason ?? '',
    notes: app.notes ?? '',
  }
}

/** 创建请求体：空可选字段省略；allowDuplicate 当前后端不支持（V1 唯一索引限制），恒为 false。 */
export function toCreateRequest(
  jobId: string,
  v: ApplicationFormValues,
): ApplicationCreateRequest {
  const dueAt = localDatetimeToIso(v.nextActionDueAt)
  return {
    jobId,
    appliedAt: v.appliedAt,
    channel: v.channel.trim(),
    resumeVersion: v.resumeVersion.trim() || undefined,
    expectedSalary: v.expectedSalary.trim() || undefined,
    contact: v.contact.trim() || undefined,
    nextAction: v.nextAction.trim() || undefined,
    nextActionDueAt: dueAt ?? undefined,
    allowDuplicate: false,
    notes: v.notes.trim() || undefined,
  }
}

/** 更新请求体：全字段覆盖写。可空字段空值传 null 以显式清空。 */
export function toUpdateRequest(
  v: ApplicationFormValues,
): ApplicationUpdateRequest {
  return {
    channel: v.channel.trim(),
    resumeVersion: v.resumeVersion.trim(),
    expectedSalary: v.expectedSalary.trim(),
    contact: v.contact.trim(),
    nextAction: v.nextAction.trim() || null,
    nextActionDueAt: localDatetimeToIso(v.nextActionDueAt),
    rejectionReason: v.rejectionReason.trim() || null,
    notes: v.notes.trim(),
  }
}
