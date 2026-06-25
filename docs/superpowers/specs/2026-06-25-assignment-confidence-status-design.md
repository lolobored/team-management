# Assignment Confidence Status — Design Spec

**Date:** 2026-06-25
**Feature:** Backlog F5 — replace the binary `tentative` flag with a three-level confidence status.
**Area:** Full stack (backend entity + migration + API, frontend types + timeline + map + export).

## Summary

Assignments currently carry a boolean `tentative`. This reflects sales-pipeline reality
poorly: some assignments are signed, some are probable pre-sales, some are speculative.
Replace `tentative` with an enum `status` of three levels:

| Status | Meaning | Pill style |
|---|---|---|
| `CONFIRMED` (default) | Deal signed, assignment is happening | Blue, solid border (current look) |
| `PROBABLE` | Pre-sales, likely | Orange, dashed border |
| `POTENTIAL` | Might happen, not certain | Yellow, dotted border |

Users change a pill's status via a **right-click context menu**. Status is display/labeling
only — it does **not** weight usage totals (the current `tentative` doesn't either).

## Goals

- One enum field replaces `tentative` across the stack; no dual-field coexistence.
- Existing data migrates: `tentative=true → POTENTIAL`, `tentative=false → CONFIRMED`.
- Each status is visually distinct on the timeline; a legend explains the colors.
- Changing status is a direct gesture (right-click) and persists per assignment.

## Non-Goals

- No change to usage aggregation math (status never weights totals).
- No back-compat for the old `tentative` field (internal tool, no external API clients).
- No bulk status editing UI beyond "whole span" (a right-click applies to every month in the
  spanned pill, consistent with how usage-edit and unassign already work post-F1/F2).

## Decisions (resolved during brainstorming)

- **Storage:** enum persisted as STRING (`@Enumerated(EnumType.STRING)`), not ordinal.
- **Default:** new assignments are `CONFIRMED`.
- **Status-change UX:** right-click context menu on a pill (not folded into the click/usage popup).
- **Scope of a status change:** the whole spanned pill (all `assignmentIds` in it).
- **No firm/soft business rule:** status is display-only; usage totals ignore it.

## Backend

### Domain model

- New enum `org.lolobored.tm.assignment.AssignmentStatus { CONFIRMED, PROBABLE, POTENTIAL }`.
- `Assignment` entity: replace
  `@Column(nullable = false) private boolean tentative;`
  with
  `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AssignmentStatus status = AssignmentStatus.CONFIRMED;`
- Replace `isTentative()/setTentative(boolean)` with `getStatus()/setStatus(AssignmentStatus)`.

### Migration — `db/changelog/013-assignment-status.yaml`

Registered in `db.changelog-master.yaml`. Portable across H2 (dev) and PostgreSQL (prod):

1. `addColumn`: `status VARCHAR(20)`, nullable initially.
2. Backfill with two portable `update` changes (no `CASE`):
   - set `status = 'POTENTIAL'` where `tentative = TRUE`
   - set `status = 'CONFIRMED'` where `tentative = FALSE`
3. `addNotNullConstraint` on `status` with `defaultValue: 'CONFIRMED'`.
4. `dropColumn`: `tentative`.

No existing row maps to `PROBABLE`; it only arises from later user action. Never edit prior
changelogs — this is a new file.

### API

- **`AssignmentUsageDto`** (`record`): `int usage, boolean tentative` → `int usage,
  AssignmentStatus status`. `UsageService` builds the DTO with `a.getStatus()`.
- **POST `/assignments`:** request body's `status` is optional; when omitted the entity's
  `CONFIRMED` default applies. (The frontend sends `CONFIRMED` explicitly on sidebar drop.)
- **PATCH `/assignments/{id}`:** replace the `tentative` key handling. When `updates`
  contains `status`, read the string and `AssignmentStatus.valueOf(...)`; on an unknown value
  return HTTP **400** (do not silently ignore). This endpoint backs the context menu.
- Jackson serializes/deserializes the enum as its name string (`"PROBABLE"`); no custom
  converter needed.

### Export

- `UsageExportService`: the per-assignment cell prefix changes from `(T) ` for tentative to a
  per-status prefix — `CONFIRMED` → `""`, `PROBABLE` → `"(P) "`, `POTENTIAL` → `"(T) "`.

## Frontend

### Types

`frontend/src/types/index.ts`:

- Add `export type AssignmentStatus = 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL'`.
- `Assignment.tentative: boolean` → `status: AssignmentStatus`.
- `AssignmentUsage.tentative: boolean` → `status: AssignmentStatus`.

### Data flow

- `CustomerSidebar` drag payload: replace `tentative: false` with `status: 'CONFIRMED'`.
- `useTimelineLayout`: the merge-equality check keys on `status` instead of `tentative`
  (two adjacent months merge only if customer + usage + **status** match). `LayoutPill.tentative`
  → `status: AssignmentStatus`.
- `TimelineGrid`:
  - Pill CSS variant is chosen by `status`: base/confirmed = current blue-solid;
    `.status-probable` = orange (`#fed7aa` bg, `#fb923c` dashed border);
    `.status-potential` = yellow (`#fef9c3` bg, `#facc15` dotted border). The existing
    `.tentative` class is removed.
  - New emit `set-status: [assignmentIds: number[], status: AssignmentStatus]`.
  - Right-click handler (`@contextmenu.prevent`) opens an inline context menu positioned at
    the cursor (same inline `position: fixed` overlay pattern as the F2 conflict popup — **not**
    `<Teleport>`, so component tests can find it). The menu lists the three statuses with a
    color swatch + label; the pill's current status is marked (checkmark). Selecting one emits
    `set-status` with the pill's full `assignmentIds` and closes the menu. Click-outside or Esc
    closes without change.
- `UsageTimelineView`:
  - `onDrop` / `onExtend` carry `status` instead of `tentative` (extend copies the dragged
    pill's `status`).
  - New handler `onSetStatus(assignmentIds, status)`: `Promise.all` PATCH each id with
    `{ status }`, then `loadData()`. Wrap with the existing try/catch + `finally loadData`
    style used by `onExtend`/`onDrop`.
  - A small **legend** (three color swatches + labels) added to the timeline controls.

### Map

- `WorldMapView`: the assignment type's `tentative` → `status`; the popup line shows
  `(probable)` or `(potential)` for those statuses and nothing for `CONFIRMED`.

## Testing

### Backend

- Migration applies cleanly on the test DB; an assignment seeded as `tentative=true` reads
  back as `POTENTIAL`, `tentative=false` as `CONFIRMED` (via repository/integration test).
- POST without `status` creates a `CONFIRMED` assignment.
- PATCH `{status: "PROBABLE"}` updates the status; PATCH `{status: "BOGUS"}` returns 400.
- Existing assignment controller/usage tests updated from `tentative` to `status`.

### Frontend

- `useTimelineLayout`: two adjacent months with the same customer+usage but different
  `status` split into separate pills; same status merges. (Replaces the existing
  tentative-split test.)
- `TimelineGrid`: right-click a pill opens the context menu; selecting a status emits
  `set-status` with all span ids and the chosen status; pill carries the correct status class.
- `CustomerSidebar`: drag payload includes `status: 'CONFIRMED'`.
- `UsageTimelineView`: `onSetStatus` PATCHes each id and refetches.
- `WorldMapView`: popup shows the right per-status label.

## Files

**Backend**
- New: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentStatus.java`
- New: `backend/src/main/resources/db/changelog/013-assignment-status.yaml` (+ register in master)
- Modify: `Assignment.java`, `AssignmentController.java`, `AssignmentUsageDto.java`,
  `UsageService.java`, `UsageExportService.java`
- Modify tests: assignment controller test, usage service test

**Frontend**
- Modify: `types/index.ts`, `components/CustomerSidebar.vue`, `composables/useTimelineLayout.ts`,
  `components/TimelineGrid.vue`, `views/UsageTimelineView.vue`, `components/WorldMapView.vue`
- Modify tests: `useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`,
  `WorldMapView.spec.ts`

## Sequencing note

Backend (enum + migration + API + export) is independent of and precedes the frontend wiring.
Within frontend, the type change ripples to all consumers, so the type + layout + sidebar land
together, then the timeline context menu, then the map/legend polish.
