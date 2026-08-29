
export const evidenceStatusLabels: Record<string, string> = {
  NO_EVIDENCE: '无证据',
  WEAK: '证据薄弱',
  VALID: '证据有效',
}

export function evidenceStatusLabel(status: string | null | undefined): string {
  if (!status) return '未评估'
  return evidenceStatusLabels[status] ?? status
}

export function selfLevelLabel(level: number | null | undefined): string {
  return level === null || level === undefined ? '未评估' : `${level} / 5`
}
