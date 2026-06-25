import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CustomersView from '@/views/CustomersView.vue'

vi.mock('@/api/client', () => ({
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme Corp', country: 'Australia', city: 'Sydney' },
      { id: 2, name: 'Beta Inc', country: 'Singapore' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, name: 'New Co' }),
    delete: vi.fn().mockResolvedValue(undefined),
    logoUrl: vi.fn((id: number) => `/api/customers/${id}/logo`),
  },
}))

describe('CustomersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders customers with location', async () => {
    const wrapper = mount(CustomersView)
    await flushPromises()

    expect(wrapper.text()).toContain('Acme Corp')
    expect(wrapper.text()).toContain('Sydney')
    expect(wrapper.text()).toContain('Australia')
    expect(wrapper.text()).toContain('Beta Inc')
  })

  it('filters customers by text', async () => {
    const wrapper = mount(CustomersView)
    await flushPromises()

    await wrapper.find('.filter-input').setValue('beta')
    expect(wrapper.text()).toContain('Beta Inc')
    expect(wrapper.text()).not.toContain('Acme Corp')
  })
})
