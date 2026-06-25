# Proportional Usage Pills & Drag-to-Extend — Design Spec

**Date:** 2026-06-25
**Features:** Backlog F1 (Proportional Usage Pill Display) + F2 (Drag-to-Extend Assignments, replaces Copy/Paste)
**Area:** Timeline grid — frontend only. No backend, schema, or API changes.

## Summary

Today the timeline renders each assignment as a fixed-height pill inside an independent
table cell. Pills never cross month boundaries, height is uniform regardless of usage, and
multi-assignment workflows rely on Cmd+Click copy/paste.

This redesign:

- Makes pill **height proportional** to usage percentage.
- **Merges** consecutive months of the same assignment (same customer, usage, and status)
  into a single continuous horizontal bar.
- Replaces copy/paste with **drag-to-extend**: drag a pill's edge to grow/shrink the
  assignment across months.
- Adds a **zoom slider** to control row scale.

Both features are frontend-only — the backend already supports per-month assignment CRUD
(one assignment per architect-customer-month), so extend/shrink/edit map to existing
create/delete/update calls.

## Goals

- Visual utilization is readable at a glance: a 5% pill is clearly smaller than a 20% pill.
- A multi-month assignment reads as one bar, not N repeated pills.
- Extending/shrinking an assignment across months is a direct, discoverable drag gesture.
- Over-allocation (a month summing past 100%) is visually obvious.

## Non-Goals

- No backend/schema/API change.
- No change to the world-map tab.
- Confidence-status (F5) is a separate spec; this spec treats `status`/`tentative` as an
  opaque attribute that participates in pill-merge equality. (Currently `tentative: boolean`;
  if F5 lands first, substitute its `status` enum — the merge logic is identical.)

## Architecture

### Layout pass (new composable)

Introduce `frontend/src/composables/useTimelineLayout.ts`:

```
useTimelineLayout(usageData: ArchitectUsage[], months: string[], ref: number) => LayoutRow[]
```

A **pure function** (no DOM, no API) so it is unit-testable in isolation. `ref` is the
zoom value in pixels-per-100%-usage.

`TimelineGrid.vue` consumes the result and is reduced to presentation + drag gesture
handling. The heavy layout/merge logic lives in the composable.

### Output shape

```ts
interface LayoutRow {
  architectId: number
  architectName: string
  country: string
  totalsByMonth: Record<string, number>   // per-month sum %, for the total label + bg color
  rowHeightPx: number                       // sum of lane heights
  lanes: Lane[]                             // ordered top -> bottom
}

interface Lane {
  customerId: number
  customerName: string
  topOffsetPx: number    // cumulative height of lanes above
  laneHeightPx: number   // customer max usage in window * ref
  pills: Pill[]
}

interface Pill {
  assignmentIds: number[]   // every assignment row in the span (drives whole-span ops)
  customerId: number
  customerName: string
  startIdx: number          // index into months[]
  span: number              // number of months
  usage: number             // usage % for this segment
  status: AssignmentStatus  // opaque; participates in merge equality (today: tentative bool)
  leftPct: number           // startIdx / months.length
  widthPct: number          // span / months.length
  heightPx: number          // max(usage * ref, MIN_PILL_PX)
}
```

### Lane layout algorithm

Per architect, over the visible month window:

1. **Group** assignments by `customerId` → one lane each.
2. **Order lanes** top→bottom by the customer's earliest active month (min month present in
   the window). Tie-break alphabetically by `customerName`.
3. **Segment each lane into pills**: walk the lane's months left→right; start a new segment
   whenever `usagePercent` *or* `status` changes, or a month is missing (gap). Contiguous
   months with identical `(usage, status)` merge into one pill spanning them.
4. **Lane height** = customer's max usage % in the window × `ref`. A lane with a 20%→30%
   step reserves the 30% height; the 20% months render shorter, bottom-aligned, producing a
   visible step.
5. **Row height** = Σ lane heights. Each lane's `topOffsetPx` = cumulative height of lanes
   above it.
6. **Pill geometry**: horizontal from month indices as fractions (`leftPct`, `widthPct`) —
   **no pixel measurement needed**. Vertical from usage: `heightPx = max(usage * ref,
   MIN_PILL_PX)`, bottom-aligned within its lane.

`MIN_PILL_PX` ≈ 16px so small-percentage labels stay readable at low zoom.

### Rendering (TimelineGrid.vue)

- Architect name column stays a sticky left column.
- Each architect's months area is a `position: relative` container of height `rowHeightPx`.
  - **Background layer**: a CSS grid of N empty cells (`grid-template-columns: repeat(N,
    minmax(0,1fr))`) — these are the drop targets and drag-preview highlight surfaces.
  - **Pill layer**: absolutely positioned pills using `leftPct`/`widthPct`/`heightPx` +
    lane offset.
- Per-month total label + background color (existing `usageColor`/`usageLabelColor` logic)
  is retained, driven by `totalsByMonth`.

Column-header row and month formatting are unchanged.

## F2 — Drag-to-extend interaction

`TimelineGrid` is presentational: it computes drag intent locally and emits a resolved plan;
the parent `UsageTimelineView` performs the API calls and refetch (same division as today's
`drop`/`unassign`/`editUsage`).

### Edge handles

- Each pill shows draggable **left and right grips** on hover only (hidden otherwise to keep
  the grid clean). Both single-month and multi-month pills get both grips.
- The **unassign (`−`) button** sits at the pill's **right end** (the last month of the span).

### Drag lifecycle

- **During drag**: derive the target month range from pointer-x against column boundaries
  (fraction math). Highlight target cells and show a hatched ghost extension. Local state
  only — no API calls.
- **On release**, diff the old span against the new span:
  - **Extend** (grip dragged outward): for each newly covered month, check for a conflict —
    an existing assignment with the same architect + customer + month in `usageData`.
    - No conflict → plan a `create` (customerId, month, usagePercent, status copied from the
      dragged pill).
    - Conflict → collect for the confirmation popup.
  - **Shrink** (grip dragged inward): for each removed month, plan an `unassign` of that
    month's assignment id.

### Conflict confirmation

If any extended month already has this architect↔customer assignment, show **one batched
popup** listing all conflicting months, with three actions:

- **Replace** — for each conflicting month: `unassign` the existing row, then `create` the
  new one.
- **Skip month** — leave the existing assignment; do not create for that month. (The
  resulting span has a gap → renders as split pills, which is acceptable.)
- **Cancel** — abort the entire drag; no API calls.

### Emitted events

```ts
// One resolved plan after the popup is dismissed:
extend: [{
  architectId: number
  customerId: number
  usagePercent: number
  status: AssignmentStatus
  addMonths: string[]                                   // create these
  removeAssignmentIds: number[]                         // unassign these (shrink)
  replaceMonths: { month: string; oldAssignmentId: number }[]  // unassign+create
}]
```

`UsageTimelineView` executes the plan: issue the create/unassign calls (existing API
client methods), then refetch usage. Ordering: process `removeAssignmentIds` and
`replaceMonths` deletes, then creates; a single refetch at the end.

## Other interaction changes

- **Usage edit (whole span).** Clicking a pill opens the existing slider editor. Applying a
  new value retunes **every month in the span together** — the pill stays one bar. `editUsage`
  changes signature to carry the span's ids:
  `editUsage: [assignmentIds: number[], usage: number]`. `UsageTimelineView` loops the ids
  (or a future batch endpoint). To make a single month differ, the user shrinks/re-adds or
  drags — this spec does not add per-month editing inside a span.
- **Drop from sidebar** is unchanged: still creates a **single-month** assignment. It
  auto-merges into a neighboring pill on the next render if customer + usage + status match.
- **Unassign** via the `−` button removes the whole span (all `assignmentIds`).

## Removals (copy/paste)

Delete from `TimelineGrid.vue`:

- `ClipboardItem` interface; `clipboard`, `pasteCount` refs.
- `isInClipboard`, `toggleClipboardItem`, `clearClipboard`, `onCellClick`, `onKeydown`.
- The Escape key listener (`onMounted`/`onUnmounted` for keydown).
- Clipboard-bar UI, copy-hint UI, and the `data-testid="clipboard-bar"` block.
- `.paste-target` and `.selected` styles + the `selected` class plumbing on pills.
- The `isMac` platform sniff (only used by the copy hint).

After removal: cell `@click` (paste) is gone; plain pill click only opens the usage editor.
Delete the obsolete copy/paste component tests.

## Zoom slider

- A range input in the timeline controls (`UsageTimelineView`) sets `ref` (px per 100%).
- Range **80–320px**, default **160px**, step 20.
- Passed as a prop into `TimelineGrid` and on into `useTimelineLayout`.
- Persisted to `localStorage` (key `timeline.zoom`) so it survives reload.
- Tall rows simply scroll; not all architects need to fit on screen (accepted per backlog).

## Testing

**Unit — `useTimelineLayout` (primary coverage, pure function):**

- Merges contiguous same-(customer,usage,status) months into one pill.
- Splits on usage change, status change, and month gaps.
- Lane ordering by earliest start month; alphabetical tie-break.
- Lane height = customer max usage; row height = sum of lanes.
- Overlapping assignments stack into stable lanes with correct offsets.
- `MIN_PILL_PX` floor applied to small percentages.
- `leftPct`/`widthPct` correct for spans at the window edges.

**Component — `TimelineGrid`:**

- Grips appear on hover, hidden otherwise.
- Extend emits `extend` with correct `addMonths`.
- Shrink emits `extend` with correct `removeAssignmentIds`.
- Conflict path: popup lists conflicting months; Replace/Skip/Cancel produce the correct
  plan (`replaceMonths` vs gap vs no-op).
- Whole-span usage edit emits `editUsage` with all span ids.
- Right-end unassign removes all span ids.

**Cleanup:** delete copy/paste tests (clipboard-bar suite).

## Files

- **New:** `frontend/src/composables/useTimelineLayout.ts`
- **New:** `frontend/src/composables/__tests__/useTimelineLayout.spec.ts`
- **Modify:** `frontend/src/components/TimelineGrid.vue` — new rendering, drag handlers,
  remove copy/paste.
- **Modify:** `frontend/src/views/UsageTimelineView.vue` — zoom slider, handle `extend`,
  updated `editUsage`.
- **Modify:** `frontend/src/types/index.ts` — layout types (if not colocated in the
  composable).
- **Modify:** timeline component tests.

## Open dependencies / sequencing

- F2 depends on F1 (multi-month pills must exist before edges can be dragged across months).
  Build F1 first, then layer F2 onto the same composable output.
- If F5 (confidence status) is implemented first, `status` replaces `tentative` in the merge
  equality and the pill `status` field; no structural change to this design.
