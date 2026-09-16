# BugLoop

BugLoop 是面向公司内部使用的轻量级 Bug 收集、处理、验收和追踪系统。

当前仓库采用单仓库、前后端分离结构：后端为 Spring Boot 单体应用，前端为 Vue 3 单页应用，MySQL 保存业务数据，本地文件系统保存一期附件。

已实现 Milestone 1：数据库基线（Flyway 管理全部业务表）、注册登录、Sa-Token 会话鉴权与统一响应/异常处理。

## 项目结构

```text
backend/    Spring Boot 后端
frontend/   Vue 3 前端
deploy/     Docker 与 Nginx 部署配置
docs/       产品、技术与实现 Spec
scripts/    辅助脚本
data/       本地运行数据，不提交到版本库
```

完整规范入口见 [BugLoop Spec](BugLoop_SPEC_v1.0.md)。

## 环境要求

- Java 21
- Maven 3.6.3+
- Node.js 22.18+ 或 24.12+
- pnpm 11+
- Docker 与 Docker Compose

## 本地启动

复制环境变量示例并按需修改：

```bash
cp .env.example .env
```

启动 MySQL：

```bash
docker compose up -d mysql
```

启动后端：

```bash
cd backend
./mvnw spring-boot:run
```

启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

也可以在仓库根目录使用开发联调脚本统一管理前后端进程：

```bash
./scripts/dev.sh start    # 启动并等待前后端就绪
./scripts/dev.sh status   # 查询 PID、端口和健康状态
./scripts/dev.sh restart  # 重启前后端
./scripts/dev.sh logs     # 跟踪前后端日志
./scripts/dev.sh stop     # 停止本脚本启动的进程
```

前端默认地址为 `http://localhost:5173`，后端健康检查地址为 `http://localhost:8080/actuator/health`。

## 鉴权说明

- 登录方式为用户名 + 密码，密码使用 BCrypt 哈希保存。
- 登录凭证通过 `Authorization: Bearer <token>` 请求头传递，一段时间不活跃后需要重新登录。
- 注册默认角色为 `USER`；开启 `BUGLOOP_ADMIN_BOOTSTRAP_ENABLED` 时，应用启动会创建内置系统管理员
  （默认 `admin` / `admin@123`，可用 `BUGLOOP_ADMIN_USERNAME`、`BUGLOOP_ADMIN_PASSWORD` 覆盖）。
- 未登录或凭证失效统一返回 `401 + 40101`，错误码定义见 [API Spec](docs/05-api-spec.md)。

## 验证命令

```bash
cd backend && ./mvnw test
cd frontend && pnpm test:unit --run && pnpm build
```

后端测试包含两组用例：

- 单元测试使用 H2 内存库，并执行与生产完全相同的 Flyway 迁移脚本；
- 集成测试（`*IT`）通过 Testcontainers 启动真实 MySQL，验证迁移结果与完整认证链路，需要本机 Docker 可用，否则自动跳过。

## Linux 部署

仓库提供独立的生产 Compose 和一键部署脚本，服务器只需安装 Docker Engine、Docker Compose v2 与 Git。前置条件、初始化管理员、HTTPS 和备份说明见 [Linux 部署文档](docs/08-linux-deployment.md)。
