import { evaluatePassphraseStrength, passphraseLevelLabel, type PassphraseLevel } from './passphraseStrength'

/**
 * passphrase 强度提示计：纯前端实时评估，passphrase 不离开浏览器。
 * 仅作提示——不禁用提交按钮，后端是唯一强制闸门（创建/武装对 score<40 返回 400）。
 * 弱口令时文案提示「弱口令将无法提交」；passphrase 为空时不渲染。
 */
const LEVEL_VARIANT: Record<PassphraseLevel, { bar: string; text: string; activeCount: number }> = {
  weak: { bar: 'strength-bar-weak', text: 'strength-text-weak', activeCount: 1 },
  fair: { bar: 'strength-bar-fair', text: 'strength-text-fair', activeCount: 2 },
  strong: { bar: 'strength-bar-strong', text: 'strength-text-strong', activeCount: 3 },
}

export function PassphraseStrengthMeter({ passphrase }: { passphrase: string }) {
  if (!passphrase) return null
  const { level, suggestions } = evaluatePassphraseStrength(passphrase)
  const variant = LEVEL_VARIANT[level]

  return (
    <div className="strength-meter" aria-live="polite">
      <div className="flex-row" style={{ gap: 6, alignItems: 'center' }}>
        <div className="strength-bars">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className={`strength-segment ${i < variant.activeCount ? variant.bar : 'strength-segment-idle'}`}
            />
          ))}
        </div>
        <span className={`strength-label ${variant.text}`}>
          强度：{passphraseLevelLabel(level)}
        </span>
      </div>
      {suggestions.length > 0 && (
        <ul className="strength-suggestions">
          {level === 'weak' && <li key="reject-warn">弱口令将无法提交（后端拒绝）</li>}
          {suggestions.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
