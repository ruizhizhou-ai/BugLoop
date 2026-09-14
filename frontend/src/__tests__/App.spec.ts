/**
 * 本文件验证根组件能够把渲染职责交给路由出口。
 */
import { describe, it, expect } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('应渲染路由出口', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<div class="router-view-stub" />' },
        },
      },
    })
    expect(wrapper.find('.router-view-stub').exists()).toBe(true)
  })
})
