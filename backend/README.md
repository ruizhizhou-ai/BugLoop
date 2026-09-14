# BugLoop 后端

本目录是公司 `wjfz` 的 BugLoop Spring Boot 单体应用，Maven 坐标和 Java 根包统一使用 `com.wjfz:bugloop` 与 `com.wjfz.bugloop`。

业务代码按功能分包：

```text
com.wjfz.bugloop
├── common/       统一响应、异常、安全与校验基础能力
├── config/       Spring 和 MyBatis 等框架配置
├── auth/         注册登录与 Sa-Token 会话
├── user/         系统用户
├── workspace/    工作空间、成员和角色
├── bug/          Bug、附件、评论、验收、历史与操作日志
└── file/         本地文件存储抽象
```

状态机、权限和事务必须在 Service 层执行，Controller 不直接修改数据库，Entity 不直接作为 API 请求或响应对象。

## Workspace 接口

Milestone 2 已提供以下受登录保护的接口：

```text
POST   /api/workspaces
GET    /api/workspaces
GET    /api/workspaces/{workspaceId}
PUT    /api/workspaces/{workspaceId}
GET    /api/workspaces/{workspaceId}/members
POST   /api/workspaces/{workspaceId}/members
PUT    /api/workspaces/{workspaceId}/members/{userId}/role
DELETE /api/workspaces/{workspaceId}/members/{userId}
POST   /api/workspaces/{workspaceId}/disable
```

工作空间切换不在服务端保存隐式状态。前端从 `GET /api/workspaces` 选择空间后，使用
`GET /api/workspaces/{workspaceId}` 校验访问权限，并把 `workspaceId` 保存到 URL 或前端 Store；
后续所有 Bug 接口都必须显式携带该 ID，以保证数据隔离边界清晰。

## 常用命令

```bash
./mvnw test
./mvnw spring-boot:run
./mvnw clean package
```
