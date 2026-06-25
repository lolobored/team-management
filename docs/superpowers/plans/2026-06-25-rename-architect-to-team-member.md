# Rename Architect → Team Member Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the `Architect` domain concept to `TeamMember` across the entire stack — backend code, REST API, database schema, frontend, docs.

**Architecture:** A wide but mechanical rename. Backend (package move + entity + migration + API + tests) is one coupled task (Java compiles together, migration must match the entity). Frontend (types + store + components + route + consumers + tests) is a second coupled task (TS types ripple). Docs are a third small task. Each task's completeness is gated by `grep -ric architect <area> == 0` plus a green suite.

**Tech Stack:** Java 25 / Spring Boot (Liquibase, JPA, MockMvc), Vue 3 `<script setup>` + TypeScript + Vitest.

## Global Constraints

- Clean switch — **zero** `architect` (any case) may remain after each task, except in historical changelogs `003/004/006/012` which are NEVER edited.
- Per-layer naming: Java/TS identifiers `TeamMember` / `teamMemberId` / `teamMemberName` (no space); DB `team_member` / `team_member_id` (snake); REST `/team-members` (kebab); **user-visible copy** "Team Member" / "Team Members" / "+ Add Team Member" (WITH a space).
- **Identifiers get `TeamMember` (no space); human-readable strings get "Team Member" (with space).** A naive global replace breaks one or the other — distinguish per occurrence.
- DB migration: new changelog only, registered in `db.changelog-master.yaml`, portable across H2 (test) and PostgreSQL (dev/prod). Drop the unique constraint before renaming its column, then re-add.
- No `architect` API/DB compatibility alias.
- Backend tests: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env && ./gradlew test` (needs PostgreSQL? No — `test` profile uses H2; tests boot their own context). Frontend: `cd frontend && npx vitest run`; type-check `npx vue-tsc --noEmit`.

---

## Task 1: Backend rename + DB migration

Java compilation + the migration couple these into one task. No new behavior, so the existing tests are the spec: keep them green (with their JSON/field references renamed).

**Files (moves):**
- `git mv backend/src/main/java/org/lolobored/tm/architect` → `…/teammember` (then rename `Architect*.java` inside)
- `git mv backend/src/test/java/org/lolobored/tm/architect` → `…/teammember` (rename `ArchitectControllerTest.java`)
- Rename `usage/ArchitectUsageDto.java` → `usage/TeamMemberUsageDto.java`

**Files (modify):** every `.java` under `backend/src` referencing architect, plus the new migration + master changelog.

**Interfaces produced (frontend Task 2 depends on these):**
- REST `/api/team-members` (CRUD + `/{id}/photo` POST/GET/DELETE).
- JSON: team member object unchanged shape but served at the new path; `Assignment` and usage JSON use `teamMemberId` / `teamMemberName`.
- Usage endpoint query param `architectId` → `teamMemberId`.

- [ ] **Step 1: Move and rename the package + files**

```bash
cd /Users/laurentlaborde/projects/team-management
for base in backend/src/main/java backend/src/test/java; do
  git mv "$base/org/lolobored/tm/architect" "$base/org/lolobored/tm/teammember"
done
git mv backend/src/main/java/org/lolobored/tm/teammember/Architect.java            backend/src/main/java/org/lolobored/tm/teammember/TeamMember.java
git mv backend/src/main/java/org/lolobored/tm/teammember/ArchitectController.java   backend/src/main/java/org/lolobored/tm/teammember/TeamMemberController.java
git mv backend/src/main/java/org/lolobored/tm/teammember/ArchitectRepository.java   backend/src/main/java/org/lolobored/tm/teammember/TeamMemberRepository.java
git mv backend/src/test/java/org/lolobored/tm/teammember/ArchitectControllerTest.java backend/src/test/java/org/lolobored/tm/teammember/TeamMemberControllerTest.java
git mv backend/src/main/java/org/lolobored/tm/usage/ArchitectUsageDto.java          backend/src/main/java/org/lolobored/tm/usage/TeamMemberUsageDto.java
```

- [ ] **Step 2: Apply the identifier replacements across backend/src**

Run these ordered replacements over all `.java` files (longest/most-specific first so they don't collide):

```bash
cd /Users/laurentlaborde/projects/team-management
FILES=$(grep -rIl -i architect backend/src --include='*.java')
for f in $FILES; do
  sed -i '' \
    -e 's/architect_id/team_member_id/g' \
    -e 's/architectId/teamMemberId/g' \
    -e 's/architectName/teamMemberName/g' \
    -e 's/findByArchitectId/findByTeamMemberId/g' \
    -e 's/org\.lolobored\.tm\.architect/org.lolobored.tm.teammember/g' \
    -e 's#/api/architects#/api/team-members#g' \
    -e 's#/architects#/team-members#g' \
    -e 's/ArchitectUsageDto/TeamMemberUsageDto/g' \
    -e 's/Architect/TeamMember/g' \
    "$f"
done
```

Then fix the remaining lowercase `architect` occurrences by hand — these are local variable names and user-facing message strings, which the rules above don't cover:

- In `AssignmentController.java`: the two messages `"Assignment already exists for this architect/customer/month"` → `"…for this team member/customer/month"`.
- Any local variable named `architect` (e.g. `TeamMember architect = …`) → `teamMember`.
- `TeamMemberController.java`: confirm `@RequestMapping("/api/team-members")` and that photo paths read `/{id}/photo` (unchanged suffix).

- [ ] **Step 3: Update the Assignment entity's table constraint**

In `backend/src/main/java/org/lolobored/tm/assignment/Assignment.java`, the `@Table` annotation's unique constraint must match the new column + name. Ensure it reads:

```java
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_assignment_team_member_customer_month",
        columnNames = {"team_member_id", "customer_id", "month"}))
```

and the field is:

```java
    @Column(name = "team_member_id", nullable = false)
    private Long teamMemberId;
```

with `getTeamMemberId()` / `setTeamMemberId(Long)` (the Step 2 sed renames the getter/setter bodies; verify they read `teamMemberId`). (The old constraint name was `uk_assignment_architect_customer_month` — Step 2's `architect`→`team_member` via the `architect_id`/word rules already produced `team_member_id`; double-check the constraint NAME became `…team_member…` not a half-rename.)

- [ ] **Step 4: Add the migration changelog**

Create `backend/src/main/resources/db/changelog/014-rename-architect-to-team-member.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 14a
      author: laurent
      comment: "Rename architect table to team_member"
      changes:
        - renameTable:
            oldTableName: architect
            newTableName: team_member
  - changeSet:
      id: 14b
      author: laurent
      comment: "Drop old unique constraint before renaming the column it covers"
      changes:
        - dropUniqueConstraint:
            tableName: assignment
            constraintName: uk_assignment_architect_customer_month
  - changeSet:
      id: 14c
      author: laurent
      comment: "Rename assignment.architect_id to team_member_id"
      changes:
        - renameColumn:
            tableName: assignment
            oldColumnName: architect_id
            newColumnName: team_member_id
            columnDataType: BIGINT
  - changeSet:
      id: 14d
      author: laurent
      comment: "Re-add the unique constraint with the new column and name"
      changes:
        - addUniqueConstraint:
            tableName: assignment
            columnNames: team_member_id, customer_id, month
            constraintName: uk_assignment_team_member_customer_month
```

Append to `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (after the `013-assignment-status.yaml` include):

```yaml
  - include:
      file: db/changelog/014-rename-architect-to-team-member.yaml
```

- [ ] **Step 5: Update the backend tests' JSON/fields**

In `TeamMemberControllerTest.java` (CRUD/photo) and `AssignmentControllerTest.java`, Step 2's sed already renamed `Architect`/`architectId`. Verify the assignment request bodies use `"teamMemberId"` and the controller test hits `/api/team-members`. In `UsageServiceTest.java`, verify assertions read `teamMemberId()` / `teamMemberName()` on `TeamMemberUsageDto`.

- [ ] **Step 6: Verify zero architect references remain (completeness gate)**

```bash
cd /Users/laurentlaborde/projects/team-management
grep -ric architect backend/src | grep -v ':0$' || echo "BACKEND CLEAN (0 architect refs)"
grep -rin architect backend/src/main/resources/db/changelog/db.changelog-master.yaml && echo "check master" || true
```
Expected: `BACKEND CLEAN`. (Historical changelogs 003/004/006/012 still contain `architect` and are intentionally untouched — they are not under `backend/src/main/java` and describe past schema; do not edit them. The grep above covers `backend/src` broadly, so exclude those four files from the "must be 0" judgement — they legitimately still reference the old name.)

Refined gate (excludes the four frozen changelogs):
```bash
grep -ril architect backend/src | grep -vE '00[346]-|012-remove-projects' || echo "CLEAN excluding frozen changelogs"
```
Expected: `CLEAN excluding frozen changelogs`.

- [ ] **Step 7: Compile + run backend tests**

```bash
cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env
./gradlew test
```
Expected: BUILD SUCCESSFUL — the migration applies on the H2 test DB (constraint dropped → column renamed → constraint re-added), and all controller/usage/assignment tests pass against `/api/team-members` and `teamMemberId`.

- [ ] **Step 8: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add backend/src backend/src/main/resources/db/changelog
git commit -m "refactor: rename Architect to TeamMember across backend + DB"
```

---

## Task 2: Frontend rename

TS types ripple to every consumer, so types + API client + store + components + route + consumers + tests land together to keep `vue-tsc` green.

**Files (moves):**
- `git mv frontend/src/stores/architects.ts` → `stores/teamMembers.ts`
- `git mv frontend/src/components/ArchitectForm.vue` → `components/TeamMemberForm.vue`
- `git mv frontend/src/views/ArchitectsView.vue` → `views/TeamMembersView.vue`
- `git mv frontend/src/__tests__/ArchitectsView.spec.ts` → `__tests__/TeamMembersView.spec.ts`

**Files (modify):** `types/index.ts`, `api/client.ts`, `router/index.ts`, `components/AppLayout.vue`, `components/WorldMapView.vue`, `components/TimelineGrid.vue`, `composables/useTimelineLayout.ts`, `views/UsageTimelineView.vue`, `stores/usage.ts`, and the specs `useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`, `WorldMapView.spec.ts`.

**Interfaces:**
- Consumes: backend `/api/team-members` + `teamMemberId`/`teamMemberName` JSON (Task 1).
- Produces: `TeamMember`/`TeamMemberUsage` types; `teamMemberApi`; `useTeamMembersStore`; route `/team-members`.

- [ ] **Step 1: Move and rename the frontend files**

```bash
cd /Users/laurentlaborde/projects/team-management/frontend
git mv src/stores/architects.ts src/stores/teamMembers.ts
git mv src/components/ArchitectForm.vue src/components/TeamMemberForm.vue
git mv src/views/ArchitectsView.vue src/views/TeamMembersView.vue
git mv src/__tests__/ArchitectsView.spec.ts src/__tests__/TeamMembersView.spec.ts
```

- [ ] **Step 2: Apply the identifier replacements across frontend/src**

```bash
cd /Users/laurentlaborde/projects/team-management/frontend
FILES=$(grep -rIl -i architect src)
for f in $FILES; do
  sed -i '' \
    -e 's/architectId/teamMemberId/g' \
    -e 's/architectName/teamMemberName/g' \
    -e 's/architectApi/teamMemberApi/g' \
    -e 's/useArchitectsStore/useTeamMembersStore/g' \
    -e 's/ArchitectForm/TeamMemberForm/g' \
    -e 's/ArchitectsView/TeamMembersView/g' \
    -e 's/ArchitectUsage/TeamMemberUsage/g' \
    -e "s#'/architects'#'/team-members'#g" \
    -e 's#/architects#/team-members#g' \
    -e 's/Architect/TeamMember/g' \
    "$f"
done
```

- [ ] **Step 3: Fix user-visible copy (spaces) and route metadata by hand**

Step 2 produced `TeamMember` (no space) everywhere, which is wrong for display text and the route name. Fix these specific spots:

- `components/AppLayout.vue`: nav link label "Architects" → "Team Members"; the `<RouterLink to="/team-members">` target is already renamed by Step 2 — confirm.
- `views/TeamMembersView.vue`: heading "Team Members"; create button "+ Add Team Member"; modal title `{{ editing ? 'Edit' : 'Add' }} Team Member`; any other visible "Architect"/"Architects" copy → "Team Member"/"Team Members" (with a space). The store import is `useTeamMembersStore` from `@/stores/teamMembers`.
- `router/index.ts`: route `name` should be `'team-members'` (Step 2 turned `'architects'`→`'team-members'` only if it matched `/architects`; the bare name string `name: 'architects'` is NOT matched by the path rules — change it explicitly to `name: 'team-members'`). The lazy import path must point to `@/views/TeamMembersView.vue`.
- `components/WorldMapView.vue`: any visible "Architect" label → "Team Member"; identifier refs already handled.
- Grep for leftover display strings: `grep -rn 'TeamMembers\b' src --include='*.vue'` — any inside text nodes or quoted UI strings (not identifiers/imports/types) should read "Team Members".

- [ ] **Step 4: Update the moved/renamed specs' fixtures**

In `useTimelineLayout.spec.ts`, `TimelineGrid.spec.ts`, `UsageTimelineView.spec.ts`, `WorldMapView.spec.ts`, `TeamMembersView.spec.ts`: Step 2 already renamed `architectId`/`architectName`/types. Verify mock data now uses `teamMemberId`/`teamMemberName` and any mocked store is `useTeamMembersStore`. In `TeamMembersView.spec.ts`, update any assertion on heading/button text to "Team Member(s)".

- [ ] **Step 5: Verify zero architect references remain (completeness gate)**

```bash
cd /Users/laurentlaborde/projects/team-management/frontend
grep -ril architect src || echo "FRONTEND CLEAN (0 architect refs)"
```
Expected: `FRONTEND CLEAN`.

- [ ] **Step 6: Run the full frontend suite + type-check**

```bash
cd /Users/laurentlaborde/projects/team-management/frontend
npx vitest run && npx vue-tsc --noEmit
```
Expected: all suites pass, no type errors. (`CustomerSidebar`, `AutocompleteInput`, `CustomersView`, `geo` specs are unaffected and stay green.)

- [ ] **Step 7: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add frontend/src
git commit -m "refactor: rename Architect to TeamMember across frontend"
```

---

## Task 3: Docs

- [ ] **Step 1: Update CLAUDE.md**

In `CLAUDE.md`, replace the architect references with team-member wording:
- Domain model bullet: "**Architect** — team member (firstName, …)" → "**Team Member** — a member of the team (firstName, lastName, email, country, city, photo)".
- Project structure: `architect/` package line → `teammember/`.
- Key views: `/architects` — CRUD for architects → `/team-members` — CRUD for team members.
- Any other "architect" mention → "team member" (display wording, with a space).

Verify:
```bash
grep -in architect CLAUDE.md || echo "CLAUDE.md CLEAN"
```
Expected: `CLAUDE.md CLEAN`.

- [ ] **Step 2: Commit**

```bash
cd /Users/laurentlaborde/projects/team-management
git add CLAUDE.md
git commit -m "docs: rename architect to team member in CLAUDE.md"
```

---

## Self-Review Notes

- **Spec coverage:** package/class/controller/repo rename (T1 S1-2); Assignment field+column+constraint (T1 S3); REST `/team-members` incl. photo (T1 S2); DTO + usage + param rename (T1 S2/S5); migration 014 + master (T1 S4); backend tests (T1 S5/S7). Frontend types/api/store/components/view/route/nav/consumers (T2 S1-3); frontend tests (T2 S4). Docs (T3). Completeness enforced by `grep -ric architect == 0` gates (T1 S6, T2 S5, T3 S1).
- **Display vs identifier:** called out explicitly (Global Constraints + T2 S3 + T3) — identifiers `TeamMember`, copy "Team Member".
- **Migration safety:** drop-constraint → rename-column → re-add-constraint ordering (T1 S4); portable Liquibase ops; frozen changelogs untouched (T1 S6 note).
- **Sequencing:** T1 (backend+DB, defines the API contract) → T2 (frontend, consumes it) → T3 (docs). Identifier-replacement sed lists are ordered most-specific-first to avoid collisions; the grep-zero gate catches anything the sed missed.
```
