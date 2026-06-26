# Timeline Capacity Columns & Member Separation — Design Spec

**Date:** 2026-06-26
**Feature:** Redesign the usage timeline so each member-month is a fixed 100%-capacity column showing used vs. unused capacity, with clearer member separation (cards) and month separation (gaps), while assignment pills stay continuous across months.
**Area:** Frontend only — `useTimelineLayout` composable + `TimelineGrid.vue` + tests. No backend/API/data change.

## Summary

The current timeline sizes each member row by its summed usage (a 55%-utilized member is shorter than a fully-booked one) and tints whole month cells by total utilization. Two problems: members are hard to tell apart (a faint 1px line between rows, heatmap colors of adjacent members bleed together), and you can't see a member's **unused capacity** at a glance.

This redesign:

1. Makes each month a **fixed-height 100% capacity column** — assignment pills fill from the bottom, the unused remainder is an explicit colored block on top. Every member row is now the same height.
2. Renders each member as a **banded card** with a gutter, for obvious separation.
3. Adds a **small gap between month columns**, while assignment pills remain **continuous** across months.

## Decisions (from brainstorming)

- **Capacity model A:** used pills at the bottom (fill gauge), unused **remaining block on top**.
- **Fixed row height:** every month is a 100%-capacity column; rows no longer shrink for low usage.
- **Over-allocation (>100%):** clamp the pill stack to the column top (no remaining block) and flag the month **red**.
- **Member separation:** banded cards (option D) with a gutter.
- **Month separation:** small `column-gap` on the per-month layer; **pills stay continuous** (a spanning assignment is one unbroken bar bridging the gaps).
- Utilization color moves from the whole-cell heatmap into the **remaining block** (+ its total-% label).

## Layout model

### Capacity column

`ref` = the zoom value, interpreted as **pixels per 100% capacity** (it already means px-per-100%-usage; the meaning is unchanged, the consequence is new). It is the **fixed height of every member row**.

Per member-month:

- `total` = sum of that month's assignment `usagePercent` (already computed today as the cell total).
- `usedPx = min(total, 100) / 100 × ref` — assignment pills occupy the bottom `usedPx`.
- `remainingPx = max(0, 100 − total) / 100 × ref` — the unused block on top.
- `over = total > 100`.

Assignment pills keep their existing geometry: positioned by month index (`leftPct`/`widthPct`), height `usagePercent/100 × ref`, stacked bottom-up in lanes. The **only** change is that the row height is now the fixed `ref` (capped), not the sum of lane heights, and the stack is clamped at `ref` when `over`.

Spanning/merge logic (consecutive same customer+usage+status months → one pill) is **unchanged** — pills remain continuous multi-month bars.

### Remaining-capacity block

On top of the pills, per month:

- Background = `usageColor(total)` (existing band logic: green 50–70, amber >30–<50, red otherwise; transparent/neutral at 0).
- Height = `remainingPx`.
- Shows the **total %** label in `usageLabelColor(total)`.
- `total = 100` → `remainingPx = 0`; the total label still renders, pinned at the column top.
- `over` (`total > 100`) → no remaining block; pills clamped to the top; total label rendered in **red** with a red top accent on the column.

This block **replaces** the old per-cell heatmap fill. The per-month column area remains the **drag-and-drop target** for creating assignments.

## `useTimelineLayout.ts` changes

Extend `LayoutRow`:

```ts
interface LayoutMonth {
  month: string
  total: number       // % (may exceed 100)
  remainingPx: number // max(0, 100 - total)/100 * ref
  over: boolean       // total > 100
}

interface LayoutRow {
  // ...existing: teamMemberId, teamMemberName, country, lanes, totalsByMonth
  rowHeightPx: number   // now FIXED = ref (was sum of lane heights)
  months: LayoutMonth[] // per-month capacity info, in window order
}
```

- `rowHeightPx = ref` (fixed for every row).
- Lane stacking unchanged except the cumulative offset is **capped at `ref`** (a month summing >100% clamps; the pills above the cap are not drawn / are clipped).
- `months[]` computed from `totalsByMonth` and `ref`.
- Pill `heightPx` formula unchanged (`max(usage/100 × ref, MIN_PILL_PX)`); `MIN_PILL_PX` floor retained for label legibility.

## `TimelineGrid.vue` changes

**Structure per member row (card):**
- A **remaining-block grid**: N columns with `column-gap` (~4px), each cell a flex column whose **top** holds the remaining block (`height: remainingPx`, `usageColor` bg, total label); the bottom is empty (pills overlay it). This grid is also the **drop-target** surface (one droppable cell per month).
- A **continuous pill layer**: absolute, positioned by `leftPct`/`widthPct`/`heightPx` (unchanged) — so spanning pills bridge the month gaps.

**Separation CSS:**
- Scroll container background → `#f1f5f9`; horizontal padding so cards inset.
- `.grid-row` → card: `border-radius: 6px`, `outline: 1px solid #e2e8f0` (offset `-1px`; outline, not border, so month columns stay aligned with the sticky header), `box-shadow: 0 1px 2px rgba(0,0,0,.06)`, `margin-bottom: ~6px`.
- Name column: left rounded corners; lane area: right rounded corners with `overflow: hidden` **on the lane area only** (not the row) so the sticky name column keeps sticking during horizontal scroll.
- Header `.months-head` gets the **same `column-gap`** as the remaining-block grid so labels stay aligned with month columns.

**Removed:** the old `.bg-cell` whole-cell heatmap coloring (utilization now lives in the remaining block).

**Zoom:** the `zoom` prop is the capacity-column height (`ref`); range/default unchanged (80–320, default 160). Taller zoom = taller capacity columns.

Drag-to-extend, right-click status menu, usage editor, unassign, drop-from-sidebar — all unchanged in behavior; they operate on the same pills/columns.

## Testing

**`useTimelineLayout.spec.ts`:**
- `rowHeightPx === ref` for every row (fixed), regardless of usage.
- `months[i].remainingPx === (100 - total)/100 * ref` for under-100; `0` at exactly 100; `0` + `over === true` when total > 100.
- Pill stack clamps at `ref` when a month is over-allocated (top pill not drawn beyond the cap).
- Spanning: consecutive same customer+usage+status months still merge to one pill (unchanged).

**`TimelineGrid.spec.ts`:**
- A member-month at 55% renders a remaining block of the right relative height with label "55%" and the band color class.
- Over-allocated month: total label is red / over-accent present; no remaining block.
- A multi-month assignment renders as **one** `[data-testid="pill"]` element spanning the months (continuous across the gap).
- Card present (`.grid-row` has the card class/outline); month `column-gap` applied to the remaining grid and the header.

## Non-Goals / Future

- **Rounder pill edges** — a separate future tweak; captured in `docs/BACKLOG.md` (recreated as part of this work), to be designed with options later.
- No release/tag after this change — it will ship in a later release bundled with other features.
- No backend, API, schema, or usage-aggregation change. Customer/assignment/geo behavior unchanged.

## Risks

- **Sticky name column vs rounded card:** `overflow: hidden` must be on the lane area only, never the row, or `position: sticky` on the name column breaks. Use `outline` (not `border`) on the card to avoid shifting month columns out of alignment with the header.
- **Over-allocation visual:** clamping must clip cleanly without overflowing the card; the red flag must be unmistakable.
- **Continuous pills vs gapped months:** pill edges (computed from equal `1/N` divisions) won't perfectly meet the gapped cell edges — acceptable and intended, since pills are continuous spans, not per-cell.
