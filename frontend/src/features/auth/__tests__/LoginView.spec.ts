/**
 * 本文件验证登录页包含 Spec 要求的用户名与密码字段。
 */
import { describe, it, expect, beforeEach } from 'vitest'

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

import LoginView from '../LoginView.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'home', component: { template: '<div />' } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: { template: '<div />' } },
  ],
})

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('应提示输入用户名与密码', () => {
    const wrapper = mount(LoginView, {
      global: {
        plugins: [router],
        stubs: {
          ElCard: { template: '<section><slot name="header" /><slot /></section>' },
          ElForm: { template: '<form><slot /></form>' },
          ElFormItem: { template: '<label><slot /></label>' },
          ElInput: { template: '<input />' },
          ElButton: { template: '<button><slot /></button>' },
          ElAlert: { template: '<div />' },
        },
      },
    })

    const inputs = wrapper.get('.auth-card__form').findAll('input')
    expect(inputs).toHaveLength(2)
    expect(inputs[0]?.attributes('placeholder')).toContain('用户名')
    expect(inputs[1]?.attributes('placeholder')).toContain('密码')
    expect(inputs[1]?.attributes('type')).toBe('password')
    expect(wrapper.text()).toContain('立即注册')
  })
})
