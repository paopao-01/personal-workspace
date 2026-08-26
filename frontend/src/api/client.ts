import axios, {
  type AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import {
  NetworkError,
  emptyBodyError,
  normalizeApiError,
} from '@/api/errors'
import { newIdempotencyKey } from '@/api/idempotency'

/**
 * axios 实例。baseURL '/api' —— 开发环境经 Vite proxy 转发到后端
 * 127.0.0.1:8080；生产由 Spring Boot 静态托管前端构建产物，同源。
 */
export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

const WRITE_METHODS = new Set(['post', 'put', 'delete', 'patch'])

/**
 * 请求拦截器：对写操作自动注入 Idempotency-Key。
 * 调用方已显式设置则不覆盖（支持幂等重试复用同一 key）。
 * 不自动注入 If-Match-Version（需调用方按资源当前版本显式传）。
 */
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const method = (config.method ?? 'get').toLowerCase()
  if (WRITE_METHODS.has(method)) {
    const headers = config.headers
    if (!headers.get('Idempotency-Key')) {
      headers.set('Idempotency-Key', newIdempotencyKey())
    }
  }
  return config
})

/**
 * 响应拦截器错误分支：将后端错误归一化为 ApiError / NetworkError。
 * - 有 JSON body 且含 code → normalizeApiError
 * - 有 response 但 body 为空（如缺 If-Match-Version 的空 400）→ emptyBodyError
 * - 无 response（网络错误）→ NetworkError
 */
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError) => {
    // 无响应：网络层错误
    if (!error.response) {
      return Promise.reject(
        new NetworkError(
          '无法连接本地服务，请确认后端已启动（127.0.0.1:8080）',
        ),
      )
    }

    const { status, data, headers } = error.response

    // 有 JSON body 且形如错误对象 → 标准归一化
    const contentType = String(headers?.['content-type'] ?? '')
    if (
      typeof data === 'object' &&
      data !== null &&
      !Array.isArray(data) &&
      contentType.includes('application/json') &&
      'code' in data
    ) {
      return Promise.reject(normalizeApiError(data, status))
    }

    // 空 body 错误
    return Promise.reject(emptyBodyError(status))
  },
)
