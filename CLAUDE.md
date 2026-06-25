# Team Management Tool

Internal tool for managing team members and their assignments to customers.

## Tech Stack

- **Backend:** Java 25 (Spring Boot), Gradle, Liquibase migrations, PostgreSQL via Docker Compose (dev and prod); H2 in-memory for tests only
- **Frontend:** Vue 3 (Composition API, `<script setup>`), TypeScript, Vite, Vitest, Pinia stores
- **Infrastructure:** Docker Compose (`docker-compose.yml`), single-repo monolith

## Project Structure

```
backend/
  src/main/java/org/lolobored/tm/
    teammember/      # Team Member entity, controller, repository
    assignment/      # Assignment entity, controller, repository
    customer/        # Customer entity, controller, repository, logo search
    usage/           # Usage aggregation service, controller, Excel export
    config/          # WebConfig (CORS)
  src/main/resources/
    db/changelog/    # Liquibase migration XML files
frontend/
  src/
    api/client.ts    # API client (typed fetch wrappers)
    components/      # Vue components (TimelineGrid, CustomerSidebar, WorldMapView, etc.)
    stores/          # Pinia stores (usage, customers, assignments, teamMembers, geo)
    views/           # Route-level views (TeamMembersView, CustomersView, UsageTimelineView)
    types/index.ts   # Shared TypeScript interfaces
docs/
  BACKLOG.md         # Feature backlog for future work
  superpowers/       # Plans and design specs from prior implementation sessions
```

## Domain Model

- **Team Member** — a member of the team (firstName, lastName, email, country, city, photo)
- **Customer** — client organization (name, country, city, logo)
- **Assignment** — links one team member to one customer for a specific month, with usage percentage and tentative flag

Assignments are per-month granularity (one row per team-member-customer-month). There is no Project entity — it was removed in favor of direct customer assignments.

## Key Views

- `/team-members` — CRUD for team members
- `/customers` — CRUD for customers (with logo search via Google Custom Search API)
- `/timeline` — Main usage timeline grid + world map tab
  - Timeline shows team members as rows, months as columns; assignment pills are proportional-height and span consecutive months
  - Supports drag-and-drop from the customer sidebar, drag-to-extend pills across months, usage editing (click to slider), and right-click to set confidence status (confirmed/probable/potential)
  - World map shows geographic utilization breakdown

## Development

### Toolchain

Use SDKMAN for JDK management. `backend/.sdkmanrc` is the source of truth
(`java=25.0.3-amzn`). Activate it before any Gradle command:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env   # reads backend/.sdkmanrc
```

### Running

The backend's default profile connects to PostgreSQL at `localhost:5432`, so
Postgres must be running first. `scripts/dev.sh` starts it (or run the docker
command directly):

```bash
# 1. Start PostgreSQL (required before the backend)
docker compose up -d postgres   # or: ./scripts/dev.sh

# 2. Backend (default profile → PostgreSQL on localhost:5432)
cd backend && ./gradlew bootRun

# 3. Frontend
cd frontend && npm run dev
```

### Tests

```bash
# Backend tests
cd backend && ./gradlew test

# Frontend tests (Vitest — there is no `npm run test` script)
cd frontend && npx vitest run        # add `npx vue-tsc --noEmit` to type-check
```

### Database

Liquibase handles all schema migrations. Migration files are in `backend/src/main/resources/db/changelog/`. Never modify the database schema manually — always add a new changelog file.

Profiles: the default profile (`application.yml`) and the `docker` profile both use **PostgreSQL**; the `test` profile (`application-test.yml`) uses an **H2** in-memory database (PostgreSQL compatibility mode) so the test suite needs no running Postgres.

## Conventions

- Backend follows standard Spring Boot patterns: entity, repository (JPA), controller (REST)
- Frontend uses Vue 3 Composition API with `<script setup>` exclusively — no Options API
- Pinia stores handle state management and API calls
- Components are single-file `.vue` with `<style scoped>`
- No authentication currently — architecture allows adding Spring Security later
- Assignment uniqueness: one assignment per team-member-customer-month (409 on duplicate)

## History

This project has been built iteratively through several feature sessions. See `docs/superpowers/` for design specs and implementation plans from previous work:

1. **Initial build** (2026-05-14) — core entities, timeline grid, drag-and-drop
2. **Geo API migration** (2026-05-15) — replaced static region enum with restcountries API
3. **World map view** (2026-05-15) — Leaflet-based geographic utilization map
4. **Assignment simplification** (2026-05-19) — moved from date-range to per-month model
5. **Copy/paste assignments** (2026-06-02) — Cmd+Click selection, clipboard bar, paste-on-click
6. **Remove projects** (2026-06-24) — eliminated Project entity, assignments link directly to customers
