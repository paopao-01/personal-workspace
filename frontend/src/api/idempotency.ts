/**
 * 生成 Idempotency-Key（8-128 字符）。
 * 使用 crypto.randomUUID()（36 字符，满足约束）。
 * localhost / https 下浏览器均提供 crypto.randomUUID()。
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}
