# BugLoop

BugLoop 是面向公司内部使用的轻量级 Bug 收集、处理、验收和追踪系统。

当前仓库采用单仓库、前后端分离结构：后端为 Spring Boot 单体应用，前端为 Vue 3 单页应用，MySQL 保存业务数据，本地文件系统保存一期附件。

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

前端默认地址为 `http://localhost:5173`，后端健康检查地址为 `http://localhost:8080/actuator/health`。

## 验证命令

```bash
cd backend && ./mvnw test
cd frontend && pnpm test:unit --run && pnpm build
```

