import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const state = { loaded: true, isAuthenticated: false, isAdmin: false, currentUser: null as any }
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get loaded() { return state.loaded },
    get isAuthenticated() { return state.isAuthenticated },
    get isAdmin() { return state.isAdmin },
    get currentUser() { return state.currentUser },
    fetchMe: vi.fn(),
  }),
}))

import router from '@/router'

describe('router guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    state.loaded = true
    state.isAuthenticated = false
    state.isAdmin = false
    state.currentUser = null
  })

  it('redirects unauthenticated to /login', async () => {
    await router.push('/timeline')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('forces must-change users to /set-password', async () => {
    state.isAuthenticated = true
    state.currentUser = { email: 'a@x.com', role: 'VIEW', mustChangePassword: true }
    await router.push('/customers')
    expect(router.currentRoute.value.path).toBe('/set-password')
  })

  it('keeps non-admins off /users', async () => {
    state.isAuthenticated = true
    state.isAdmin = false
    state.currentUser = { email: 'a@x.com', role: 'VIEW_WRITE', mustChangePassword: false }
    await router.push('/users')
    expect(router.currentRoute.value.path).toBe('/timeline')
  })

  it('allows admins onto /users', async () => {
    state.isAuthenticated = true
    state.isAdmin = true
    state.currentUser = { email: 'a@x.com', role: 'ADMIN', mustChangePassword: false }
    await router.push('/users')
    expect(router.currentRoute.value.path).toBe('/users')
  })
})
