import { evaluatePassphraseStrength, passphraseLevelLabel, type PassphraseLevel } from './passphraseStrength'

/**
 * passphrase 强度提示计：纯前端实时评估，passphrase 不离开浏览器。
 * 仅作提示，不阻塞提交（提交仍受 8–256 位长度限制）。passphrase 为空时不渲染。
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
          {suggestions.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
