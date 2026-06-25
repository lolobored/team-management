# Period Nav Arrows & Sidebar Country Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `«`/`»` period-navigation buttons to the timeline controls and show each customer's country in the sidebar.

**Architecture:** Two independent frontend-only tweaks. F3 adds buttons in `UsageTimelineView`'s timeline controls that shift `fromMonth` by `monthCount` using the existing `addMonths` helper. F4 adds a country line to each `CustomerSidebar` item.

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vitest, `@vue/test-utils`.

## Global Constraints

- Frontend only — no backend, schema, API, or dependency changes.
- F3 is timeline-tab only; the map tab's one-month nav is unchanged.
- `«` shifts `fromMonth` by `-monthCount`; `»` by `+monthCount`. Reuse the existing `addMonths(ym, n)` helper and the existing `.month-nav-btn` CSS class.
- testids: `period-prev`, `period-next`.
- The sidebar filter already matches `c.country` — do not change filtering logic.
- Tests live in `frontend/src/__tests__/`. Run with `cd frontend && npx vitest run <path>`; type-check `npx vue-tsc --noEmit`.

---

## File Structure

- `frontend/src/views/UsageTimelineView.vue` — F3 buttons in the timeline controls.
- `frontend/src/components/CustomerSidebar.vue` — F4 country line.
- `frontend/src/__tests__/UsageTimelineView.spec.ts` — F3 test.
- `frontend/src/__tests__/CustomerSidebar.spec.ts` — new, F4 test.

---

## Task 1: F3 — Period navigation arrows

**Files:**
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Test: `frontend/src/__tests__/UsageTimelineView.spec.ts`

**Interfaces:**
- Consumes: existing `fromMonth` ref, `monthCount` ref, and `addMonths(ym: string, n: number): string` helper, all already in `UsageTimelineView.vue`. The existing `watch([countryFilter, fromMonth, monthCount], loadData)` triggers the refetch.
- Produces: two buttons with `data-testid="period-prev"` / `period-next`.

- [ ] **Step 1: Write the failing test**

Add to `frontend/src/__tests__/UsageTimelineView.spec.ts` (reuse the file's existing mount/setup for the view; if it has a `mountView()`-style helper use it, otherwise mount the same way the existing tests do). The test reads the `From` month input, clicks next, and asserts it advanced by `monthCount` months:

```ts
it('period-next advances fromMonth by monthCount, period-prev moves it back', async () => {
  const wrapper = mount(UsageTimelineView)   // match the existing tests' mount (plugins/mocks)
  await flushPromises()

  // helper: add n months to a YYYY-MM string
  const addM = (ym: string, n: number) => {
    const [y, m] = ym.split('-').map(Number)
    const d = new Date(y, m - 1 + n)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }

  const monthInput = () => wrapper.find('input[type="month"]')
  const start = (monthInput().element as HTMLInputElement).value  // current fromMonth, default monthCount = 6

  await wrapper.find('[data-testid="period-next"]').trigger('click')
  await flushPromises()
  expect((monthInput().element as HTMLInputElement).value).toBe(addM(start, 6))

  await wrapper.find('[data-testid="period-prev"]').trigger('click')
  await flushPromises()
  expect((monthInput().element as HTMLInputElement).value).toBe(start)
})
```

(If the existing suite imports `mount`, `flushPromises`, and `UsageTimelineView` already, reuse those imports rather than re-adding them.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts -t "period-next advances"`
Expected: FAIL — no element with `data-testid="period-next"`.

- [ ] **Step 3: Add the buttons**

In `frontend/src/views/UsageTimelineView.vue`, inside the `<template v-if="activeTab === 'timeline'">` block in the controls, replace the existing `From:` label line:

```html
            <label>From: <input v-model="fromMonth" type="month" /></label>
```

with the From input wrapped by period-nav buttons:

```html
            <div class="period-nav">
              <button class="month-nav-btn" data-testid="period-prev" title="Previous period"
                @click="fromMonth = addMonths(fromMonth, -monthCount)">&laquo;</button>
              <label>From: <input v-model="fromMonth" type="month" /></label>
              <button class="month-nav-btn" data-testid="period-next" title="Next period"
                @click="fromMonth = addMonths(fromMonth, monthCount)">&raquo;</button>
            </div>
```

`addMonths`, `fromMonth`, and `monthCount` are already in scope. `.month-nav-btn` styles already exist (used by the map tab). Add one small style to the scoped `<style>` for the wrapper:

```css
.period-nav { display: flex; align-items: center; gap: 0.25rem; }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts -t "period-next advances"`
Expected: PASS.

- [ ] **Step 5: Run the whole view spec + type-check**

Run: `cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts && npx vue-tsc --noEmit`
Expected: PASS, no type errors.

- [ ] **Step 6: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src/views/UsageTimelineView.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add period navigation arrows to timeline controls"
```

---

## Task 2: F4 — Country in customer sidebar

**Files:**
- Modify: `frontend/src/components/CustomerSidebar.vue`
- Test: `frontend/src/__tests__/CustomerSidebar.spec.ts` (new)

**Interfaces:**
- Consumes: the `customers: Customer[]` prop already declared in `CustomerSidebar.vue`; `Customer.country?: string` (already in `@/types`).
- Produces: a `.customer-country` element under each customer name (only when the customer has a country).

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/CustomerSidebar.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CustomerSidebar from '@/components/CustomerSidebar.vue'
import type { Customer } from '@/types'

const customers: Customer[] = [
  { id: 1, name: 'Acme', country: 'Australia' },
  { id: 2, name: 'Globex', country: 'Singapore' },
  { id: 3, name: 'Initech' }, // no country
]

describe('CustomerSidebar', () => {
  it('shows each customer country under the name', () => {
    const wrapper = mount(CustomerSidebar, { props: { customers } })
    const countries = wrapper.findAll('.customer-country').map(c => c.text())
    expect(countries).toContain('Australia')
    expect(countries).toContain('Singapore')
  })

  it('renders no country element for a customer without a country', () => {
    const wrapper = mount(CustomerSidebar, { props: { customers: [{ id: 3, name: 'Initech' }] } })
    expect(wrapper.find('.customer-country').exists()).toBe(false)
    expect(wrapper.text()).toContain('Initech')
  })

  it('filters by country', async () => {
    const wrapper = mount(CustomerSidebar, { props: { customers } })
    await wrapper.find('.customer-filter').setValue('singap')
    expect(wrapper.text()).toContain('Globex')
    expect(wrapper.text()).not.toContain('Acme')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/__tests__/CustomerSidebar.spec.ts`
Expected: FAIL — `.customer-country` doesn't exist yet (first two assertions fail).

- [ ] **Step 3: Add the country line**

In `frontend/src/components/CustomerSidebar.vue`, replace the customer-name block:

```html
          <div class="customer-name">{{ customer.name }}</div>
```

with the name plus a guarded country line wrapped so they stack:

```html
          <div class="customer-text">
            <div class="customer-name">{{ customer.name }}</div>
            <div v-if="customer.country" class="customer-country">{{ customer.country }}</div>
          </div>
```

Add scoped styles (the `.customer-item` is already `display: flex; align-items: center; gap: 8px;`):

```css
.customer-text { overflow: hidden; }
.customer-country { font-size: 0.7rem; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
```

(Keep the existing `.customer-name` rule; it already handles ellipsis.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/CustomerSidebar.spec.ts`
Expected: PASS — all three tests green.

- [ ] **Step 5: Run the full frontend suite + type-check**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: PASS for all feature suites and no type errors. (A pre-existing `geo.spec.ts` localStorage failure may appear in some node environments — it is unrelated to this task; everything else passes.)

- [ ] **Step 6: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src/components/CustomerSidebar.vue frontend/src/__tests__/CustomerSidebar.spec.ts
git commit -m "feat: show customer country in sidebar items"
```

---

## Self-Review Notes

- **Spec coverage:** F3 buttons + shift-by-monthCount + reuse addMonths/watch (Task 1 S3); F3 test prev/next (Task 1 S1). F4 country line under name + v-if guard (Task 2 S3); filter unchanged (verified by Task 2 S1 filter test); F4 tests for render/absent/filter (Task 2 S1). All spec "Files" entries map to a task.
- **No placeholders:** every code/test step shows the actual code and exact run command.
- **Consistency:** `period-prev`/`period-next` and `.customer-country` names are identical across the implementation steps and their tests; `addMonths`/`fromMonth`/`monthCount` match the existing `UsageTimelineView` symbols.
- **Independence:** Task 1 and Task 2 touch disjoint files and can be done/reviewed in either order.
```
