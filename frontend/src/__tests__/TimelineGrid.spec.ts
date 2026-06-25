import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TimelineGrid from '@/components/TimelineGrid.vue'
import type { TeamMemberUsage } from '@/types'

const months = ['2026-01', '2026-02', '2026-03']

const usageData: TeamMemberUsage[] = [{
  teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
  months: {
    '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
    '2026-02': { total: 20, assignments: [{ assignmentId: 11, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
  },
}]

function mountGrid() {
  return mount(TimelineGrid, { props: { usageData, months, zoom: 160 } })
}

describe('TimelineGrid (F1 rendering)', () => {
  it('renders one merged pill spanning two months', () => {
    const pills = mountGrid().findAll('[data-testid="pill"]')
    expect(pills).toHaveLength(1)
    expect(pills[0].attributes('style')).toContain('width: 66.6') // 2/3 * 100%
    expect(pills[0].text()).toContain('Acme')
  })

  it('pill height is proportional to usage and zoom', () => {
    const pill = mountGrid().find('[data-testid="pill"]')
    expect(pill.attributes('style')).toContain('height: 32px') // 20% * 160
  })

  it('emits unassign with all span assignment ids', async () => {
    const wrapper = mountGrid()
    await wrapper.find('[data-testid="pill-unassign"]').trigger('click')
    expect(wrapper.emitted('unassign')![0]).toEqual([[10, 11]])
  })

  it('emits edit-usage with span ids when the editor is applied', async () => {
    const wrapper = mountGrid()
    await wrapper.find('[data-testid="pill"]').trigger('click')
    const slider = wrapper.find('input[type="range"]')
    await slider.setValue(40)
    await wrapper.find('[data-testid="usage-apply"]').trigger('click')
    expect(wrapper.emitted('edit-usage')![0]).toEqual([[10, 11], 40])
  })

  it('has no clipboard/copy-paste UI', () => {
    const wrapper = mountGrid()
    expect(wrapper.find('[data-testid="clipboard-bar"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('to copy it')
  })

  it('PROBABLE pill carries the status-probable class', () => {
    const probableData: TeamMemberUsage[] = [{
      teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
      months: {
        '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'PROBABLE' }] },
      },
    }]
    const wrapper = mount(TimelineGrid, { props: { usageData: probableData, months, zoom: 160 } })
    expect(wrapper.find('[data-testid="pill"]').classes()).toContain('status-probable')
  })
})

describe('TimelineGrid (I1 — save edit on pill switch)', () => {
  const twoPillData: TeamMemberUsage[] = [{
    teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
    months: {
      '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
      '2026-02': { total: 30, assignments: [{ assignmentId: 20, customerId: 9, customerName: 'Beta', usage: 30, status: 'CONFIRMED' }] },
    },
  }]

  it('saves in-progress edit when switching to a different pill', async () => {
    const wrapper = mount(TimelineGrid, { props: { usageData: twoPillData, months, zoom: 160 } })
    const pills = wrapper.findAll('[data-testid="pill"]')
    expect(pills).toHaveLength(2)

    // Open first pill and move slider to 55
    await pills[0].trigger('click')
    const slider = wrapper.find('input[type="range"]')
    await slider.setValue(55)

    // Click second pill WITHOUT pressing Apply — should auto-save the first
    await pills[1].trigger('click')

    const emitted = wrapper.emitted('edit-usage')
    expect(emitted).toBeTruthy()
    expect(emitted![0]).toEqual([[10], 55])
  })
})

describe('TimelineGrid (M2 — empty rows droppable)', () => {
  const emptyRowData: TeamMemberUsage[] = [
    {
      teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
      months: {
        '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
      },
    },
    {
      teamMemberId: 2, teamMemberName: 'Bob B', country: 'France',
      months: {},
    },
  ]

  it('renders .lane-area for a team member with no assignments', () => {
    const wrapper = mount(TimelineGrid, { props: { usageData: emptyRowData, months, zoom: 160 } })
    const laneAreas = wrapper.findAll('.lane-area')
    expect(laneAreas).toHaveLength(2)
    expect(laneAreas[1].classes()).toContain('lane-area')
  })
})

describe('TimelineGrid (status context menu)', () => {
  const data: TeamMemberUsage[] = [{
    teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
    months: {
      '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
      '2026-02': { total: 20, assignments: [{ assignmentId: 11, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
    },
  }]
  function mountGrid() {
    return mount(TimelineGrid, { props: { usageData: data, months: ['2026-01', '2026-02', '2026-03'], zoom: 160 } })
  }

  it('opens the status menu on right-click', async () => {
    const wrapper = mountGrid()
    await wrapper.find('[data-testid="pill"]').trigger('contextmenu')
    expect(wrapper.find('[data-testid="status-menu"]').exists()).toBe(true)
  })

  it('emits set-status for the whole span when a status is picked', async () => {
    const wrapper = mountGrid()
    await wrapper.find('[data-testid="pill"]').trigger('contextmenu')
    await wrapper.find('[data-testid="status-option-PROBABLE"]').trigger('click')
    expect(wrapper.emitted('set-status')![0]).toEqual([[10, 11], 'PROBABLE'])
    expect(wrapper.find('[data-testid="status-menu"]').exists()).toBe(false)
  })

  it('closes the status menu when Escape is pressed', async () => {
    const wrapper = mountGrid()
    await wrapper.find('[data-testid="pill"]').trigger('contextmenu')
    expect(wrapper.find('[data-testid="status-menu"]').exists()).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.find('[data-testid="status-menu"]').exists()).toBe(false)
  })
})

describe('TimelineGrid (F2 drag-to-extend)', () => {
  // team member 1, customer Acme spans Jan-Feb (idx 0-1); customer Beta in Mar (idx 2)
  const data: TeamMemberUsage[] = [{
    teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
    months: {
      '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
      '2026-02': { total: 20, assignments: [{ assignmentId: 11, customerId: 5, customerName: 'Acme', usage: 20, status: 'CONFIRMED' }] },
      '2026-03': { total: 30, assignments: [{ assignmentId: 30, customerId: 6, customerName: 'Beta', usage: 30, status: 'CONFIRMED' }] },
    },
  }]

  function mountGrid() {
    return mount(TimelineGrid, { props: { usageData: data, months, zoom: 160 } })
  }

  it('emits extend with addMonths when a pill is extended into a free month', async () => {
    const wrapper = mountGrid()
    // call the exposed handler directly: extend Acme (start 0 span 2) to span 3 (add Mar idx 2 -> but Mar is Beta=different customer, so free for Acme)
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, status: 'CONFIRMED', customerName: 'Acme' },
      0, 3, 1,
    )
    await nextTick()
    const payload = wrapper.emitted('extend')![0][0] as any
    expect(payload).toMatchObject({
      teamMemberId: 1, customerId: 5, usagePercent: 20, status: 'CONFIRMED',
      addMonths: ['2026-03'], removeAssignmentIds: [], replaceMonths: [],
    })
  })

  it('opens a conflict popup instead of emitting when the target month has the same customer', async () => {
    const wrapper = mountGrid()
    // Acme also appears in a month that already has an Acme assignment -> conflict.
    // Simulate by extending onto idx 2 while pretending Acme already occupies it.
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, status: 'CONFIRMED', customerName: 'Acme' },
      0, 3, 1,
      new Map([[2, 77]]), // forced existing Acme id at idx 2
    )
    await nextTick()
    expect(wrapper.emitted('extend')).toBeUndefined()
    expect(wrapper.find('[data-testid="conflict-popup"]').exists()).toBe(true)
  })

  it('Replace resolves the conflict into replaceMonths and emits', async () => {
    const wrapper = mountGrid()
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, status: 'CONFIRMED', customerName: 'Acme' },
      0, 3, 1, new Map([[2, 77]]),
    )
    await nextTick()
    await wrapper.find('[data-testid="conflict-replace"]').trigger('click')
    const payload = wrapper.emitted('extend')![0][0] as any
    expect(payload.replaceMonths).toEqual([{ month: '2026-03', oldAssignmentId: 77 }])
    expect(payload.addMonths).toEqual([])
  })

  it('Skip month drops the conflicting month from the plan and emits', async () => {
    const wrapper = mountGrid()
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, status: 'CONFIRMED', customerName: 'Acme' },
      0, 3, 1, new Map([[2, 77]]),
    )
    await nextTick()
    await wrapper.find('[data-testid="conflict-skip"]').trigger('click')
    const payload = wrapper.emitted('extend')![0][0] as any
    expect(payload.replaceMonths).toEqual([])
    expect(payload.addMonths).toEqual([])
  })

  it('Cancel emits nothing', async () => {
    const wrapper = mountGrid()
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, status: 'CONFIRMED', customerName: 'Acme' },
      0, 3, 1, new Map([[2, 77]]),
    )
    await nextTick()
    await wrapper.find('[data-testid="conflict-cancel"]').trigger('click')
    expect(wrapper.emitted('extend')).toBeUndefined()
    expect(wrapper.find('[data-testid="conflict-popup"]').exists()).toBe(false)
  })
})
