# Simplify App: Remove Projects, Add Customer Logos

**Date:** 2026-06-24
**Status:** Draft

## Goal

Simplify the team management app by removing the "Project" concept. Architects are assigned directly to customers instead of to projects-within-customers. Add customer logos for visual identification.

## Current State

- Data model: `Customer -> Project -> Assignment -> Architect`
- Assignment has `projectId`, unique on `(architect_id, project_id, month)`
- Projects have `status` (ACTIVE/UPCOMING), `defaultUsagePercent`, `startMonth`
- BacklogView shows upcoming projects
- CustomersProjectsView shows customers with expandable project tables
- ProjectSidebar in timeline view shows draggable projects
- TimelineGrid cells show "Customer - Project XX%"

## Target State

- Data model: `Customer -> Assignment -> Architect`
- Assignment has `customerId`, unique on `(architect_id, customer_id, month)`
- `usagePercent` is required (NOT NULL), no fallback to project default
- Fixed default of 20% when dragging a customer onto timeline
- No backlog/upcoming concept
- Customers view is a flat list with logos
- CustomerSidebar in timeline shows draggable customers with logos
- TimelineGrid cells show "CustomerName XX%"

## Data Migration

Liquibase changelog `012-remove-projects.yaml`:

1. Add `customer_id` column (nullable initially) to `assignment` table
2. Populate `customer_id` by joining `assignment.project_id` to `project.id` and reading `project.customer_id`
3. For rows where `usage_percent` is NULL, populate from `project.default_usage_percent`
4. Aggregate duplicates: for each `(architect_id, customer_id, month)` group with multiple rows, keep one row with `usage_percent = SUM(usage_percent)` and `tentative = false`, delete the rest
5. Make `customer_id` NOT NULL
6. Make `usage_percent` NOT NULL
7. Drop `project_id` column from `assignment`
8. Add unique constraint `(architect_id, customer_id, month)`
9. Drop `project` table
10. Add `logo` (bytea) and `logo_content_type` (varchar) columns to `customer` table

All tentative flags become `false` during migration (clean slate).

## Backend Changes

### Deleted
- `project/Project.java`
- `project/ProjectController.java`
- `project/ProjectRepository.java`
- `project/ProjectStatus.java`

### Modified

**Assignment.java:**
- Replace `projectId` (Long) with `customerId` (Long)
- `usagePercent` changes from `Integer` (nullable) to `int` (required)
- Unique constraint: `(architect_id, customer_id, month)`

**AssignmentController.java:**
- Create/update endpoints use `customerId` instead of `projectId`
- No more conflict with project-level defaults; `usagePercent` is always provided

**Customer.java:**
- Add `logo` (byte[]), `logoContentType` (String) fields, `@JsonIgnore` on both
- Add `hasLogo()` method

**CustomerController.java:**
- Add `POST /{id}/logo` — upload logo (multipart, must be image)
- Add `GET /{id}/logo` — serve logo bytes
- Add `DELETE /{id}/logo` — remove logo
- Mirror existing ArchitectController photo pattern exactly

**UsageService.java:**
- Remove project cache/lookup
- Join assignment directly to customer for name
- `AssignmentUsageDto` becomes `(assignmentId, customerId, customerName, usage, tentative)`

**UsageExportService.java:**
- Cell content: `"CustomerName XX%"` instead of `"Customer - Project XX%"`

**AssignmentUsageDto.java:**
- Remove `projectId`, `projectName`
- Add `customerId`

### New

**LogoSearchController.java** (in `customer/` or new `logo/` package):
- `GET /api/logo-search?q={query}` — proxy to Google Custom Search JSON API
- Searches for `"{query} logo"` with `searchType=image`
- Returns `List<LogoSearchResult>` with `url` and `thumbnailUrl`
- Requires `GOOGLE_API_KEY` and `GOOGLE_CSE_ID` configuration in `application.yml`
- `POST /api/customers/{id}/logo-from-url` — takes `{url}` body, fetches the image server-side, and stores it directly as the customer's logo
  - Validates response is an image content type
  - Max size limit (2MB) to prevent abuse
  - Returns 200 on success (logo saved), 400 if not an image or too large

## Frontend Changes

### Deleted
- `stores/projects.ts`
- `components/ProjectForm.vue`
- `components/ProjectSidebar.vue`
- `views/BacklogView.vue`
- `__tests__/BacklogView.spec.ts`

### Modified

**types/index.ts:**
- Remove `Project`, `ProjectStatus`
- `Assignment`: `projectId` -> `customerId`, `usagePercent` becomes required (not optional)
- `AssignmentUsage`: remove `projectId`, `projectName`, add `customerId`
- Add `LogoSearchResult` type

**router/index.ts:**
- Remove `/backlog` route
- Rename `/customers` component import to `CustomersView.vue`

**AppLayout.vue:**
- Remove "Project Backlog" nav item
- Rename "Customers & Projects" label to "Customers"

**CustomersProjectsView.vue -> CustomersView.vue (rename + rewrite):**
- Flat list of customer cards (no expandable project section)
- Each card shows: logo (or placeholder), name, country, city
- Add/Edit/Delete actions
- Filter by name/country/city

**CustomerForm.vue:**
- Add logo section at top:
  - Display current logo (or placeholder)
  - Drag & drop zone for file upload
  - "Browse" button for file picker
  - "Search" button that opens logo search panel
  - Logo search panel: text input + grid of Google Image results, click to select

**TimelineGrid.vue:**
- Assignment blocks: show `"CustomerName XX%"` (no project name)
- Copy/paste clipboard: use `customerId` instead of `projectId`
- Drop handler: receives `customerId` instead of `projectId`

**UsageTimelineView.vue:**
- Replace `ProjectSidebar` import with `CustomerSidebar`
- Drop handler: pass `customerId` + fixed 20% default
- Remove `projectStore` usage

**api/client.ts:**
- Remove `projectApi`
- Add `customerApi.uploadLogo()`, `customerApi.logoUrl()`, `customerApi.deleteLogo()`
- Add `logoSearchApi.search()`
- Add `customerApi.setLogoFromUrl()` — calls `POST /api/customers/{id}/logo-from-url`
- `assignmentApi` create/update: use `customerId` instead of `projectId`
- `usageApi` stays same (backend returns different shape)

### New

**CustomerSidebar.vue** (replaces ProjectSidebar):
- Shows list of customers with small logo + name
- Filter input
- Each item is draggable
- Drag data: `{ type: 'customer', customerId, tentative: false, defaultUsagePercent: 20 }`

### Tests

- `CustomersProjectsView.spec.ts` — rewrite as `CustomersView.spec.ts` for flat list
- `UsageTimelineView.spec.ts` — update for customer-based assignments
- `BacklogView.spec.ts` — delete
- Backend tests: update `AssignmentControllerTest`, `ProjectControllerTest` (delete)

## Configuration

Add to `application.yml`:
```yaml
logo-search:
  google-api-key: ${GOOGLE_API_KEY:}
  google-cse-id: ${GOOGLE_CSE_ID:}
```

Google Custom Search setup:
1. Create a Custom Search Engine at https://programmablesearchengine.google.com/
2. Enable "Search the entire web"
3. Get API key from Google Cloud Console (Custom Search JSON API)
4. Free tier: 100 queries/day

## Scope Exclusions

- No per-customer default usage percent (fixed 20% for all)
- No backlog/upcoming concept at all (removed entirely)
- No project history preserved (projects table dropped after migration)
- Logo search limited to Google Custom Search API (100 free queries/day)
