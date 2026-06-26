# Authentication & RBAC — Design Spec

> Tracking issue: [#25](https://github.com/lolobored/team-management/issues/25)
> Date: 2026-06-26

## Goal

Restrict access to the team-management app. The app currently has no
authentication — anyone reaching the frontend can read and write everything.
Add session-based login, three permission tiers, and admin-managed user
accounts with a forced first-login password change.

## Summary of Decisions

| Decision | Choice |
|---|---|
| Session transport | Server-side session, HttpOnly cookie (Spring Security default) |
| First admin | Seeded from `ADMIN_EMAIL` / `ADMIN_PASSWORD` env on startup |
| User identity | Standalone `User` entity keyed by **email**, not linked to TeamMember |
| Roles | Single role per user: `VIEW` ⊂ `VIEW_WRITE` ⊂ `ADMIN` (hierarchical) |
| Password policy | ≥ 12 chars, ≥ 3 of 4 classes (upper / lower / digit / symbol) |
| Admin powers over users | Create, reset password, change role, enable/disable, delete |
| First login | User must set their own password before doing anything else |

## Out of Scope (v1 — YAGNI)

Account lockout / login rate-limiting; email-based self-service reset (admin
resets instead); 2FA; audit log; "remember me"; multiple roles per user.
Architecture must not preclude adding these later.

---

## Architecture

### Backend

New package `org.lolobored.tm.user`. Add `spring-boot-starter-security`.

**`User` entity / `users` table** (Liquibase changelog `015-users.yaml`):

| column | type | constraints |
|---|---|---|
| `id` | bigint | PK, generated |
| `email` | varchar | unique, not null |
| `password_hash` | varchar | not null (BCrypt) |
| `role` | varchar | not null — `VIEW` \| `VIEW_WRITE` \| `ADMIN` |
| `enabled` | boolean | not null, default true |
| `must_change_password` | boolean | not null, default true |
| `created_at` | timestamp | not null |

`role` stored as string (JPA `@Enumerated(EnumType.STRING)`).

**Password encoding:** `BCryptPasswordEncoder` bean.

**Role hierarchy** (Spring `RoleHierarchy`): `ADMIN > VIEW_WRITE > VIEW`, so a
single matcher of `hasRole('VIEW')` admits all three, `hasRole('VIEW_WRITE')`
admits VIEW_WRITE + ADMIN, etc. Spring authorities use the `ROLE_` prefix.

**Security configuration** (`SecurityConfig`):
- Stateful session; HttpOnly cookie.
- CSRF enabled via `CookieCsrfTokenRepository.withHttpOnlyFalse()` → emits
  `XSRF-TOKEN` cookie; expects `X-XSRF-TOKEN` header on mutating requests.
- Configurable idle timeout: `server.servlet.session.timeout` (default `1h`).
- Authorization rules (request matchers), evaluated top-down:

  | Matcher | Rule |
  |---|---|
  | `POST /api/auth/login` | `permitAll` |
  | `/api/auth/me`, `/api/auth/logout`, `/api/auth/change-password` | authenticated |
  | `/api/users/**` (any method, incl. GET) | `hasRole('ADMIN')` |
  | `GET /api/**` | `hasRole('VIEW')` (any authenticated role) |
  | `POST`/`PUT`/`PATCH`/`DELETE /api/**` (all other data writes) | `hasRole('VIEW_WRITE')` |
  | anything else | `denyAll` |

  **Order matters** — matchers evaluate top-down, first match wins. The
  `/api/users/**` rule must precede the generic `GET /api/**` rule, otherwise a
  `GET /api/users` would match the VIEW rule and leak the user list to non-admins.

**First-login lockdown:** a servlet `Filter` (or `HandlerInterceptor`) rejects
(`403`) any request from an authenticated principal whose `mustChangePassword`
is true, **except** `/api/auth/me`, `/api/auth/logout`,
`/api/auth/change-password`. Enforced server-side so the API cannot be bypassed
even if the frontend guard is skipped.

**Auth endpoints** (`AuthController`, `/api/auth`):

| method + path | body | returns | notes |
|---|---|---|---|
| `POST /login` | `{email, password}` | `{email, role, mustChangePassword}` | authenticates via `AuthenticationManager`, establishes session; 401 on bad creds or disabled account |
| `GET /me` | — | `{email, role, mustChangePassword}` | 401 if no session |
| `POST /logout` | — | 204 | invalidates session |
| `POST /change-password` | `{currentPassword, newPassword}` | 204 | validates current password + policy; sets new hash; clears `mustChangePassword`; 422 on policy failure, 400 on wrong current password |

**Password policy validator** (`PasswordPolicy`): pure function, ≥ 12 chars and
≥ 3 of {has-upper, has-lower, has-digit, has-symbol}. Returns the list of
failed rules (so the API can report which). Single source of truth on the
backend; the frontend mirrors it for live UX only.

**User-management endpoints** (`UserController`, `/api/users`, ADMIN only):

| method + path | body | notes |
|---|---|---|
| `GET /` | — | list users (no password hash in DTO) |
| `POST /` | `{email, role, initialPassword}` | create; `mustChangePassword=true`, `enabled=true`; 409 on duplicate email; initial password is **not** policy-checked (it's a throwaway temp the user must replace) but must be non-blank |
| `PATCH /{id}/role` | `{role}` | change role |
| `POST /{id}/reset-password` | `{initialPassword}` | set temp hash, `mustChangePassword=true` |
| `PATCH /{id}/enabled` | `{enabled}` | disable/enable |
| `DELETE /{id}` | — | delete |

An admin cannot disable, delete, or demote **their own** account (guard against
locking out the last admin); reject with `409`/`422` and a clear message.

**Bootstrap** (`AdminBootstrapRunner`, `ApplicationRunner`): on startup, if the
`users` table is empty:
- `ADMIN_EMAIL` + `ADMIN_PASSWORD` both present → create that admin
  (`role=ADMIN`, `enabled=true`, `mustChangePassword=true`).
- otherwise → log a prominent `WARN` ("no users and no ADMIN_EMAIL/ADMIN_PASSWORD
  — nobody can log in; set them and restart"). App still starts.

`docker-compose.yml` backend service gains `ADMIN_EMAIL` / `ADMIN_PASSWORD`
environment entries (documented; values supplied by the operator).

### Frontend

**Auth store** (`stores/auth.ts`):
- state: `currentUser: { email, role, mustChangePassword } | null`
- actions: `fetchMe()`, `login(email, password)`, `logout()`,
  `changePassword(current, next)`
- getters: `isAuthenticated`, `canWrite` (role is `VIEW_WRITE` or `ADMIN`),
  `isAdmin`

**API client** (`api/client.ts`): add `withCredentials: true` to the axios
instance (Spring's default `XSRF-TOKEN` / `X-XSRF-TOKEN` names already match
axios defaults, so CSRF works without extra config). Add `authApi` and
`userApi`. Response interceptor: on `401`, clear the store and redirect to
`/login`.

**Router guards** (`router/index.ts`, global `beforeEach`):
1. If `currentUser` unknown, `fetchMe()` once.
2. Not authenticated and route ≠ `/login` → redirect `/login`.
3. Authenticated + `mustChangePassword` and route ≠ `/set-password` → redirect
   `/set-password`.
4. Route `/users` and not `isAdmin` → redirect `/timeline`.
5. The three existing data routes (`/timeline`, `/customers`, `/team-members`)
   → any authenticated role.

**New views:**
- `LoginView.vue` — email + password; on success → `/timeline`, or
  `/set-password` if `mustChangePassword`. Shows error on 401.
- `SetPasswordView.vue` — serves both forced first-login change and voluntary
  "change my password". Fields: current password, new password, confirm. Live
  policy checklist (each of: ≥12 chars, upper, lower, digit, symbol — with
  "3 of 4 classes" satisfied indicator) ticking green. Submit disabled until
  valid. Calls `changePassword()`.
- `UsersView.vue` (admin only) — table of users with create form (email, role,
  temp password), per-row: change role (select), reset password, enable/disable
  toggle, delete (with confirm). Own-account-protected actions are disabled.

**Role-based UI gating** (UX only — backend is the real gate):
- `VIEW`: timeline, customers, team-members render **read-only** — hide
  drag-and-drop, pill editing, add/edit/delete buttons, logo search.
- `VIEW_WRITE`: full data editing; no Users nav link.
- `ADMIN`: adds **Users** nav entry + view.
- Sidebar footer: current user email, role badge, **Logout** button.

**App shell:** `main.ts` / `App.vue` runs `fetchMe()` before mounting routed
content; while unauthenticated the router lands on `LoginView`.

---

## Error Handling

- Bad login / disabled account → `401`, generic "invalid email or password"
  (don't reveal which).
- Policy failure on change-password → `422` with the list of failed rules.
- Wrong current password on change-password → `400`.
- Duplicate email on create → `409`.
- Last-admin self-lockout attempts → `409`/`422` with explanation.
- Frontend `401` interceptor → clears session state, routes to `/login`.

## Testing

**Backend** (MockMvc, H2 test profile — JPA `ddl-auto` creates `users`, since
Liquibase is disabled under `test`):
- Authorization matrix: each role × a representative read endpoint, data-write
  endpoint, and `/api/users` endpoint → expected `200`/`403`.
- Login → `/me` → logout happy path; bad creds → `401`; disabled user → `401`.
- `change-password`: valid new password succeeds and clears the flag; each
  policy violation → `422`; wrong current password → `400`.
- First-login lockdown: flagged user blocked (`403`) from a data endpoint, but
  allowed on `/me`, `/logout`, `/change-password`.
- Bootstrap runner: empty table + env → admin row created with the flag;
  non-empty table → no-op; empty + no env → no row, warning path.
- User management: create (dup email → `409`), reset, role change, enable/disable,
  delete; last-admin self-lockout guards.
- `PasswordPolicy` unit tests (accept/reject table).

**Frontend** (Vitest):
- Auth store: `login`/`logout`/`fetchMe`/getters (`canWrite`, `isAdmin`).
- Route-guard logic: unauth → `/login`; must-change → `/set-password`;
  non-admin on `/users` → `/timeline`.
- Password-policy validator unit (mirror of backend rules).
- Role gating: `VIEW` hides write controls; `ADMIN` shows Users link.
- `UsersView` CRUD against a mocked `userApi`.

## Migration / Compatibility Notes

- Existing CORS config (`WebConfig`) stays; Spring Security must permit the
  same origins and allow credentials.
- All existing API routes move behind authentication — the running app's
  unauthenticated callers (if any) will start receiving `401`. Acceptable: this
  is the point of the feature.
- No change to existing data tables; only the new `users` table is added.
