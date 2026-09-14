# BugLoop 技术设计

> 本文件的核心职责：描述技术选型、总体架构、概要数据与接口设计、安全、测试、部署和项目风险。章节编号沿用 `BugLoop_SPEC_v1.0` 原编号，便于追溯。

## 二十二、技术选型

### 前端

```Plain
Vue3

Element Plus

Axios

Vue Router

Pinia

Markdown Editor
```

Markdown 编辑器可选择：

```Plain
md-editor-v3

或者

Vditor
```

---

### 后端

```Plain
Java

Spring Boot

MyBatis-Plus
```

---

### 数据库

```Plain
MySQL
```

---

### 文件存储

第一阶段：

```Plain
本地文件
```

后续：

```Plain
MinIO
```

---

## 二十三、系统架构

一期采用非常简单的前后端分离单体架构：

```Plain
Vue3
   │
   │ REST API
   ↓
Spring Boot
   │
   ↓
MyBatis-Plus
   │
   ↓
MySQL

Spring Boot
   │
   ↓
File Storage
```

业务层需要明确工作空间边界：

```Plain
用户
  ↓
工作空间成员关系
  ↓
工作空间
  ↓
Bug
  ↓
附件 / 评论 / 验收 / 操作日志
```

Bug 查询、创建、指派、验收等操作都必须在工作空间范围内执行。

第一阶段不引入：

```Plain
Spring Cloud

Kafka

Elasticsearch

复杂MQ

微服务
```

避免过度设计。

---

## 二十四、数据库设计

主要表：

```Plain
sys_user

workspace

workspace_member

bug

bug_attachment

bug_comment

bug_operation_log

workspace_operation_log

bug_description_history

bug_acceptance
```

其中：

```Plain
workspace
```

主要用于保存工作空间基础信息：

```Plain
id
name
description
owner_id
status
created_at
updated_at
```

```Plain
workspace_member
```

主要用于维护用户和工作空间之间的成员关系：

```Plain
id
workspace_id
user_id
role
joined_at
```

角色第一期统一为：

```Plain
OWNER
ADMIN
MEMBER
```

Bug 主表主要字段：

```Plain
id

workspace_id

bug_no

title

description_md

priority

status

creator_id

assignee_id

acceptor_id

fix_description_md

created_at

updated_at

closed_at
```

---

## 二十五、API设计

建议采用业务语义明确的接口。

工作空间接口：

```Plain
POST /api/workspaces
创建工作空间

GET /api/workspaces
查询当前用户加入的工作空间

GET /api/workspaces/{id}
工作空间详情

PUT /api/workspaces/{id}
修改工作空间

POST /api/workspaces/{id}/disable
停用工作空间

POST /api/workspaces/{id}/enable
重新启用工作空间

GET /api/workspaces/{id}/members
工作空间成员列表

POST /api/workspaces/{id}/members
添加工作空间成员

DELETE /api/workspaces/{id}/members/{userId}
移除工作空间成员
```

Bug 接口建议明确工作空间归属：

```Plain
POST /api/workspaces/{workspaceId}/bugs
创建Bug

GET /api/workspaces/{workspaceId}/bugs
Bug列表

GET /api/bugs/{id}
Bug详情

PUT /api/bugs/{id}
修改Bug

POST /api/bugs/{id}/assign
指派

POST /api/bugs/{id}/start
开始处理

POST /api/bugs/{id}/submit
提交验收

POST /api/bugs/{id}/accept
验收通过

POST /api/bugs/{id}/reject
验收失败

POST /api/bugs/{id}/comments
评论

POST /api/bugs/{id}/attachments
附件

GET /api/bugs/{id}/logs
操作记录
```

状态不能由前端直接随意修改。

---

## 二十六、权限与安全

一期权限校验分为两层：

```Plain
第一层：当前用户是否属于目标工作空间
        ↓
第二层：当前用户在工作空间中的角色，以及其与 Bug 的关系
```

如果用户不是当前工作空间成员，则默认不能访问该工作空间下的 Bug 数据。

一期主要考虑：

```Plain
谁可以创建工作空间

谁可以管理工作空间成员

谁可以创建Bug

谁可以修改Bug

谁可以指派

谁可以处理

谁可以验收

谁可以查看工作空间全部Bug
```

同时考虑：

- 文件大小限制；
- 文件类型限制；
- SQL 注入；
- XSS；
- Markdown HTML 安全过滤；
- 接口权限；
- 登录鉴权。

---

## 二十七、测试方案

主要包括：

#### 功能测试

测试：

```Plain
工作空间创建
工作空间切换
成员添加/移除
成员角色修改
工作空间数据隔离
Bug提交
Markdown
附件
指派
状态
修复
验收
评论
日志
```

#### 状态流转测试

例如：

```Plain
待处理 → 已关闭
```

属于非法转换，必须被后端拒绝。

#### 权限测试

测试不同人员是否只能执行允许的操作。

#### 文件测试

测试：

```Plain
大文件
重复文件
错误格式
上传失败
预览
下载
删除
```

---

## 二十八、部署方案

第一阶段：

```Plain
Nginx
   ↓
Vue

Spring Boot

MySQL

附件目录
```

也可以采用：

```Plain
Docker Compose
```

统一部署。

---

## 二十九、项目实施流程

完整项目实施过程调整为：

```Plain
1. 项目背景调研
        ↓
2. 当前问题分析
        ↓
3. 用户需求访谈
        ↓
4. 需求确认
        ↓
5. 一期范围确定
        ↓
6. 开源产品 / 技术方案调研
        ↓
7. 禅道 / Redmine / MantisBT / Plane 对比
        ↓
8. 自研与二开成本评估
        ↓
9. 确定自研方案
        ↓
10. Bug业务流程设计
        ↓
11. 页面原型设计
        ↓
12. 技术选型
        ↓
13. 数据库设计
        ↓
14. API设计
        ↓
15. 前后端开发
        ↓
16. 联调
        ↓
17. 功能 / 权限 / 状态测试
        ↓
18. 内部试用
        ↓
19. 收集使用反馈
        ↓
20. 问题修复和优化
        ↓
21. 正式部署
        ↓
22. 项目验收
        ↓
23. 项目复盘
        ↓
24. 二期需求评估
```

这一流程比直接：

```Plain
需求 → 开发
```

更能够体现完整的软件项目实施过程。

---

## 三十、项目开发周期

采用 Vibe Coding 辅助开发，预计：

#### 第一阶段

```Plain
Day 1-2

需求最终确认
系统设计
项目初始化
数据库
用户模块
工作空间基础模块
成员关系与角色
```

#### 第二阶段

```Plain
Day 3-4

工作空间切换
成员管理
Bug列表
Bug创建
Bug详情
Markdown
```

#### 第三阶段

```Plain
Day 5-6

附件
人员指派
优先级
状态流转
```

#### 第四阶段

```Plain
Day 7-8

修复说明
验收
评论
日志
Markdown历史版本
```

#### 第五阶段

```Plain
Day 9-10

联调
异常处理
权限检查
基础测试
```

之后预留：

```Plain
3～5个工作日
```

进行：

```Plain
内部试用
反馈
Bug修复
部署
```

正式项目周期建议：

> **2～3周。**

---

## 三十二、项目风险

### 需求范围不断扩大

最容易出现：

```Plain
Bug系统
↓
任务系统
↓
项目管理
↓
研发管理平台
```

因此一期必须严格控制 Scope。

---

### 工作空间边界设计不清

如果工作空间既代表部门、又代表项目、又代表业务系统，后续使用中容易产生概念混乱。

第一期需要明确：

> 工作空间主要用于人员组织和 Bug 数据隔离，不承担完整项目管理职责。

---

### 使用习惯问题

员工可能仍然习惯微信群直接反馈。

因此建议原则是：

> 微信可以继续沟通，但是正式需要跟踪的问题必须进入 Bug 系统。

---

### 自研维护成本

选择自研意味着系统的：

```Plain
安全
数据
升级
Bug
维护
```

由内部承担。

因此第一期需要坚持简单架构，避免过度设计。

---

### Vibe Coding风险

AI 可以提升：

```Plain
CRUD
页面
SQL
接口
```

开发速度。

但是：

```Plain
状态机
权限
安全
异常处理
数据一致性
```

必须人工重点检查。

---

## 三十三、后续迭代方向

一期稳定以后再考虑：

```Plain
企业微信提醒

GitLab / Gitea集成

Bug关联Commit

Bug关联MR

代码修改文件统计

新增/删除代码统计

Bug Dashboard

Bug趋势

平均处理时间

高频问题模块

重新打开率
```

不在第一期一次性开发。

---
