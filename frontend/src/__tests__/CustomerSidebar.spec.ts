import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import CustomerSidebar from '@/components/CustomerSidebar.vue'
import type { Customer } from '@/types'

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    canWrite: true,
    isAdmin: false,
    currentUser: { email: 'writer@test.com', role: 'VIEW_WRITE', mustChangePassword: false },
  }),
}))

vi.mock('@/api/client', () => ({
  customerApi: {
    logoUrl: vi.fn((id: number) => `/api/customers/${id}/logo`),
  },
}))

const customers: Customer[] = [
  { id: 1, name: 'Acme', country: 'Australia' },
  { id: 2, name: 'Globex', country: 'Singapore' },
  { id: 3, name: 'Initech' }, // no country
]

describe('CustomerSidebar', () => {
  it('shows each customer country under the name', () => {
    const wrapper = mount(CustomerSidebar, { props: { customers } })
    const countries = wrapper.findAll('.customer-country').map(c => c.text())
    expect(countries).toContain('Australia')
    expect(countries).toContain('Singapore')
  })

  it('renders no country element for a customer without a country', () => {
    const wrapper = mount(CustomerSidebar, { props: { customers: [{ id: 3, name: 'Initech' }] } })
    expect(wrapper.find('.customer-country').exists()).toBe(false)
    expect(wrapper.text()).toContain('Initech')
  })

  it('filters by country', async () => {
    const wrapper = mount(CustomerSidebar, { props: { customers } })
    await wrapper.find('.customer-filter').setValue('singap')
    expect(wrapper.text()).toContain('Globex')
    expect(wrapper.text()).not.toContain('Acme')
  })
})
