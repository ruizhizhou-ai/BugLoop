/**
 * 本文件保存当前登录用户与凭证，是页面判断登录状态的唯一来源。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { clearToken, readToken, writeToken } from '@/shared/api/http'
import { fetchCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from './authApi'
import type { AuthUser, LoginPayload, RegisterPayload } from './authApi'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  const user = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => token.value !== null)

  async function login(payload: LoginPayload): Promise<void> {
    applySession(await loginApi(payload))
  }

  async function register(payload: RegisterPayload): Promise<void> {
    applySession(await registerApi(payload))
  }

  async function loadCurrentUser(): Promise<void> {
    user.value = await fetchCurrentUser()
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      resetSession()
    }
  }

  function resetSession(): void {
    token.value = null
    user.value = null
    clearToken()
  }

  function applySession(result: { token: string; user: AuthUser }): void {
    token.value = result.token
    user.value = result.user
    writeToken(result.token)
  }

  return { token, user, isLoggedIn, login, register, loadCurrentUser, logout, resetSession }
})
