/**
 * 用户设置的显示时区（IANA 名称）。应用启动后由 AppLayout 从 GET /api/settings 同步；
 * 未加载完成时为 undefined，按浏览器本地时区显示。所有时间展示工具在格式化时读取此值。
 */
let configuredTimeZone: string | undefined

export function setDisplayTimeZone(timeZone: string | null | undefined): void {
  configuredTimeZone = timeZone && timeZone.trim() ? timeZone.trim() : undefined
}

export function getDisplayTimeZone(): string | undefined {
  return configuredTimeZone
}
