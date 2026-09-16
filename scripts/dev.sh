#!/usr/bin/env bash
# 本脚本统一管理 BugLoop 本地前后端开发进程，提供启动、停止、重启、状态查询和日志跟踪能力。
# 脚本只终止自身记录的 PID；端口被其他进程占用时仅报告冲突，避免误杀无关服务。

set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="${project_dir}/data/dev-runtime"
backend_pid_file="${runtime_dir}/backend.pid"
frontend_pid_file="${runtime_dir}/frontend.pid"
backend_log_file="${runtime_dir}/backend.log"
frontend_log_file="${runtime_dir}/frontend.log"
env_file="${project_dir}/.env"

# 本地开发变量与 Docker Compose 共用 .env；不存在时沿用应用内置默认值。
if [[ -f "${env_file}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
fi

backend_port="${SERVER_PORT:-8080}"
frontend_port="${FRONTEND_PORT:-5173}"
backend_health_url="http://127.0.0.1:${backend_port}/actuator/health"
frontend_health_url="http://127.0.0.1:${frontend_port}/"

# 相对附件目录统一按仓库根目录解析，避免后端工作目录改变实际存储位置。
if [[ -n "${BUGLOOP_STORAGE_ROOT:-}" && "${BUGLOOP_STORAGE_ROOT}" != /* ]]; then
  export BUGLOOP_STORAGE_ROOT="${project_dir}/${BUGLOOP_STORAGE_ROOT#./}"
fi

mkdir -p "${runtime_dir}"

print_usage() {
  cat <<'EOF'
用法：./scripts/dev.sh <命令> [服务]

命令：
  start              启动后端和前端，并等待健康检查通过
  stop               停止由本脚本启动的前端和后端
  restart            重启前后端
  status             查询 PID、端口与健康状态（默认命令）
  logs [all]         同时跟踪前后端日志
  logs backend       跟踪后端日志
  logs frontend      跟踪前端日志
  help               显示帮助

可选环境变量：
  SERVER_PORT         后端端口，默认 8080
  FRONTEND_PORT       前端端口，默认 5173
  VITE_API_PROXY_TARGET  前端代理目标，默认跟随后端端口
EOF
}

require_command() {
  local command_name="$1"
  local description="$2"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "错误：未找到 ${description}（${command_name}）。" >&2
    return 1
  fi
}

read_pid() {
  local pid_file="$1"
  if [[ -f "${pid_file}" ]]; then
    tr -d '[:space:]' < "${pid_file}"
  fi
}

is_pid_running() {
  local pid="${1:-}"
  [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null
}

listener_pids() {
  local port="$1"
  lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | paste -sd ',' - || true
}

is_healthy() {
  local url="$1"
  curl --silent --show-error --fail --max-time 2 "${url}" >/dev/null 2>&1
}

# 清理已退出进程留下的 PID 文件，端口检查仍会识别未被脚本管理的监听者。
clear_stale_pid_file() {
  local pid_file="$1"
  local pid
  pid="$(read_pid "${pid_file}")"
  if [[ -n "${pid}" ]] && ! is_pid_running "${pid}"; then
    rm -f "${pid_file}"
  fi
}

ensure_port_available() {
  local service_name="$1"
  local pid_file="$2"
  local port="$3"
  local pid listeners

  clear_stale_pid_file "${pid_file}"
  pid="$(read_pid "${pid_file}")"
  if is_pid_running "${pid}"; then
    return 0
  fi

  listeners="$(listener_pids "${port}")"
  if [[ -n "${listeners}" ]]; then
    echo "错误：${service_name}端口 ${port} 已被未跟踪进程占用，PID：${listeners}。" >&2
    echo "请先确认并停止该进程，或通过环境变量修改端口。" >&2
    return 1
  fi
}

start_backend() {
  local pid
  pid="$(read_pid "${backend_pid_file}")"
  if is_pid_running "${pid}"; then
    echo "后端已由本脚本启动，PID：${pid}。"
    return
  fi

  : > "${backend_log_file}"
  (
    cd "${project_dir}/backend"
    nohup env SERVER_PORT="${backend_port}" ./mvnw spring-boot:run \
      >> "${backend_log_file}" 2>&1 < /dev/null &
    echo "$!" > "${backend_pid_file}"
  )
  echo "正在启动后端，日志：${backend_log_file}"
}

start_frontend() {
  local pid api_target
  pid="$(read_pid "${frontend_pid_file}")"
  if is_pid_running "${pid}"; then
    echo "前端已由本脚本启动，PID：${pid}。"
    return
  fi

  api_target="${VITE_API_PROXY_TARGET:-http://127.0.0.1:${backend_port}}"
  : > "${frontend_log_file}"
  (
    cd "${project_dir}/frontend"
    nohup env VITE_API_PROXY_TARGET="${api_target}" pnpm dev \
      --host 0.0.0.0 --port "${frontend_port}" --strictPort \
      >> "${frontend_log_file}" 2>&1 < /dev/null &
    echo "$!" > "${frontend_pid_file}"
  )
  echo "正在启动前端，日志：${frontend_log_file}"
}

wait_until_ready() {
  local service_name="$1"
  local pid_file="$2"
  local health_url="$3"
  local log_file="$4"
  local timeout_seconds="${5:-60}"
  local elapsed pid

  for ((elapsed = 0; elapsed < timeout_seconds; elapsed++)); do
    pid="$(read_pid "${pid_file}")"
    if ! is_pid_running "${pid}"; then
      echo "错误：${service_name}启动进程已退出，请查看日志：${log_file}" >&2
      tail -n 30 "${log_file}" >&2 || true
      rm -f "${pid_file}"
      return 1
    fi
    if is_healthy "${health_url}"; then
      echo "${service_name}已就绪：${health_url}"
      return 0
    fi
    sleep 1
  done

  echo "错误：等待${service_name}就绪超时（${timeout_seconds} 秒），请查看日志：${log_file}" >&2
  tail -n 30 "${log_file}" >&2 || true
  return 1
}

status_service() {
  local service_name="$1"
  local pid_file="$2"
  local port="$3"
  local health_url="$4"
  local pid listeners

  clear_stale_pid_file "${pid_file}"
  pid="$(read_pid "${pid_file}")"
  if is_pid_running "${pid}"; then
    if is_healthy "${health_url}"; then
      printf '[正常] %s：PID=%s，端口=%s，地址=%s\n' "${service_name}" "${pid}" "${port}" "${health_url}"
      return 0
    fi
    printf '[启动中/异常] %s：PID=%s，端口=%s，健康检查未通过\n' "${service_name}" "${pid}" "${port}"
    return 1
  fi

  listeners="$(listener_pids "${port}")"
  if [[ -n "${listeners}" ]]; then
    printf '[未跟踪] %s：端口=%s 被其他进程占用，PID=%s\n' "${service_name}" "${port}" "${listeners}"
  else
    printf '[已停止] %s：端口=%s 未监听\n' "${service_name}" "${port}"
  fi
  return 1
}

status_all() {
  local failed=0
  status_service "后端" "${backend_pid_file}" "${backend_port}" "${backend_health_url}" || failed=1
  status_service "前端" "${frontend_pid_file}" "${frontend_port}" "${frontend_health_url}" || failed=1
  return "${failed}"
}

# 收集子进程后与父进程一起退出，避免 pnpm 或 Maven 的子进程遗留并继续占用端口。
collect_descendants() {
  local parent_pid="$1"
  local child_pid
  while IFS= read -r child_pid; do
    [[ -n "${child_pid}" ]] || continue
    collect_descendants "${child_pid}"
    echo "${child_pid}"
  done < <(pgrep -P "${parent_pid}" 2>/dev/null || true)
}

stop_service() {
  local service_name="$1"
  local pid_file="$2"
  local pid descendants all_pids remaining_pid

  pid="$(read_pid "${pid_file}")"
  if ! is_pid_running "${pid}"; then
    rm -f "${pid_file}"
    echo "${service_name}未由本脚本运行。"
    return
  fi

  descendants="$(collect_descendants "${pid}")"
  all_pids="${pid} ${descendants}"
  # 先发送 TERM 让开发服务释放资源，超时后只强制结束本次记录的进程树。
  kill ${all_pids} 2>/dev/null || true
  for _ in {1..10}; do
    remaining_pid=""
    for pid in ${all_pids}; do
      if is_pid_running "${pid}"; then
        remaining_pid="${pid}"
        break
      fi
    done
    [[ -z "${remaining_pid}" ]] && break
    sleep 1
  done

  for pid in ${all_pids}; do
    if is_pid_running "${pid}"; then
      kill -KILL "${pid}" 2>/dev/null || true
    fi
  done
  rm -f "${pid_file}"
  echo "${service_name}已停止。"
}

start_all() {
  require_command lsof "端口查询工具" || return 1
  require_command curl "健康检查工具" || return 1
  require_command java "Java 运行环境" || return 1
  require_command pnpm "pnpm" || return 1
  [[ -x "${project_dir}/backend/mvnw" ]] || {
    echo "错误：backend/mvnw 不存在或不可执行。" >&2
    return 1
  }
  [[ -d "${project_dir}/frontend/node_modules" ]] || {
    echo "错误：前端依赖尚未安装，请先执行：cd frontend && pnpm install" >&2
    return 1
  }

  # 两个端口都通过预检后才启动，避免只启动一半服务。
  ensure_port_available "后端" "${backend_pid_file}" "${backend_port}" || return 1
  ensure_port_available "前端" "${frontend_pid_file}" "${frontend_port}" || return 1
  start_backend
  start_frontend

  local failed=0
  wait_until_ready "后端" "${backend_pid_file}" "${backend_health_url}" "${backend_log_file}" 60 || failed=1
  wait_until_ready "前端" "${frontend_pid_file}" "${frontend_health_url}" "${frontend_log_file}" 60 || failed=1
  echo
  status_all || true
  return "${failed}"
}

stop_all() {
  stop_service "前端" "${frontend_pid_file}"
  stop_service "后端" "${backend_pid_file}"
}

follow_logs() {
  local target="${1:-all}"
  case "${target}" in
    backend)
      [[ -f "${backend_log_file}" ]] || { echo "后端日志尚未生成。" >&2; return 1; }
      tail -n 200 -f "${backend_log_file}"
      ;;
    frontend)
      [[ -f "${frontend_log_file}" ]] || { echo "前端日志尚未生成。" >&2; return 1; }
      tail -n 200 -f "${frontend_log_file}"
      ;;
    all)
      [[ -f "${backend_log_file}" || -f "${frontend_log_file}" ]] || {
        echo "前后端日志尚未生成。" >&2
        return 1
      }
      touch "${backend_log_file}" "${frontend_log_file}"
      tail -n 100 -f "${backend_log_file}" "${frontend_log_file}"
      ;;
    *)
      echo "错误：未知日志目标 ${target}，可选 all、backend、frontend。" >&2
      return 1
      ;;
  esac
}

action="${1:-status}"
case "${action}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    start_all
    ;;
  status)
    status_all
    ;;
  logs)
    follow_logs "${2:-all}"
    ;;
  help|-h|--help)
    print_usage
    ;;
  *)
    echo "错误：未知命令 ${action}。" >&2
    print_usage >&2
    exit 2
    ;;
esac
