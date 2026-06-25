# Rename Architect → Team Member — Design Spec

**Date:** 2026-06-25
**Feature:** Generalize the "Architect" domain concept to "Team Member" across the whole stack.
**Area:** Full stack — backend (entity, package, controller, DTO, REST paths, DB schema),
frontend (types, store, components, view, route, UI), docs, tests.

## Summary

The tool currently models team members as `Architect`. The team includes other roles, so the
concept is renamed to the generic **Team Member** everywhere — code, API, database, and UI.
This is a clean rename with no `architect` alias retained (internal tool, no external API
clients).

## Decision

**Depth: full-stack including the database** (chosen over UI-only or code-only). The physical
DB table and column are renamed via a Liquibase migration so nothing — user-facing or
internal — still says "architect".

## Naming Map

| Layer | Architect → Team Member |
|---|---|
| Java class | `Architect` → `TeamMember`; `ArchitectController/Repository` → `TeamMemberController/Repository` |
| Java package | `org.lolobored.tm.architect` → `org.lolobored.tm.teammember` |
| DTO | `ArchitectUsageDto` → `TeamMemberUsageDto`; fields `architectId/architectName` → `teamMemberId/teamMemberName` |
| Assignment field/column | `Assignment.architectId` (`architect_id`) → `teamMemberId` (`team_member_id`) |
| REST path | `/api/architects` → `/api/team-members` (incl. `/{id}/photo`) |
| DB table | `architect` → `team_member` |
| Unique constraint | `uk_assignment_architect_customer_month` → `uk_assignment_team_member_customer_month` |
| TS types | `Architect` → `TeamMember`; `ArchitectUsage` → `TeamMemberUsage`; `architectId/architectName` → `teamMemberId/teamMemberName` |
| TS API client | `architectApi` → `teamMemberApi` |
| Frontend files | `stores/architects.ts` → `teamMembers.ts`; `ArchitectForm.vue` → `TeamMemberForm.vue`; `ArchitectsView.vue` → `TeamMembersView.vue` |
| Pinia store | `useArchitectsStore` → `useTeamMembersStore` |
| Route | `/architects` (name `architects`) → `/team-members` (name `team-members`) |
| UI text | "Architect(s)" → "Team Member(s)" — nav label, view heading, "+ Add Team Member", form/empty-state copy |

## Backend

### Code
- Move package `architect/` → `teammember/`. Rename `Architect` → `TeamMember`,
  `ArchitectController` → `TeamMemberController`, `ArchitectRepository` → `TeamMemberRepository`.
- `TeamMemberController`: `@RequestMapping("/api/team-members")`; rename all handler methods/paths
  including the photo upload/serve/delete endpoints.
- Hibernate maps class `TeamMember` to table `team_member` by default (snake-case physical
  naming) — no explicit `@Table` needed, matching how `Architect` mapped to `architect`.
- `Assignment`: field `architectId` → `teamMemberId`; `@Column(name = "team_member_id")`; the
  `@Table` `uniqueConstraints` entry updated to name `uk_assignment_team_member_customer_month`
  and `columnNames = {"team_member_id", "customer_id", "month"}`.
- `AssignmentRepository.findByArchitectId` → `findByTeamMemberId`.
- `AssignmentController`: list query param `architectId` → `teamMemberId`.
- `ArchitectUsageDto` → `TeamMemberUsageDto` (`teamMemberId`, `teamMemberName`, plus existing
  fields). `UsageService` consumes `TeamMemberRepository` and builds `TeamMemberUsageDto`;
  `UsageController` param `architectId` → `teamMemberId` (if such a param exists).
- `UsageExportService` and any other `architect` references updated.

### Migration — `db/changelog/014-rename-architect-to-team-member.yaml`

Registered in `db.changelog-master.yaml`. Portable across H2 (test) and PostgreSQL (dev/prod).
Order chosen so the unique constraint is dropped before the column it covers is renamed, then
re-added:

1. `renameTable`: `architect` → `team_member`.
2. `dropUniqueConstraint`: `uk_assignment_architect_customer_month` on `assignment`.
3. `renameColumn`: `assignment.architect_id` → `team_member_id` (type `BIGINT`).
4. `addUniqueConstraint`: `(team_member_id, customer_id, month)` on `assignment`, named
   `uk_assignment_team_member_customer_month`.

Prior changelogs (003/004/006/012, which reference the old names) are **not** edited — they
describe history; the new changelog moves the schema forward.

## Frontend

- `types/index.ts`: rename the interfaces and the `architectId`/`architectName` fields as in the
  map.
- `api/client.ts`: `architectApi` → `teamMemberApi`; all `/architects` paths → `/team-members`;
  `photoUrl(id)` path updated.
- `stores/architects.ts` → `stores/teamMembers.ts`: `useArchitectsStore` → `useTeamMembersStore`;
  internal `architects` ref → `teamMembers`.
- `components/ArchitectForm.vue` → `TeamMemberForm.vue` (geo country/city autocomplete unchanged).
- `views/ArchitectsView.vue` → `TeamMembersView.vue`: heading "Team Members", create button
  "+ Add Team Member", modal title "Add/Edit Team Member", any "architect" copy.
- `router/index.ts`: route path `/architects` → `/team-members`, name `architects` →
  `team-members`, lazy import path to the renamed view. The `/` redirect target is `/timeline`
  (unchanged).
- Navigation menu (App layout / nav component): label "Architects" → "Team Members".
- Consumers updated to the new types/fields: `TimelineGrid.vue`, `WorldMapView.vue`,
  `UsageTimelineView.vue`, `composables/useTimelineLayout.ts` (`architectId/architectName` →
  `teamMemberId/teamMemberName`), and the usage store if it surfaces those fields.

## Docs

- `CLAUDE.md`: domain model bullet (`Architect` → `Team Member`), project structure
  (`architect/` → `teammember/`), key views (`/architects` → `/team-members`).

## Testing

### Backend
- `ArchitectControllerTest` → `TeamMemberControllerTest`: CRUD + photo against `/api/team-members`.
- `AssignmentControllerTest`: request/response JSON `architectId` → `teamMemberId`.
- `UsageServiceTest`: `teamMemberId/teamMemberName` assertions.
- Full migration applies on the H2 test DB (the `@SpringBootTest` context boots Liquibase); an
  assignment seeded against a team member round-trips through the renamed column/constraint.

### Frontend
- `ArchitectsView.spec.ts` → `TeamMembersView.spec.ts`: list renders, country filter, "+ Add
  Team Member" opens the form.
- `useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`,
  `WorldMapView.spec.ts`: fixtures updated `architectId/architectName` → `teamMemberId/teamMemberName`.
- `AutocompleteInput.spec.ts`, `CustomerSidebar.spec.ts`, `CustomersView.spec.ts`, `geo.spec.ts`
  are unaffected.

## Non-Goals

- No `architect` compatibility alias on the API or DB.
- No change to Customer, Assignment semantics, Usage math, or the geo autocomplete behavior —
  only identifiers/labels referencing "architect" change.
- No new roles/role field on Team Member (the rename generalizes the concept; per-role typing is
  out of scope).

## Risk / Sequencing

- Backend (package move + entity + migration + API + tests) is one coupled unit (Java
  compilation + the migration). It precedes the frontend.
- Frontend type rename ripples to all consumers; land types + store + API client + components +
  route + consumers together so `vue-tsc` stays green.
- The rename is mechanical but wide; the main hazards are missing a case variant
  (`architect_id`, `architectId`, `/architects`, `Architect`) and the DB migration ordering.
