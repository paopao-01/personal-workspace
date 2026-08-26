import type { ApplicationStatus } from '@/api/applications/applicationApi'

/**
 * 投递状态 UI 文案与颜色映射、合法转换矩阵。
 *
 * - applicationStatusLabel/Variant：8 状态的中文文案与 Badge 颜色（颜色非传达状态的唯一方式）。
 * - ALLOWED_TRANSITIONS：编码 02-state-machines.md §3 转换矩阵，决定 ApplicationStatusSection
 *   显示哪些目标状态按钮。这是业务逻辑映射（非类型重复），手写允许。
 * - transitionTargetLabel：目标状态对应的操作文案。
 *
 * ON_HOLD 不在矩阵中提供普通目标（resume 走 previousActiveStatus 特殊分支）。
 * OFFER/REJECTED/WITHDRAWN 为终止状态，无合法目标。
 */

export const applicationStatusLabel: Record<ApplicationStatus, string> = {
  DRAFT: '草稿',
  APPLIED: '已投递',
  RESUME_PASSED: '简历通过',
  INTERVIEWING: '面试中',
  OFFER: '录用',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回',
  ON_HOLD: '暂停',
}

export const applicationStatusVariant: Record<
  ApplicationStatus,
  'neutral' | 'primary' | 'success' | 'warning' | 'info' | 'danger' | 'subtle'
> = {
  DRAFT: 'subtle',
  APPLIED: 'primary',
  RESUME_PASSED: 'info',
  INTERVIEWING: 'primary',
  OFFER: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
  ON_HOLD: 'warning',
}

/** 当前状态 → 允许的目标状态列表（编码 02-state-machines.md §3 转换矩阵）。 */
export const ALLOWED_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> =
  {
    DRAFT: ['APPLIED', 'WITHDRAWN', 'ON_HOLD'],
    APPLIED: ['RESUME_PASSED', 'REJECTED', 'WITHDRAWN', 'ON_HOLD'],
    RESUME_PASSED: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN', 'ON_HOLD'],
    INTERVIEWING: ['OFFER', 'REJECTED', 'WITHDRAWN', 'ON_HOLD'],
    ON_HOLD: [],
    OFFER: [],
    REJECTED: [],
    WITHDRAWN: [],
  }

/** 目标状态 → 转换操作文案（按钮文字）。 */
export const transitionTargetLabel: Record<ApplicationStatus, string> = {
  DRAFT: '恢复为草稿',
  APPLIED: '提交投递',
  RESUME_PASSED: '简历通过',
  INTERVIEWING: '开始面试',
  OFFER: '发出录用',
  REJECTED: '标记拒绝',
  WITHDRAWN: '撤回投递',
  ON_HOLD: '暂停',
}

/** 是否为活动状态（dashboard / 唯一索引口径一致）。 */
export const ACTIVE_STATUSES: ApplicationStatus[] = [
  'DRAFT',
  'APPLIED',
  'RESUME_PASSED',
  'INTERVIEWING',
  'ON_HOLD',
]

/** 是否为终止状态。 */
export function isTerminalStatus(status: ApplicationStatus): boolean {
  return ALLOWED_TRANSITIONS[status].length === 0 && status !== 'ON_HOLD'
}
