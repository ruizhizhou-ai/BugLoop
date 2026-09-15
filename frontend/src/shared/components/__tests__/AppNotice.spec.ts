/**
 * 本文件验证居中提示的自动消失：到点先淡出再请求关闭，新提示重新计时，卸载后不再触发。
 */
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { mount } from '@vue/test-utils'

import AppNotice from '../AppNotice.vue'

// Vue Test Utils 默认把 Transition 桩掉，这里用真实过渡才能验证离场动画后再通知父级。
const mountOptions = { global: { stubs: { transition: false } } }

/** jsdom 里过渡由 0ms 兜底定时器收尾，推进假定时器让离场动画结束。 */
async function flushLeaveTransition(): Promise<void> {
  await nextTick()
  vi.advanceTimersByTime(100)
  await nextTick()
}

describe('AppNotice', () => {
  it('应在 3 秒后淡出并在离场结束后请求关闭', async () => {
    // 同时接替 requestAnimationFrame：Vue 的离场动画依赖 rAF 推进。
    vi.useFakeTimers({
      toFake: [
        'setTimeout',
        'clearTimeout',
        'setInterval',
        'clearInterval',
        'Date',
        'requestAnimationFrame',
        'cancelAnimationFrame',
      ],
    })
    try {
      const wrapper = mount(AppNotice, { props: { message: '评论内容不能为空' }, ...mountOptions })

      vi.advanceTimersByTime(2999)
      await nextTick()
      expect(wrapper.emitted('close')).toBeUndefined()
      expect(wrapper.find('.app-notice').exists()).toBe(true)

      vi.advanceTimersByTime(1)
      await nextTick()
      // jsdom 没有过渡样式，离场会立刻结束；浏览器里先播淡出动画，结束后才通知父级。
      await flushLeaveTransition()
      expect(wrapper.find('.app-notice').exists()).toBe(false)
      expect(wrapper.emitted('close')).toHaveLength(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('新提示出现时应重新计时而不是沿用上一条的剩余时间', async () => {
    // 同时接替 requestAnimationFrame：Vue 的离场动画依赖 rAF 推进。
    vi.useFakeTimers({
      toFake: [
        'setTimeout',
        'clearTimeout',
        'setInterval',
        'clearInterval',
        'Date',
        'requestAnimationFrame',
        'cancelAnimationFrame',
      ],
    })
    try {
      const wrapper = mount(AppNotice, { props: { message: '第一条提示' }, ...mountOptions })

      vi.advanceTimersByTime(2500)
      await wrapper.setProps({ message: '第二条提示' })

      vi.advanceTimersByTime(2500)
      await nextTick()
      expect(wrapper.emitted('close')).toBeUndefined()

      vi.advanceTimersByTime(500)
      await flushLeaveTransition()
      expect(wrapper.emitted('close')).toHaveLength(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('卸载后不应再触发关闭', async () => {
    // 同时接替 requestAnimationFrame：Vue 的离场动画依赖 rAF 推进。
    vi.useFakeTimers({
      toFake: [
        'setTimeout',
        'clearTimeout',
        'setInterval',
        'clearInterval',
        'Date',
        'requestAnimationFrame',
        'cancelAnimationFrame',
      ],
    })
    try {
      const wrapper = mount(AppNotice, { props: { message: '提示' }, ...mountOptions })
      wrapper.unmount()

      vi.advanceTimersByTime(10000)
      expect(wrapper.emitted('close')).toBeUndefined()
    } finally {
      vi.useRealTimers()
    }
  })
})
