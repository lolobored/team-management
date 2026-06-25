# Simplify Assignments & Project Model — Design Spec

## Overview

Replace the range-based assignment model (startMonth/endMonth with auto-fill) with a per-month assignment model where each assignment row represents exactly one architect on one project for one month. Remove project duration and most date fields, simplifying the data model and giving the user full manual control over month-by-month planning.

Additionally, update the sidebar project filter on the usage page to search against both project name and customer name.

## Data Model Changes

### Project

Remove fields:
- `durationMonths`
- `officialDate`
- `plausibleDate`

Keep fields:
- `id` (PK)
- `customerId` (FK)
- `name`
- `defaultUsagePercent`
- `status` — values: `ACTIVE`, `UPCOMING` (rename `POTENTIAL` to `UPCOMING`)
- `startDate` — required for UPCOMING projects, null for ACTIVE projects

### Assignment

Replace `startMonth` + `endMonth` with a single `month` field (format: `"YYYY-MM"`).

Final fields:
- `id` (PK)
- `architectId` (FK)
- `projectId` (FK)
- `usagePercent` (nullable, falls back to project's defaultUsagePercent)
- `tentative` (true for assignments on UPCOMING projects)
- `month` (e.g. `"2026-05"`)

Unique constraint: `(architect_id, project_id, month)`.

Each row = one architect, one project, one month. An architect on a project for May-August = 4 assignment rows.

## Project Lifecycle

- **UPCOMING**: has a required start date. Assignments are created as `tentative = true`. Displayed with dashed yellow styling.
- **ACTIVE**: no date fields. Assignments are `tentative = false`. Displayed with solid blue styling.
- **Conversion (one-way)**: UPCOMING to ACTIVE. Sets `status = ACTIVE`, clears `startDate`, bulk-updates all assignments for this project to `tentative = false`. No reverse conversion (ACTIVE to UPCOMING is not allowed).

## Backend Changes

### Database Migration (Liquibase)

- Expand existing assignment rows that have `start_month`/`end_month` ranges into individual per-month rows before dropping the old columns
- Drop `start_month` and `end_month` columns from `assignment` table, add `month` column (not null)
- Update unique constraint on `assignment` to `(architect_id, project_id, month)`
- Drop `duration_months`, `official_date`, `plausible_date` columns from `project` table
- Keep `start_date` as nullable on `project`
- Rename status value `POTENTIAL` to `UPCOMING` in existing project rows
- Remove `assignment_usage_override` table (no longer needed — per-month usage is stored directly on assignment)

### UsageService

Simplified computation: for each assignment, check if `assignment.month` falls within the requested `from`/`to` range. No more date-range calculation, no `getEffectiveStartDate()` logic. The usage override system simplifies: since each assignment is now for a single month, overrides are effectively per-assignment. The `AssignmentUsageOverride` table and its `month` column can be removed — the override usage can be stored directly on the assignment's `usagePercent` field.

### Project API

- `PATCH /api/projects/{id}/activate` — sets `status = ACTIVE`, nulls `startDate`, bulk-updates all assignments for this project to `tentative = false`
- Project validation: `startDate` required when `status = UPCOMING`, null when `status = ACTIVE`

### Assignment API

- `POST /api/assignments` accepts `{ architectId, projectId, month }`. Sets `tentative` based on the project's status.
- Returns 409 Conflict if the `(architectId, projectId, month)` combination already exists.
- `DELETE /api/assignments/{id}` deletes a single assignment row.

## Frontend Changes

### TimelineGrid

- Drop creates a single assignment for the target month only. No auto-fill animation.
- Unassign button deletes the single assignment row immediately with no confirmation dialog.

### UsageTimelineView

- `onDrop` handler calls `POST /api/assignments` with `{ architectId, projectId, month }`.
- On 409 response, shows a brief toast: "Already assigned for this month".
- `onUnassign` handler calls `DELETE /api/assignments/{id}` directly — no range logic.

### ProjectSidebar

- Filter input searches both project name and customer name (single text field, matches against either).

### ProjectForm

- UPCOMING projects: shows `startDate` field (required).
- ACTIVE projects: no date fields.
- Remove `durationMonths`, `officialDate`, `plausibleDate` fields from the form.

### BacklogView

- Shows single start date instead of official/plausible date pair.
- Convert-to-active button stays (one-way only).

### Assignment Styling

- Tentative assignments (UPCOMING projects): dashed yellow border, yellow background (existing style).
- Regular assignments (ACTIVE projects): solid blue border, blue background (existing style).
