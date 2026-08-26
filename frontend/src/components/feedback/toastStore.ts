/**
 * Toast 状态管理（模块级单例），与 ToastContainer 组件分离，
 * 避免 react/only-export-components 警告（fast refresh 要求文件只导出组件）。
 */
export type ToastVariant = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  message: string
  variant: ToastVariant
}

let nextId = 1
let toasts: Toast[] = []
let listeners: Array<(toasts: Toast[]) => void> = []

function emit() {
  for (const l of listeners) l(toasts)
}

export function pushToast(message: string, variant: ToastVariant = 'success') {
  const id = nextId++
  toasts = [...toasts, { id, message, variant }]
  emit()
  if (variant !== 'error') {
    setTimeout(() => dismissToast(id), 3000)
  }
}

export function dismissToast(id: number) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

export function subscribe(listener: (toasts: Toast[]) => void): () => void {
  listeners.push(listener)
  listener(toasts)
  return () => {
    listeners = listeners.filter((l) => l !== listener)
  }
}
