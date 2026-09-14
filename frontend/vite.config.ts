/**
 * 本文件配置 Vue 构建、源码别名和本地 API 代理，保证开发环境无需额外处理跨域。
 */
import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

/**
 * 构建与单元测试共用的 Vue 插件和路径别名。
 * 独立导出普通对象，避免 Vitest 合并动态配置回调时启动失败。
 */
export const sharedViteConfig = {
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    ...sharedViteConfig,
    server: {
      port: 5173,
      proxy: {
        '/api': {
          // 允许联调或 CI 使用隔离端口，默认仍代理到本地后端 8080。
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
