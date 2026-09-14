# BugLoop 前端

本目录是 BugLoop 的 Vue 3 单页应用，负责登录、工作空间切换、Bug 管理和验收交互。

## 常用命令

```bash
pnpm install
pnpm dev
pnpm test:unit --run
pnpm lint
pnpm build
```

业务代码按 `auth`、`workspace` 和 `bug` 模块组织；跨模块复用的组件、请求和工具放在 `src/shared`。

