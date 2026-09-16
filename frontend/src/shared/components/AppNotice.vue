<!-- 本文件实现居中的页面提示：固定在当前视口中央，避免长页面滚动后顶部提示看不到。 -->
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import AppIcon from './AppIcon.vue'

/** 提示自动消失的时间：足够读完一句话，又不长期遮挡页面。 */
const AUTO_CLOSE_DELAY = 3000

const props = withDefaults(
  defineProps<{
    message: string
    /** 提示语义配色，默认错误；成功提示用于复制链接等无页面状态变化的操作。 */
    tone?: 'danger' | 'success'
  }>(),
  { tone: 'danger' },
)
const emit = defineEmits<{ close: [] }>()

const visible = ref(true)
let timer: number | undefined

/** 新提示出现时恢复显示并重新计时，避免上一条的剩余时间提前收掉新提示。 */
function scheduleAutoClose(): void {
  visible.value = true
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    visible.value = false
  }, AUTO_CLOSE_DELAY)
}

onMounted(scheduleAutoClose)
watch(() => props.message, scheduleAutoClose)
onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<template>
  <!-- 淡出动画播完再通知父级移除，避免提示被瞬间抽走。 -->
  <Transition name="app-notice" appear @after-leave="emit('close')">
    <div
      v-if="visible"
      class="app-notice"
      :class="`app-notice--${tone}`"
      :role="tone === 'success' ? 'status' : 'alert'"
    >
      <AppIcon :name="tone === 'success' ? 'check' : 'alert'" :size="18" />
      <span class="app-notice__text">{{ message }}</span>
      <button
        type="button"
        class="app-notice__close"
        aria-label="关闭提示"
        @click="visible = false"
      >
        <AppIcon name="close" :size="15" />
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.app-notice {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(90vw, 480px);
  padding: 14px 16px;
  color: var(--el-color-danger);
  font-size: 14px;
  line-height: 1.5;
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 10px;
  box-shadow: var(--bl-overlay-shadow);
  transform: translate(-50%, -50%);
}

.app-notice--success {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}

.app-notice-enter-active,
.app-notice-leave-active {
  transition:
    opacity 0.35s ease,
    transform 0.35s ease;
}

/* 淡出时保留居中位移，只做透明度和轻微缩放变化。 */
.app-notice-enter-from,
.app-notice-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.96);
}

.app-notice__text {
  min-width: 0;
  word-break: break-word;
}

.app-notice__close {
  display: grid;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  place-items: center;
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
  opacity: 0.72;
}

.app-notice__close:hover {
  opacity: 1;
}
</style>
