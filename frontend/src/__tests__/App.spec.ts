/**
 * 本文件验证前端根组件能够正常渲染，作为 Vue 项目骨架的最小回归测试。
 */
import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('应显示项目骨架就绪状态', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          ElCard: { template: '<section><slot name="header" /><slot /></section>' },
          ElTag: { template: '<span><slot /></span>' },
        },
      },
    })
    expect(wrapper.text()).toContain('项目骨架已就绪')
  })
})
