import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api/client'
import type { CurrentUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUser | null>(null)
  const loaded = ref(false)

  const isAuthenticated = computed(() => currentUser.value !== null)
  const canWrite = computed(() =>
    currentUser.value?.role === 'VIEW_WRITE' || currentUser.value?.role === 'ADMIN')
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

  async function fetchMe() {
    try {
      currentUser.value = await authApi.me()
    } catch {
      currentUser.value = null
    } finally {
      loaded.value = true
    }
  }

  async function login(email: string, password: string) {
    currentUser.value = await authApi.login(email, password)
  }

  async function logout() {
    await authApi.logout()
    currentUser.value = null
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await authApi.changePassword(currentPassword, newPassword)
    if (currentUser.value) {
      currentUser.value.mustChangePassword = false
    }
  }

  return { currentUser, loaded, isAuthenticated, canWrite, isAdmin, fetchMe, login, logout, changePassword }
})
