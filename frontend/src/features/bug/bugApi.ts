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

export interface BugListQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: BugStatus
  priority?: BugPriority
  assigneeId?: number
  creatorId?: number
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

export function fetchBugs(workspaceId: number, query: BugListQuery): Promise<PageResponse<BugSummary>> {
  return http.get<unknown, PageResponse<BugSummary>>(`/workspaces/${workspaceId}/bugs`, { params: query })
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
