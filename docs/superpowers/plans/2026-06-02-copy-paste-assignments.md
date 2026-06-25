# Copy & Paste Assignments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Cmd+Click copy/paste for assignments in the timeline grid so users can quickly replicate project assignments across months and architects.

**Architecture:** All changes are frontend-only in TimelineGrid.vue. A `clipboard` ref holds selected assignment data. Cmd+Click toggles selection, normal cell click during paste mode emits existing `drop` events. A clipboard bar at the bottom shows state and provides exit controls.

**Tech Stack:** Vue 3 (Composition API), TypeScript, Vitest + @vue/test-utils

---

### Task 1: Add clipboard state and Cmd+Click selection logic

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (script section)

- [ ] **Step 1: Write failing test — Cmd+Click adds assignment to clipboard**

Add to `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
it('Cmd+Click on assignment selects it for clipboard', async () => {
  const wrapper = mount(UsageTimelineView)
  await flushPromises()

  const assignmentBlock = wrapper.find('.assignment-block')
  expect(assignmentBlock.exists()).toBe(true)

  await assignmentBlock.trigger('click', { metaKey: true })

  expect(wrapper.find('.assignment-block.selected').exists()).toBe(true)
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: FAIL — no `.selected` class exists yet

- [ ] **Step 3: Add clipboard state and toggle logic to TimelineGrid.vue**

In `frontend/src/components/TimelineGrid.vue`, add to the `<script setup>` section after the existing refs:

```typescript
interface ClipboardItem {
  projectId: number
  tentative: boolean
  usagePercent: number
}

const clipboard = ref<ClipboardItem[]>([])
const pasteCount = ref(0)

function isInClipboard(projectId: number): boolean {
  return clipboard.value.some(c => c.projectId === projectId)
}

function toggleClipboardItem(event: MouseEvent, assignment: AssignmentUsage) {
  if (!event.metaKey && !event.ctrlKey) return false
  event.stopPropagation()
  event.preventDefault()

  const idx = clipboard.value.findIndex(c => c.projectId === assignment.projectId)
  if (idx >= 0) {
    clipboard.value.splice(idx, 1)
  } else {
    clipboard.value.push({
      projectId: assignment.projectId,
      tentative: assignment.tentative,
      usagePercent: assignment.usage,
    })
  }
  pasteCount.value = 0
  return true
}

function clearClipboard() {
  clipboard.value = []
  pasteCount.value = 0
}
```

Also add the `AssignmentUsage` import to the imports at the top:

```typescript
import type { ArchitectUsage, MonthUsage, AssignmentUsage } from '@/types'
```

- [ ] **Step 4: Wire Cmd+Click on assignment blocks in the template**

Change the assignment block's `@click.stop` handler in the template. Replace:

```html
@click.stop="openUsageEditor($event, assignment.assignmentId, assignment.usage, month)"
```

With:

```html
@click.stop="toggleClipboardItem($event, assignment) || openUsageEditor($event, assignment.assignmentId, assignment.usage, month)"
```

Add the `selected` class binding. Replace:

```html
:class="{ tentative: assignment.tentative }"
```

With:

```html
:class="{ tentative: assignment.tentative, selected: isInClipboard(assignment.projectId) }"
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add clipboard state and Cmd+Click selection for assignments"
```

---

### Task 2: Add selected assignment visual styles

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (style section)

- [ ] **Step 1: Add CSS for selected assignment blocks and clipboard-active cell hints**

Add to the `<style scoped>` section in `frontend/src/components/TimelineGrid.vue`:

```css
.assignment-block.selected { background: #60a5fa; border: 2px solid #2563eb; color: #fff; font-weight: 600; }
.assignment-block.selected:hover { background: #3b82f6; }
.assignment-block.selected .unassign-btn { color: rgba(255,255,255,0.6); }
.assignment-block.selected .unassign-btn:hover { color: #fff; background: rgba(255,255,255,0.2); }
```

- [ ] **Step 2: Add the ✓ prefix to selected assignments in the template**

In `frontend/src/components/TimelineGrid.vue`, update the assignment label span. Replace:

```html
<span class="assignment-label">{{ assignment.customerName }} — {{ assignment.projectName }} {{ assignment.usage }}%</span>
```

With:

```html
<span class="assignment-label">{{ isInClipboard(assignment.projectId) ? '✓ ' : '' }}{{ assignment.customerName }} — {{ assignment.projectName }} {{ assignment.usage }}%</span>
```

- [ ] **Step 3: Verify visually**

Run: `cd frontend && npm run dev`
Open http://localhost:5173, go to Usage Timeline. Cmd+Click an assignment — it should turn blue with ✓ prefix. Cmd+Click again — it should deselect. Normal click should still open usage editor.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue
git commit -m "feat: add visual styles for selected assignment blocks"
```

---

### Task 3: Add clipboard bar UI

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (template + style)

- [ ] **Step 1: Write failing test — clipboard bar appears when items selected**

Add to `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
it('shows clipboard bar when assignments are selected', async () => {
  const wrapper = mount(UsageTimelineView)
  await flushPromises()

  expect(wrapper.find('.clipboard-bar').exists()).toBe(false)

  const assignmentBlock = wrapper.find('.assignment-block')
  await assignmentBlock.trigger('click', { metaKey: true })

  expect(wrapper.find('.clipboard-bar').exists()).toBe(true)
  expect(wrapper.find('.clipboard-bar').text()).toContain('1 assignment')
  expect(wrapper.find('.clipboard-bar').text()).toContain('click cells to paste')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: FAIL — `.clipboard-bar` doesn't exist

- [ ] **Step 3: Add clipboard bar to the template**

In `frontend/src/components/TimelineGrid.vue`, add after the `</table>` tag (inside the `.timeline-grid` div):

```html
<div v-if="clipboard.length > 0" class="clipboard-bar" data-testid="clipboard-bar">
  <span>📋 <strong>{{ clipboard.length }} assignment{{ clipboard.length > 1 ? 's' : '' }}</strong>
    {{ pasteCount > 0 ? `— pasted to ${pasteCount} cell${pasteCount > 1 ? 's' : ''} so far` : '— click cells to paste' }}
    &nbsp;•&nbsp; <strong>Esc</strong> to {{ pasteCount > 0 ? 'finish' : 'cancel' }}
  </span>
  <button class="clipboard-clear" @click="clearClipboard">✕ {{ pasteCount > 0 ? 'Done' : 'Clear' }}</button>
</div>
```

- [ ] **Step 4: Add clipboard bar styles**

Add to the `<style scoped>` section:

```css
.clipboard-bar { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 6px; margin: 8px; font-size: 0.85rem; }
.clipboard-clear { background: none; border: none; color: #3b82f6; cursor: pointer; font-weight: 600; font-size: 0.85rem; padding: 2px 6px; border-radius: 4px; }
.clipboard-clear:hover { background: #dbeafe; }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add clipboard bar UI for copy/paste mode"
```

---

### Task 4: Add paste-on-cell-click logic

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (script + template)

- [ ] **Step 1: Write failing test — clicking a cell during paste mode emits drop events**

Add to `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
import { assignmentApi } from '@/api/client'

it('clicking a cell while clipboard has items creates assignments', async () => {
  const wrapper = mount(UsageTimelineView)
  await flushPromises()

  // Select an assignment via Cmd+Click
  const assignmentBlock = wrapper.find('.assignment-block')
  await assignmentBlock.trigger('click', { metaKey: true })

  // Click a different month cell (the third month-cell is Jul for Alice)
  const cells = wrapper.findAll('.month-cell')
  // Find an empty cell — cells are ordered by month for each architect
  const targetCell = cells[2] // third month cell for Alice (Aug)
  await targetCell.trigger('click')

  expect(assignmentApi.create).toHaveBeenCalled()
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: FAIL — cell click doesn't create assignment yet

- [ ] **Step 3: Add paste logic to cell click handler**

In `frontend/src/components/TimelineGrid.vue`, add a new function to the script section:

```typescript
function onCellClick(architectId: number, month: string, monthData: MonthUsage) {
  if (clipboard.value.length === 0) return

  const existingProjectIds = new Set(monthData.assignments.map(a => a.projectId))
  const toPaste = clipboard.value.filter(c => !existingProjectIds.has(c.projectId))

  for (const item of toPaste) {
    emit('drop', architectId, month, {
      projectId: item.projectId,
      tentative: item.tentative,
      defaultUsagePercent: item.usagePercent,
    })
  }

  if (toPaste.length > 0) {
    pasteCount.value++
  }
}
```

- [ ] **Step 4: Wire cell click in the template**

On the `<td>` for month cells, add a click handler. Replace:

```html
<td v-for="month in months" :key="month" class="month-cell"
  :style="{ background: usageColor(getMonthData(architect, month).total) }"
  @dragover="onDragOver" @drop="onDrop($event, architect.architectId, month)">
```

With:

```html
<td v-for="month in months" :key="month" class="month-cell"
  :style="{ background: usageColor(getMonthData(architect, month).total) }"
  :class="{ 'paste-target': clipboard.length > 0 }"
  @dragover="onDragOver" @drop="onDrop($event, architect.architectId, month)"
  @click="onCellClick(architect.architectId, month, getMonthData(architect, month))">
```

- [ ] **Step 5: Add paste-target cell hint style**

Add to the `<style scoped>` section:

```css
.month-cell.paste-target { outline: 2px dashed #93c5fd; outline-offset: -2px; cursor: pointer; }
.month-cell.paste-target:hover { outline-color: #3b82f6; background: #eff6ff !important; }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add paste-on-cell-click with duplicate skipping"
```

---

### Task 5: Add Escape key to exit and prevent cell-click conflicts

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (script + template)

- [ ] **Step 1: Write failing test — Escape clears clipboard**

Add to `frontend/src/__tests__/UsageTimelineView.spec.ts`:

```typescript
it('pressing Escape clears clipboard and exits paste mode', async () => {
  const wrapper = mount(UsageTimelineView)
  await flushPromises()

  const assignmentBlock = wrapper.find('.assignment-block')
  await assignmentBlock.trigger('click', { metaKey: true })
  expect(wrapper.find('.clipboard-bar').exists()).toBe(true)

  await wrapper.find('.timeline-grid').trigger('keydown', { key: 'Escape' })

  expect(wrapper.find('.clipboard-bar').exists()).toBe(false)
  expect(wrapper.find('.assignment-block.selected').exists()).toBe(false)
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: FAIL — Escape doesn't clear clipboard

- [ ] **Step 3: Add Escape key handler**

In `frontend/src/components/TimelineGrid.vue`, add an `onMounted`/`onUnmounted` lifecycle hook for the keydown listener. Add to imports:

```typescript
import { ref, onMounted, onUnmounted } from 'vue'
```

Add to the script section:

```typescript
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && clipboard.value.length > 0) {
    clearClipboard()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add Escape key to exit paste mode"
```

---

### Task 6: Add hint tooltip and final polish

**Files:**
- Modify: `frontend/src/components/TimelineGrid.vue` (template + style)

- [ ] **Step 1: Add subtle hint below the grid when not in paste mode**

In the template, add after the `</table>` tag and before the clipboard bar:

```html
<div v-if="clipboard.length === 0" class="copy-hint">
  💡 <strong>Tip:</strong> {{ navigator.platform?.includes('Mac') ? '⌘' : 'Ctrl' }}+Click an assignment to copy it
</div>
```

Add to the script section:

```typescript
const navigator = window.navigator
```

- [ ] **Step 2: Add hint style**

Add to `<style scoped>`:

```css
.copy-hint { text-align: center; font-size: 0.75rem; color: #94a3b8; padding: 6px; }
```

- [ ] **Step 3: Run all tests**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts --reporter=verbose`
Expected: All tests PASS

- [ ] **Step 4: Manual verification**

Run: `cd frontend && npm run dev`
Open http://localhost:5173, go to Usage Timeline. Test the full flow:
1. Cmd+Click an assignment — should highlight blue with ✓, clipboard bar appears
2. Cmd+Click another assignment (same or different row) — should add to clipboard, count updates
3. Click an empty cell — assignments should appear, paste count updates
4. Click a cell that already has the same project — should skip (no duplicate)
5. Press Escape — clipboard clears, all visuals revert
6. Normal click on assignment — should still open usage editor
7. Drag from sidebar — should still work during paste mode

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/TimelineGrid.vue
git commit -m "feat: add copy hint tooltip and polish paste mode"
```

---

### Task 7: Rebuild Docker and verify

**Files:**
- No code changes

- [ ] **Step 1: Rebuild frontend Docker image**

```bash
docker compose build frontend
```

- [ ] **Step 2: Restart containers**

```bash
docker compose up -d frontend
```

- [ ] **Step 3: Verify in Docker**

Open http://localhost:3000, go to Usage Timeline. Run through the same manual verification as Task 6 Step 4.

- [ ] **Step 4: Final commit if any adjustments needed**

Only if Docker testing reveals issues that need fixing.
