<!-- 本文件提供全局主题选择控件，放置在页面右上角，让用户随时切换浅色或深色工作台。 -->
<script setup lang="ts">
import { ref } from 'vue'

import AppIcon from './AppIcon.vue'
import { useTheme } from '@/shared/theme/theme'
import type { AppTheme } from '@/shared/theme/theme'

const { theme, label, setTheme, toggleTheme } = useTheme()
const menuOpen = ref(false)

/**
 * 选择指定主题并关闭菜单，立即让根节点颜色变量重新计算。
 *
 * @param value 用户选择的主题
 */
function selectTheme(value: AppTheme): void {
  setTheme(value)
  menuOpen.value = false
}
</script>

<template>
  <div class="theme-toggle">
    <button
      type="button"
      class="theme-toggle__button"
      :title="`当前为${label}模式，点击切换`"
      :aria-label="`当前为${label}模式，点击切换`"
      @click="toggleTheme"
      @contextmenu.prevent="menuOpen = !menuOpen"
    >
      <AppIcon :name="theme === 'dark' ? 'moon' : 'sun'" :size="19" />
    </button>
    <button
      type="button"
      class="theme-toggle__more"
      title="选择主题"
      aria-label="选择主题"
      @click="menuOpen = !menuOpen"
    >
      <AppIcon name="chevron-down" :size="14" />
    </button>
    <div v-if="menuOpen" class="theme-toggle__menu">
      <span>主题设置</span>
      <button
        type="button"
        :class="{ 'theme-toggle__option--active': theme === 'light' }"
        @click="selectTheme('light')"
      >
        <AppIcon name="sun" :size="16" /> 浅色
      </button>
      <button
        type="button"
        :class="{ 'theme-toggle__option--active': theme === 'dark' }"
        @click="selectTheme('dark')"
      >
        <AppIcon name="moon" :size="16" /> 深色
      </button>
    </div>
  </div>
</template>

<style scoped>
.theme-toggle {
  position: relative;
  display: flex;
  height: 37px;
  color: var(--bl-toolbar-icon);
  background: var(--bl-control-bg);
  border: 1px solid var(--bl-control-border);
  border-radius: 8px;
}

.theme-toggle__button,
.theme-toggle__more {
  display: grid;
  height: 100%;
  place-items: center;
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.theme-toggle__button {
  width: 34px;
}

.theme-toggle__more {
  width: 20px;
  border-left: 1px solid var(--bl-control-border);
}

.theme-toggle__button:hover,
.theme-toggle__more:hover {
  color: var(--bl-primary);
  background: var(--bl-control-hover);
}

.theme-toggle__menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 50;
  display: grid;
  width: 142px;
  gap: 3px;
  padding: 9px;
  color: var(--bl-text);
  background: var(--bl-overlay);
  border: 1px solid var(--bl-border-strong);
  border-radius: 9px;
  box-shadow: var(--bl-overlay-shadow);
}

.theme-toggle__menu > span {
  padding: 4px 7px 7px;
  color: var(--bl-muted);
  font-size: 11px;
}

.theme-toggle__menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 7px;
  color: var(--bl-text-secondary);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.theme-toggle__menu button:hover,
.theme-toggle__option--active {
  color: var(--bl-primary);
  background: var(--bl-control-hover) !important;
}
</style>
