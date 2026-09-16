# BugLoop API Spec

> 本文件的核心职责：定义统一 API、错误码、Workspace、Bug、评论、日志、历史版本、附件和 Markdown 安全契约。章节编号沿用 `BugLoop_SPEC_v1.0` 原编号，便于追溯。

## 四十五、统一 API 规范

API 前缀：

```Plain
/api
```

统一返回格式：

```Json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页返回：

```Json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

HTTP 状态码需要与业务语义一致：

```Plain
200 成功
400 参数错误
401 未登录
403 无权限
404 数据不存在
409 状态冲突 / 并发冲突
500 系统内部异常
```

---

## 四十六、错误码 Spec

| code | 含义 |
|---:|---|
| 0 | success |
| 40001 | 参数校验失败 |
| 40101 | 未登录或 Token 无效 |
| 40102 | 用户名或密码错误 |
| 40301 | 无系统权限 |
| 40302 | 不是当前工作空间成员 |
| 40303 | 当前用户不是 Bug 负责人 |
| 40304 | 当前用户不是 Bug 验收人 |
| 40305 | 用户已被禁用 |
| 40401 | 用户不存在 |
| 40402 | 工作空间不存在 |
| 40403 | Bug 不存在 |
| 40404 | 附件不存在 |
| 40405 | 描述历史版本不存在 |
| 40406 | 评论不存在或已删除 |
| 40901 | Bug 状态不允许当前操作 |
| 40902 | 数据版本冲突 |
| 40903 | 用户已经是工作空间成员 |
| 40904 | 不能删除最后一个 Owner |
| 40905 | 用户名已存在 |
| 42201 | 未填写修复说明 |
| 42202 | 验收驳回原因不能为空 |
| 42203 | Bug 尚未指定负责人 |
| 42204 | Bug 尚未指定验收人 |
| 42205 | 指定人员不是当前工作空间成员 |
| 42206 | 被回复评论不属于当前 Bug |
| 50000 | 系统内部异常 |

错误信息必须面向用户可理解，不能直接返回 Java 异常堆栈。

---

## 四十六之二、认证 API Spec

除登录和注册外，所有 `/api` 接口都必须携带登录凭证。

请求头：

```Plain
Authorization: Bearer <token>
```

缺少、无效或已过期的 Token 返回：

```Plain
401 + 40101
```

### 46A.1 注册

```Plain
POST /api/auth/register
```

Request：

```Json
{
  "username": "zhangsan",
  "displayName": "张三",
  "password": "bugloop123"
}
```

成功后：

- 创建 `sys_user`，`system_role` 固定为 `USER`（管理员由内置初始化提供，见 37.1）；
- 直接返回登录结果，前端无需二次登录。

失败：

```Plain
400 + 40001 参数校验失败（用户名、密码、显示名不满足 37.1 规则）
409 + 40905 用户名已存在
```

### 46A.2 登录

```Plain
POST /api/auth/login
```

Request：

```Json
{
  "username": "zhangsan",
  "password": "bugloop123"
}
```

Response：

```Json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "xxxx",
    "user": {
      "id": 1001,
      "username": "zhangsan",
      "displayName": "张三",
      "systemRole": "SYSTEM_ADMIN",
      "createdAt": "2026-09-14T10:00:00"
    }
  }
}
```

失败：

```Plain
401 + 40102 用户名或密码错误（不区分用户名不存在与密码错误）
403 + 40305 用户已被禁用
```

### 46A.3 登出

```Plain
POST /api/auth/logout
```

成功后服务端清除当前登录会话，Token 立即失效。

### 46A.4 当前用户

```Plain
GET /api/users/me
```

Response data：

```Json
{
  "id": 1001,
  "username": "zhangsan",
  "displayName": "张三",
  "systemRole": "SYSTEM_ADMIN",
  "enabled": true,
  "createdAt": "2026-09-14T10:00:00"
}
```

### 46A.5 查询系统用户

```Plain
GET /api/users
```

权限：仅 `SYSTEM_ADMIN`。返回全部系统账号，按创建时间倒序排列，不返回密码哈希。

Response data：

```Json
[
  {
    "id": 1001,
    "username": "zhangsan",
    "displayName": "张三",
    "systemRole": "SYSTEM_ADMIN",
    "enabled": true,
    "createdAt": "2026-09-14T10:00:00"
  }
]
```

### 46A.6 系统管理员创建用户

```Plain
POST /api/users
```

权限：仅 `SYSTEM_ADMIN`。

Request：

```Json
{
  "username": "lisi",
  "displayName": "李四",
  "password": "initial-password"
}
```

成功后创建一个立即启用的 `USER` 账号，不改变当前管理员会话。
用户名重复返回 `40905`；参数不合法返回 `40001`；非系统管理员返回 `40301`。

### 46A.7 停用系统用户

```Plain
POST /api/users/{userId}/disable
```

权限：仅 `SYSTEM_ADMIN`，且只能停用普通用户账号。

- 停用后该账号不能登录，登录返回 `403 + 40305`
- 已建立的在线会话立即失效，旧 Token 再次访问接口返回 `401 + 40101`
- `SYSTEM_ADMIN` 账号（包括自己）不在停用范围内，返回 `40001`
- 重复停用返回 `40901`；用户不存在返回 `40401`
- 停用不改动工作空间成员关系、Bug 指派和历史数据，重新启用即恢复

Response data：更新后的用户信息，`enabled` 为 `false`。

### 46A.8 重新启用系统用户

```Plain
POST /api/users/{userId}/enable
```

权限：仅 `SYSTEM_ADMIN`。启用后账号恢复登录能力，原密码保持不变；
已经启用的账号重复启用返回 `40901`，用户不存在返回 `40401`。

Response data：更新后的用户信息，`enabled` 为 `true`。

---

## 四十七、Workspace API Spec

### 47.1 创建工作空间

```Plain
POST /api/workspaces
```

权限：仅 `SYSTEM_ADMIN`，或已在任一工作空间担任 `OWNER / ADMIN` 的用户可以创建；
其他普通用户返回 `40301`。

Request：

```Json
{
  "name": "研发中心",
  "description": "研发内部问题跟踪"
}
```

成功后：

- 创建 workspace；
- 当前用户成为 OWNER；
- 创建 workspace_member；
- 写 workspace_operation_log。

### 47.2 我的工作空间

```Plain
GET /api/workspaces
```

只返回：

> 当前用户有成员关系的工作空间。

### 47.3 工作空间详情

```Plain
GET /api/workspaces/{workspaceId}
```

访问前必须校验：

```Plain
currentUser ∈ workspace
```

SYSTEM_ADMIN 除外。

### 47.4 修改工作空间

```Plain
PUT /api/workspaces/{workspaceId}
```

可修改：

```Plain
name
description
```

### 47.5 工作空间成员

```Plain
GET /api/workspaces/{workspaceId}/members
```

### 47.6 添加成员

```Plain
POST /api/workspaces/{workspaceId}/members
```

Request：

```Json
{
  "userId": 1002,
  "role": "MEMBER"
}
```

### 47.7 修改成员角色

```Plain
PUT /api/workspaces/{workspaceId}/members/{userId}/role
```

Request：

```Json
{
  "role": "ADMIN"
}
```

### 47.8 移除成员

```Plain
DELETE /api/workspaces/{workspaceId}/members/{userId}
```

如果用户仍然是该工作空间某个未关闭 Bug 的：

```Plain
assignee
acceptor
```

第一期默认禁止移除，并提示先完成转派。

### 47.9 停用工作空间

```Plain
POST /api/workspaces/{workspaceId}/disable
```

只允许 OWNER 或 SYSTEM_ADMIN。

### 47.10 重新启用工作空间

```Plain
POST /api/workspaces/{workspaceId}/enable
```

只允许 OWNER 或 SYSTEM_ADMIN。

仅允许：

```Plain
DISABLED → ENABLED
```

重新启用后保留原成员、Bug 和历史数据，并写入 `workspace_operation_log`。
工作空间已经处于 `ENABLED` 状态时返回 `40901`。

---

## 四十八、Bug API Spec

### 48.1 创建 Bug

```Plain
POST /api/workspaces/{workspaceId}/bugs
```

Request：

```Json
{
  "title": "用户登录后个人中心500",
  "descriptionMd": "# 问题现象\n...",
  "priority": "P1",
  "assigneeId": 1002,
  "acceptorId": 1003
}
```

返回：

```Json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "bugNo": "BUG-000001"
  }
}
```

### 48.2 Bug 列表

```Plain
GET /api/workspaces/{workspaceId}/bugs
```

Query：

```Plain
page
pageSize
keyword
status
priority
assigneeId
creatorId
acceptorId
startDate
endDate
```

keyword 匹配：

```Plain
bug_no
title
```

默认排序：

```Plain
updated_at DESC
```

### 48.3 Bug 详情

```Plain
GET /api/bugs/{bugId}
```

返回至少包含：

```Plain
基础信息
Workspace信息
创建人
负责人
验收人
问题描述
修复说明
附件
最近验收记录
```

评论和操作日志可以使用独立分页接口加载。

### 48.4 修改 Bug 基础信息

```Plain
PUT /api/bugs/{bugId}
```

Request：

```Json
{
  "title": "更新后的标题",
  "descriptionMd": "# 更新后的描述",
  "priority": "P1",
  "version": 3
}
```

不接受 `status` 字段。

### 48.5 指派负责人

```Plain
POST /api/bugs/{bugId}/assign
```

Request：

```Json
{
  "assigneeId": 1002
}
```

允许状态：

```Plain
TODO
PROCESSING
REOPENED
```

WAIT_ACCEPTANCE 和 CLOSED 第一阶段不允许转派负责人。

### 48.6 设置验收人

```Plain
POST /api/bugs/{bugId}/acceptor
```

Request：

```Json
{
  "acceptorId": 1003
}
```

允许状态：

```Plain
TODO
PROCESSING
REOPENED
```

### 48.7 开始处理

```Plain
POST /api/bugs/{bugId}/start
```

前置条件：

```Plain
currentUser = assignee
status ∈ {TODO, REOPENED}
```

成功：

```Plain
status = PROCESSING
```

### 48.8 保存修复说明

```Plain
PUT /api/bugs/{bugId}/fix-description
```

Request：

```Json
{
  "fixDescriptionMd": "## 问题原因\n..."
}
```

前置条件：

```Plain
currentUser = assignee
status ∈ {PROCESSING, REOPENED}
```

### 48.9 提交验收

```Plain
POST /api/bugs/{bugId}/submit
```

前置条件：

```Plain
currentUser = assignee
status = PROCESSING
fixDescriptionMd 非空
acceptorId 非空
```

成功：

```Plain
PROCESSING → WAIT_ACCEPTANCE
```

### 48.10 验收通过

```Plain
POST /api/bugs/{bugId}/accept
```

Request：

```Json
{
  "commentMd": "验证通过"
}
```

前置条件：

```Plain
currentUser = acceptor
status = WAIT_ACCEPTANCE
```

成功：

```Plain
status = CLOSED
closedAt = now
```

同时写入 `bug_acceptance`。

### 48.11 验收驳回

```Plain
POST /api/bugs/{bugId}/reject
```

Request：

```Json
{
  "commentMd": "邮箱为空时仍然报错"
}
```

`commentMd` 必填。

成功：

```Plain
WAIT_ACCEPTANCE → REOPENED
reopen_count = reopen_count + 1
closed_at = NULL
```

同时写入 `bug_acceptance`。

---

## 四十九、评论 / 日志 / 历史版本 API

### 49.1 评论列表

```Plain
GET /api/bugs/{bugId}/comments
```

支持分页，按创建时间正序返回（最早的评论在前），同秒写入按主键兜底排序。

每条评论包含：

```Plain
commentId
bugId
userId / username / displayName / avatar
contentMd
parentId / replyUserId / replyUsername
parentDeleted
deleted
createdAt
```

- `parentId` 保留服务端记录的完整直接父子关系，前端可自行压缩展示层级
- `parentDeleted` 表示父评论已被删除，用于把回复标记为「原评论已删除」
- `avatar` 预留给后续头像资料，为空时前端用显示名称首字母兜底
- 已逻辑删除的评论不出现在列表里

### 49.2 新增评论

```Plain
POST /api/bugs/{bugId}/comments
```

Request：

```Json
{
  "contentMd": "该问题测试环境也可以复现。"
}
```

评论内容不能为空，最多 5000 个字符（服务端另有 60KB 字节上限兜底，与 `bug_comment.content_md` 的 TEXT 列上限对齐）。
内容按 Markdown 保存，写入前用 HTML 解析器做基础 XSS 清洗，直接返回 `code 0` 的评论视图。

评论遵守与 Bug 相同的空间边界：空间停用后不允许新增评论，但历史评论始终可查看；Bug 关闭后仍可评论。

### 49.2.1 回复评论

```Plain
POST /api/bugs/{bugId}/comments/{parentCommentId}/replies
```

Request：

```Json
{
  "contentMd": "我这边也复现了。"
}
```

约束：

- 回复内容不能为空，最多 5000 个字符
- 父评论必须属于同一个 Bug 且未被删除，否则返回 `42206`
- 被回复人由服务端按父评论作者确定，客户端不能指定
- 父评论作者同样可以回复自己的评论

### 49.2.2 删除评论

```Plain
DELETE /api/bugs/{bugId}/comments/{commentId}
```

- 逻辑删除：记录保留 `deleted_by` / `deleted_at`，列表不再返回该评论
- 只有评论作者本人或 `SYSTEM_ADMIN` 可以删除；空间 `OWNER / ADMIN` 没有代删权限，越权返回 `40301`
- 已删除或不存在返回 `40406`
- 删除后回复记录仍保留，回复的 `parentDeleted` 变为 true
- 写入 `DELETE_COMMENT` 操作日志

### 49.3 操作日志

```Plain
GET /api/bugs/{bugId}/logs
```

只读。

默认：

```Plain
created_at DESC
```

### 49.4 Markdown 历史版本

```Plain
GET /api/bugs/{bugId}/description-history
```

### 49.5 历史版本详情

```Plain
GET /api/bugs/{bugId}/description-history/{versionNo}
```

第一期只支持：

> 查看历史版本。

不支持直接恢复历史版本。

### 49.6 验收历史

```Plain
GET /api/bugs/{bugId}/acceptances
```

返回该 Bug 的完整验收历史，默认按实际写入时间倒序。字段包括验收人、通过/驳回结果、验收意见、状态变化和本次验收附件；
详情接口中的 `latestAcceptance` 仍只表示最近一条记录。

服务端先读取验收记录，再批量读取 `ACCEPT_REJECT` / `ACCEPT_PASS` 来源的附件并按 `bizId` 组装，禁止逐条验收记录查询附件。

---

## 五十、附件 API 和文件规则

### 50.1 上传

```Plain
POST /api/bugs/{bugId}/attachments
Content-Type: multipart/form-data
```

除 `file` 外支持：

| 字段 | 必填 | 说明 |
|---|---:|---|
| bizType | 否 | `BUG_CREATE`、`BUG_PROCESS`、`ACCEPT_REJECT`、`ACCEPT_PASS`、`COMMENT` |
| bizId | 否 | 对应业务记录主键 |

未传来源字段的旧客户端按 `BUG_PROCESS + bugId` 兼容。服务端会验证验收记录、评论记录与当前 Bug 的归属及上传人责任，不能跨 Bug 绑定。

第一期单文件大小：

```Plain
≤ 20MB
```

单个 Bug 默认最多：

```Plain
20 个附件
```

允许：

```Plain
png
jpg
jpeg
gif
webp
pdf
txt
log
```

禁止：

```Plain
exe
bat
cmd
sh
jar
class
js
html
php
```

以及无法识别的可执行文件。

### 50.2 文件存储路径

建议：

```Plain
/data/bugloop/{workspaceId}/{bugId}/{yyyy}/{MM}/{uuid}.{ext}
```

真实磁盘文件名必须使用 UUID。

用户原始文件名只保存到数据库。

### 50.3 下载

```Plain
GET /api/attachments/{attachmentId}/download
```

必须先校验：

> 当前用户是否有权限访问附件对应 Bug 所属工作空间。

### 50.4 删除

```Plain
DELETE /api/attachments/{attachmentId}
```

采用逻辑删除并写操作日志。

响应中的附件 VO 至少包含：`id`、`bugId`、`bizType`、`bizId`、原始文件名、大小、内容类型、上传人信息、创建时间与 `canDelete`。`canDelete` 由服务端计算，前端不得自行推断权限。

---

## 五十一、Markdown 安全和编辑规则

Markdown 编辑器第一期统一采用：

```Plain
md-editor-v3
```

如果项目已经接入其他编辑器，则不强制替换。

必须支持：

```Plain
编辑
预览
标题
列表
表格
引用
代码块
链接
图片
```

Markdown 数据库保存原文：

```Plain
description_md
fix_description_md
comment_md
```

不得只保存 HTML。

渲染时：

- 禁止执行 script；
- 禁止危险 iframe；
- 禁止事件属性如 onclick；
- 对 URL 进行安全校验；
- 默认关闭不必要的原生 HTML 能力。

---

## 五十三、操作日志 Spec

需要记录以下 Bug 操作：

```Plain
CREATE_BUG
UPDATE_TITLE
UPDATE_DESCRIPTION
CHANGE_PRIORITY
ASSIGN_USER
CHANGE_ACCEPTOR
START_PROCESS
UPDATE_FIX_DESCRIPTION
SUBMIT_ACCEPTANCE
ACCEPT_BUG
REJECT_BUG
ADD_ATTACHMENT
DELETE_ATTACHMENT
ADD_COMMENT
```

日志描述应便于用户阅读。

例如：

```Plain
张三 将负责人从“李四”修改为“王五”

张三 将状态从“处理中”修改为“待验收”

李四 验收未通过：邮箱为空时仍然报错
```

不要只展示：

```Plain
old=2 new=3
```

日志页面按时间倒序展示。

---
