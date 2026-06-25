# Proportional Pills & Drag-to-Extend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the timeline's fixed-height per-cell pills with proportional-height, multi-month-spanning pills, and replace Cmd+Click copy/paste with drag-to-extend.

**Architecture:** A pure layout function (`useTimelineLayout`) transforms the existing usage data into ordered per-customer lanes of merged pills. `TimelineGrid.vue` renders that structure as an absolute pill layer over a background grid of drop-target cells. Drag-to-extend computes a create/delete plan via a second pure function (`computeExtendPlan`); `UsageTimelineView.vue` executes the plan against the existing per-month assignment API.

**Tech Stack:** Vue 3 (`<script setup>`, Composition API), TypeScript, Vitest, `@vue/test-utils`, Pinia. Frontend only.

## Global Constraints

- Frontend only — no backend, schema, or API changes. Use existing `assignmentApi.create` / `.update` / `.delete`.
- Vue 3 Composition API with `<script setup>` exclusively. No Options API.
- One assignment per architect-customer-month (backend returns 409 on duplicate create).
- Tests live in `frontend/src/__tests__/` (matches existing convention), run with `npm run test`.
- The merge/identity attribute is `tentative: boolean` (current model). If F5 confidence-status lands first, substitute its `status` field everywhere `tentative` participates in merge equality — structure is identical.
- `ref` (zoom) is **pixels per 100% usage**: a pill's height = `(usage / 100) * ref`, floored to `MIN_PILL_PX`.

---

## File Structure

- **Create** `frontend/src/composables/useTimelineLayout.ts` — pure layout + drag-plan functions and their exported types (`LayoutRow`, `LayoutLane`, `LayoutPill`, `ExtendPlan`, `buildTimelineLayout`, `computeExtendPlan`, `MIN_PILL_PX`, `LANE_GAP_PX`).
- **Create** `frontend/src/__tests__/useTimelineLayout.spec.ts` — unit tests for both pure functions.
- **Modify** `frontend/src/components/TimelineGrid.vue` — new rendering, remove copy/paste (Task 2); add drag-to-extend (Task 3).
- **Modify** `frontend/src/views/UsageTimelineView.vue` — zoom slider, span-aware `unassign`/`edit-usage` handlers (Task 2); `extend` handler (Task 3).
- **Modify** `frontend/src/__tests__/UsageTimelineView.spec.ts` — remove clipboard tests, adapt handler tests.

---

## Task 1: Layout composable (`useTimelineLayout`)

Pure functions, no DOM. This is the foundation both UI tasks consume.

**Files:**
- Create: `frontend/src/composables/useTimelineLayout.ts`
- Test: `frontend/src/__tests__/useTimelineLayout.spec.ts`

**Interfaces:**
- Consumes: `ArchitectUsage` from `@/types` (`{ architectId, architectName, country, months: Record<string, { total: number; assignments: { assignmentId, customerId, customerName, usage, tentative }[] }> }`).
- Produces:
  - `buildTimelineLayout(usageData: ArchitectUsage[], months: string[], ref: number): LayoutRow[]`
  - `computeExtendPlan(pill: { startIdx: number; span: number; assignmentIds: number[] }, newStartIdx: number, newSpan: number, months: string[], existingByIdx: Map<number, number>): ExtendPlan`
  - Types `LayoutRow`, `LayoutLane`, `LayoutPill`, `ExtendPlan`; constants `MIN_PILL_PX = 16`, `LANE_GAP_PX = 2`.

- [ ] **Step 1: Write failing tests for `buildTimelineLayout`**

Create `frontend/src/__tests__/useTimelineLayout.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import {
  buildTimelineLayout,
  computeExtendPlan,
  MIN_PILL_PX,
  LANE_GAP_PX,
} from '@/composables/useTimelineLayout'
import type { ArchitectUsage } from '@/types'

const MONTHS = ['2026-01', '2026-02', '2026-03', '2026-04']

function arch(months: ArchitectUsage['months']): ArchitectUsage[] {
  return [{ architectId: 1, architectName: 'Alice A', country: 'Singapore', months }]
}

function cell(assignmentId: number, customerId: number, customerName: string, usage: number, tentative = false) {
  return { assignmentId, customerId, customerName, usage, tentative }
}

describe('buildTimelineLayout', () => {
  it('merges consecutive same-customer same-usage months into one pill', () => {
    const data = arch({
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
    const data = arch({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20)] },
      '2026-02': { total: 30, assignments: [cell(11, 5, 'Acme', 30)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills.map(p => p.usage)).toEqual([20, 30])
    // lane height reserves the taller (30%) pill
    expect(row.lanes[0].laneHeightPx).toBe((30 / 100) * 160)
  })

  it('splits when tentative differs', () => {
    const data = arch({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20, false)] },
      '2026-02': { total: 20, assignments: [cell(11, 5, 'Acme', 20, true)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills).toHaveLength(2)
  })

  it('splits on a month gap', () => {
    const data = arch({
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
    const data = arch({
      '2026-01': { total: 35, assignments: [cell(10, 5, 'Acme', 20), cell(20, 7, 'Delta', 15)] },
      '2026-02': { total: 30, assignments: [cell(30, 6, 'Beta', 30)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    // Acme & Delta both start at idx 0 -> alphabetical; Beta starts idx 1 -> last
    expect(row.lanes.map(l => l.customerName)).toEqual(['Acme', 'Delta', 'Beta'])
  })

  it('stacks lanes with cumulative offsets and gaps', () => {
    const data = arch({
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
    const data = arch({
      '2026-01': { total: 5, assignments: [cell(10, 5, 'Acme', 5)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills[0].heightPx).toBe(MIN_PILL_PX) // 5% * 160 = 8 -> floored to 16
  })

  it('copies per-month totals and handles an empty architect', () => {
    const data = arch({})
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes).toHaveLength(0)
    expect(row.rowHeightPx).toBe(0)
    expect(row.totalsByMonth['2026-01']).toBe(0)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: FAIL — "Failed to resolve import '@/composables/useTimelineLayout'".

- [ ] **Step 3: Implement `buildTimelineLayout`**

Create `frontend/src/composables/useTimelineLayout.ts`:

```ts
import type { ArchitectUsage } from '@/types'

export const MIN_PILL_PX = 16
export const LANE_GAP_PX = 2

export interface LayoutPill {
  assignmentIds: number[]
  customerId: number
  customerName: string
  startIdx: number
  span: number
  usage: number
  tentative: boolean
  leftPct: number
  widthPct: number
  heightPx: number
}

export interface LayoutLane {
  customerId: number
  customerName: string
  topOffsetPx: number
  laneHeightPx: number
  pills: LayoutPill[]
}

export interface LayoutRow {
  architectId: number
  architectName: string
  country: string
  totalsByMonth: Record<string, number>
  rowHeightPx: number
  lanes: LayoutLane[]
}

interface Cell {
  assignmentId: number
  usage: number
  tentative: boolean
}

export function buildTimelineLayout(
  usageData: ArchitectUsage[],
  months: string[],
  ref: number,
): LayoutRow[] {
  const n = months.length

  return usageData.map((arch) => {
    // customerId -> lane scratch data
    const laneMap = new Map<number, { customerName: string; firstIdx: number; cells: (Cell | null)[] }>()

    months.forEach((m, i) => {
      const mu = arch.months[m]
      if (!mu) return
      for (const a of mu.assignments) {
        let lane = laneMap.get(a.customerId)
        if (!lane) {
          lane = { customerName: a.customerName, firstIdx: i, cells: new Array(n).fill(null) }
          laneMap.set(a.customerId, lane)
        }
        lane.cells[i] = { assignmentId: a.assignmentId, usage: a.usage, tentative: a.tentative }
      }
    })

    const ordered = [...laneMap.entries()].sort((x, y) => {
      if (x[1].firstIdx !== y[1].firstIdx) return x[1].firstIdx - y[1].firstIdx
      return x[1].customerName.localeCompare(y[1].customerName)
    })

    let topOffset = 0
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
          if (!cj || cj.usage !== c.usage || cj.tentative !== c.tentative) break
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
          tentative: c.tentative,
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
        topOffsetPx: topOffset,
        laneHeightPx,
        pills,
      }
      topOffset += laneHeightPx + LANE_GAP_PX
      return laneObj
    })

    const totalsByMonth: Record<string, number> = {}
    months.forEach((m) => { totalsByMonth[m] = arch.months[m]?.total ?? 0 })

    return {
      architectId: arch.architectId,
      architectName: arch.architectName,
      country: arch.country,
      totalsByMonth,
      rowHeightPx: lanes.length ? topOffset - LANE_GAP_PX : 0,
      lanes,
    }
  })
}
```

- [ ] **Step 4: Run tests to verify `buildTimelineLayout` passes**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: PASS for the `buildTimelineLayout` describe block. (`computeExtendPlan` import is unused until Step 5 — that's fine.)

- [ ] **Step 5: Append failing tests for `computeExtendPlan`**

Append to `frontend/src/__tests__/useTimelineLayout.spec.ts`:

```ts
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
```

- [ ] **Step 6: Run to verify the new tests fail**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: FAIL — "computeExtendPlan is not a function".

- [ ] **Step 7: Implement `computeExtendPlan`**

Append to `frontend/src/composables/useTimelineLayout.ts`:

```ts
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
```

- [ ] **Step 8: Run the full spec to verify all pass**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: PASS — all tests in both describe blocks green.

- [ ] **Step 9: Commit**

```bash
cd frontend && git add src/composables/useTimelineLayout.ts src/__tests__/useTimelineLayout.spec.ts
git commit -m "feat: add timeline layout composable for proportional spanning pills"
```

---

## Task 2: F1 rendering + remove copy/paste + zoom

Rewrite `TimelineGrid.vue` to render the layout; make unassign/edit operate on a whole span; add the zoom slider in the view. Remove all copy/paste code.

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (full rewrite)
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Modify: `frontend/src/__tests__/UsageTimelineView.spec.ts`
- Test: `frontend/src/__tests__/TimelineGrid.spec.ts` (new)

**Interfaces:**
- Consumes: `buildTimelineLayout`, `LayoutRow`, `LayoutPill` from Task 1.
- Produces (new emit signatures):
  - `drop: [architectId: number, month: string, data: { customerId: number; tentative: boolean; defaultUsagePercent: number; usagePercent?: number }]` (unchanged)
  - `unassign: [assignmentIds: number[]]` (changed from single id)
  - `edit-usage: [assignmentIds: number[], usage: number]` (changed from id+usage+month)
  - New prop `zoom: number` (px per 100%).

- [ ] **Step 1: Write failing component tests**

Create `frontend/src/__tests__/TimelineGrid.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TimelineGrid from '@/components/TimelineGrid.vue'
import type { ArchitectUsage } from '@/types'

const months = ['2026-01', '2026-02', '2026-03']

const usageData: ArchitectUsage[] = [{
  architectId: 1, architectName: 'Alice A', country: 'Singapore',
  months: {
    '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, tentative: false }] },
    '2026-02': { total: 20, assignments: [{ assignmentId: 11, customerId: 5, customerName: 'Acme', usage: 20, tentative: false }] },
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
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: FAIL — current component has no `data-testid="pill"`, wrong emit shapes.

- [ ] **Step 3: Rewrite `TimelineGrid.vue`**

Replace the entire contents of `frontend/src/components/TimelineGrid.vue`:

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { architectApi } from '@/api/client'
import type { ArchitectUsage } from '@/types'
import { buildTimelineLayout, type LayoutPill } from '@/composables/useTimelineLayout'

const props = defineProps<{ usageData: ArchitectUsage[]; months: string[]; zoom: number }>()

const emit = defineEmits<{
  drop: [architectId: number, month: string, data: { customerId: number; tentative: boolean; defaultUsagePercent: number; usagePercent?: number }]
  unassign: [assignmentIds: number[]]
  'edit-usage': [assignmentIds: number[], usage: number]
}>()

const layout = computed(() => buildTimelineLayout(props.usageData, props.months, props.zoom))

const editing = ref<{ assignmentIds: number[]; usage: number; el: HTMLElement } | null>(null)
const hoverPhoto = ref<{ src: string; top: number; left: number } | null>(null)

function onAvatarEnter(event: MouseEvent, architectId: number) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  hoverPhoto.value = { src: architectApi.photoUrl(architectId), top: rect.top - 20, left: rect.right + 8 }
}
function onAvatarLeave() { hoverPhoto.value = null }

function openEditor(event: MouseEvent, pill: LayoutPill) {
  event.stopPropagation()
  editing.value = { assignmentIds: pill.assignmentIds, usage: pill.usage, el: event.currentTarget as HTMLElement }
}
function onSlider(value: number) { if (editing.value) editing.value.usage = value }
function saveUsage() {
  if (!editing.value) return
  emit('edit-usage', editing.value.assignmentIds, editing.value.usage)
  editing.value = null
}
function closeEditor() { if (editing.value) saveUsage() }

function usageColor(total: number): string {
  if (total === 0) return 'transparent'
  if (total >= 50 && total <= 70) return '#dcfce7'
  if (total > 30 && total < 50) return '#fef3c7'
  return '#fecaca'
}
function usageLabelColor(total: number): string {
  if (total === 0) return '#94a3b8'
  if (total >= 50 && total <= 70) return '#16a34a'
  if (total > 30 && total < 50) return '#d97706'
  return '#dc2626'
}
function formatMonth(ym: string): string {
  const [year, month] = ym.split('-')
  const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${names[parseInt(month) - 1]} ${year.slice(2)}`
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  event.dataTransfer!.dropEffect = 'copy'
}
function onDrop(event: DragEvent, architectId: number, month: string) {
  event.preventDefault()
  const raw = event.dataTransfer!.getData('application/json')
  if (!raw) return
  const data = JSON.parse(raw)
  if (data.type === 'customer') {
    emit('drop', architectId, month, {
      customerId: data.customerId,
      tentative: data.tentative,
      defaultUsagePercent: data.defaultUsagePercent,
    })
  }
}
</script>

<template>
  <div class="timeline-grid" @click="closeEditor">
    <div class="grid-head">
      <div class="architect-col head">Architect</div>
      <div class="months-head">
        <div v-for="month in months" :key="month" class="month-col">{{ formatMonth(month) }}</div>
      </div>
    </div>

    <div v-for="row in layout" :key="row.architectId" class="grid-row">
      <div class="architect-col">
        <div class="architect-info">
          <img :src="architectApi.photoUrl(row.architectId)" class="architect-avatar"
            @mouseenter="onAvatarEnter($event, row.architectId)" @mouseleave="onAvatarLeave"
            @error="($event.target as HTMLImageElement).style.display = 'none'" />
          <div>
            <div class="architect-name">{{ row.architectName.split(' ')[0] }}</div>
            <div class="architect-name">{{ row.architectName.split(' ').slice(1).join(' ') }}</div>
            <div class="architect-country">{{ row.country }}</div>
          </div>
        </div>
      </div>

      <div class="lane-area" :style="{ height: row.rowHeightPx + 'px' }">
        <!-- background drop-target cells + per-month totals -->
        <div class="cell-grid">
          <div v-for="month in months" :key="month" class="bg-cell"
            :style="{ background: usageColor(row.totalsByMonth[month]) }"
            @dragover="onDragOver" @drop="onDrop($event, row.architectId, month)">
            <span v-if="row.totalsByMonth[month] > 0" class="total-label"
              :style="{ color: usageLabelColor(row.totalsByMonth[month]) }">
              {{ row.totalsByMonth[month] }}%
            </span>
          </div>
        </div>

        <!-- pill layer -->
        <div v-for="lane in row.lanes" :key="lane.customerId" class="lane"
          :style="{ top: lane.topOffsetPx + 'px', height: lane.laneHeightPx + 'px' }">
          <div v-for="pill in lane.pills" :key="pill.startIdx" data-testid="pill"
            class="pill" :class="{ tentative: pill.tentative }"
            :style="{
              left: (pill.leftPct * 100) + '%',
              width: (pill.widthPct * 100) + '%',
              height: pill.heightPx + 'px',
            }"
            :title="`${pill.customerName} - ${pill.usage}%`"
            @click.stop="openEditor($event, pill)">
            <span class="pill-label">{{ pill.customerName }} {{ pill.usage }}%</span>
            <button class="pill-unassign" data-testid="pill-unassign"
              @click.stop="emit('unassign', pill.assignmentIds)" title="Unassign">&minus;</button>
          </div>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="editing" class="usage-editor-overlay" @click="closeEditor">
        <div class="usage-editor" @click.stop :style="{
          top: (editing.el.getBoundingClientRect().bottom + 4) + 'px',
          left: editing.el.getBoundingClientRect().left + 'px',
        }">
          <div class="usage-editor-value">{{ editing.usage }}%</div>
          <input type="range" min="0" max="100" step="5" :value="editing.usage"
            @input="onSlider(Number(($event.target as HTMLInputElement).value))" />
          <div class="usage-editor-labels"><span>0%</span><span>50%</span><span>100%</span></div>
          <button class="usage-editor-save" data-testid="usage-apply" @click="saveUsage">Apply</button>
        </div>
      </div>
    </Teleport>
    <Teleport to="body">
      <div v-if="hoverPhoto" class="avatar-popup" :style="{ top: hoverPhoto.top + 'px', left: hoverPhoto.left + 'px' }">
        <img :src="hoverPhoto.src" />
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.timeline-grid { overflow: auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; flex: 1; min-height: 0; }
.grid-head { display: flex; position: sticky; top: 0; z-index: 2; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
.grid-row { display: flex; border-bottom: 1px solid #f1f5f9; }
.architect-col { width: 160px; flex-shrink: 0; position: sticky; left: 0; background: #f8fafc; z-index: 1; padding: 4px; box-sizing: border-box; }
.architect-col.head { display: flex; align-items: center; font-weight: 600; font-size: 0.8rem; }
.architect-info { display: flex; align-items: center; gap: 8px; }
.architect-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; border: 2px solid #e2e8f0; cursor: pointer; }
.architect-name { font-weight: 600; font-size: 0.85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.architect-country { font-size: 0.75rem; color: #64748b; }
.months-head { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; flex: 1; }
.month-col { text-align: center; font-size: 0.8rem; padding: 4px 0; }
.lane-area { position: relative; flex: 1; min-width: 0; }
.cell-grid { position: absolute; inset: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; }
.bg-cell { border-right: 1px solid #f1f5f9; position: relative; transition: background 0.15s; }
.bg-cell:hover { outline: 2px dashed #3b82f6; outline-offset: -2px; }
.total-label { position: absolute; bottom: 1px; right: 2px; font-size: 0.7rem; font-weight: 700; }
.lane { position: absolute; left: 0; right: 0; }
.pill { position: absolute; bottom: 0; box-sizing: border-box; font-size: 0.7rem; padding: 1px 4px; border-radius: 3px;
  background: #bfdbfe; border: 1px solid #93c5fd; white-space: nowrap; overflow: hidden; display: flex; align-items: flex-end;
  gap: 2px; cursor: pointer; }
.pill:hover { background: #93c5fd; }
.pill.tentative { background: #fef9c3; border: 1px dashed #fbbf24; }
.pill.tentative:hover { background: #fde68a; }
.pill-label { overflow: hidden; text-overflow: ellipsis; flex: 1; }
.pill-unassign { background: none; border: none; color: #94a3b8; cursor: pointer; font-size: 0.85rem; font-weight: 700; line-height: 1; padding: 0 2px; flex-shrink: 0; border-radius: 3px; }
.pill-unassign:hover { color: #dc2626; background: rgba(220,38,38,0.1); }
.usage-editor-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 200; }
.usage-editor { position: fixed; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); width: 180px; z-index: 201; }
.usage-editor-value { text-align: center; font-size: 1.1rem; font-weight: 700; margin-bottom: 6px; color: #0f172a; }
.usage-editor input[type="range"] { width: 100%; accent-color: #3b82f6; }
.usage-editor-labels { display: flex; justify-content: space-between; font-size: 0.7rem; color: #94a3b8; margin-top: 2px; }
.usage-editor-save { width: 100%; margin-top: 8px; padding: 4px; font-size: 0.8rem; background: #3b82f6; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.usage-editor-save:hover { background: #2563eb; }
</style>

<style>
.avatar-popup { position: fixed; z-index: 300; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.2); padding: 4px; pointer-events: none; }
.avatar-popup img { width: 200px; height: 200px; object-fit: cover; border-radius: 6px; display: block; }
</style>
```

- [ ] **Step 4: Run component tests to verify they pass**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: PASS — all five tests green. (If the `width: 66.6` assertion is brittle on rounding, the value is `200/3 = 66.666…%`; the substring `66.6` matches.)

- [ ] **Step 5: Add the zoom slider and span-aware handlers in `UsageTimelineView.vue`**

In `frontend/src/views/UsageTimelineView.vue`, add a zoom ref initialised from `localStorage`. Insert after `const monthCount = ref(6)` (line ~44):

```ts
const zoom = ref(Number(localStorage.getItem('timeline.zoom')) || 160)
watch(zoom, (v) => localStorage.setItem('timeline.zoom', String(v)))
```

Replace `onUnassign` and `onEditUsage` (lines ~108-116) with span-aware versions:

```ts
async function onUnassign(assignmentIds: number[]) {
  await Promise.all(assignmentIds.map(id => assignmentApi.delete(id)))
  await loadData()
}

async function onEditUsage(assignmentIds: number[], usage: number) {
  await Promise.all(assignmentIds.map(id => assignmentApi.update(id, { usagePercent: usage })))
  await loadData()
}
```

In the timeline controls `<template v-if="activeTab === 'timeline'">` block, add a zoom slider before the export button (after the Months `<label>`, line ~150):

```html
<label>Zoom:
  <input type="range" min="80" max="320" step="20" v-model.number="zoom" data-testid="zoom-slider" />
</label>
```

Update the `<TimelineGrid>` usage to pass `zoom` and the new emit signatures (replace lines ~176-183):

```html
<TimelineGrid
  v-if="activeTab === 'timeline'"
  :usage-data="filteredUsage"
  :months="months"
  :zoom="zoom"
  @drop="onDrop"
  @unassign="onUnassign"
  @edit-usage="onEditUsage"
/>
```

- [ ] **Step 6: Remove clipboard tests and adapt handler tests in `UsageTimelineView.spec.ts`**

Open `frontend/src/__tests__/UsageTimelineView.spec.ts`. Delete every test that references `clipboard`, `metaKey`, `clipboard-bar`, or paste behavior. For any test that triggers `unassign`/`edit-usage`, update the emitted payloads to the new array signatures, e.g.:

```ts
// unassign now carries an array of ids
await grid.vm.$emit('unassign', [42])
// edit-usage now carries (ids, usage)
await grid.vm.$emit('edit-usage', [42], 30)
```

(Adjust to the file's actual mounting style — the key change is the payload shapes.)

- [ ] **Step 7: Run the timeline + view tests**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts src/__tests__/UsageTimelineView.spec.ts`
Expected: PASS — no remaining references to clipboard; handler tests use array payloads.

- [ ] **Step 8: Run the whole frontend suite and type-check**

Run: `cd frontend && npm run test && npx vue-tsc --noEmit`
Expected: PASS — full suite green, no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
cd frontend && git add src/components/TimelineGrid.vue src/views/UsageTimelineView.vue src/__tests__/TimelineGrid.spec.ts src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: proportional spanning pills + zoom, remove copy/paste"
```

---

## Task 3: F2 drag-to-extend

Add edge grips, a live drag preview, a conflict popup, and the `extend` plan emission. `computeExtendPlan` (Task 1) does the diffing; this task wires pointer events and the popup, plus the view handler.

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue`
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Test: `frontend/src/__tests__/TimelineGrid.spec.ts`

**Interfaces:**
- Consumes: `computeExtendPlan`, `ExtendPlan` from Task 1; `LayoutPill`.
- Produces new emit:
  - `extend: [{ architectId: number; customerId: number; usagePercent: number; tentative: boolean; addMonths: string[]; removeAssignmentIds: number[]; replaceMonths: { month: string; oldAssignmentId: number }[] }]`

- [ ] **Step 1: Write failing tests for the extend emission and conflict popup**

Append to `frontend/src/__tests__/TimelineGrid.spec.ts`:

```ts
import { nextTick } from 'vue'

describe('TimelineGrid (F2 drag-to-extend)', () => {
  // architect 1, customer Acme spans Jan-Feb (idx 0-1); customer Beta in Mar (idx 2)
  const data: ArchitectUsage[] = [{
    architectId: 1, architectName: 'Alice A', country: 'Singapore',
    months: {
      '2026-01': { total: 20, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 20, tentative: false }] },
      '2026-02': { total: 20, assignments: [{ assignmentId: 11, customerId: 5, customerName: 'Acme', usage: 20, tentative: false }] },
      '2026-03': { total: 30, assignments: [{ assignmentId: 30, customerId: 6, customerName: 'Beta', usage: 30, tentative: false }] },
    },
  }]

  function mountGrid() {
    return mount(TimelineGrid, { props: { usageData: data, months, zoom: 160 } })
  }

  it('emits extend with addMonths when a pill is extended into a free month', async () => {
    const wrapper = mountGrid()
    // call the exposed handler directly: extend Acme (start 0 span 2) to span 3 (add Mar idx 2 -> but Mar is Beta=different customer, so free for Acme)
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, tentative: false, customerName: 'Acme' },
      0, 3, 1,
    )
    await nextTick()
    const payload = wrapper.emitted('extend')![0][0] as any
    expect(payload).toMatchObject({
      architectId: 1, customerId: 5, usagePercent: 20, tentative: false,
      addMonths: ['2026-03'], removeAssignmentIds: [], replaceMonths: [],
    })
  })

  it('opens a conflict popup instead of emitting when the target month has the same customer', async () => {
    const wrapper = mountGrid()
    // Acme also appears in a month that already has an Acme assignment -> conflict.
    // Simulate by extending onto idx 2 while pretending Acme already occupies it.
    ;(wrapper.vm as any).applyExtend(
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, tentative: false, customerName: 'Acme' },
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
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, tentative: false, customerName: 'Acme' },
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
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, tentative: false, customerName: 'Acme' },
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
      { startIdx: 0, span: 2, assignmentIds: [10, 11], customerId: 5, usage: 20, tentative: false, customerName: 'Acme' },
      0, 3, 1, new Map([[2, 77]]),
    )
    await nextTick()
    await wrapper.find('[data-testid="conflict-cancel"]').trigger('click')
    expect(wrapper.emitted('extend')).toBeUndefined()
    expect(wrapper.find('[data-testid="conflict-popup"]').exists()).toBe(false)
  })
})
```

Note: tests drive the resolved-plan logic through an exposed `applyExtend(pill, newStartIdx, newSpan, architectId, existingByIdx?)` method (pointer-pixel math is too brittle for jsdom). The pointer handlers in Step 3 build the same arguments from drag geometry and call `applyExtend`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: FAIL — `applyExtend` is not defined; no conflict popup.

- [ ] **Step 3: Add drag state, `applyExtend`, grips, preview, and popup to `TimelineGrid.vue`**

In the `<script setup>` of `frontend/src/components/TimelineGrid.vue`, add imports and state. Update the import line:

```ts
import { buildTimelineLayout, computeExtendPlan, type LayoutPill, type LayoutRow } from '@/composables/useTimelineLayout'
```

Add the `extend` emit to `defineEmits`:

```ts
  extend: [{ architectId: number; customerId: number; usagePercent: number; tentative: boolean; addMonths: string[]; removeAssignmentIds: number[]; replaceMonths: { month: string; oldAssignmentId: number }[] }]
```

Add drag + popup state and logic (after the `editing`/`hoverPhoto` refs):

```ts
const drag = ref<{
  row: LayoutRow; pill: LayoutPill; edge: 'l' | 'r'; areaEl: HTMLElement
  previewStart: number; previewSpan: number
} | null>(null)

const conflict = ref<{
  architectId: number; pill: LayoutPill
  base: { addMonths: string[]; removeAssignmentIds: number[] }
  months: { month: string; oldAssignmentId: number }[]
} | null>(null)

// Map monthIdx -> existing assignmentId for this architect+customer across the window.
function existingForCustomer(row: LayoutRow, customerId: number): Map<number, number> {
  const m = new Map<number, number>()
  const lane = row.lanes.find(l => l.customerId === customerId)
  if (!lane) return m
  for (const p of lane.pills) {
    for (let k = 0; k < p.span; k++) m.set(p.startIdx + k, p.assignmentIds[k])
  }
  return m
}

function startDrag(event: PointerEvent, row: LayoutRow, pill: LayoutPill, edge: 'l' | 'r') {
  event.stopPropagation()
  event.preventDefault()
  const areaEl = (event.currentTarget as HTMLElement).closest('.lane-area') as HTMLElement
  drag.value = { row, pill, edge, areaEl, previewStart: pill.startIdx, previewSpan: pill.span }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd, { once: true })
}

function pointerIdx(d: NonNullable<typeof drag.value>, clientX: number): number {
  const rect = d.areaEl.getBoundingClientRect()
  const frac = (clientX - rect.left) / rect.width
  const idx = Math.floor(frac * props.months.length)
  return Math.max(0, Math.min(props.months.length - 1, idx))
}

function onDragMove(event: PointerEvent) {
  const d = drag.value
  if (!d) return
  const idx = pointerIdx(d, event.clientX)
  if (d.edge === 'r') {
    const start = d.pill.startIdx
    d.previewStart = start
    d.previewSpan = Math.max(1, idx - start + 1)
  } else {
    const end = d.pill.startIdx + d.pill.span - 1
    d.previewStart = Math.min(idx, end)
    d.previewSpan = end - d.previewStart + 1
  }
}

function onDragEnd() {
  window.removeEventListener('pointermove', onDragMove)
  const d = drag.value
  drag.value = null
  if (!d) return
  if (d.previewStart === d.pill.startIdx && d.previewSpan === d.pill.span) return
  applyExtend(d.pill, d.previewStart, d.previewSpan, d.row.architectId, existingForCustomer(d.row, d.pill.customerId))
}

// Exposed for tests and called by onDragEnd. existingByIdx defaults to the customer's real occupancy.
function applyExtend(
  pill: LayoutPill, newStartIdx: number, newSpan: number, architectId: number,
  existingByIdx?: Map<number, number>,
) {
  const row = layout.value.find(r => r.architectId === architectId)!
  const existing = existingByIdx ?? existingForCustomer(row, pill.customerId)
  const plan = computeExtendPlan(pill, newStartIdx, newSpan, props.months, existing)
  if (plan.conflicts.length > 0) {
    conflict.value = {
      architectId, pill,
      base: { addMonths: plan.addMonths, removeAssignmentIds: plan.removeAssignmentIds },
      months: plan.conflicts,
    }
    return
  }
  emitExtend(architectId, pill, plan.addMonths, plan.removeAssignmentIds, [])
}

function emitExtend(
  architectId: number, pill: LayoutPill,
  addMonths: string[], removeAssignmentIds: number[],
  replaceMonths: { month: string; oldAssignmentId: number }[],
) {
  emit('extend', {
    architectId, customerId: pill.customerId, usagePercent: pill.usage, tentative: pill.tentative,
    addMonths, removeAssignmentIds, replaceMonths,
  })
}

function resolveConflict(mode: 'replace' | 'skip' | 'cancel') {
  const c = conflict.value
  conflict.value = null
  if (!c || mode === 'cancel') return
  const replaceMonths = mode === 'replace' ? c.months : []
  emitExtend(c.architectId, c.pill, c.base.addMonths, c.base.removeAssignmentIds, replaceMonths)
}

defineExpose({ applyExtend })
```

In the `<template>`, add grips inside the `.pill` div (after the `pill-unassign` button):

```html
            <span class="grip grip-l" @pointerdown="startDrag($event, row, pill, 'l')"></span>
            <span class="grip grip-r" @pointerdown="startDrag($event, row, pill, 'r')"></span>
```

Add a drag preview overlay inside `.lane-area` (after the `lane` v-for loop, before `</div>` of `.lane-area`):

```html
        <div v-if="drag && drag.row.architectId === row.architectId" class="drag-preview" :style="{
          left: (drag.previewStart / months.length * 100) + '%',
          width: (drag.previewSpan / months.length * 100) + '%',
        }"></div>
```

Add the conflict popup before the closing `</div>` of `.timeline-grid` (as a Teleport):

```html
    <Teleport to="body">
      <div v-if="conflict" class="conflict-overlay" @click="resolveConflict('cancel')">
        <div class="conflict-popup" data-testid="conflict-popup" @click.stop>
          <p><strong>{{ conflict.months.length }} month(s)</strong> already have
            {{ conflict.pill.customerName }}.</p>
          <p>Replace them with the extended {{ conflict.pill.usage }}%?</p>
          <div class="conflict-btns">
            <button class="primary" data-testid="conflict-replace" @click="resolveConflict('replace')">Replace</button>
            <button data-testid="conflict-skip" @click="resolveConflict('skip')">Skip month</button>
            <button data-testid="conflict-cancel" @click="resolveConflict('cancel')">Cancel</button>
          </div>
        </div>
      </div>
    </Teleport>
```

Add styles to the scoped `<style>` block:

```css
.pill .grip { position: absolute; top: 0; bottom: 0; width: 7px; cursor: ew-resize; opacity: 0; background: #2563eb; border-radius: 3px; }
.pill:hover .grip { opacity: 0.85; }
.grip-l { left: -3px; }
.grip-r { right: -3px; }
.drag-preview { position: absolute; top: 0; bottom: 0; background: #eff6ff; outline: 2px dashed #3b82f6; outline-offset: -2px; pointer-events: none; z-index: 1; }
.conflict-overlay { position: fixed; inset: 0; z-index: 210; background: rgba(15,23,42,0.15); }
.conflict-popup { position: fixed; top: 40%; left: 50%; transform: translateX(-50%); background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 6px 24px rgba(0,0,0,0.18); padding: 14px; width: 240px; font-size: 0.85rem; z-index: 211; }
.conflict-btns { display: flex; gap: 6px; margin-top: 10px; }
.conflict-btns button { font-size: 0.8rem; padding: 4px 8px; border-radius: 4px; border: 1px solid #cbd5e1; background: #f8fafc; cursor: pointer; }
.conflict-btns button.primary { background: #3b82f6; color: #fff; border-color: #3b82f6; }
```

- [ ] **Step 4: Run the F2 tests to verify they pass**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: PASS — all F1 and F2 tests green.

- [ ] **Step 5: Add the `extend` handler in `UsageTimelineView.vue`**

After `onEditUsage` in `frontend/src/views/UsageTimelineView.vue`, add:

```ts
async function onExtend(plan: {
  architectId: number; customerId: number; usagePercent: number; tentative: boolean
  addMonths: string[]; removeAssignmentIds: number[]
  replaceMonths: { month: string; oldAssignmentId: number }[]
}) {
  // deletes first (shrink + replace), then creates (add + replace)
  await Promise.all([
    ...plan.removeAssignmentIds.map(id => assignmentApi.delete(id)),
    ...plan.replaceMonths.map(r => assignmentApi.delete(r.oldAssignmentId)),
  ])
  const createMonths = [...plan.addMonths, ...plan.replaceMonths.map(r => r.month)]
  await Promise.all(createMonths.map(month => assignmentStore.create({
    architectId: plan.architectId,
    customerId: plan.customerId,
    usagePercent: plan.usagePercent,
    tentative: plan.tentative,
    month,
  })))
  await loadData()
}
```

Wire it on the `<TimelineGrid>` element:

```html
  @extend="onExtend"
```

- [ ] **Step 6: Add a view-level test for `onExtend`**

In `frontend/src/__tests__/UsageTimelineView.spec.ts`, add a test that emits `extend` from the grid stub and asserts the API calls. Match the file's existing mocking of `@/api/client`; the assertions:

```ts
// given a mocked assignmentApi with delete + create spies and a grid wrapper:
await grid.vm.$emit('extend', {
  architectId: 1, customerId: 5, usagePercent: 20, tentative: false,
  addMonths: ['2026-03'], removeAssignmentIds: [], replaceMonths: [{ month: '2026-04', oldAssignmentId: 77 }],
})
await flushPromises()
expect(assignmentApi.delete).toHaveBeenCalledWith(77)
// two creates: the added month and the replaced month
expect(assignmentApi.create).toHaveBeenCalledTimes(2)
```

- [ ] **Step 7: Run the full suite and type-check**

Run: `cd frontend && npm run test && npx vue-tsc --noEmit`
Expected: PASS — entire suite green, no TypeScript errors.

- [ ] **Step 8: Manual smoke check**

Run the app (`cd frontend && npm run dev`, backend running per CLAUDE.md). Verify: pills are proportional and span months; hovering shows edge grips; dragging the right grip previews and extends; extending onto an existing same-customer month shows the popup with Replace / Skip month / Cancel; the zoom slider rescales rows; copy/paste is gone.

- [ ] **Step 9: Commit**

```bash
cd frontend && git add src/components/TimelineGrid.vue src/views/UsageTimelineView.vue src/__tests__/TimelineGrid.spec.ts src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: drag-to-extend assignments with conflict resolution"
```

---

## Self-Review Notes

- **Spec coverage:** F1 proportional height (Task 2 Step 1/3), multi-month merge (Task 1), delete button at right end of span (`pill-unassign` is the last flex child; Task 2), min height floor (Task 1), zoom slider + persistence (Task 2 Step 5). F2 edge handles (Task 3 grips), extend/shrink create-delete mapping (Task 1 `computeExtendPlan` + Task 3 `onExtend`), conflict confirm with Replace/Skip/Cancel (Task 3), full copy/paste removal (Task 2 Step 3 rewrite + Step 6 test cleanup). Usage edit whole span + drop single-month unchanged (Task 2).
- **Type consistency:** emit names `unassign(assignmentIds[])`, `edit-usage(assignmentIds[], usage)`, `extend({...})` are identical across component, view, and tests. `LayoutPill` fields (`startIdx`, `span`, `assignmentIds`, `usage`, `tentative`, `customerId`, `customerName`, `leftPct`, `widthPct`, `heightPx`) match Task 1's definition and Task 3's usage.
- **Sequencing:** Task 1 (pure, no UI) → Task 2 (F1 display, depends on Task 1) → Task 3 (F2, depends on Tasks 1 & 2). Matches the spec's F2-depends-on-F1 constraint.
```
