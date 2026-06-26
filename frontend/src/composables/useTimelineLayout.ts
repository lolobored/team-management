import type { TeamMemberUsage, AssignmentStatus } from '@/types'

export const MIN_PILL_PX = 16
export const LANE_GAP_PX = 2

export interface LayoutPill {
  assignmentIds: number[]
  customerId: number
  customerName: string
  startIdx: number
  span: number
  usage: number
  status: AssignmentStatus
  leftPct: number
  widthPct: number
  heightPx: number
}

export interface LayoutLane {
  customerId: number
  customerName: string
  bottomOffsetPx: number
  laneHeightPx: number
  pills: LayoutPill[]
}

export interface LayoutMonth {
  month: string
  total: number
  remainingPx: number
  over: boolean
}

export interface LayoutRow {
  teamMemberId: number
  teamMemberName: string
  country: string
  months: LayoutMonth[]
  rowHeightPx: number
  lanes: LayoutLane[]
}

interface Cell {
  assignmentId: number
  usage: number
  status: AssignmentStatus
}

export function buildTimelineLayout(
  usageData: TeamMemberUsage[],
  months: string[],
  ref: number,
): LayoutRow[] {
  const n = months.length

  return usageData.map((member) => {
    // customerId -> lane scratch data
    const laneMap = new Map<number, { customerName: string; firstIdx: number; cells: (Cell | null)[] }>()

    months.forEach((m, i) => {
      const mu = member.months[m]
      if (!mu) return
      for (const a of mu.assignments) {
        let lane = laneMap.get(a.customerId)
        if (!lane) {
          lane = { customerName: a.customerName, firstIdx: i, cells: new Array(n).fill(null) }
          laneMap.set(a.customerId, lane)
        }
        lane.cells[i] = { assignmentId: a.assignmentId, usage: a.usage, status: a.status }
      }
    })

    const ordered = [...laneMap.entries()].sort((x, y) => {
      if (x[1].firstIdx !== y[1].firstIdx) return x[1].firstIdx - y[1].firstIdx
      return x[1].customerName.localeCompare(y[1].customerName)
    })

    let bottomOffset = 0
    const lanes: LayoutLane[] = ordered.map(([customerId, lane]) => {
      const pills: LayoutPill[] = []
      let i = 0
      while (i < n) {
        const c = lane.cells[i]
        if (!c) { i++; continue }
        let j = i + 1
        const ids = [c.assignmentId]
        while (j < n) {
          const cj = lane.cells[j]
          if (!cj || cj.usage !== c.usage || cj.status !== c.status) break
          ids.push(cj.assignmentId)
          j++
        }
        const span = j - i
        pills.push({
          assignmentIds: ids,
          customerId,
          customerName: lane.customerName,
          startIdx: i,
          span,
          usage: c.usage,
          status: c.status,
          leftPct: i / n,
          widthPct: span / n,
          heightPx: Math.max((c.usage / 100) * ref, MIN_PILL_PX),
        })
        i = j
      }
      const laneHeightPx = pills.reduce((mx, p) => Math.max(mx, p.heightPx), MIN_PILL_PX)
      const laneObj: LayoutLane = {
        customerId,
        customerName: lane.customerName,
        bottomOffsetPx: bottomOffset,
        laneHeightPx,
        pills,
      }
      bottomOffset += laneHeightPx + LANE_GAP_PX
      return laneObj
    })

    const monthsOut: LayoutMonth[] = months.map((m) => {
      const total = member.months[m]?.total ?? 0
      return {
        month: m,
        total,
        remainingPx: (Math.max(0, 100 - total) / 100) * ref,
        over: total > 100,
      }
    })

    return {
      teamMemberId: member.teamMemberId,
      teamMemberName: member.teamMemberName,
      country: member.country,
      months: monthsOut,
      rowHeightPx: ref,
      lanes,
    }
  })
}

export interface ExtendPlan {
  addMonths: string[]
  removeAssignmentIds: number[]
  conflicts: { month: string; oldAssignmentId: number }[]
}

export function computeExtendPlan(
  pill: { startIdx: number; span: number; assignmentIds: number[] },
  newStartIdx: number,
  newSpan: number,
  months: string[],
  existingByIdx: Map<number, number>,
): ExtendPlan {
  const oldIdx = new Set<number>()
  for (let i = pill.startIdx; i < pill.startIdx + pill.span; i++) oldIdx.add(i)

  const addMonths: string[] = []
  const removeAssignmentIds: number[] = []
  const conflicts: { month: string; oldAssignmentId: number }[] = []

  const newEnd = newStartIdx + newSpan
  for (let i = newStartIdx; i < newEnd; i++) {
    if (oldIdx.has(i)) continue // unchanged month
    const existing = existingByIdx.get(i)
    if (existing !== undefined) {
      conflicts.push({ month: months[i], oldAssignmentId: existing })
    } else {
      addMonths.push(months[i])
    }
  }

  for (const i of oldIdx) {
    if (i < newStartIdx || i >= newEnd) {
      removeAssignmentIds.push(pill.assignmentIds[i - pill.startIdx])
    }
  }
  removeAssignmentIds.sort((a, b) => a - b)

  return { addMonths, removeAssignmentIds, conflicts }
}
