import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/client', () => ({
  authApi: {
    me: vi.fn(),
    login: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    changePassword: vi.fn().mockResolvedValue(undefined),
  },
}))

import { authApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchMe populates currentUser', async () => {
    ;(authApi.me as any).mockResolvedValue({ email: 'a@x.com', role: 'ADMIN', mustChangePassword: false })
    const store = useAuthStore()
    await store.fetchMe()
    expect(store.currentUser?.email).toBe('a@x.com')
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(store.canWrite).toBe(true)
    expect(store.loaded).toBe(true)
  })

  it('fetchMe clears user on 401', async () => {
    ;(authApi.me as any).mockRejectedValue(new Error('401'))
    const store = useAuthStore()
    await store.fetchMe()
    expect(store.currentUser).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.loaded).toBe(true)
  })

  it('canWrite true for VIEW_WRITE, false for VIEW', async () => {
    ;(authApi.me as any).mockResolvedValue({ email: 'w@x.com', role: 'VIEW_WRITE', mustChangePassword: false })
    const store = useAuthStore()
    await store.fetchMe()
    expect(store.canWrite).toBe(true)
    expect(store.isAdmin).toBe(false)

    ;(authApi.me as any).mockResolvedValue({ email: 'v@x.com', role: 'VIEW', mustChangePassword: false })
    await store.fetchMe()
    expect(store.canWrite).toBe(false)
  })

  it('login sets user; logout clears', async () => {
    ;(authApi.login as any).mockResolvedValue({ email: 'a@x.com', role: 'VIEW', mustChangePassword: true })
    const store = useAuthStore()
    await store.login('a@x.com', 'pw')
    expect(store.currentUser?.mustChangePassword).toBe(true)
    await store.logout()
    expect(store.currentUser).toBeNull()
  })

  it('changePassword clears the mustChangePassword flag', async () => {
    ;(authApi.login as any).mockResolvedValue({ email: 'a@x.com', role: 'VIEW', mustChangePassword: true })
    const store = useAuthStore()
    await store.login('a@x.com', 'pw')
    await store.changePassword('pw', 'NewStrongPass9')
    expect(store.currentUser?.mustChangePassword).toBe(false)
  })
})
