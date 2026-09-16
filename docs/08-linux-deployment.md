# BugLoop 单机 Linux 部署

本文档用于将 BugLoop 直接部署到一台 Linux 服务器。部署采用 Docker Compose，服务器不需要单独安装 Java、Maven、Node.js、pnpm、MySQL 或 Nginx。

## 一、服务器前置条件

- 64 位 Linux，推荐仍在安全支持期内的 Ubuntu LTS、Debian stable 或同等级发行版。
- 最低 2 核 CPU、2GB 内存；建议 2 核 CPU、4GB 内存及以上。首次构建前后端镜像时内存峰值较高。
- 至少 10GB 可用磁盘，附件和数据库增长空间另行预留。
- Docker Engine 与 Docker Compose v2 插件。
- 服务器能够从配置的容器镜像仓库拉取 MySQL、Maven、Eclipse Temurin、Node.js 和 Nginx 基础镜像；第三方镜像加速器失效时需切换到可用镜像源。
- Git；若使用私有仓库，还需要服务器 SSH Key 或其他仓库凭据。
- 防火墙开放 SSH 端口和 Web 端口。直接使用 HTTP 时开放 80；配置 HTTPS 时开放 80、443。若需要外部数据库客户端连接，还需仅向可信来源 IP 开放 3306。
- 可选域名。公网或跨不可信网络使用时必须配置 HTTPS，不建议直接传输登录 Token。

生产 Compose 默认将 MySQL 映射为宿主机 `3306`，后端 `8080` 不对外开放，前端 Nginx 映射为 Web 入口。数据库存放在 `mysql-data` 命名卷，附件存放在 `attachment-data` 命名卷，容器升级不会删除数据。

如仅需服务器本机通过 MySQL 客户端运维，可在 `.env.production` 中将 `MYSQL_BIND_ADDRESS` 改为 `127.0.0.1`；如需外部连接，则保持 `0.0.0.0`，并在云安全组或服务器防火墙中只放行可信 IP，禁止向全网开放 3306。

## 二、首次直接部署

在服务器拉取代码并进入项目目录：

```bash
git clone git@github.com:ruizhizhou-ai/BugLoop.git
cd BugLoop
```

创建生产环境变量：

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

生成随机密码，并分别填写到 `.env.production` 的 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`：

```bash
openssl rand -hex 24
openssl rand -hex 24
```

执行部署：

```bash
chmod +x scripts/deploy-linux.sh
./scripts/deploy-linux.sh
```

脚本会构建前后端镜像、等待 MySQL 就绪、自动执行 Flyway 迁移并启动服务。完成后访问 `http://服务器IP`。

## 三、初始化管理员

`BUGLOOP_ADMIN_BOOTSTRAP_ENABLED=true`（默认）时，后端启动会自动创建内置系统管理员：
用户名与初始密码取自 `BUGLOOP_ADMIN_USERNAME` / `BUGLOOP_ADMIN_PASSWORD`（默认 `admin` / `admin@123`）。
账号已存在时跳过，重复部署不会重复创建，也不会覆盖已经修改过的密码。

建议首次部署前就先把 `.env.production` 里的 `BUGLOOP_ADMIN_PASSWORD` 改成强密码；
配置只在账号首次创建时生效，账号创建后再改这个变量不会同步密码，需要重置时先删除该账号再重新部署。
注册页开放的是普通用户自助注册，不会再产生系统管理员。

## 四、HTTPS 与域名

如果服务器已有 Caddy、Nginx、Traefik 或云负载均衡作为 HTTPS 入口：

1. 将域名 A/AAAA 记录指向服务器。
2. 把 `.env.production` 中 `HTTP_BIND_ADDRESS` 改为 `127.0.0.1`，避免容器 HTTP 端口绕过 HTTPS 入口。
3. 将外层反向代理转发到 `http://127.0.0.1:80`。
4. 证书交给外层反向代理自动签发或续期。

## 五、验证与运维

查看服务状态：

```bash
docker compose --env-file .env.production -f compose.production.yaml ps
```

查看后端日志：

```bash
docker compose --env-file .env.production -f compose.production.yaml logs -f backend
```

在服务器内部验证健康检查：

```bash
docker compose --env-file .env.production -f compose.production.yaml exec backend \
  java -version
curl -fsS http://127.0.0.1/api/actuator/health
```

更新部署：

```bash
git pull --ff-only
./scripts/deploy-linux.sh
```

停止服务但保留数据：

```bash
docker compose --env-file .env.production -f compose.production.yaml down
```

不要在生产环境执行 `down -v`，该参数会删除数据库和附件持久卷。

## 六、备份要求

至少备份以下两部分，并把备份复制到服务器之外：

- MySQL：定期通过 `mysqldump` 导出 `MYSQL_DATABASE`。
- 附件：定期备份 `attachment-data` Docker 命名卷。

数据库与附件应处于同一备份时间点。只恢复数据库而不恢复附件，会出现元数据存在但文件丢失；只恢复附件而不恢复数据库，则文件无法被业务记录引用。
