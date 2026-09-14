# 本文件构建 Vue 静态资源，并通过 Nginx 提供页面和反向代理入口。
FROM node:24-alpine AS builder
WORKDIR /workspace/frontend
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend .
RUN pnpm build

FROM nginx:1.29-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 80

