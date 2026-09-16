# 辅助脚本目录

本目录用于保存可复用的本地开发、构建和部署脚本。脚本必须说明用途、所需环境和可能产生的外部影响，不在这里放置一次性临时脚本。

## 本地前后端联调

`dev.sh` 统一管理本地前后端开发进程，PID 和日志保存在已被 Git 忽略的
`data/dev-runtime/`。脚本只停止自己启动的进程，检测到未跟踪进程占用端口时不会自动终止它。

```bash
./scripts/dev.sh start
./scripts/dev.sh status
./scripts/dev.sh restart
./scripts/dev.sh logs
./scripts/dev.sh stop
```

默认使用后端 `8080`、前端 `5173`；可以通过 `SERVER_PORT` 和 `FRONTEND_PORT` 临时覆盖。
