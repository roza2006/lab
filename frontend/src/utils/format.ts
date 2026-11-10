/** 时间展示：兼容 ISO 与 `yyyy-MM-dd HH:mm:ss` 两种后端格式 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').replace(/\.\d+/, '').slice(0, 16)
}
