/**
 * 本文件集中定义 Bug 状态、优先级的展示映射与通用格式化，避免各视图重复维护文案。
 */
import type { BugPriority, BugStatus } from './bugApi'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

export const STATUS_META: Record<BugStatus, { label: string; tag: TagType }> = {
  TODO: { label: '待处理', tag: 'info' },
  PROCESSING: { label: '处理中', tag: 'warning' },
  WAIT_ACCEPTANCE: { label: '待验收', tag: 'primary' },
  REOPENED: { label: '重新打开', tag: 'danger' },
  CLOSED: { label: '已关闭', tag: 'success' },
}

export const PRIORITY_META: Record<BugPriority, { label: string; tag: TagType }> = {
  P0: { label: '紧急', tag: 'danger' },
  P1: { label: '高', tag: 'warning' },
  P2: { label: '中', tag: 'primary' },
  P3: { label: '低', tag: 'info' },
}

export const BUG_STATUS_OPTIONS = (Object.keys(STATUS_META) as BugStatus[]).map((value) => ({
  value,
  label: STATUS_META[value].label,
}))

export const BUG_PRIORITY_OPTIONS = (Object.keys(PRIORITY_META) as BugPriority[]).map((value) => ({
  value,
  label: PRIORITY_META[value].label,
}))

/** 把后端 LocalDateTime 字符串格式化为 YYYY-MM-DD HH:mm。 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

/** 附件白名单、体积和数量上限与后端校验保持一致，用于上传前的前置提示。 */
export const ATTACHMENT_MAX_SIZE = 20 * 1024 * 1024
export const ATTACHMENT_MAX_COUNT = 20
export const ALLOWED_ATTACHMENT_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'txt', 'log']
export const ATTACHMENT_ACCEPT = ALLOWED_ATTACHMENT_EXTENSIONS.map((ext) => `.${ext}`).join(',')

/** 返回附件不满足上传条件的原因，校验通过时返回 null。 */
export function attachmentValidationError(file: File, existingCount = 0): string | null {
  if (file.size > ATTACHMENT_MAX_SIZE) {
    return '单个附件不能超过 20MB'
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_ATTACHMENT_EXTENSIONS.includes(extension)) {
    return `不支持该附件类型：${file.name}`
  }
  if (existingCount >= ATTACHMENT_MAX_COUNT) {
    return `单个 Bug 最多上传 ${ATTACHMENT_MAX_COUNT} 个附件`
  }
  return null
}

/** 把字节数格式化为易读大小，用于附件列表。 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
