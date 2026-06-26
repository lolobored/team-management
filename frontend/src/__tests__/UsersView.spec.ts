import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ currentUser: { email: 'admin@x.com', role: 'ADMIN', mustChangePassword: false } }),
}))
vi.mock('@/api/client', () => ({
  userApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, email: 'admin@x.com', role: 'ADMIN', enabled: true, mustChangePassword: false, createdAt: '2026-06-01T00:00:00Z' },
      { id: 2, email: 'viewer@x.com', role: 'VIEW', enabled: true, mustChangePassword: true, createdAt: '2026-06-02T00:00:00Z' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, email: 'new@x.com', role: 'VIEW', enabled: true, mustChangePassword: true, createdAt: '2026-06-03T00:00:00Z' }),
    changeRole: vi.fn(), resetPassword: vi.fn(), setEnabled: vi.fn(), remove: vi.fn().mockResolvedValue(undefined),
  },
}))

import { userApi } from '@/api/client'
import UsersView from '@/views/UsersView.vue'

describe('UsersView', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('lists users', async () => {
    const wrapper = mount(UsersView)
    await flushPromises()
    expect(wrapper.text()).toContain('admin@x.com')
    expect(wrapper.text()).toContain('viewer@x.com')
  })

  it('creates a user', async () => {
    const wrapper = mount(UsersView)
    await flushPromises()
    await wrapper.get('[data-testid="new-email"]').setValue('new@x.com')
    await wrapper.get('[data-testid="new-password"]').setValue('temp1')
    await wrapper.get('[data-testid="create-user"]').trigger('submit')
    await flushPromises()
    expect(userApi.create).toHaveBeenCalledWith('new@x.com', 'VIEW', 'temp1')
  })

  it('disables self-account actions', async () => {
    const wrapper = mount(UsersView)
    await flushPromises()
    // row for admin@x.com (the current user) has its delete button disabled
    const adminDelete = wrapper.get('[data-testid="delete-1"]').element as HTMLButtonElement
    expect(adminDelete.disabled).toBe(true)
  })
})
