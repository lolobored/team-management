# Period Navigation Arrows & Sidebar Country — Design Spec

**Date:** 2026-06-25
**Features:** Backlog F3 (Period Navigation Arrows) + F4 (Show Country in Customer Sidebar).
**Area:** Frontend only. Two small, independent timeline-area tweaks.

## Summary

Two minor UX additions:

- **F3** — `«` / `»` buttons in the timeline controls that page the visible window by a full
  period (`monthCount` months) at a time.
- **F4** — the customer sidebar shows each customer's country under its name.

No backend, schema, API, or dependency changes.

## F3 — Period Navigation Arrows

**Where:** the timeline controls block in `UsageTimelineView.vue`
(`<template v-if="activeTab === 'timeline'">`), flanking the existing `From:` month input —
the same visual pattern the map tab already uses (`month-prev` / `month-next`), except these
shift by the whole period rather than one month.

**Behavior:**
- `«` (prev) → `fromMonth = addMonths(fromMonth, -monthCount)`
- `»` (next) → `fromMonth = addMonths(fromMonth, monthCount)`
- Reuses the existing `addMonths(ym, n)` helper. The existing
  `watch([countryFilter, fromMonth, monthCount], loadData)` already refetches when `fromMonth`
  changes, so the buttons only set `fromMonth`.
- Works for any `monthCount` (3/6/9/12): a 6-month view jumps 6 months per click.
- `data-testid="period-prev"` / `data-testid="period-next"`.

This is timeline-only; the map tab keeps its own one-month nav unchanged.

## F4 — Country in Customer Sidebar

**Where:** each `.customer-item` in `CustomerSidebar.vue`.

**Behavior:**
- Under the existing `.customer-name`, render `<div class="customer-country"
  v-if="customer.country">{{ customer.country }}</div>` in smaller, muted text.
- The filter input already matches on `c.country` (existing code) — no change to filtering.
- Customers with no country render just the name (the `v-if` guards it).

## Testing

- `UsageTimelineView.spec.ts`: with a known `fromMonth` and `monthCount`, clicking
  `period-next` advances `fromMonth` by `monthCount` months; clicking `period-prev` moves it
  back by `monthCount`.
- New `CustomerSidebar.spec.ts`: a customer with a country renders the country text under its
  name; typing a country fragment in the filter narrows the list to matching customers; a
  customer without a country renders only the name (no empty country element).

## Files

- Modify: `frontend/src/views/UsageTimelineView.vue` (F3)
- Modify: `frontend/src/components/CustomerSidebar.vue` (F4)
- Modify: `frontend/src/__tests__/UsageTimelineView.spec.ts` (F3 test)
- New: `frontend/src/__tests__/CustomerSidebar.spec.ts` (F4 test)

## Non-Goals

- No arrows embedded in the grid header (controls-bar placement chosen for zero risk to the
  timeline's column alignment).
- No change to the map tab's month navigation.
- No backend or data-model change.
