import type {
  InputHTMLAttributes,
  TextareaHTMLAttributes,
  SelectHTMLAttributes,
  ReactNode,
} from 'react'

interface FieldProps {
  label?: ReactNode
  required?: boolean
  hint?: ReactNode
  error?: ReactNode
  children: ReactNode
}

export function Field({
  label,
  required,
  hint,
  error,
  children,
}: FieldProps) {
  return (
    <div className="form-field">
      {label ? (
        <label className="form-label">
          {label}
          {required ? <span className="required" aria-hidden="true">*</span> : null}
        </label>
      ) : null}
      {children}
      {hint ? <span className="form-hint">{hint}</span> : null}
      {error ? <span className="field-error-text">{error}</span> : null}
    </div>
  )
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className="input" {...props} />
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className="textarea" {...props} />
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className="select" {...props} />
}
