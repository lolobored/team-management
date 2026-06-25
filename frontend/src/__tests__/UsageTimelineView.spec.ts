import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UsageTimelineView from '@/views/UsageTimelineView.vue'
import { assignmentApi } from '@/api/client'

vi.mock('@/api/client', () => ({
  teamMemberApi: {
    photoUrl: (id: number) => `/api/team-members/${id}/photo`,
    list: vi.fn().mockResolvedValue([
      { id: 1, firstName: 'Alice', lastName: 'Smith', country: 'Australia' },
    ]),
  },
  usageApi: {
    get: vi.fn().mockResolvedValue([
      {
        teamMemberId: 1,
        teamMemberName: 'Alice Smith',
        country: 'Australia',
        months: {
          '2026-06': {
            total: 40,
            assignments: [
              { assignmentId: 1, customerId: 1, customerName: 'Acme', usage: 20, status: 'CONFIRMED' },
              { assignmentId: 2, customerId: 2, customerName: 'Beta Corp', usage: 20, status: 'PROBABLE' },
            ],
          },
          '2026-07': {
            total: 20,
            assignments: [
              { assignmentId: 3, customerId: 1, customerName: 'Acme', usage: 20, status: 'CONFIRMED' },
            ],
          },
        },
      },
    ]),
  },
  assignmentApi: {
    create: vi.fn().mockResolvedValue({ id: 99 }),
    update: vi.fn().mockResolvedValue({ id: 99 }),
    delete: vi.fn().mockResolvedValue(undefined),
    list: vi.fn().mockResolvedValue([]),
  },
  customerApi: {
    list: vi.fn().mockResolvedValue([
      { id: 1, name: 'Acme' },
      { id: 2, name: 'Beta Corp' },
    ]),
    logoUrl: (id: number) => `/api/customers/${id}/logo`,
  },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countries: ['Australia', 'Japan'],
    fetchCountries: vi.fn(),
  }),
}))

describe('UsageTimelineView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders team member rows with usage data', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Smith')
    expect(wrapper.text()).toContain('Acme')
  })

  it('shows total usage in cells', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).toContain('20%')
  })

  it('has country filter', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const select = wrapper.find('[data-testid="country-filter"]')
    expect(select.exists()).toBe(true)
  })

  it('renders tab bar with Timeline and Map tabs', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const tabs = wrapper.findAll('[data-testid="view-tab"]')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('Timeline')
    expect(tabs[1].text()).toBe('Map')
  })

  it('defaults to Timeline tab', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const activeTab = wrapper.find('[data-testid="view-tab"].active')
    expect(activeTab.text()).toBe('Timeline')
    expect(wrapper.find('.timeline-grid').exists()).toBe(true)
  })

  it('calls assignmentApi.delete for each id when unassign is emitted', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const grid = wrapper.findComponent({ name: 'TimelineGrid' })
    await grid.vm.$emit('unassign', [42])
    await flushPromises()

    expect(assignmentApi.delete).toHaveBeenCalledWith(42)
  })

  it('calls assignmentApi.update for each id when edit-usage is emitted', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const grid = wrapper.findComponent({ name: 'TimelineGrid' })
    await grid.vm.$emit('edit-usage', [42], 30)
    await flushPromises()

    expect(assignmentApi.update).toHaveBeenCalledWith(42, { usagePercent: 30 })
  })

  it('onSetStatus patches each id with the new status and refetches', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const grid = wrapper.findComponent({ name: 'TimelineGrid' })
    await grid.vm.$emit('set-status', [10, 11], 'PROBABLE')
    await flushPromises()
    expect(assignmentApi.update).toHaveBeenCalledWith(10, { status: 'PROBABLE' })
    expect(assignmentApi.update).toHaveBeenCalledWith(11, { status: 'PROBABLE' })
  })

  it('calls assignmentApi.delete and create when extend is emitted', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const grid = wrapper.findComponent({ name: 'TimelineGrid' })
    await grid.vm.$emit('extend', {
      teamMemberId: 1, customerId: 5, usagePercent: 20, status: 'CONFIRMED',
      addMonths: ['2026-03'], removeAssignmentIds: [], replaceMonths: [{ month: '2026-04', oldAssignmentId: 77 }],
    })
    await flushPromises()
    expect(assignmentApi.delete).toHaveBeenCalledWith(77)
    // two creates: the added month and the replaced month
    expect(assignmentApi.create).toHaveBeenCalledTimes(2)
    expect(assignmentApi.create).toHaveBeenCalledWith(expect.objectContaining({ month: '2026-03' }))
    expect(assignmentApi.create).toHaveBeenCalledWith(expect.objectContaining({ month: '2026-04' }))
  })

  it('period-next advances fromMonth by monthCount, period-prev moves it back', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    // helper: add n months to a YYYY-MM string
    const addM = (ym: string, n: number) => {
      const [y, m] = ym.split('-').map(Number)
      const d = new Date(y, m - 1 + n)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    }

    const monthInput = () => wrapper.find('input[type="month"]')
    const start = (monthInput().element as HTMLInputElement).value  // current fromMonth, default monthCount = 6

    await wrapper.find('[data-testid="period-next"]').trigger('click')
    await flushPromises()
    expect((monthInput().element as HTMLInputElement).value).toBe(addM(start, 6))

    await wrapper.find('[data-testid="period-prev"]').trigger('click')
    await flushPromises()
    expect((monthInput().element as HTMLInputElement).value).toBe(start)
  })
})
