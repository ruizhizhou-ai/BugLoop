<!-- 本文件提供 BugLoop 工作台统一使用的线性图标，避免页面分别维护重复 SVG，并保证深色主题下的视觉一致性。 -->
<script setup lang="ts">
/**
 * 图标名称只覆盖当前产品导航和核心操作，新增名称时应同时补充对应 SVG 路径。
 */
defineProps<{
  name:
    | 'home'
    | 'box'
    | 'bugs'
    | 'submitted'
    | 'assigned'
    | 'acceptance'
    | 'users'
    | 'settings'
    | 'search'
    | 'bell'
    | 'help'
    | 'plus'
    | 'arrow-right'
    | 'edit'
    | 'logout'
    | 'menu'
    | 'close'
    | 'sun'
    | 'moon'
    | 'chevron-down'
    | 'external-link'
    | 'priority'
    | 'calendar'
    | 'clock'
    | 'paperclip'
    | 'filter'
    | 'alert'
  size?: number
}>()
</script>

<template>
  <svg
    class="app-icon"
    :width="size ?? 20"
    :height="size ?? 20"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'home'">
      <path d="m3 11 9-8 9 8" />
      <path d="M5 10v10h14V10M9 20v-6h6v6" />
    </template>
    <template v-else-if="name === 'box'">
      <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" />
      <path d="m4.5 7.5 7.5 4 7.5-4M12 11.5V21" />
    </template>
    <template v-else-if="name === 'bugs'">
      <path d="M8 8.5h8v6.75a4 4 0 0 1-8 0V8.5Z" />
      <path d="M10 5.5h4M12 3v2.5M5 10h3M16 10h3M5 15h3M16 15h3M7 20l2-2M17 20l-2-2" />
    </template>
    <template v-else-if="name === 'submitted'">
      <path d="M4 4h12l4 4v12H4V4Z" />
      <path d="M16 4v4h4M8 13h8M8 17h5" />
    </template>
    <template v-else-if="name === 'assigned'">
      <circle cx="9" cy="8" r="3" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0M16 12h5M18.5 9.5 21 12l-2.5 2.5" />
    </template>
    <template v-else-if="name === 'acceptance'">
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12 2.5 2.5L16.5 9" />
    </template>
    <template v-else-if="name === 'users'">
      <circle cx="9" cy="8" r="3" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0M15.5 6.5a3 3 0 0 1 0 5.5M16 15a5 5 0 0 1 4.5 4" />
    </template>
    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3" />
      <path
        d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.86 2.86-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1v.1H9.5V21a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.88.34l-.06.06-2.86-2.86.06-.06A1.7 1.7 0 0 0 4 15a1.7 1.7 0 0 0-1.6-1H2.3V10h.1A1.7 1.7 0 0 0 4 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06L6.46 4.2l.06.06A1.7 1.7 0 0 0 8.4 4a1.7 1.7 0 0 0 1.1-1.6v-.1h4.1v.1A1.7 1.7 0 0 0 15 4a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.86 2.86-.06.06A1.7 1.7 0 0 0 19.4 9a1.7 1.7 0 0 0 1.6 1h.1v4H21a1.7 1.7 0 0 0-1.6 1Z"
      />
    </template>
    <template v-else-if="name === 'search'">
      <circle cx="11" cy="11" r="7" />
      <path d="m16 16 4 4" />
    </template>
    <template v-else-if="name === 'bell'">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
    </template>
    <template v-else-if="name === 'help'">
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9a2.7 2.7 0 1 1 4.2 2.25c-1.1.65-1.7 1.1-1.7 2.25M12 17.5h.01" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>
    <template v-else-if="name === 'arrow-right'">
      <path d="M5 12h14M14 7l5 5-5 5" />
    </template>
    <template v-else-if="name === 'edit'">
      <path d="m14 4 6 6L9 21H3v-6L14 4ZM12 6l6 6" />
    </template>
    <template v-else-if="name === 'logout'">
      <path d="M10 4H4v16h6M14 8l4 4-4 4M18 12H8" />
    </template>
    <template v-else-if="name === 'menu'">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </template>
    <template v-else-if="name === 'sun'">
      <circle cx="12" cy="12" r="4" />
      <path
        d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42"
      />
    </template>
    <template v-else-if="name === 'moon'">
      <path d="M20.2 15.2A8.5 8.5 0 0 1 8.8 3.8 8.5 8.5 0 1 0 20.2 15.2Z" />
    </template>
    <template v-else-if="name === 'chevron-down'">
      <path d="m7 10 5 5 5-5" />
    </template>
    <template v-else-if="name === 'external-link'">
      <path d="M14 4h6v6M13 11l7-7M20 13v7H4V4h7" />
    </template>
    <template v-else-if="name === 'priority'">
      <path d="M6 20v-7M12 20V8M18 20V4" />
    </template>
    <template v-else-if="name === 'calendar'">
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4M17 3v4M3 10h18" />
    </template>
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.5 2" />
    </template>
    <template v-else-if="name === 'paperclip'">
      <path d="m20.5 11.5-8.3 8.3a5 5 0 0 1-7.1-7.1l8.3-8.3a3.5 3.5 0 0 1 5 5L10 17.7a2 2 0 1 1-2.8-2.8l7.6-7.6" />
    </template>
    <template v-else-if="name === 'filter'">
      <path d="M4 6h16M7 12h10M10 18h4" />
    </template>
    <template v-else-if="name === 'alert'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v5M12 16.5h.01" />
    </template>
    <template v-else>
      <path d="M6 6l12 12M18 6 6 18" />
    </template>
  </svg>
</template>

<style scoped>
.app-icon {
  display: block;
  flex: 0 0 auto;
}
</style>
