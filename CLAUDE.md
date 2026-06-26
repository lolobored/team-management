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
    user/            # Auth: User entity, AuthController, UserController, PasswordPolicy,
                     #   AppUserDetailsService, AdminBootstrapRunner, MustChangePasswordFilter
    config/          # WebConfig (CORS), SecurityConfig (Spring Security), CsrfCookieFilter, HealthController
  src/main/resources/
    db/changelog/    # Liquibase migration XML files
frontend/
  src/
    api/client.ts    # API client (axios; authApi, userApi, withCredentials + 401 interceptor)
    components/      # Vue components (TimelineGrid, CustomerSidebar, WorldMapView, etc.)
    stores/          # Pinia stores (usage, customers, assignments, teamMembers, geo, auth)
    utils/password.ts # Frontend mirror of the password policy
    views/           # Route-level views (TeamMembersView, CustomersView, UsageTimelineView,
                     #   LoginView, SetPasswordView, UsersView)
    types/index.ts   # Shared TypeScript interfaces
docs/
  BACKLOG.md         # Feature backlog for future work
  superpowers/       # Plans and design specs from prior implementation sessions
```

## Domain Model

- **Team Member** — a member of the team (firstName, lastName, email, country, city, photo)
- **Customer** — client organization (name, country, city, logo)
- **Assignment** — links one team member to one customer for a specific month, with usage percentage and confidence status (CONFIRMED/PROBABLE/POTENTIAL)
- **User** — an app login account (email, BCrypt password hash, role, enabled, mustChangePassword). Standalone — NOT linked to Team Member. See Authentication & Authorization below.

Assignments are per-month granularity (one row per team-member-customer-month). There is no Project entity — it was removed in favor of direct customer assignments.

## Key Views

- `/login` — email + password login (public route)
- `/set-password` — forced first-login password change and self-service change
- `/team-members` — CRUD for team members
- `/customers` — CRUD for customers (with logo search via Google Custom Search API)
- `/users` — admin-only user management (create, change role, reset password, enable/disable, delete)
- `/timeline` — Main usage timeline grid + world map tab
  - Timeline shows team members as rows, months as columns; assignment pills are proportional-height, capsule-shaped, and span consecutive months
  - Supports drag-and-drop from the customer sidebar, drag-to-extend pills across months, usage editing (click to slider), and right-click to set confidence status (confirmed/probable/potential)
  - World map shows geographic utilization breakdown

Write controls across views are hidden for `VIEW`-role users (read-only UI); the backend is the real gate.

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
#    On an empty users table, set ADMIN_EMAIL/ADMIN_PASSWORD to seed the first admin
#    (created with mustChangePassword=true). Without them, a WARN logs and nobody can log in.
cd backend && ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=ChangeMe1234 ./gradlew bootRun

# 3. Frontend
cd frontend && npm run dev
```

`docker-compose.yml` reads `ADMIN_EMAIL` / `ADMIN_PASSWORD` from the environment for
the backend service — set them before the first boot of a fresh deployment.

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

## Authentication & Authorization

Spring Security with **server-side sessions** (HttpOnly cookie) — not JWT. CSRF is on
via a readable `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header (`CookieCsrfTokenRepository`
+ `CsrfCookieFilter`); the frontend axios client sends both automatically with
`withCredentials: true`.

**Roles** (single role per user, hierarchical `ADMIN > VIEW_WRITE > VIEW`, `ROLE_`-prefixed authorities):
- `VIEW` — read everything (`GET /api/**`)
- `VIEW_WRITE` — reads + all data writes (members, customers, assignments)
- `ADMIN` — everything + user management (`/api/users/**`)

**Authorization is backend-enforced** (the source of truth) via request-matcher rules in
`SecurityConfig`. Frontend role gating is UX only. Matcher order matters: `/api/users/**`
(ADMIN) precedes `GET /api/**` (VIEW). `GET /api/health` is `permitAll` (used by the
docker healthcheck). The `ERROR` dispatch is permitted so controller error statuses
(400/404/409/422) render instead of being masked as 403 by `anyRequest().denyAll()`.

**Auth endpoints** (`/api/auth`): `POST /login` `{email,password}` → `{email, role, mustChangePassword}`;
`GET /me`; `POST /logout`; `POST /change-password` `{currentPassword, newPassword}`.

**First admin** is seeded on startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` when the `users`
table is empty (`AdminBootstrapRunner`), created with `mustChangePassword=true`. Admins
create other users (email + role + temp password); admins cannot disable/delete/demote
their own account.

**Password policy** (self-set passwords only, `PasswordPolicy` + `utils/password.ts` mirror):
≥ 12 chars AND ≥ 3 of 4 classes (upper / lower / digit / symbol). Admin-issued temp
passwords are not policy-checked (non-blank only).

**First-login lockdown** (`MustChangePasswordFilter`): a user with `mustChangePassword=true`
can reach only `/api/auth/{me,logout,change-password}` until they set a new password —
enforced server-side, not just in the frontend guard.

Note: backend MockMvc tests can't reproduce the real servlet `/error` dispatch, so the
ERROR-dispatch permit is verified at runtime (drive the running app), not by the suite.

## Conventions

- Backend follows standard Spring Boot patterns: entity, repository (JPA), controller (REST)
- Frontend uses Vue 3 Composition API with `<script setup>` exclusively — no Options API
- Pinia stores handle state management and API calls
- Components are single-file `.vue` with `<style scoped>`
- Authentication is enforced via Spring Security (session cookie + CSRF) — see Authentication & Authorization below
- Assignment uniqueness: one assignment per team-member-customer-month (409 on duplicate)

## History

This project has been built iteratively through several feature sessions. See `docs/superpowers/` for design specs and implementation plans from previous work:

1. **Initial build** (2026-05-14) — core entities, timeline grid, drag-and-drop
2. **Geo API migration** (2026-05-15) — replaced static region enum with restcountries API
3. **World map view** (2026-05-15) — Leaflet-based geographic utilization map
4. **Assignment simplification** (2026-05-19) — moved from date-range to per-month model
5. **Copy/paste assignments** (2026-06-02) — Cmd+Click selection, clipboard bar, paste-on-click
6. **Remove projects** (2026-06-24) — eliminated Project entity, assignments link directly to customers
7. **Authentication & RBAC** (2026-06-26) — Spring Security session login, VIEW/VIEW_WRITE/ADMIN roles, admin-managed users, forced first-login password change, role-based UI gating (released in 2026.2.5)
