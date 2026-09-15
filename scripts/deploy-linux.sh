#!/usr/bin/env bash
# 本脚本在单台 Linux 服务器上校验生产变量、构建镜像并启动 BugLoop 全部服务，可安全重复执行用于升级。

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${1:-${project_dir}/.env.production}"
compose_file="${project_dir}/compose.production.yaml"

# 部署依赖 Docker Compose v2；提前失败可以避免只启动一半服务。
command -v docker >/dev/null 2>&1 || {
  echo "错误：未安装 Docker Engine。" >&2
  exit 1
}
docker compose version >/dev/null 2>&1 || {
  echo "错误：未安装 Docker Compose v2 插件。" >&2
  exit 1
}

if [[ ! -f "${env_file}" ]]; then
  echo "错误：环境变量文件不存在：${env_file}" >&2
  echo "请先执行：cp .env.production.example .env.production" >&2
  exit 1
fi

# 示例密码不能进入真实环境，避免误把公开默认值部署到服务器。
if grep -q "CHANGE_ME" "${env_file}"; then
  echo "错误：请先替换 ${env_file} 中所有 CHANGE_ME 配置。" >&2
  exit 1
fi

cd "${project_dir}"

# 先完整解析 Compose，变量缺失或语法错误时不会变更已有服务。
docker compose --env-file "${env_file}" -f "${compose_file}" config >/dev/null
docker compose --env-file "${env_file}" -f "${compose_file}" up -d --build --remove-orphans
docker compose --env-file "${env_file}" -f "${compose_file}" ps

web_address="$(docker compose --env-file "${env_file}" -f "${compose_file}" port frontend 80 2>/dev/null || true)"
echo "BugLoop 已启动，Web 监听地址：${web_address:-请查看上方 frontend 端口映射}。"
