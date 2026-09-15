/**
 * 本文件集中管理界面主题状态，并将用户选择同步到根节点与浏览器存储。
 * 所有页面只读取这一份状态，避免局部切换导致侧栏、弹窗和表格主题不一致。
 */
import { computed, ref } from 'vue'

export type AppTheme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'bugloop.theme'
const savedTheme = readSavedTheme()
// 参考设计以深色工作台为默认视觉；只有用户主动选择后才切换并记住浅色模式。
const theme = ref<AppTheme>(savedTheme ?? 'dark')

/** 在模块初始化时先写入根节点，避免首屏按默认色闪烁。 */
applyTheme(theme.value)

/** 读取用户显式选择；非法值会被忽略并交给系统偏好决定。 */
function readSavedTheme(): AppTheme | null {
  const value = localStorage.getItem(THEME_STORAGE_KEY)
  return value === 'light' || value === 'dark' ? value : null
}

/**
 * 把主题标记写入 html 元素，CSS 变量和 Element Plus 可以在同一渲染周期响应。
 *
 * @param value 要应用的主题
 */
function applyTheme(value: AppTheme): void {
  document.documentElement.dataset.theme = value
  document.documentElement.style.colorScheme = value
}

/**
 * 返回全局主题状态和切换方法。
 *
 * @return 主题值、中文标签、设置与切换函数
 */
export function useTheme() {
  const label = computed(() => (theme.value === 'dark' ? '深色' : '浅色'))

  /** 设置主题并持久化，避免刷新后回到系统默认主题。 */
  function setTheme(value: AppTheme): void {
    theme.value = value
    localStorage.setItem(THEME_STORAGE_KEY, value)
    applyTheme(value)
  }

  /** 在两种主题之间切换，供紧凑的顶部工具栏直接调用。 */
  function toggleTheme(): void {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, label, setTheme, toggleTheme }
}
