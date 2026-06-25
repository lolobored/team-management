# Assignment Confidence Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the boolean `tentative` flag on assignments with a three-level `AssignmentStatus` enum (CONFIRMED / PROBABLE / POTENTIAL) across the whole stack, changed via a right-click context menu on timeline pills.

**Architecture:** Backend first — new enum, entity field swap, a Liquibase migration backfilling existing data, and API/export updates (Java compilation couples these into one task). Then frontend type migration + 3-variant pill rendering, then the right-click context menu + persistence + legend.

**Tech Stack:** Java 25 / Spring Boot (Liquibase, JPA, MockMvc tests), Vue 3 `<script setup>` + TypeScript + Vitest.

## Global Constraints

- Enum values exactly: `CONFIRMED`, `PROBABLE`, `POTENTIAL`. Default = `CONFIRMED`.
- Enum persisted as STRING (`@Enumerated(EnumType.STRING)`), column `varchar(20)`.
- Migration: `tentative=true → POTENTIAL`, `tentative=false → CONFIRMED`. New changelog only — never edit an existing changelog. Register in `db.changelog-master.yaml`. Must work on H2 (dev) and PostgreSQL (prod).
- Clean switch — no `tentative` field remains anywhere after this work.
- Status is display-only: it must NOT change usage-total math.
- PATCH `/assignments/{id}` with an unknown `status` string returns HTTP 400.
- Frontend type: `export type AssignmentStatus = 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL'`.
- Pill styles — CONFIRMED: blue solid (current); PROBABLE: `#fed7aa` bg, `#fb923c` dashed border; POTENTIAL: `#fef9c3` bg, `#facc15` dotted border.
- Export prefixes — CONFIRMED: `""`; PROBABLE: `"(P) "`; POTENTIAL: `"(T) "`.
- Map popup label — `(probable)` / `(potential)`; nothing for CONFIRMED.
- Backend tests: `cd backend && ./gradlew test` (Java 25 via SDKMAN: `source "$HOME/.sdkman/bin/sdkman-init.sh"; export JAVA_HOME=~/.sdkman/candidates/java/25.0.3-amzn`). Frontend tests: `cd frontend && npx vitest run`; type-check `npx vue-tsc --noEmit`.

---

## File Structure

**Backend (Task 1):**
- New `assignment/AssignmentStatus.java` — the enum.
- `assignment/Assignment.java` — field swap.
- New `db/changelog/013-assignment-status.yaml` + master include — migration.
- `assignment/AssignmentController.java` — PATCH handles `status`.
- `usage/AssignmentUsageDto.java`, `usage/UsageService.java` — DTO carries status.
- `usage/UsageExportService.java` — per-status prefix.
- Tests: `AssignmentControllerTest.java`, `usage/UsageServiceTest.java`.

**Frontend plumbing (Task 2):**
- `types/index.ts` — `AssignmentStatus` type + field swaps.
- `composables/useTimelineLayout.ts` — merge on status; `LayoutPill.status`.
- `components/CustomerSidebar.vue` — drag default `status: 'CONFIRMED'`.
- `views/UsageTimelineView.vue` — `onDrop`/`onExtend` carry status.
- `components/WorldMapView.vue` — status label.
- `components/TimelineGrid.vue` — pill variant classes + extend payload status.
- Tests: `useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`, `WorldMapView.spec.ts`.

**Frontend context menu (Task 3):**
- `components/TimelineGrid.vue` — `@contextmenu` menu + `set-status` emit.
- `views/UsageTimelineView.vue` — `onSetStatus` + legend.
- Tests: `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`.

---

## Task 1: Backend — enum, migration, API, export

Java compilation couples every `tentative` reference, so these land together. TDD: update/extend the MockMvc + service tests first (they fail to compile / assert against the new field), then implement, then green.

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentStatus.java`
- Create: `backend/src/main/resources/db/changelog/013-assignment-status.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`
- Modify: `backend/src/main/java/org/lolobored/tm/assignment/AssignmentController.java:65-67`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/AssignmentUsageDto.java`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageService.java:63-65`
- Modify: `backend/src/main/java/org/lolobored/tm/usage/UsageExportService.java:70`
- Test: `backend/src/test/java/org/lolobored/tm/assignment/AssignmentControllerTest.java`
- Test: `backend/src/test/java/org/lolobored/tm/usage/UsageServiceTest.java`

**Interfaces:**
- Produces: enum `AssignmentStatus { CONFIRMED, PROBABLE, POTENTIAL }`; `Assignment.getStatus()/setStatus(AssignmentStatus)`; `AssignmentUsageDto(Long, Long, String, int, AssignmentStatus)`; REST contract — POST accepts optional `status` (defaults CONFIRMED), PATCH accepts `status` string (400 on invalid), GET/usage serialize `status` as its name string. The frontend (Tasks 2-3) depends on the JSON field being named `status` with values `"CONFIRMED"|"PROBABLE"|"POTENTIAL"`.

- [ ] **Step 1: Update the backend tests to the `status` contract (RED)**

In `AssignmentControllerTest.java`, replace every `"tentative": false` in the JSON request bodies with `"status": "CONFIRMED"` (lines 51, 71, 87, 103, 126). Then add three tests (place after the existing create test; adapt the existing `architectId`/`customerId`/`mockMvc`/helpers already in the class):

```java
    @Test
    void createDefaultsToConfirmedWhenStatusOmitted() throws Exception {
        String body = """
                {"architectId": %d, "customerId": %d, "usagePercent": 25, "month": "2026-06"}
                """.formatted(architectId, customerId);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void patchUpdatesStatus() throws Exception {
        String body = """
                {"architectId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(architectId, customerId);
        String created = mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PROBABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROBABLE"));
    }

    @Test
    void patchRejectsInvalidStatus() throws Exception {
        String body = """
                {"architectId": %d, "customerId": %d, "usagePercent": 25, "status": "CONFIRMED", "month": "2026-06"}
                """.formatted(architectId, customerId);
        String created = mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(patch("/api/assignments/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }
```

In `UsageServiceTest.java:83`, replace:
```java
        assertFalse(june.assignments().get(0).tentative());
```
with:
```java
        assertEquals(org.lolobored.tm.assignment.AssignmentStatus.CONFIRMED, june.assignments().get(0).status());
```
(Add a static import or use the fully-qualified name as shown. If the file lacks `assertEquals`, it is in `org.junit.jupiter.api.Assertions` — already imported alongside `assertFalse`.)

- [ ] **Step 2: Run backend tests to verify they fail (RED)**

```bash
cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && export JAVA_HOME=~/.sdkman/candidates/java/25.0.3-amzn
./gradlew test
```
Expected: compilation failure (`status()` / `setStatus` / `AssignmentStatus` don't exist yet).

- [ ] **Step 3: Create the enum**

`backend/src/main/java/org/lolobored/tm/assignment/AssignmentStatus.java`:
```java
package org.lolobored.tm.assignment;

public enum AssignmentStatus {
    CONFIRMED, PROBABLE, POTENTIAL
}
```

- [ ] **Step 4: Swap the entity field**

In `Assignment.java`, replace lines 27-28:
```java
    @Column(nullable = false)
    private boolean tentative;
```
with:
```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.CONFIRMED;
```
And replace the accessors (lines 42-43):
```java
    public boolean isTentative() { return tentative; }
    public void setTentative(boolean tentative) { this.tentative = tentative; }
```
with:
```java
    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }
```
(`jakarta.persistence.*` is already imported, so `@Enumerated`/`EnumType` resolve.)

- [ ] **Step 5: Add the migration changelog**

Create `backend/src/main/resources/db/changelog/013-assignment-status.yaml`:
```yaml
databaseChangeLog:
  - changeSet:
      id: 13a
      author: laurent
      comment: "Add status column to assignment"
      changes:
        - addColumn:
            tableName: assignment
            columns:
              - column:
                  name: status
                  type: varchar(20)
                  constraints:
                    nullable: true
  - changeSet:
      id: 13b
      author: laurent
      comment: "Backfill status from tentative"
      changes:
        - update:
            tableName: assignment
            columns:
              - column:
                  name: status
                  value: POTENTIAL
            where: tentative = TRUE
        - update:
            tableName: assignment
            columns:
              - column:
                  name: status
                  value: CONFIRMED
            where: tentative = FALSE
  - changeSet:
      id: 13c
      author: laurent
      comment: "Default + non-null on status, drop tentative"
      changes:
        - addDefaultValue:
            tableName: assignment
            columnName: status
            columnDataType: varchar(20)
            defaultValue: CONFIRMED
        - addNotNullConstraint:
            tableName: assignment
            columnName: status
            columnDataType: varchar(20)
            defaultNullValue: CONFIRMED
        - dropColumn:
            tableName: assignment
            columnName: tentative
```

Append to `db.changelog-master.yaml` (after the `012-remove-projects.yaml` include):
```yaml
  - include:
      file: db/changelog/013-assignment-status.yaml
```

- [ ] **Step 6: Update the PATCH handler**

In `AssignmentController.java`, replace lines 65-67:
```java
        if (updates.containsKey("tentative")) {
            existing.setTentative((Boolean) updates.get("tentative"));
        }
```
with:
```java
        if (updates.containsKey("status")) {
            try {
                existing.setStatus(AssignmentStatus.valueOf((String) updates.get("status")));
            } catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
            }
        }
```
(`AssignmentStatus` is in the same package — no import needed.)

- [ ] **Step 7: Update the usage DTO and its construction**

Replace the whole of `AssignmentUsageDto.java`:
```java
package org.lolobored.tm.usage;

import org.lolobored.tm.assignment.AssignmentStatus;

public record AssignmentUsageDto(
        Long assignmentId, Long customerId, String customerName,
        int usage, AssignmentStatus status
) {}
```

In `UsageService.java`, replace lines 63-65:
```java
                    monthAssignments.add(new AssignmentUsageDto(
                            assignment.getId(), assignment.getCustomerId(), customerName,
                            assignment.getUsagePercent(), assignment.isTentative()));
```
with:
```java
                    monthAssignments.add(new AssignmentUsageDto(
                            assignment.getId(), assignment.getCustomerId(), customerName,
                            assignment.getUsagePercent(), assignment.getStatus()));
```

- [ ] **Step 8: Update the export prefix**

In `UsageExportService.java`, replace line 70:
```java
                                String prefix = a.tentative() ? "(T) " : "";
```
with:
```java
                                String prefix = switch (a.status()) {
                                    case PROBABLE -> "(P) ";
                                    case POTENTIAL -> "(T) ";
                                    case CONFIRMED -> "";
                                };
```

- [ ] **Step 9: Run backend tests to verify green**

```bash
cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && export JAVA_HOME=~/.sdkman/candidates/java/25.0.3-amzn
./gradlew test
```
Expected: BUILD SUCCESSFUL — all tests pass, including the new create-default / patch-status / invalid-status tests and the Liquibase migration applying on the H2 test DB.

- [ ] **Step 10: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add backend/src
git commit -m "feat: replace assignment tentative boolean with status enum (backend)"
```

---

## Task 2: Frontend — type migration + 3-variant pill rendering

Swap the type everywhere it ripples and render the three pill variants. No new interaction yet (status still can't be changed by the user — that's Task 3). TDD where the change is logic (`useTimelineLayout`), direct edit + test-update where it's wiring/markup.

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/composables/useTimelineLayout.ts`
- Modify: `frontend/src/components/CustomerSidebar.vue:24-28`
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Modify: `frontend/src/components/WorldMapView.vue`
- Modify: `frontend/src/components/TimelineGrid.vue`
- Test: `frontend/src/__tests__/useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`, `WorldMapView.spec.ts`

**Interfaces:**
- Consumes: backend JSON `status` field (Task 1).
- Produces: `AssignmentStatus` TS type; `LayoutPill.status: AssignmentStatus` (was `tentative`); `TimelineGrid` `extend` emit payload field `status` (was `tentative`); pill CSS classes `status-probable` / `status-potential`. Task 3 consumes `LayoutPill.status` and the pill element for the context menu.

- [ ] **Step 1: Update the layout test to split on status (RED)**

In `useTimelineLayout.spec.ts`, update the `cell` helper and the tentative test. Change the helper signature from `tentative = false` to a status param:
```ts
function cell(assignmentId: number, customerId: number, customerName: string, usage: number, status: 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL' = 'CONFIRMED') {
  return { assignmentId, customerId, customerName, usage, status }
}
```
Replace the `'splits when tentative differs'` test with:
```ts
  it('splits when status differs', () => {
    const data = arch({
      '2026-01': { total: 20, assignments: [cell(10, 5, 'Acme', 20, 'CONFIRMED')] },
      '2026-02': { total: 20, assignments: [cell(11, 5, 'Acme', 20, 'PROBABLE')] },
    })
    const [row] = buildTimelineLayout(data, MONTHS, 160)
    expect(row.lanes[0].pills).toHaveLength(2)
    expect(row.lanes[0].pills.map(p => p.status)).toEqual(['CONFIRMED', 'PROBABLE'])
  })
```

- [ ] **Step 2: Run the layout test to verify failure (RED)**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: FAIL — `cell(...)` now produces `status`, but the composable still reads `tentative`, so pills lack a `status` field and the merge doesn't split.

- [ ] **Step 3: Update the shared types**

In `frontend/src/types/index.ts`:
- Add near the top: `export type AssignmentStatus = 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL'`
- In `Assignment`, replace `tentative: boolean` with `status: AssignmentStatus`.
- In `AssignmentUsage`, replace `tentative: boolean` with `status: AssignmentStatus`.

- [ ] **Step 4: Update the layout composable**

In `frontend/src/composables/useTimelineLayout.ts`:
- Update the import to include the type: `import type { ArchitectUsage, AssignmentStatus } from '@/types'`
- In the `Cell` interface, replace `tentative: boolean` with `status: AssignmentStatus`.
- In `LayoutPill`, replace `tentative: boolean` with `status: AssignmentStatus`.
- Where the cell is built (`lane.cells[i] = { assignmentId: a.assignmentId, usage: a.usage, tentative: a.tentative }`), change to `{ assignmentId: a.assignmentId, usage: a.usage, status: a.status }`.
- In the merge break condition, replace `cj.tentative !== c.tentative` with `cj.status !== c.status`.
- In the pushed pill object, replace `tentative: c.tentative` with `status: c.status`.

- [ ] **Step 5: Run the layout test to verify green**

Run: `cd frontend && npx vitest run src/__tests__/useTimelineLayout.spec.ts`
Expected: PASS — all layout tests including `splits when status differs`.

- [ ] **Step 6: Update the sidebar drag default**

In `frontend/src/components/CustomerSidebar.vue`, in the `onDragStart` JSON payload (around lines 24-28), replace `tentative: false,` with `status: 'CONFIRMED',`.

- [ ] **Step 7: Update the TimelineGrid pill variants and extend payload**

In `frontend/src/components/TimelineGrid.vue`:
- Update the layout import to also pull the type if needed: `import { buildTimelineLayout, computeExtendPlan, type LayoutPill, type LayoutRow } from '@/composables/useTimelineLayout'` (unchanged) and `import type { ArchitectUsage, AssignmentStatus } from '@/types'`.
- In the `extend` emit type, replace `tentative: boolean` with `status: AssignmentStatus`.
- In `applyExtend` / `emitExtend`, replace the `tentative: pill.tentative` field with `status: pill.status`.
- Replace the pill class binding. Find `:class="{ tentative: pill.tentative }"` and change to:
  `:class="{ 'status-probable': pill.status === 'PROBABLE', 'status-potential': pill.status === 'POTENTIAL' }"`
- In the scoped `<style>`, replace the two `.pill.tentative` rules:
```css
.pill.tentative { background: #fef9c3; border: 1px dashed #fbbf24; }
.pill.tentative:hover { background: #fde68a; }
```
with:
```css
.pill.status-probable { background: #fed7aa; border: 1px dashed #fb923c; }
.pill.status-probable:hover { background: #fdba74; }
.pill.status-potential { background: #fef9c3; border: 1px dotted #facc15; }
.pill.status-potential:hover { background: #fde68a; }
```

- [ ] **Step 8: Update the view drop/extend handlers**

In `frontend/src/views/UsageTimelineView.vue`:
- In `onDrop`'s `data` parameter type, replace `tentative: boolean` with `status: AssignmentStatus` and in the `assignmentStore.create({...})` call replace `tentative: data.tentative` with `status: data.status`. Import the type: add `AssignmentStatus` to the `@/types` import (or `import type { AssignmentStatus } from '@/types'`).
- In `onExtend`'s `plan` parameter type, replace `tentative: boolean` with `status: AssignmentStatus`, and in its `assignmentStore.create({...})` replace `tentative: plan.tentative` with `status: plan.status`.

- [ ] **Step 9: Update WorldMapView**

In `frontend/src/components/WorldMapView.vue`:
- In the local assignment type (line ~23), replace `tentative: boolean` with `status: AssignmentStatus` (import the type from `@/types`).
- Where the cell is built (line ~51), replace `tentative: a.tentative,` with `status: a.status,`.
- Replace the popup label (line ~146) `<template v-if="a.tentative"> (tentative)</template>` with:
  `<template v-if="a.status === 'PROBABLE'"> (probable)</template><template v-else-if="a.status === 'POTENTIAL'"> (potential)</template>`

- [ ] **Step 10: Update the remaining frontend tests' fixtures**

Update test fixtures that build assignments with `tentative` to use `status`:
- `TimelineGrid.spec.ts`: in every `usageData` assignment object, replace `tentative: false` with `status: 'CONFIRMED'`. Add one assertion that a `PROBABLE` pill carries the class — e.g. mount data with a `PROBABLE` assignment and assert `wrapper.find('[data-testid="pill"]').classes()` contains `status-probable`.
- `UsageTimelineView.spec.ts`: in the `extend`/`drop` emit payloads and any mocked usage data, replace `tentative` with `status` (`status: 'CONFIRMED'`).
- `WorldMapView.spec.ts`: in mocked assignment data replace `tentative: <bool>` with `status: 'CONFIRMED'|'PROBABLE'|'POTENTIAL'`; update any label assertion from `(tentative)` to `(probable)`/`(potential)` to match the chosen status.

- [ ] **Step 11: Run the full frontend suite and type-check**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: PASS — all suites green (the pre-existing `geo.spec.ts` localStorage failures, if present in this environment, are unrelated to this task; everything else passes), no TypeScript errors.

- [ ] **Step 12: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src
git commit -m "feat: render assignment status as three pill variants (frontend)"
```

---

## Task 3: Frontend — right-click status context menu + legend

Add the gesture that actually changes status, plus the persistence handler and the legend.

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue`
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Test: `frontend/src/__tests__/TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`

**Interfaces:**
- Consumes: `LayoutPill.status` and `assignmentIds` (Task 2); `assignmentApi.update(id, { status })` (PATCH, Task 1).
- Produces: `TimelineGrid` emit `set-status: [assignmentIds: number[], status: AssignmentStatus]`; view handler `onSetStatus`.

- [ ] **Step 1: Write failing tests for the context menu (RED)**

Append to `frontend/src/__tests__/TimelineGrid.spec.ts`:
```ts
describe('TimelineGrid (status context menu)', () => {
  const data: ArchitectUsage[] = [{
    architectId: 1, architectName: 'Alice A', country: 'Singapore',
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
})
```

- [ ] **Step 2: Run to verify failure (RED)**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: FAIL — no `status-menu` / `set-status` yet.

- [ ] **Step 3: Add the context menu to TimelineGrid**

In `frontend/src/components/TimelineGrid.vue` `<script setup>`:
- Add to `defineEmits`: `'set-status': [assignmentIds: number[], status: AssignmentStatus]`
- Add state and handlers (near the other refs):
```ts
const STATUS_OPTIONS: { value: AssignmentStatus; label: string; swatch: string }[] = [
  { value: 'CONFIRMED', label: 'Confirmed', swatch: '#bfdbfe' },
  { value: 'PROBABLE', label: 'Probable', swatch: '#fed7aa' },
  { value: 'POTENTIAL', label: 'Potential', swatch: '#fef9c3' },
]

const statusMenu = ref<{ assignmentIds: number[]; current: AssignmentStatus; x: number; y: number } | null>(null)

function openStatusMenu(event: MouseEvent, pill: LayoutPill) {
  event.preventDefault()
  event.stopPropagation()
  statusMenu.value = { assignmentIds: pill.assignmentIds, current: pill.status, x: event.clientX, y: event.clientY }
}

function pickStatus(status: AssignmentStatus) {
  if (statusMenu.value) emit('set-status', statusMenu.value.assignmentIds, status)
  statusMenu.value = null
}

function closeStatusMenu() { statusMenu.value = null }
```
- Make the top-level `@click="closeEditor"` on `.timeline-grid` also close the menu by adding `closeStatusMenu()` there, or add `closeStatusMenu` to the existing click handler. Simplest: change the root handler to `@click="closeEditor(); closeStatusMenu()"` — but Vue inline needs a method; instead add a wrapper:
```ts
function onRootClick() { closeEditor(); closeStatusMenu() }
```
and set the root element to `@click="onRootClick"`.
- Add the Esc handling: register a keydown listener while the menu is open. Add:
```ts
import { onUnmounted } from 'vue'
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') closeStatusMenu() }
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))
```
(If `onMounted` isn't already imported, add it to the `vue` import.)

In the `<template>`, add `@contextmenu="openStatusMenu($event, pill)"` to the `.pill` element. After the conflict popup block (before the closing `.timeline-grid` div, inline — NOT teleported), add:
```html
    <div v-if="statusMenu" class="status-menu-overlay" @click="closeStatusMenu" @contextmenu.prevent>
      <div class="status-menu" data-testid="status-menu" @click.stop
        :style="{ top: statusMenu.y + 'px', left: statusMenu.x + 'px' }">
        <button v-for="opt in STATUS_OPTIONS" :key="opt.value"
          class="status-option" :data-testid="`status-option-${opt.value}`"
          @click="pickStatus(opt.value)">
          <span class="status-swatch" :style="{ background: opt.swatch }"></span>
          <span class="status-label">{{ opt.label }}</span>
          <span v-if="statusMenu.current === opt.value" class="status-check">✓</span>
        </button>
      </div>
    </div>
```
Add styles to the scoped `<style>`:
```css
.status-menu-overlay { position: fixed; inset: 0; z-index: 220; }
.status-menu { position: fixed; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 6px 24px rgba(0,0,0,0.18); padding: 4px; z-index: 221; min-width: 150px; }
.status-option { display: flex; align-items: center; gap: 8px; width: 100%; padding: 6px 8px; border: none; background: none; cursor: pointer; font-size: 0.8rem; border-radius: 4px; text-align: left; }
.status-option:hover { background: #f1f5f9; }
.status-swatch { width: 14px; height: 14px; border-radius: 3px; border: 1px solid #cbd5e1; flex-shrink: 0; }
.status-label { flex: 1; }
.status-check { color: #2563eb; font-weight: 700; }
```

- [ ] **Step 4: Run TimelineGrid tests to verify green**

Run: `cd frontend && npx vitest run src/__tests__/TimelineGrid.spec.ts`
Expected: PASS — menu opens on right-click; picking PROBABLE emits `['set-status', [10,11], 'PROBABLE']` and closes.

- [ ] **Step 5: Write the failing view handler test (RED)**

In `frontend/src/__tests__/UsageTimelineView.spec.ts`, add a test mirroring the existing `extend`/`unassign` handler tests' style (mocked `assignmentApi`):
```ts
  it('onSetStatus patches each id with the new status and refetches', async () => {
    // mount view, grab the TimelineGrid stub/wrapper as the existing tests do
    await grid.vm.$emit('set-status', [10, 11], 'PROBABLE')
    await flushPromises()
    expect(assignmentApi.update).toHaveBeenCalledWith(10, { status: 'PROBABLE' })
    expect(assignmentApi.update).toHaveBeenCalledWith(11, { status: 'PROBABLE' })
  })
```
(Adapt `grid`/mount to the file's existing pattern.)

- [ ] **Step 6: Run to verify failure (RED)**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts`
Expected: FAIL — no `onSetStatus` / `@set-status` wiring yet.

- [ ] **Step 7: Add the view handler and legend**

In `frontend/src/views/UsageTimelineView.vue`:
- Add the handler after `onExtend`:
```ts
async function onSetStatus(assignmentIds: number[], status: AssignmentStatus) {
  try {
    await Promise.all(assignmentIds.map(id => assignmentApi.update(id, { status })))
  } finally {
    await loadData()
  }
}
```
- Wire it on the `<TimelineGrid>` element: add `@set-status="onSetStatus"`.
- Add a legend in the timeline controls block (inside `<template v-if="activeTab === 'timeline'">`, e.g. after the zoom slider):
```html
<div class="status-legend">
  <span class="legend-item"><span class="legend-swatch confirmed"></span>Confirmed</span>
  <span class="legend-item"><span class="legend-swatch probable"></span>Probable</span>
  <span class="legend-item"><span class="legend-swatch potential"></span>Potential</span>
</div>
```
- Add scoped styles:
```css
.status-legend { display: flex; gap: 0.75rem; align-items: center; font-size: 0.75rem; color: #475569; }
.legend-item { display: flex; align-items: center; gap: 0.25rem; }
.legend-swatch { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.legend-swatch.confirmed { background: #bfdbfe; border: 1px solid #93c5fd; }
.legend-swatch.probable { background: #fed7aa; border: 1px dashed #fb923c; }
.legend-swatch.potential { background: #fef9c3; border: 1px dotted #facc15; }
```

- [ ] **Step 8: Run the full suite and type-check**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: PASS — all suites green (pre-existing `geo.spec.ts` localStorage failures, if any, unrelated), no TypeScript errors.

- [ ] **Step 9: Manual smoke check**

Run frontend + backend (per CLAUDE.md). Verify: pills show three colors/borders by status; right-clicking a pill opens the status menu with the current status checked; picking a different status recolors the pill and persists across reload; the legend renders; drag-extend keeps the dragged pill's status; the world map popup shows `(probable)`/`(potential)`.

- [ ] **Step 10: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src
git commit -m "feat: right-click status context menu and timeline legend"
```

---

## Self-Review Notes

- **Spec coverage:** enum + STRING storage + default CONFIRMED (T1 S3-4); migration true→POTENTIAL/false→CONFIRMED + master include (T1 S5); POST default + PATCH 400 on bad status (T1 S1/S6/S9); DTO/usage/export status (T1 S7-8); types (T2 S3); merge-by-status (T2 S1/S4); sidebar default (T2 S6); drop/extend status (T2 S7-8); 3 pill variants (T2 S7); map label (T2 S9); right-click menu whole-span + set-status (T3 S3); persistence handler (T3 S7); legend (T3 S7). No usage-total math touched (status only flows through display paths).
- **Type consistency:** `AssignmentStatus` values `CONFIRMED|PROBABLE|POTENTIAL` identical across backend enum, TS type, JSON; `LayoutPill.status`, the `extend` payload `status`, and `set-status: [number[], AssignmentStatus]` align across Tasks 2-3 and the view handlers; export prefixes and pill colors copied verbatim from Global Constraints.
- **Sequencing:** T1 (backend, independent) → T2 (type migration, depends on the `status` JSON contract) → T3 (interaction, depends on `LayoutPill.status` + PATCH). Matches the spec's "backend precedes frontend" note.
```
