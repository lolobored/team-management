import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const loginMock = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock, currentUser: { email: 'admin@x.com', role: 'ADMIN', mustChangePassword: false } }),
}))

import LoginView from '@/views/LoginView.vue'

describe('LoginView lockout message', () => {
  beforeEach(() => { setActivePinia(createPinia()); loginMock.mockReset() })

  it('shows the lock message on 423', async () => {
    loginMock.mockRejectedValue({ response: { status: 423 } })
    const wrapper = mount(LoginView)
    await wrapper.findAll('input')[0].setValue('u@x.com')
    await wrapper.findAll('input')[1].setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="login-error"]').text().toLowerCase()).toContain('locked')
  })

  it('shows the generic message on 401', async () => {
    loginMock.mockRejectedValue({ response: { status: 401 } })
    const wrapper = mount(LoginView)
    await wrapper.findAll('input')[0].setValue('u@x.com')
    await wrapper.findAll('input')[1].setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('Invalid email or password')
  })
})

import UsersView from '@/views/UsersView.vue'

vi.mock('@/api/client', () => ({
  userApi: {
    list: vi.fn(),
    unlock: vi.fn().mockResolvedValue({}),
    create: vi.fn(), changeRole: vi.fn(), resetPassword: vi.fn(), setEnabled: vi.fn(), remove: vi.fn(),
  },
}))
import { userApi } from '@/api/client'

describe('UsersView unlock', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('shows Unlock for a locked user and calls userApi.unlock', async () => {
    const future = new Date(Date.now() + 15 * 60_000).toISOString()
    ;(userApi.list as any)
      .mockResolvedValueOnce([{ id: 7, email: 'locked@x.com', role: 'VIEW', enabled: true, mustChangePassword: false, lockedUntil: future, createdAt: '2026-06-01T00:00:00Z' }])
      .mockResolvedValue([])
    const wrapper = mount(UsersView)
    await flushPromises()
    const btn = wrapper.get('[data-testid="unlock-7"]')
    expect(wrapper.find('[data-testid="locked-7"]').exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    expect(userApi.unlock).toHaveBeenCalledWith(7)
  })

  it('does not show Unlock for an unlocked user', async () => {
    ;(userApi.list as any).mockResolvedValue([{ id: 8, email: 'ok@x.com', role: 'VIEW', enabled: true, mustChangePassword: false, lockedUntil: null, createdAt: '2026-06-01T00:00:00Z' }])
    const wrapper = mount(UsersView)
    await flushPromises()
    expect(wrapper.find('[data-testid="unlock-8"]').exists()).toBe(false)
  })
})
