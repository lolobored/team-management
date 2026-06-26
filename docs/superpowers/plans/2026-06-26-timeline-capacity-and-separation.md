# Timeline Capacity Columns & Member Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each member-month a fixed 100%-capacity column (used pills at the bottom, unused remaining block on top), render members as separated cards with small month gaps, and keep multi-month pills continuous.

**Architecture:** Two frontend units. The pure `useTimelineLayout` composable changes the height model (fixed row height = the zoom `ref`, lanes anchored to the bottom, a new per-month `months[]` carrying total + remaining + over-allocation). `TimelineGrid.vue` re-renders: per-month remaining blocks replace the whole-cell heatmap, lanes render bottom-anchored, member rows become cards, and the month layer gains a column-gap while the pill layer stays continuous.

**Tech Stack:** Vue 3 `<script setup>` + TypeScript + Vitest + `@vue/test-utils`.

## Global Constraints

- Frontend only — no backend/API/data change.
- Capacity model A: used pills at the bottom, unused **remaining block on top**.
- `ref` (the `zoom` prop) = pixels per 100% capacity = the **fixed height of every member row**.
- Per month: `total` = sum of usage; `remainingPx = max(0, 100 − total)/100 × ref`; `over = total > 100`.
- Over-allocation (`over`): no remaining block; pills clamp to the column top (clipped); month flagged **red**.
- Utilization band colors keep the existing `usageColor` / `usageLabelColor` logic; the color now lives in the remaining block, not the whole cell.
- Member cards: grey container, rounded rows with `outline` (not `border`, to keep month columns aligned with the sticky header), `box-shadow`, ~6px gutter. `overflow: hidden` only on the lane area, never the row (preserves the sticky name column).
- Month gap (~4px `column-gap`) on the per-month layer **and** the header; **pills stay continuous** across gaps.
- Multi-month merge/span logic unchanged (consecutive same customer+usage+status → one pill).
- Tests: `cd frontend && npx vitest run <path>`; type-check `npx vue-tsc --noEmit`.

---

## Task 1: Capacity model in `useTimelineLayout`

Change the height model and add per-month capacity data. Pure function — TDD.

**Files:**
- Modify: `frontend/src/composables/useTimelineLayout.ts`
- Test: `frontend/src/__tests__/useTimelineLayout.spec.ts`

**Interfaces:**
- Produces (Task 2 consumes): `LayoutRow.rowHeightPx` is now **fixed = ref**; `LayoutRow.months: LayoutMonth[]` replaces `totalsByMonth`; lanes carry `bottomOffsetPx` (was `topOffsetPx`).
  ```ts
  interface LayoutMonth { month: string; total: number; remainingPx: number; over: boolean }
  interface LayoutLane { customerId: number; customerName: string; bottomOffsetPx: number; laneHeightPx: number; pills: LayoutPill[] }
  interface LayoutRow { teamMemberId: number; teamMemberName: string; country: string; months: LayoutMonth[]; rowHeightPx: number; lanes: LayoutLane[] }
  ```
  `LayoutPill`, `MIN_PILL_PX`, `LANE_GAP_PX`, `computeExtendPlan`, `ExtendPlan` unchanged.

- [ ] **Step 1: Update the layout tests (RED)**

In `frontend/src/__tests__/useTimelineLayout.spec.ts`, replace the assertions that reference `topOffsetPx`, `rowHeightPx` (as sum), and `totalsByMonth` with the new model. Add/replace these tests inside the `buildTimelineLayout` describe block (keep the existing merge/split/gap/order tests, which are unaffected):

```ts
  it('row height is fixed to ref regardless of usage', () => {
    const data = arch({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.rowHeightPx).toBe(160)
    // an empty member is still a full-height capacity row
    const [empty] = buildTimelineLayout(arch({}), MONTHS, 160)
    expect(empty.rowHeightPx).toBe(160)
  })

  it('computes per-month remaining capacity and over-allocation', () => {
    const data = arch({
      '2026-01': { total: 55, assignments: [cell(10, 5, 'Acme', 55)] },
      '2026-02': { total: 100, assignments: [cell(11, 5, 'Acme', 100)] },
      '2026-03': { total: 130, assignments: [cell(12, 5, 'Acme', 70), cell(13, 6, 'Beta', 60)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    const by = (m: string) => row.months.find(x => x.month === m)!
    expect(by('2026-01')).toMatchObject({ total: 55, remainingPx: (45 / 100) * 160, over: false })
    expect(by('2026-02')).toMatchObject({ total: 100, remainingPx: 0, over: false })
    expect(by('2026-03')).toMatchObject({ total: 130, remainingPx: 0, over: true })
    // a month with no data is 0% used -> full remaining
    expect(by('2026-04')).toMatchObject({ total: 0, remainingPx: 160, over: false })
  })

  it('stacks lanes from the bottom with cumulative bottomOffsetPx', () => {
    const data = arch({
      '2026-01': { total: 35, assignments: [cell(10, 5, 'Acme', 20), cell(20, 7, 'Delta', 15)] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    const [acme, delta] = row.lanes // earliest-start, alphabetical tiebreak: Acme then Delta
    expect(acme.bottomOffsetPx).toBe(0)
    expect(acme.laneHeightPx).toBe((20 / 100) * 160) // 32
    expect(delta.bottomOffsetPx).toBe(acme.laneHeightPx + LANE_GAP_PX) // 34
  })
```

(The existing `cell()` / `arch()` helpers stay. Remove any old assertions on `topOffsetPx` or `totalsByMonth`.)

- [ ] **Step 2: Run the layout tests to verify failure (RED)**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: FAIL — `rowHeightPx` is still the lane sum, `months` / `bottomOffsetPx` don't exist.

- [ ] **Step 3: Update the composable**

In `frontend/src/composables/useTimelineLayout.ts`:

Replace the `LayoutLane` and `LayoutRow` interfaces and add `LayoutMonth`:

```ts
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
```

In `buildTimelineLayout`, in the `ordered.map(...)` lane builder, rename the offset field from `topOffsetPx` to `bottomOffsetPx` (the cumulative value is identical — only the render meaning changes):

```ts
      const laneObj: LayoutLane = {
        customerId,
        customerName: lane.customerName,
        bottomOffsetPx: topOffset,
        laneHeightPx,
        pills,
      }
```

Replace the `totalsByMonth` block and the `return` with the fixed-height + months model:

```ts
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
```

(The local accumulator variable is still named `topOffset`; that's fine — it is the cumulative-from-bottom stack height. Leave the `let topOffset = 0` / `topOffset += laneHeightPx + LANE_GAP_PX` lines as-is.)

- [ ] **Step 4: Run the layout tests to verify they pass**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: PASS — all `buildTimelineLayout` tests including the new fixed-height, per-month remaining, and bottom-offset tests; `computeExtendPlan` tests still pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src/composables/useTimelineLayout.ts frontend/src/__tests__/useTimelineLayout.spec.ts
git commit -m "feat: fixed-capacity timeline layout (per-month remaining, bottom-anchored lanes)"
```

---

## Task 2: Capacity rendering, member cards, month gaps in `TimelineGrid`

Render the remaining blocks, bottom-anchored lanes, member cards, and month gaps; keep pills continuous.

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue`
- Test: `frontend/src/__tests__/TimelineGrid.spec.ts`
- Create: `docs/BACKLOG.md` (capture the future "rounder pill edges" item)

**Interfaces:**
- Consumes: `LayoutRow.months` (`LayoutMonth[]`), `LayoutRow.rowHeightPx` (= ref), `LayoutLane.bottomOffsetPx` from Task 1; existing `usageColor`/`usageLabelColor` helpers in the component.

- [ ] **Step 1: Write the failing component tests (RED)**

Append to `frontend/src/__tests__/TimelineGrid.spec.ts` (the existing fixtures use `status: 'CONFIRMED'`; reuse the file's `mount` style):

```ts
describe('TimelineGrid (capacity columns)', () => {
  const months = ['2026-01', '2026-02', '2026-03']
  const data: ArchitectUsageLike = [{
    teamMemberId: 1, teamMemberName: 'Alice A', country: 'Singapore',
    months: {
      '2026-01': { total: 55, assignments: [{ assignmentId: 10, customerId: 5, customerName: 'Acme', usage: 55, status: 'CONFIRMED' }] },
      '2026-03': { total: 130, assignments: [
        { assignmentId: 12, customerId: 5, customerName: 'Acme', usage: 70, status: 'CONFIRMED' },
        { assignmentId: 13, customerId: 6, customerName: 'Beta', usage: 60, status: 'CONFIRMED' },
      ] },
    },
  }] as any

  function mountGrid() {
    return mount(TimelineGrid, { props: { usageData: data, months, zoom: 160 } })
  }

  it('renders a remaining-capacity block with the total label for an under-100 month', () => {
    const wrapper = mountGrid()
    const rem = wrapper.findAll('[data-testid="remaining"]')
    expect(rem.length).toBe(3) // one per month
    // the 55% month: remaining block ~45% tall, label shows 55%
    const jan = wrapper.find('[data-testid="remaining-2026-01"]')
    expect(jan.attributes('style')).toContain('height: 45%')
    expect(jan.text()).toContain('55%')
  })

  it('flags an over-allocated month red with no remaining block', () => {
    const wrapper = mountGrid()
    const mar = wrapper.find('[data-testid="remaining-2026-03"]')
    expect(mar.attributes('style')).toContain('height: 0%')
    expect(mar.classes()).toContain('over')
    expect(mar.text()).toContain('130%')
  })

  it('member rows are fixed height and rendered as cards', () => {
    const wrapper = mountGrid()
    const lane = wrapper.find('.lane-area')
    expect(lane.attributes('style')).toContain('height: 160px')
    expect(wrapper.find('.grid-row').classes()).toContain('card')
  })
})
```

(If the spec file uses a shared `ArchitectUsageLike`/`TeamMemberUsage` fixture type, reuse it; the key is the `months` shape with `total` + `assignments`.)

- [ ] **Step 2: Run the component tests to verify failure (RED)**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: FAIL — no `remaining` testids, no `card` class, lane-area height not fixed.

- [ ] **Step 3: Replace the lane-area template (remaining blocks + bottom-anchored lanes)**

In `frontend/src/components/TimelineGrid.vue`, replace the entire `<div class="lane-area" ...>` block (the cell-grid + pill lanes + drag preview) with:

```html
      <div class="lane-area" :style="{ height: row.rowHeightPx + 'px' }">
        <!-- per-month remaining-capacity blocks + drop targets -->
        <div class="cell-grid">
          <div v-for="mo in row.months" :key="mo.month" class="bg-cell"
            @dragover="onDragOver" @drop="onDrop($event, row.teamMemberId, mo.month)">
            <div class="remaining" :class="{ over: mo.over }"
              :data-testid="`remaining-${mo.month}`"
              :style="{
                height: (mo.over ? 0 : Math.max(0, 100 - mo.total)) + '%',
                background: mo.over ? undefined : usageColor(mo.total),
              }">
              <span class="total-label" :style="{ color: mo.over ? '#dc2626' : usageLabelColor(mo.total) }">
                {{ mo.total }}%
              </span>
            </div>
          </div>
        </div>

        <!-- pill layer (continuous, bottom-anchored lanes) -->
        <div v-for="lane in row.lanes" :key="lane.customerId" class="lane"
          :style="{ bottom: lane.bottomOffsetPx + 'px', height: lane.laneHeightPx + 'px' }">
          <div v-for="pill in lane.pills" :key="pill.startIdx" data-testid="pill"
            class="pill" :class="{ 'status-probable': pill.status === 'PROBABLE', 'status-potential': pill.status === 'POTENTIAL' }"
            :style="{
              left: (pill.leftPct * 100) + '%',
              width: (pill.widthPct * 100) + '%',
              height: pill.heightPx + 'px',
            }"
            :title="`${pill.customerName} - ${pill.usage}%`"
            @click.stop="openEditor($event, pill)"
            @contextmenu="openStatusMenu($event, pill)">
            <span class="grip grip-l" @pointerdown="startDrag($event, row, pill, 'l')"></span>
            <span class="pill-label">{{ pill.customerName }} {{ pill.usage }}%</span>
            <button class="pill-unassign" data-testid="pill-unassign"
              @click.stop="emit('unassign', pill.assignmentIds)" title="Unassign">&minus;</button>
            <span class="grip grip-r" @pointerdown="startDrag($event, row, pill, 'r')"></span>
          </div>
        </div>
        <div v-if="drag && drag.row.teamMemberId === row.teamMemberId" class="drag-preview" :style="{
          left: (drag.previewStart / months.length * 100) + '%',
          width: (drag.previewSpan / months.length * 100) + '%',
        }"></div>
      </div>
```

Add the `card` class to the row: change `<div v-for="row in layout" :key="row.teamMemberId" class="grid-row">` to `class="grid-row card"`.

- [ ] **Step 4: Update the styles**

In the `<style scoped>` block of `TimelineGrid.vue`:

Replace these rules:

```css
.timeline-grid { overflow: auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; flex: 1; min-height: 0; }
.grid-row { display: flex; border-bottom: 1px solid #f1f5f9; }
.months-head { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; flex: 1; }
.lane-area { position: relative; flex: 1; min-width: 0; min-height: 44px; }
.cell-grid { position: absolute; inset: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; }
.bg-cell { border-right: 1px solid #f1f5f9; position: relative; transition: background 0.15s; }
.bg-cell:hover { outline: 2px dashed #3b82f6; outline-offset: -2px; }
.total-label { position: absolute; bottom: 1px; right: 2px; font-size: 0.7rem; font-weight: 700; }
.lane { position: absolute; left: 0; right: 0; }
```

with:

```css
.timeline-grid { overflow: auto; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 6px; flex: 1; min-height: 0; padding: 0 6px 6px; }
.grid-row { display: flex; }
.grid-row.card { margin-top: 6px; border-radius: 6px; outline: 1px solid #e2e8f0; outline-offset: -1px; box-shadow: 0 1px 2px rgba(0,0,0,0.06); background: #fff; }
.team-member-col { border-radius: 6px 0 0 6px; }
.months-head { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; column-gap: 4px; flex: 1; }
.lane-area { position: relative; flex: 1; min-width: 0; border-radius: 0 6px 6px 0; overflow: hidden; }
.cell-grid { position: absolute; inset: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; column-gap: 4px; }
.bg-cell { position: relative; display: flex; flex-direction: column; transition: background 0.15s; }
.bg-cell:hover { outline: 2px dashed #3b82f6; outline-offset: -2px; }
.remaining { width: 100%; position: relative; transition: height 0.15s; }
.remaining.over { box-shadow: inset 0 3px 0 #dc2626; }
.total-label { position: absolute; top: 1px; right: 3px; font-size: 0.7rem; font-weight: 700; }
.lane { position: absolute; left: 0; right: 0; }
```

(The `.team-member-col` already has `width:160px; position:sticky; left:0`; adding `border-radius` to it gives the card its left corners. The lane area's `overflow:hidden` clips the right rounded corners and the over-allocation clamp without breaking the sticky name column, which is a separate flex child of the row, not clipped.)

- [ ] **Step 5: Run the component tests to verify they pass**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: PASS — remaining blocks (one per month, 55%→`height: 45%`+label, over→`height: 0%`+`over` class+red), fixed 160px lane-area, `card` class. The existing pill/extend/status/edit tests still pass (pill markup unchanged; lanes now `bottom`-anchored but the pill `data-testid` and emits are unchanged).

- [ ] **Step 6: Capture the future "rounder pill edges" item**

Create `docs/BACKLOG.md`:

```markdown
# Feature Backlog

Future features and improvements for the Team Management tool.

## Planned

### Rounder pill edges

**Area:** Timeline Grid (frontend display)

Make the assignment pill corners rounder. Explore a few `border-radius` options
(and how rounding interacts with adjacent stacked pills and very short pills)
and pick one. Pairs with the capacity-column redesign
(`docs/superpowers/specs/2026-06-26-timeline-capacity-and-separation-design.md`).

---

*Add new features below this line.*
```

- [ ] **Step 7: Run the full suite + type-check**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: PASS — all suites green (pre-existing `geo.spec.ts` localStorage failures, if any in this environment, are unrelated), no TypeScript errors. In particular `UsageTimelineView.spec.ts` (which mounts the grid) stays green.

- [ ] **Step 8: Manual smoke check**

Run the app (`docker compose up -d postgres`; backend `bootRun`; frontend `npm run dev`). On `/timeline` verify: every member row is the same height and reads as a separated card; each month shows a colored remaining block at the top with the total %; a 55% member shows a 55% pill stack + 45% colored headroom; an over-100% month is clamped with a red flag; month columns have a small gap; a multi-month assignment is one continuous bar bridging the gaps; drag-extend, right-click status, usage slider, and drop-from-sidebar still work.

- [ ] **Step 9: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src/components/TimelineGrid.vue frontend/src/__tests__/TimelineGrid.spec.ts docs/BACKLOG.md
git commit -m "feat: capacity columns, member cards, and month gaps on the timeline"
```

---

## Self-Review Notes

- **Spec coverage:** fixed 100%-capacity row height (T1 S3; T2 lane-area 160px); per-month remaining block colored + total label (T1 months[]; T2 S3 remaining div); over-100 clamp + red flag (T1 over; T2 over class/red + lane-area overflow hidden); used pills at bottom (T2 bottom-anchored lanes); continuous spanning pills (pill layer not gapped; only cell-grid + header gapped); member cards (T2 S4 .grid-row.card + container grey + sticky-safe corners); month gaps (column-gap on cell-grid + months-head); heatmap color moved into remaining block (old `.bg-cell` fill removed); rounder-edges future item (T2 S6 BACKLOG). 
- **Type consistency:** `LayoutMonth`/`months[]`, `bottomOffsetPx`, `rowHeightPx = ref` are defined in Task 1 and consumed verbatim in Task 2's template; `usageColor`/`usageLabelColor` are existing component helpers.
- **Sticky/alignment risks honored:** `outline` (not `border`) on the card; `overflow: hidden` only on `.lane-area`, not `.grid-row`; header `.months-head` gets the same `column-gap` as `.cell-grid`.
- **Sequencing:** T1 (pure composable + tests) → T2 (render, depends on the new fields).
```
