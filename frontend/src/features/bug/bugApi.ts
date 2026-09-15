/**
 * 本文件封装 Bug 接口调用与返回类型，字段与后端 VO 逐一对齐。
 * 后端 DTO 会拒绝协议外字段，请求体必须只包含已声明的字段。
 */
import http from '@/shared/api/http'
import type { PageResponse } from '@/shared/api/types'
import type { Workspace } from '@/features/workspace/workspaceApi'

export type BugPriority = 'P0' | 'P1' | 'P2' | 'P3'
export type BugStatus = 'TODO' | 'PROCESSING' | 'WAIT_ACCEPTANCE' | 'REOPENED' | 'CLOSED'

export interface BugUser {
  id: number
  username: string
  displayName: string
}

export interface BugAttachment {
  id: number
  originalName: string
  fileSize: number
  contentType: string | null
  uploaderId: number
  createdAt: string
}

export interface BugAcceptance {
  id: number
  acceptorId: number
  result: 'PASS' | 'REJECT'
  commentMd: string | null
  fromStatus: BugStatus
  toStatus: BugStatus
  createdAt: string
}

export interface BugSummary {
  id: number
  workspaceId: number
  bugNo: string
  title: string
  priority: BugPriority
  status: BugStatus
  creatorId: number
  assigneeId: number | null
  acceptorId: number
  creator: BugUser | null
  assignee: BugUser | null
  acceptor: BugUser | null
  reopenCount: number
  version: number
  createdAt: string
  updatedAt: string
  closedAt: string | null
}

export interface BugDetail extends BugSummary {
  descriptionMd: string
  fixDescriptionMd: string | null
  workspace: Workspace
  attachments: BugAttachment[]
  latestAcceptance: BugAcceptance | null
}

export interface BugCreated {
  id: number
  bugNo: string
}

/** 评论、操作日志和描述历史的展示字段与后端追溯 VO 一一对应。 */
export interface BugComment {
  id: number
  userId: number
  username: string
  displayName: string
  contentMd: string
  createdAt: string
}

export interface BugOperationLog {
  id: number
  operatorId: number
  operatorUsername: string
  operatorDisplayName: string
  operationType: string
  fieldName: string | null
  oldValue: string | null
  newValue: string | null
  description: string
  createdAt: string
}

export interface BugDescriptionHistoryItem {
  id: number
  versionNo: number
  operatorId: number
  operatorUsername: string
  operatorDisplayName: string
  createdAt: string
}

export interface BugDescriptionHistoryDetail extends BugDescriptionHistoryItem {
  contentMd: string
}

/** 验收历史记录，比详情中的 latestAcceptance 多出完整列表能力。 */
export interface BugAcceptanceRecord {
  id: number
  acceptorId: number
  acceptorUsername: string
  acceptorDisplayName: string
  result: 'PASS' | 'REJECT'
  commentMd: string | null
  fromStatus: BugStatus
  toStatus: BugStatus
  createdAt: string
}

export interface BugListQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: BugStatus
  priority?: BugPriority
  assigneeId?: number
  creatorId?: number
  acceptorId?: number
  startDate?: string
  endDate?: string
}

export interface CreateBugPayload {
  title: string
  descriptionMd: string
  priority: BugPriority
  assigneeId: number | null
  acceptorId: number | null
}

export interface UpdateBugPayload {
  title: string
  descriptionMd: string
  priority: BugPriority
  version: number
}

export function createBug(workspaceId: number, payload: CreateBugPayload): Promise<BugCreated> {
  return http.post<unknown, BugCreated>(`/workspaces/${workspaceId}/bugs`, payload)
}

export function fetchBugs(
  workspaceId: number,
  query: BugListQuery,
): Promise<PageResponse<BugSummary>> {
  return http.get<unknown, PageResponse<BugSummary>>(`/workspaces/${workspaceId}/bugs`, {
    params: query,
  })
}

export function fetchBugDetail(bugId: number): Promise<BugDetail> {
  return http.get<unknown, BugDetail>(`/bugs/${bugId}`)
}

export function updateBug(bugId: number, payload: UpdateBugPayload): Promise<BugDetail> {
  return http.put<unknown, BugDetail>(`/bugs/${bugId}`, payload)
}

export function assignBug(bugId: number, assigneeId: number): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/assign`, { assigneeId })
}

export function setBugAcceptor(bugId: number, acceptorId: number): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/acceptor`, { acceptorId })
}

export function startBug(bugId: number): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/start`)
}

export function saveFixDescription(bugId: number, fixDescriptionMd: string): Promise<BugDetail> {
  return http.put<unknown, BugDetail>(`/bugs/${bugId}/fix-description`, { fixDescriptionMd })
}

export function submitBug(bugId: number): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/submit`)
}

export function acceptBug(bugId: number, commentMd: string | null): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/accept`, { commentMd })
}

export function rejectBug(bugId: number, commentMd: string): Promise<BugDetail> {
  return http.post<unknown, BugDetail>(`/bugs/${bugId}/reject`, { commentMd })
}

export function fetchComments(
  bugId: number,
  page: number,
  pageSize: number,
): Promise<PageResponse<BugComment>> {
  return http.get<unknown, PageResponse<BugComment>>(`/bugs/${bugId}/comments`, {
    params: { page, pageSize },
  })
}

export function createComment(bugId: number, contentMd: string): Promise<BugComment> {
  return http.post<unknown, BugComment>(`/bugs/${bugId}/comments`, { contentMd })
}

export function fetchOperationLogs(bugId: number): Promise<BugOperationLog[]> {
  return http.get<unknown, BugOperationLog[]>(`/bugs/${bugId}/logs`)
}

export function fetchDescriptionHistory(bugId: number): Promise<BugDescriptionHistoryItem[]> {
  return http.get<unknown, BugDescriptionHistoryItem[]>(`/bugs/${bugId}/description-history`)
}

export function fetchDescriptionHistoryDetail(
  bugId: number,
  versionNo: number,
): Promise<BugDescriptionHistoryDetail> {
  return http.get<unknown, BugDescriptionHistoryDetail>(
    `/bugs/${bugId}/description-history/${versionNo}`,
  )
}

export function fetchAcceptances(bugId: number): Promise<BugAcceptanceRecord[]> {
  return http.get<unknown, BugAcceptanceRecord[]>(`/bugs/${bugId}/acceptances`)
}

/** 附件以 multipart 提交，axios 会自动补上带 boundary 的 Content-Type。 */
export function uploadBugAttachment(bugId: number, file: File): Promise<BugAttachment> {
  const form = new FormData()
  form.append('file', file)
  return http.post<unknown, BugAttachment>(`/bugs/${bugId}/attachments`, form)
}

export function deleteBugAttachment(attachmentId: number): Promise<void> {
  return http.delete<unknown, void>(`/attachments/${attachmentId}`)
}
