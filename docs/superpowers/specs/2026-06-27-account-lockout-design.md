# Account Lockout After Failed Login Attempts — Design Spec

> Tracking issue: [#27](https://github.com/lolobored/team-management/issues/27)
> Builds on the Authentication & RBAC feature ([#25](https://github.com/lolobored/team-management/issues/25), released 2026.2.5)
> Date: 2026-06-27

## Goal

Slow down brute-force password guessing by temporarily locking an account after
too many consecutive failed logins. The lock auto-expires after a cooldown; an
admin can clear it sooner.

## Summary of Decisions

| Decision | Choice |
|---|---|
| Lockout type | Temporary, auto-unlock after a cooldown window |
| Threshold | 5 consecutive failed logins |
| Cooldown | 15 minutes |
| Login message when locked | Explicit ("temporarily locked, try again in ~N minutes") |
| Admin clears lock early | Dedicated **Unlock** action in the Users view; **reset-password** also clears it |
| Disable/enable vs lock | Independent — toggling `enabled` does NOT clear the lock |
| Scope | Per-account (keyed by email), single backend |

Threshold and cooldown are configurable; the values above are the defaults.

## Out of Scope (YAGNI)

Per-IP throttling, CAPTCHA, lockout-notification emails, per-user configurable
thresholds, progressive backoff, distributed/atomic counters.

---

## Architecture

### Data model

Two new columns on the existing `users` table (Liquibase changelog
`016-user-lockout.yaml`), both added to the `User` entity:

| column | type | default | meaning |
|---|---|---|---|
| `failed_attempts` | int | 0 (not null) | consecutive failed logins since the last success/reset |
| `locked_until` | timestamp | null | when the lock expires; null or in the past = not locked |

**"Locked" is derived**, not a stored boolean: `lockedUntil != null && lockedUntil.isAfter(now)`.
This avoids keeping a flag in sync with the timestamp.

Entity getters/setters follow the existing `User` style. The migration columns
must stay in parity with the entity (the test profile builds the table from the
entity via `ddl-auto`, so the migration is not exercised by the suite).

### Configuration

Bind two properties (with defaults) via `@Value` or a small config record:

```yaml
app:
  security:
    lockout:
      max-attempts: 5
      minutes: 15
```

Referenced as `${app.security.lockout.max-attempts:5}` and
`${app.security.lockout.minutes:15}`.

### Login attempt tracking — `LoginAttemptService`

A small `@Service` in `org.lolobored.tm.user` holds the failure/success logic so
it is unit-testable without the web layer. Injects `UserRepository` and the two
config values.

```java
// pseudocode of the contract
void recordFailure(String email):
    user = users.findByEmail(email).orElse(null)
    if user == null: return                      // unknown email — nothing to lock
    if user.lockedUntil != null && !user.lockedUntil.isAfter(now):
        user.failedAttempts = 0                  // previous lock expired — fresh window
        user.lockedUntil = null
    user.failedAttempts += 1
    if user.failedAttempts >= maxAttempts:
        user.lockedUntil = now.plus(minutes)
    users.save(user)

void recordSuccess(String email):
    user = users.findByEmail(email).orElseThrow()
    if user.failedAttempts != 0 || user.lockedUntil != null:
        user.failedAttempts = 0
        user.lockedUntil = null
        users.save(user)
```

`now` is obtained from `java.time.Instant.now()` inside the service (acceptable;
not injected as a clock — keep it simple, the tests assert state transitions, not
exact instants beyond before/after).

### Spring Security wiring — `AppUserDetailsService`

Map the derived lock state onto the Spring `UserDetails` so authentication is
rejected before the password is checked:

```java
.accountLocked(u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()))
```

Spring's `DaoAuthenticationProvider` runs pre-authentication checks (locked →
`LockedException`) **before** the password comparison, so a locked account is
rejected even when the supplied password is correct.

### Login flow — `AuthController.login`

```java
try {
    authentication = authenticationManager.authenticate(token);
} catch (LockedException ex) {
    // explicit message with remaining minutes (load the user to compute it)
    long mins = minutesRemaining(body.email());
    throw new ResponseStatusException(HttpStatus.LOCKED,
        "Account temporarily locked due to too many failed login attempts. "
        + "Try again in about " + mins + " minute(s).");
} catch (AuthenticationException ex) {
    loginAttemptService.recordFailure(body.email());
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password");
}
loginAttemptService.recordSuccess(body.email());
// ... existing session establishment + MeResponse
```

- **423 Locked** distinguishes a lock from a normal 401 so the frontend can show
  the explicit message.
- `minutesRemaining(email)` loads the user and returns
  `max(1, ceil((lockedUntil - now) / 60s))`; if the user/lock is somehow absent it
  falls back to the configured window.
- The generic 401 is unchanged for bad credentials (no account-existence leak on
  the normal-failure path). The 423 path does reveal that a (real) account is
  locked — an accepted tradeoff per the explicit-message decision.
- A disabled account still throws `DisabledException` → caught by the
  `AuthenticationException` branch → generic 401 (unchanged). Locked takes
  precedence over disabled in Spring's check order; both block login, so this is
  immaterial.

### Admin endpoints — `UserController` (ADMIN only)

- New: `POST /api/users/{id}/unlock` → sets `failedAttempts=0`, `lockedUntil=null`,
  returns the updated `UserDto` (200). 404 if the id is unknown.
- Modify: `resetPassword` also clears `failedAttempts` and `lockedUntil` (natural
  recovery path — a fresh temp password should not arrive pre-locked).
- `UserDto` gains a `lockedUntil` field (`Instant`, null when not locked) so the
  Users view can render lock state and conditionally show **Unlock**. (The hash is
  still never exposed.)
- The self-guard rules are unchanged; unlocking does not touch role/enabled.

### Frontend

**Types / API** (`types/index.ts`, `api/client.ts`):
- `AppUser` gains `lockedUntil?: string | null`.
- `userApi.unlock(id)` → `POST /users/${id}/unlock`.

**Login (`LoginView.vue`):** in the catch, branch on `err.response?.status`:
- `423` → show the server's message if present, else
  "Account temporarily locked due to too many failed login attempts. Try again in about 15 minutes."
- anything else → existing "Invalid email or password".

**Users view (`UsersView.vue`):**
- Helper `isLocked(u) = !!u.lockedUntil && new Date(u.lockedUntil) > new Date()`.
- Locked rows show a **"Locked"** badge (with the until time).
- Locked rows show an **Unlock** button → `userApi.unlock(u.id)` → reload list.
- Reset-password already clears the lock server-side — no extra UI.

---

## Error Handling

- Locked account login → `423 Locked` + explicit message.
- Bad credentials → `401` generic.
- Unknown email failed login → `401` generic, no row touched (cannot lock a
  non-existent account).
- Unlock unknown id → `404`.
- Concurrency: failure-counter writes are not transactionally atomic; a burst of
  simultaneous failed logins may miscount by one or two. Accepted for an internal
  single-backend tool; not mitigated.

## Testing

**Backend**
- `LoginAttemptService` unit tests (Mockito `UserRepository`): single failure
  increments; the 5th failure sets `lockedUntil ≈ now+15m`; success resets both
  fields; a failure after an expired lock resets the counter to 0 before
  incrementing to 1; unknown email is a no-op.
- `AppUserDetailsService`: maps `accountLocked=true` when `lockedUntil` is in the
  future, `false` when null or past.
- MockMvc integration (H2 test profile): 5 bad-password logins return 401, then a
  6th attempt **with the correct password** returns **423**; `POST /unlock` (as
  admin) clears it and a subsequent correct login returns 200;
  `reset-password` clears the lock; `GET /api/users` `UserDto` includes
  `lockedUntil`; a successful login resets a partial failure count.
- Authorization: `/api/users/{id}/unlock` is ADMIN-only (403 for VIEW/VIEW_WRITE).

**Frontend** (Vitest)
- `LoginView`: a rejected login with `response.status === 423` renders the lock
  message; `401` renders the generic message.
- `UsersView`: a user with a future `lockedUntil` renders the Unlock button + badge;
  clicking it calls `userApi.unlock(id)`; a non-locked user shows neither.

## Migration / Compatibility Notes

- Only additive schema change (two nullable/defaulted columns); existing rows get
  `failed_attempts=0`, `locked_until=null` → unlocked, as expected.
- No change to the session/CSRF model, role matrix, or first-login lockdown.
- Existing controller tests using `@AutoConfigureMockMvc(addFilters=false)` are
  unaffected (lockout lives in the login path + user CRUD, not the filter chain).
