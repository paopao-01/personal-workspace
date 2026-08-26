/**
 * 后端错误响应（对应 OpenAPI Error schema）+ 前端归一化错误类型。
 *
 * 后端实际行为（来自契约核对）：
 * - 标准 ErrorResponse: { code, message, traceId, fieldErrors?, currentState?, targetState?, reason? }
 * - 空 body 错误：archive/restore/PUT 缺 If-Match-Version 时返回空 body 400，无 JSON。
 * - VERSION_CONFLICT 的 reason 字符串含 "currentVersion=N"，currentState/targetState 为 null。
 */

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  status: number
  code: string
  traceId: string
  fieldErrors?: FieldError[]
  currentState?: string | null
  targetState?: string | null
  reason?: string | null

  constructor(opts: {
    status: number
    code: string
    message: string
    traceId?: string
    fieldErrors?: FieldError[]
    currentState?: string | null
    targetState?: string | null
    reason?: string | null
  }) {
    super(opts.message)
    this.name = 'ApiError'
    this.status = opts.status
    this.code = opts.code
    this.traceId = opts.traceId ?? ''
    this.fieldErrors = opts.fieldErrors
    this.currentState = opts.currentState
    this.targetState = opts.targetState
    this.reason = opts.reason
  }
}

/** 网络层错误：无响应（后端未启动 / 端口不通 / 超时）。 */
export class NetworkError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NetworkError'
  }
}

interface RawErrorBody {
  code?: unknown
  message?: unknown
  traceId?: unknown
  fieldErrors?: unknown
  currentState?: unknown
  targetState?: unknown
  reason?: unknown
}

function asString(v: unknown): string | null {
  return typeof v === 'string' ? v : null
}

function asFieldErrors(v: unknown): FieldError[] | undefined {
  if (!Array.isArray(v)) return undefined
  const result: FieldError[] = []
  for (const item of v) {
    if (item && typeof item === 'object') {
      const obj = item as Record<string, unknown>
      const field = asString(obj.field)
      const message = asString(obj.message)
      if (field && message) result.push({ field, message })
    }
  }
  return result.length > 0 ? result : undefined
}

/**
 * 将后端响应体归一化为 ApiError。仅在确认 body 是 JSON 对象时调用。
 */
export function normalizeApiError(body: RawErrorBody, status: number): ApiError {
  return new ApiError({
    status,
    code: asString(body.code) ?? 'UNKNOWN',
    message: asString(body.message) ?? `请求失败（HTTP ${status}）`,
    traceId: asString(body.traceId) ?? undefined,
    fieldErrors: asFieldErrors(body.fieldErrors),
    currentState: asString(body.currentState),
    targetState: asString(body.targetState),
    reason: asString(body.reason),
  })
}

/** 构造空 body 错误（如缺 If-Match-Version 的 400）。 */
export function emptyBodyError(status: number): ApiError {
  return new ApiError({
    status,
    code: status === 404 ? 'NOT_FOUND' : 'VALIDATION_ERROR',
    message:
      status === 404
        ? '资源不存在或不可见'
        : '请求缺少必要头或参数（如 If-Match-Version）',
  })
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError
}

export function isNetworkError(e: unknown): e is NetworkError {
  return e instanceof NetworkError
}

export function isVersionConflict(e: unknown): boolean {
  return isApiError(e) && e.code === 'VERSION_CONFLICT'
}

export function isIllegalTransition(e: unknown): boolean {
  return isApiError(e) && e.code === 'ILLEGAL_STATE_TRANSITION'
}

export function isIdempotencyConflict(e: unknown): boolean {
  return isApiError(e) && e.code === 'IDEMPOTENCY_CONFLICT'
}

export function isNotFound(e: unknown): boolean {
  return isApiError(e) && e.code === 'NOT_FOUND'
}

export function isValidationError(e: unknown): boolean {
  return isApiError(e) && e.code === 'VALIDATION_ERROR'
}
