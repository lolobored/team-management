import { describe, it, expect } from 'vitest'
import {
  buildTimelineLayout,
  computeExtendPlan,
  MIN_PILL_PX,
  LANE_GAP_PX,
} from '@/composables/useTimelineLayout'
import type { TeamMemberUsage } from '@/types'

const MONTHS = ['2026-01', '2026-02', '2026-03', '2026-04']

function member(months: TeamMemberUsage['months']): TeamMemberUsage[] {
  return [{ teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore', months }]
}

function cell(assignmentId: number, customerId: number, customerName: string, usage: number, status: 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL' = 'CONFIRMED') {
  return { assignmentId, customerId, customerName, usage, status }
}

describe('buildTimelineLayout', () => {
  it('merges consecutive same-customer same-usage months into one pill', () => {
    const data = member({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20)] },
      '2026-02': { total: 20, assignments: [cell(11, 5, 'Acme', 20)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes).toHaveLength(1)
    expect(row.lanes[0].pills).toHaveLength(1)
    const pill = row.lanes[0].pills[0]
    expect(pill.span).toBe(2)
    expect(pill.startIdx).toBe(0)
    expect(pill.assignmentIds).toEqual([10, 11])
    expect(pill.leftPct).toBe(0)
    expect(pill.widthPct).toBe(2 / 4)
  })

  it('splits when usage changes between months', () => {
    const data = member({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20)] },
      '2026-02': { total: 30, assignments: [cell(11, 5, 'Acme', 30)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills.map(p => p.usage)).toEqual([20, 30])
    // lane height reserves the taller (30%) pill
    expect(row.lanes[0].laneHeightPx).toBe((30 / 100) * 160)
  })

  it('splits when status differs', () => {
    const data = member({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20, 'CONFIRMED')] },
      '2026-02': { total: 20, assignments: [cell(11, 5, 'Acme', 20, 'PROBABLE')] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills).toHaveLength(2)
    expect(row.lanes[0].pills.map(p => p.status)).toEqual(['CONFIRMED', 'PROBABLE'])
  })

  it('splits on a month gap', () => {
    const data = member({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20)] },
      '2026-03': { total: 20, assignments: [cell(12, 5, 'Acme', 20)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    const pills = row.lanes[0].pills
    expect(pills).toHaveLength(2)
    expect(pills[0].startIdx).toBe(0)
    expect(pills[1].startIdx).toBe(2)
  })

  it('orders lanes by earliest start month, tie-broken by name', () => {
    const data = member({
      '2026-01': { total: 35, assignments: [cell(10, 5, 'Acme', 20), cell(20, 7, 'Delta', 15)] },
      '2026-02': { total: 30, assignments: [cell(30, 6, 'Beta', 30)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    // Acme & Delta both start at idx 0 -> alphabetical; Beta starts idx 1 -> last
    expect(row.lanes.map(l => l.customerName)).toEqual(['Acme', 'Delta', 'Beta'])
  })

  it('stacks lanes with cumulative offsets and gaps', () => {
    const data = member({
      '2026-01': { total: 35, assignments: [cell(10, 5, 'Acme', 20), cell(20, 7, 'Delta', 15)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    const [acme, delta] = row.lanes
    expect(acme.topOffsetPx).toBe(0)
    expect(acme.laneHeightPx).toBe((20 / 100) * 160) // 32
    expect(delta.topOffsetPx).toBe(acme.laneHeightPx + LANE_GAP_PX) // 34
    expect(row.rowHeightPx).toBe(acme.laneHeightPx + LANE_GAP_PX + delta.laneHeightPx)
  })

  it('floors tiny pills to MIN_PILL_PX', () => {
    const data = member({
      '2026-01': { total: 5, assignments: [cell(10, 5, 'Acme', 5)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills[0].heightPx).toBe(MIN_PILL_PX) // 5% * 160 = 8 -> floored to 16
  })

  it('copies per-month totals and handles an empty team member', () => {
    const data = member({})
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes).toHaveLength(0)
    expect(row.rowHeightPx).toBe(0)
    expect(row.totalsByMonth['2026-01']).toBe(0)
  })
})

describe('computeExtendPlan', () => {
  const MS = ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05']
  // pill currently spans idx 1..2 (Feb, Mar), ids [11, 12]
  const pill = { startIdx: 1, span: 2, assignmentIds: [11, 12] }

  it('extends right with no conflicts', () => {
    const plan = computeExtendPlan(pill, 1, 4, MS, new Map()) // now Feb..May
    expect(plan.addMonths).toEqual(['2026-04', '2026-05'])
    expect(plan.removeAssignmentIds).toEqual([])
    expect(plan.conflicts).toEqual([])
  })

  it('extends left with no conflicts', () => {
    const plan = computeExtendPlan(pill, 0, 3, MS, new Map()) // now Jan..Mar
    expect(plan.addMonths).toEqual(['2026-01'])
    expect(plan.removeAssignmentIds).toEqual([])
  })

  it('shrinks from the right, removing trailing month ids', () => {
    const plan = computeExtendPlan(pill, 1, 1, MS, new Map()) // now just Feb
    expect(plan.addMonths).toEqual([])
    expect(plan.removeAssignmentIds).toEqual([12]) // Mar id dropped
  })

  it('flags an added month that already has this customer as a conflict', () => {
    const existing = new Map<number, number>([[3, 99]]) // idx 3 (Apr) has assignment 99
    const plan = computeExtendPlan(pill, 1, 3, MS, existing) // extend to Apr
    expect(plan.addMonths).toEqual([])
    expect(plan.conflicts).toEqual([{ month: '2026-04', oldAssignmentId: 99 }])
  })
})
