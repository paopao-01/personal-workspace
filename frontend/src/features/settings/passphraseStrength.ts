/**
 * passphrase 强度评估（纯前端提示）。
 *
 * 在浏览器本地对 passphrase 打分，返回等级与改进建议；不调用任何 API，
 * passphrase 不离开浏览器。评估仅为提示，不禁用提交——后端是唯一强制闸门：
 * 创建备份（POST /backups）与武装调度（POST /backups/schedule/arm）要求 strong（score≥70），
 * score<70（即弱或中）返回 400（与后端 PassphraseStrengthValidator 同一算法/阈值），恢复端点豁免（passphrase 已与备份绑定）。
 *
 * 维度：长度、字符种类（小写/大写/数字/符号）、弱模式扣分（纯重复字符、常见弱口令黑名单、连续重复段）。
 * 阈值：弱 < 40 / 中 40–69 / 强 ≥ 70。达到「强」时不返回改进建议。
 */

export type PassphraseLevel = 'weak' | 'fair' | 'strong'

export interface PassphraseStrength {
  /** 0–100 */
  score: number
  level: PassphraseLevel
  /** 改进建议；达到「强」时为空数组 */
  suggestions: string[]
}

/** 常见弱口令黑名单（小写匹配前缀/整体）。 */
const COMMON_WEAK = [
  'password',
  'passphrase',
  '123456',
  '12345678',
  'qwerty',
  'admin',
  'letmein',
  'welcome',
  'abc123',
  '11111111',
  '00000000',
  '12312312',
  'password123',
  'admin123',
  'root',
  'toor',
  'iloveyou',
  'monkey',
  'dragon',
  'login',
]

const SYMBOLS = new Set('!@#$%^&*()-_=+[]{};:,.<>?/|~`')

export function evaluatePassphraseStrength(passphrase: string): PassphraseStrength {
  const pw = passphrase ?? ''
  if (pw.length === 0) {
    return { score: 0, level: 'weak', suggestions: ['输入 passphrase 以评估强度'] }
  }

  let score = 0

  // 1. 长度（上限 35）
  if (pw.length >= 16) score += 35
  else if (pw.length >= 12) score += 25
  else if (pw.length >= 8) score += 10
  // 长度 < 8 不给长度分（提交本就被拒）

  // 2. 字符种类（上限 45）
  const hasLower = /[a-z]/.test(pw)
  const hasUpper = /[A-Z]/.test(pw)
  const hasDigit = /\d/.test(pw)
  const hasSymbol = [...pw].some((c) => SYMBOLS.has(c))
  if (hasLower) score += 10
  if (hasUpper) score += 10
  if (hasDigit) score += 10
  if (hasSymbol) score += 15

  // 3. 弱模式扣分
  const lowered = pw.toLowerCase()
  const allSame = pw.length > 1 && [...pw].every((c) => c === pw[0])
  const inCommon = COMMON_WEAK.some((w) => lowered === w || lowered.startsWith(w))
  // 连续 3+ 相同字符
  const hasRun = /(.)\1{2,}/.test(pw)

  if (allSame) score -= 25
  if (inCommon) score -= 30
  if (hasRun && !allSame) score -= 10

  score = Math.max(0, Math.min(100, score))

  const level: PassphraseLevel = score >= 70 ? 'strong' : score >= 40 ? 'fair' : 'weak'

  const suggestions: string[] = []
  if (pw.length < 12) suggestions.push('建议至少 12 位')
  if (!hasUpper) suggestions.push('加入大写字母')
  if (!hasDigit) suggestions.push('加入数字')
  if (!hasSymbol) suggestions.push('加入符号（如 !@#$）')
  if (allSame || inCommon || hasRun) suggestions.push('避免常见或重复口令')

  // 达到「强」时不显示建议
  if (level === 'strong') suggestions.length = 0

  return { score, level, suggestions }
}

const LEVEL_LABEL: Record<PassphraseLevel, string> = {
  weak: '弱',
  fair: '中',
  strong: '强',
}

export function passphraseLevelLabel(level: PassphraseLevel): string {
  return LEVEL_LABEL[level]
}
