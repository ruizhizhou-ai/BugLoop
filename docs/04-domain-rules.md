# BugLoop 领域与实现规则

> 本文件的核心职责：定义一期默认约定、状态机、权限、字段、数据库以及事务并发规则，是核心业务实现的事实来源。章节编号沿用 `BugLoop_SPEC_v1.0` 原编号，便于追溯。

## 三十六、Implementation Spec 使用说明

本节开始说明**开发实现级 Spec 文档集**，用于约束 Codex、Cursor、Claude Code 等 AI Coding 工具以及人工开发。

产品需求、方案调研和技术设计文档主要回答：

> 为什么做、做什么、为什么自研、系统整体如何设计。

领域规则、API、前端和质量交付文档主要回答：

> 具体应该怎么实现，以及什么结果才算实现正确。

当文档中不同章节出现描述冲突时，优先级如下：

```Plain
1. 04-domain-rules.md 至 07-quality-and-delivery.md 中对应职责的实现级 Spec
2. 一期验收标准
3. 核心业务流程
4. 前期方案说明
```

开发过程中不得自行扩展一期范围。

如果需求没有明确规定，优先选择：

> 简单、可维护、改动最小、符合现有技术栈的实现。

---

## 三十七、一期实现默认约定

为避免 Codex 在开发过程中自行猜测，第一期统一采用以下默认约定。

### 37.1 登录认证

一期默认采用：

```Plain
用户名 + 密码
+
JWT
```

如果公司已有统一认证系统，后续只替换认证层，不修改 Workspace 和 Bug 核心业务模型。

密码必须加密保存，不允许数据库保存明文密码。

一期实现使用 Sa-Token 默认 Token 与服务端会话存储：对外仍以 `Authorization: Bearer <token>` 传递登录凭证，认证逻辑集中在认证层，后续如需服务端无状态 Token 或接入统一认证，只替换认证层实现。

账号注册规则：

```Plain
一期开放自助注册，不开放匿名访问。
username：3-64 位，仅字母、数字、下划线，唯一
password：8-72 位
display_name：1-64 位，不允许为空
```

注册成功后默认系统角色为 `USER`。

除登录页自助注册外，`SYSTEM_ADMIN` 可以在系统用户管理页创建普通用户。管理员创建的账号固定为
`USER`、默认启用，也不会改变管理员当前登录会话。

系统初始化规则：

```Plain
开启管理员引导配置时，应用启动会检查内置管理员账号：
不存在则创建 username = admin、system_role = SYSTEM_ADMIN 的启用账号；
已存在则跳过，重复启动不会产生重复账号，也不会覆盖已修改的密码。
```

- 自助注册一律创建 `USER`，不再有「第一个注册用户成为管理员」的例外
- 内置管理员的用户名与初始密码分别由 `BUGLOOP_ADMIN_USERNAME`、`BUGLOOP_ADMIN_PASSWORD` 配置，
  默认 `admin` / `admin@123`；配置只在账号首次创建时生效，账号已存在时不会覆盖密码，
  因此首次部署就应改成强密码，避免长期保留默认口令
- 普通用户自身无法创建工作空间，需要管理员创建空间后邀请加入

登录规则：

```Plain
登录成功返回 Token 与当前用户信息。
用户名不存在或密码错误统一提示"用户名或密码错误"，不区分二者。
enabled = 0 的用户不允许登录，返回 40305。
```

### 37.2 系统角色

系统级角色只保留：

```Plain
SYSTEM_ADMIN
USER
```

SYSTEM_ADMIN 负责：

- 系统用户管理；
- 查看系统运行情况；
- 必要时处理工作空间异常。

业务权限主要由工作空间角色决定。

### 37.3 工作空间角色

工作空间角色固定为：

```Plain
OWNER
ADMIN
MEMBER
```

第一期不支持自定义角色。

### 37.4 Bug 可见范围

第一期默认规则：

> 当前工作空间所有成员均可以查看该工作空间内的全部 Bug。

不同工作空间之间严格数据隔离。

### 37.5 Bug 编号

Bug 编号系统全局唯一，格式：

```Plain
BUG-000001
BUG-000002
BUG-000003
```

数据库主键 `id` 不作为用户侧主要展示编号。

### 37.6 默认优先级

新建 Bug 默认：

```Plain
P2
```

### 37.7 默认状态

新建 Bug 默认：

```Plain
TODO
```

### 37.8 默认验收人

创建 Bug 时：

- 用户可以主动选择验收人；
- 如果未选择，则默认使用 Bug 创建人；
- 验收人必须属于当前工作空间。

### 37.9 负责人

负责人可以在创建 Bug 时为空。

如果负责人为空：

```Plain
status = TODO
```

后续由 OWNER / ADMIN 指派。

### 37.10 已关闭 Bug

第一期中：

> CLOSED 为最终状态。

已关闭 Bug 不允许再次修改基础信息、修复说明或状态。

允许继续查看历史记录和评论。

第一期不实现“关闭后重新打开”。

---

## 三十八、核心领域模型

一期核心关系如下：

```Plain
System User
    │
    ├──────────────┐
    │              │
    ↓              ↓
Workspace      Workspace Member
    │
    ↓
Bug
    │
    ├── Attachment
    ├── Comment
    ├── Description History
    ├── Acceptance
    └── Operation Log
```

业务约束：

1. 一个用户可以加入多个工作空间；
2. 一个工作空间可以包含多个成员；
3. 一个 Bug 只能属于一个工作空间；
4. Bug 创建人必须是当前工作空间成员；
5. Bug 负责人必须是当前工作空间有效成员；
6. Bug 验收人必须是当前工作空间有效成员；
7. Bug 的附件、评论、验收、操作日志都继承该 Bug 的工作空间边界；
8. 禁止跨工作空间访问、指派或验收。

---

## 三十九、Bug 状态机

### 39.1 状态枚举

统一使用以下状态编码：

| 状态编码 | 中文名称 | 含义 |
|---|---|---|
| `TODO` | 待处理 | Bug 已创建，但尚未开始处理 |
| `PROCESSING` | 处理中 | 负责人正在处理 |
| `WAIT_ACCEPTANCE` | 待验收 | 负责人认为已经修复，等待验收 |
| `REOPENED` | 重新打开 | 验收失败，需要重新处理 |
| `CLOSED` | 已关闭 | 验收通过，Bug 生命周期结束 |

后端必须使用 Enum 管理状态。

禁止业务代码大量直接使用字符串判断。

### 39.2 状态转换矩阵

| 当前状态 | 业务操作 | 操作者 | 前置条件 | 目标状态 |
|---|---|---|---|---|
| TODO | 开始处理 | 当前 Assignee | 已指定负责人 | PROCESSING |
| PROCESSING | 提交验收 | 当前 Assignee | 修复说明非空；已指定验收人 | WAIT_ACCEPTANCE |
| WAIT_ACCEPTANCE | 验收通过 | 当前 Acceptor | 无 | CLOSED |
| WAIT_ACCEPTANCE | 验收驳回 | 当前 Acceptor | 驳回原因非空 | REOPENED |
| REOPENED | 开始处理 | 当前 Assignee | 已指定负责人 | PROCESSING |

除以上转换外，其他状态变化全部禁止。

### 39.3 明确禁止的状态转换

以下必须由后端拒绝：

```Plain
TODO → CLOSED

TODO → WAIT_ACCEPTANCE

PROCESSING → CLOSED

PROCESSING → REOPENED

WAIT_ACCEPTANCE → PROCESSING

REOPENED → CLOSED

CLOSED → 任意其他状态
```

### 39.4 管理员权限

第一期 OWNER / ADMIN：

- 可以指派和转派负责人；
- 可以调整优先级；
- 可以管理成员；

但**不能绕过状态机直接把 Bug 改成 CLOSED**。

第一期不提供“任意修改 status”的管理接口。

---

## 四十、权限矩阵

符号说明：

```Plain
✓ 允许
△ 有条件允许
× 不允许
```

| 操作 | SYSTEM_ADMIN | OWNER | ADMIN | MEMBER |
|---|---:|---:|---:|---:|
| 创建系统用户 | ✓ | × | × | × |
| 停用系统用户 | ✓ | × | × | × |
| 重新启用系统用户 | ✓ | × | × | × |
| 创建工作空间 | ✓ | ✓ | ✓ | × |
| 修改工作空间 | ✓ | ✓ | △ | × |
| 停用工作空间 | ✓ | ✓ | × | × |
| 重新启用工作空间 | ✓ | ✓ | × | × |
| 添加成员 | ✓ | ✓ | ✓ | × |
| 移除成员 | ✓ | ✓ | ✓ | × |
| 修改成员角色 | ✓ | ✓ | ✓ | × |
| 查看工作空间 Bug | ✓ | ✓ | ✓ | ✓ |
| 创建 Bug | ✓ | ✓ | ✓ | ✓ |
| 修改自己创建的 Bug 基础信息 | ✓ | ✓ | ✓ | △ |
| 指派 / 转派 Bug | ✓ | ✓ | ✓ | × |
| 开始处理 Bug | ✓ | △ | △ | △ |
| 编辑修复说明 | ✓ | △ | △ | △ |
| 提交验收 | ✓ | △ | △ | △ |
| 验收通过 / 驳回 | ✓ | △ | △ | △ |
| 评论 | ✓ | ✓ | ✓ | ✓ |
| 查看操作日志 | ✓ | ✓ | ✓ | ✓ |

条件规则：

#### 创建系统用户

仅 `SYSTEM_ADMIN` 可以查看系统用户列表并创建普通用户。管理员创建时必须提供用户名、显示名称和
初始密码，新账号固定为 `USER` 且立即启用；工作空间 `OWNER / ADMIN` 不因此获得系统用户管理权限。

#### 停用 / 启用系统用户

仅 `SYSTEM_ADMIN` 可以停用或重新启用普通用户账号。停用后账号不能登录，已建立的在线会话立即失效；
重新启用后恢复登录能力，密码保持不变。`SYSTEM_ADMIN` 账号不在停用范围内，也不允许停用自己，
避免把平台的用户管理能力锁死。停用不影响工作空间成员关系、Bug 指派和历史数据。

#### 创建工作空间

仅 `SYSTEM_ADMIN`，或已在任一工作空间担任 `OWNER / ADMIN` 的用户可以创建。
普通用户无论是否已经加入工作空间都不能自行创建，需要由管理员创建空间或邀请加入。

#### MEMBER 修改 Bug 基础信息

仅当：

```Plain
currentUserId = creatorId
AND status != CLOSED
```

允许修改：

- title；
- descriptionMd；
- priority。

MEMBER 不允许修改：

- workspaceId；
- creatorId；
- status。

#### 开始处理 / 修复 / 提交验收

仅当：

```Plain
currentUserId = assigneeId
```

允许。

即使 OWNER / ADMIN 不是当前负责人，也不能以管理员身份替负责人“提交验收”。

#### 验收

仅当：

```Plain
currentUserId = acceptorId
```

允许。

OWNER / ADMIN 如果需要验收，必须先成为该 Bug 的验收人。

---

## 四十一、工作空间业务规则

### 41.1 创建工作空间

只有具备以下身份之一的用户才能创建工作空间：

```Plain
SYSTEM_ADMIN

或者

未加入任何工作空间的用户（用于创建自己的第一个工作空间）

或者

已加入的工作空间中担任 OWNER 或 ADMIN
```

其他普通用户不能创建新工作空间，即使尚未加入任何空间也返回 `40301`。

创建者自动成为：

```Plain
OWNER
```

并自动写入 `workspace_member`。

工作空间名称：

- 必填；
- 长度 1～100；
- 同一系统中允许重名，但建议 UI 对同名进行提示。

### 41.2 成员管理

只能添加已存在并处于 ENABLED 状态的系统用户。

同一个用户在同一个工作空间只能存在一条成员关系。

数据库必须建立：

```Plain
UNIQUE(workspace_id, user_id)
```

### 41.3 Owner 规则

每个工作空间至少存在一个 OWNER。

第一期不允许删除最后一个 OWNER。

OWNER 不允许将自己移出工作空间，除非：

> 已经存在其他 OWNER。

### 41.4 停用工作空间

停用后：

- 原数据保留；
- 只允许查看；
- 禁止创建 Bug；
- 禁止修改 Bug；
- 禁止成员变更；
- 禁止状态流转。

停用不是物理删除。

### 41.5 重新启用工作空间

已停用的工作空间允许由 `OWNER` 或 `SYSTEM_ADMIN` 重新启用。

重新启用后：

- 恢复成员变更；
- 恢复 Bug 创建、修改和状态流转；
- 原成员、Bug 和全部历史数据保持不变；
- 写入 `workspace_operation_log` 留痕。

重复启用已处于 `ENABLED` 状态的工作空间返回状态冲突，不重复写操作日志。

---

## 四十二、Bug 字段级 Spec

### 42.1 创建 Bug

字段定义：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---:|---|
| title | String | 是 | 1～200 字符 |
| descriptionMd | String | 是 | Markdown 原文，最大建议 100KB |
| priority | Enum | 否 | 默认 P2 |
| assigneeId | Long | 否 | 必须为当前 Workspace 有效成员 |
| acceptorId | Long | 否 | 默认创建人；必须为当前 Workspace 有效成员 |
| attachments | File[] | 否 | 按附件规则校验 |

创建后系统自动生成：

```Plain
bugNo
workspaceId
creatorId
status = TODO
createdAt
updatedAt
```

### 42.2 Bug 基础信息修改

允许修改：

```Plain
title
descriptionMd
priority
```

禁止通过通用更新接口修改：

```Plain
status
workspaceId
creatorId
assigneeId
acceptorId
closedAt
```

负责人和验收人必须通过独立业务接口修改。

### 42.3 描述修改

如果 `descriptionMd` 内容发生变化：

1. 保存修改前内容到 `bug_description_history`；
2. 更新当前描述；
3. 写入 Bug 操作日志；
4. 整个过程使用同一事务。

如果内容没有发生变化：

> 不创建历史版本。

### 42.4 修复说明

字段：

```Plain
fixDescriptionMd
```

只有当前负责人可以编辑。

Bug 状态允许：

```Plain
PROCESSING
REOPENED
```

提交验收时必须非空。

---

## 四十三、数据库详细 Spec

数据库：

```Plain
MySQL 8.x
charset = utf8mb4
```

时间字段统一使用：

```Plain
DATETIME
```

Java 对应：

```Plain
LocalDateTime
```

### 43.1 sys_user

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，由应用生成随机值 |
| username | VARCHAR(64) | NOT NULL, UNIQUE |
| display_name | VARCHAR(64) | NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| system_role | VARCHAR(32) | NOT NULL, DEFAULT `USER` |
| enabled | TINYINT(1) | NOT NULL, DEFAULT 1 |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

索引：

```Plain
UNIQUE uk_user_username(username)
```

### 43.2 workspace

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，由应用生成随机值 |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(500) | NULL |
| owner_id | BIGINT | NOT NULL |
| status | VARCHAR(16) | NOT NULL, DEFAULT `ENABLED` |
| created_by | BIGINT | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

状态：

```Plain
ENABLED
DISABLED
```

### 43.3 workspace_member

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| workspace_id | BIGINT | NOT NULL |
| user_id | BIGINT | NOT NULL |
| role | VARCHAR(16) | NOT NULL |
| joined_at | DATETIME | NOT NULL |

索引：

```Plain
UNIQUE uk_workspace_user(workspace_id, user_id)

INDEX idx_workspace_member_user(user_id)
```

### 43.4 bug

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| workspace_id | BIGINT | NOT NULL |
| bug_no | VARCHAR(32) | NOT NULL, UNIQUE |
| title | VARCHAR(200) | NOT NULL |
| description_md | LONGTEXT | NOT NULL |
| priority | VARCHAR(16) | NOT NULL, DEFAULT `P2` |
| status | VARCHAR(32) | NOT NULL, DEFAULT `TODO` |
| creator_id | BIGINT | NOT NULL |
| assignee_id | BIGINT | NULL |
| acceptor_id | BIGINT | NOT NULL |
| fix_description_md | LONGTEXT | NULL |
| reopen_count | INT | NOT NULL, DEFAULT 0 |
| version | INT | NOT NULL, DEFAULT 0 |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |
| closed_at | DATETIME | NULL |

索引：

```Plain
UNIQUE uk_bug_no(bug_no)

INDEX idx_bug_workspace_status(workspace_id, status)

INDEX idx_bug_workspace_priority(workspace_id, priority)

INDEX idx_bug_assignee(assignee_id)

INDEX idx_bug_creator(creator_id)

INDEX idx_bug_acceptor(acceptor_id)

INDEX idx_bug_created_at(created_at)
```

`version` 用于乐观锁，防止两个人同时修改状态造成覆盖。

### 43.5 bug_attachment

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| bug_id | BIGINT | NOT NULL |
| original_name | VARCHAR(255) | NOT NULL |
| storage_name | VARCHAR(255) | NOT NULL |
| storage_path | VARCHAR(500) | NOT NULL |
| file_size | BIGINT | NOT NULL |
| content_type | VARCHAR(128) | NULL |
| uploader_id | BIGINT | NOT NULL |
| is_deleted | TINYINT(1) | NOT NULL, DEFAULT 0 |
| deleted_by | BIGINT | NULL |
| deleted_at | DATETIME | NULL |
| created_at | DATETIME | NOT NULL |

索引：

```Plain
INDEX idx_attachment_bug(bug_id)
```

### 43.6 bug_comment

第一期评论不支持编辑和删除。

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| bug_id | BIGINT | NOT NULL |
| user_id | BIGINT | NOT NULL |
| content_md | TEXT | NOT NULL |
| created_at | DATETIME | NOT NULL |

索引：

```Plain
INDEX idx_comment_bug_time(bug_id, created_at)
```

### 43.7 bug_operation_log

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| bug_id | BIGINT | NOT NULL |
| workspace_id | BIGINT | NOT NULL |
| operator_id | BIGINT | NOT NULL |
| operation_type | VARCHAR(64) | NOT NULL |
| field_name | VARCHAR(64) | NULL |
| old_value | TEXT | NULL |
| new_value | TEXT | NULL |
| description | VARCHAR(1000) | NULL |
| created_at | DATETIME | NOT NULL |

操作日志：

> 只新增，不修改，不删除。

### 43.8 workspace_operation_log

字段结构与 Bug 操作日志类似：

```Plain
id
workspace_id
operator_id
operation_type
old_value
new_value
description
created_at
```

### 43.9 bug_description_history

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| bug_id | BIGINT | NOT NULL |
| version_no | INT | NOT NULL |
| content_md | LONGTEXT | NOT NULL |
| operator_id | BIGINT | NOT NULL |
| created_at | DATETIME | NOT NULL |

索引：

```Plain
UNIQUE uk_bug_description_version(bug_id, version_no)
```

### 43.10 bug_acceptance

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| bug_id | BIGINT | NOT NULL |
| acceptor_id | BIGINT | NOT NULL |
| result | VARCHAR(16) | NOT NULL |
| comment_md | TEXT | NULL |
| from_status | VARCHAR(32) | NOT NULL |
| to_status | VARCHAR(32) | NOT NULL |
| created_at | DATETIME | NOT NULL |

结果：

```Plain
PASS
REJECT
```

REJECT 时 `comment_md` 必填。

### 43.11 bug_attachment 业务来源扩展

附件仍使用统一的 `bug_attachment` 表，不为提单、验收、评论分别建表。除既有文件元数据、上传人和逻辑删除字段外，增加：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| biz_type | VARCHAR(32) | NOT NULL | 附件来源类型 |
| biz_id | BIGINT | NULL | 对应业务记录主键 |
| updated_at | DATETIME | NULL | 元数据最近更新时间 |

`biz_type` 取值：

```Plain
BUG_CREATE      提单附件，biz_id = bug_id
BUG_PROCESS     处理附件，biz_id = bug_id
ACCEPT_REJECT   验收驳回附件，biz_id = bug_acceptance.id
ACCEPT_PASS     验收通过附件，biz_id = bug_acceptance.id
COMMENT         评论附件，biz_id = bug_comment.id
```

历史附件按兼容策略回填为 `BUG_CREATE`，`biz_id = bug_id`。上传时服务端必须校验业务记录属于当前 Bug；不能只信任客户端传入的来源类型或主键。

普通成员只能删除自己上传的附件；空间 `OWNER` / `ADMIN` 与 `SYSTEM_ADMIN` 可按空间权限逻辑删除附件。删除附件不得删除或改写验收记录、评论、状态流转和操作日志。

---

## 四十四、事务和并发规则

以下业务必须使用事务：

```Plain
创建Bug + 写操作日志

修改描述 + 写历史版本 + 写操作日志

人员指派 + 写操作日志

状态流转 + 写操作日志

提交验收 + 写操作日志

验收通过 + 写验收记录 + 修改状态 + 写操作日志

验收失败 + 写验收记录 + 修改状态 + reopen_count + 写操作日志
```

必须满足：

> 业务数据和审计日志要么全部成功，要么全部失败。

Bug 更新使用 `version` 乐观锁。

如果版本冲突：

```Plain
HTTP 409
```

前端提示：

> 数据已被其他用户修改，请刷新后重试。

重复执行同一个状态操作不得重复产生验收记录和操作日志。

---
