import type { ReactNode } from 'react'

type Variant = 'neutral' | 'primary' | 'success' | 'warning' | 'info' | 'danger' | 'subtle'

const variantClass: Record<Variant, string> = {
  neutral: 'badge-neutral',
  primary: 'badge-primary',
  success: 'badge-success',
  warning: 'badge-warning',
  info: 'badge-info',
  danger: 'badge-danger',
  subtle: 'badge-subtle',
}

export function Badge({
  children,
  variant = 'neutral',
  title,
}: {
  children: ReactNode
  variant?: Variant
  title?: string
}) {
  return (
    <span className={`badge ${variantClass[variant]}`} title={title}>
      {children}
    </span>
  )
}
