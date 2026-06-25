import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import TeamMembersView from '@/views/TeamMembersView.vue'

vi.mock('@/api/client', () => ({
  teamMemberApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, firstName: 'Alice', lastName: 'Smith', email: 'alice@test.com', country: 'Australia' },
      { id: 2, firstName: 'Bob', lastName: 'Jones', country: 'Japan' },
    ]),
    create: vi.fn().mockResolvedValue({ id: 3, firstName: 'Charlie', lastName: 'Brown', country: 'Australia' }),
    delete: vi.fn().mockResolvedValue(undefined),
    photoUrl: (id: number) => `/api/team-members/${id}/photo`,
  },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countries: ['Australia', 'Japan'],
    fetchCountries: vi.fn(),
  }),
}))

describe('TeamMembersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders team member list on mount', async () => {
    const wrapper = mount(TeamMembersView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Smith')
    expect(wrapper.text()).toContain('Bob')
    expect(wrapper.text()).toContain('Jones')
  })

  it('filters by country', async () => {
    const wrapper = mount(TeamMembersView)
    await flushPromises()

    const select = wrapper.find('[data-testid="country-filter"]')
    await select.setValue('Australia')

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).not.toContain('Bob')
  })
})
