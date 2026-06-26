import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const role = { value: 'VIEW' as 'VIEW' | 'VIEW_WRITE' | 'ADMIN' }
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get canWrite() { return role.value === 'VIEW_WRITE' || role.value === 'ADMIN' },
    get isAdmin() { return role.value === 'ADMIN' },
    currentUser: { email: 'x@x.com', role: role.value, mustChangePassword: false },
  }),
}))
vi.mock('@/api/client', () => ({
  customerApi: {
    list: vi.fn().mockResolvedValue([{ id: 1, name: 'Acme', country: 'AU' }]),
    create: vi.fn(), delete: vi.fn(),
    logoUrl: vi.fn((id: number) => `/api/customers/${id}/logo`),
  },
}))

import CustomersView from '@/views/CustomersView.vue'

describe('role gating on CustomersView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('hides the create form for VIEW', async () => {
    role.value = 'VIEW'
    const wrapper = mount(CustomersView)
    await flushPromises()
    expect(wrapper.find('[data-testid="customer-create"]').exists()).toBe(false)
  })

  it('shows the create form for VIEW_WRITE', async () => {
    role.value = 'VIEW_WRITE'
    const wrapper = mount(CustomersView)
    await flushPromises()
    expect(wrapper.find('[data-testid="customer-create"]').exists()).toBe(true)
  })
})
