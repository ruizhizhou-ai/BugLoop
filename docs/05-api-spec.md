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
| 40301 | 无系统权限 |
| 40302 | 不是当前工作空间成员 |
| 40303 | 当前用户不是 Bug 负责人 |
| 40304 | 当前用户不是 Bug 验收人 |
| 40401 | 用户不存在 |
| 40402 | 工作空间不存在 |
| 40403 | Bug 不存在 |
| 40901 | Bug 状态不允许当前操作 |
| 40902 | 数据版本冲突 |
| 40903 | 用户已经是工作空间成员 |
| 40904 | 不能删除最后一个 Owner |
| 42201 | 未填写修复说明 |
| 42202 | 验收驳回原因不能为空 |
| 42203 | Bug 尚未指定负责人 |
| 42204 | Bug 尚未指定验收人 |
| 42205 | 指定人员不是当前工作空间成员 |
| 50000 | 系统内部异常 |

错误信息必须面向用户可理解，不能直接返回 Java 异常堆栈。

---

## 四十七、Workspace API Spec

### 47.1 创建工作空间

```Plain
POST /api/workspaces
```

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

支持分页。

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

评论内容不能为空。

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

---

## 五十、附件 API 和文件规则

### 50.1 上传

```Plain
POST /api/bugs/{bugId}/attachments
Content-Type: multipart/form-data
```

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
