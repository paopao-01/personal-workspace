export function Spinner({ label }: { label?: string }) {
  return (
    <div className="loading-container" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      {label ? <span>{label}</span> : null}
    </div>
  )
}
