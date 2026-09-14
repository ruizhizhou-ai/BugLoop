/**
 * 本文件配置前端单元测试环境，并复用 Vite 的路径别名和 Vue 插件设置。
 */
import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/__tests__/setup.ts'],
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      server: {
        deps: {
          // Element Plus 的组件样式需要交给 Vite 转换，Node.js 无法直接加载 CSS 文件。
          inline: [/element-plus/],
        },
      },
    },
  }),
)
