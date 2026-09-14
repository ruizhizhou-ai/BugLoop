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

`workspace/` 模块采用以下分层，后续 `bug/` 等业务模块沿用同一约定：

```text
controller/     REST 控制器
service/        业务服务、事务和权限校验
mapper/         MyBatis 数据访问入口
entity/         数据库实体和业务枚举
dto/            请求参数对象
vo/             接口响应对象
```

`auth/` 模块同样按 `controller / service / dto / vo` 分层；认证复用 `user/` 模块的用户实体与 Mapper，不重复维护账号持久化对象。

`user/` 模块按 `controller / service / mapper / entity / vo` 分层；当前只有“读取当前用户”接口，没有独立请求参数，因此暂不创建空的 `dto/` 目录。

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
POST   /api/workspaces/{workspaceId}/enable
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
