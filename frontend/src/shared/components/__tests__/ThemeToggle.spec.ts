/** 本文件验证主题控件会立即更新根节点、持久化用户选择，并能通过菜单准确切换指定主题。 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

describe('ThemeToggle', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    vi.resetModules()
  })

  it('点击主按钮后应在浅色与深色之间切换并保存选择', async () => {
    const { default: ThemeToggle } = await import('../ThemeToggle.vue')
    const wrapper = mount(ThemeToggle)

    expect(document.documentElement.dataset.theme).toBe('dark')
    await wrapper.find('.theme-toggle__button').trigger('click')

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('bugloop.theme')).toBe('light')
  })

  it('应通过右侧菜单选择指定主题', async () => {
    const { default: ThemeToggle } = await import('../ThemeToggle.vue')
    const wrapper = mount(ThemeToggle)

    await wrapper.find('.theme-toggle__more').trigger('click')
    expect(wrapper.text()).toContain('主题设置')
    await wrapper.findAll('.theme-toggle__menu button')[0]!.trigger('click')

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('bugloop.theme')).toBe('light')
  })
})
