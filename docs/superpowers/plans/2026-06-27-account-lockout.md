# Account Lockout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Temporarily lock an account after 5 consecutive failed logins (auto-unlock after 15 min), with an admin force-unlock and an explicit locked message at login.

**Architecture:** Add `failedAttempts` + `lockedUntil` to the `User` entity. A `LoginAttemptService` increments/resets the counter and sets `lockedUntil`. `AppUserDetailsService` maps the derived lock onto `UserDetails.accountLocked`, so Spring throws `LockedException` before the password check; `AuthController.login` records failures/successes and translates `LockedException` to 423. Admin `unlock` and `reset-password` clear the lock; the frontend shows the lock state.

**Tech Stack:** Spring Boot 3.5.16 (Java 25), Spring Security, Liquibase, JPA/H2(test)/PostgreSQL; Vue 3 `<script setup>`, Pinia, axios, Vitest.

## Global Constraints

- Lockout is temporary auto-unlock: **5** consecutive failed logins → locked **15** minutes. Both values configurable (`app.security.lockout.max-attempts`, `app.security.lockout.minutes`); the numbers above are defaults.
- "Locked" is derived: `lockedUntil != null && lockedUntil.isAfter(now)`. No stored boolean.
- Locked login → **423 Locked** with an explicit message; bad credentials → **401** generic; unknown email failure → 401, no row touched.
- Admin clears a lock via `POST /api/users/{id}/unlock` (ADMIN-only) and via `reset-password`; disable/enable does NOT clear the lock.
- A successful login resets `failedAttempts` and `lockedUntil`. A failed login after an expired lock resets the counter to 0 before incrementing.
- Counter writes are not transactionally atomic — accepted for an internal single-backend tool.
- Table is `users`; migration columns must stay in parity with the entity (test profile builds the table from the entity via `ddl-auto`, so the migration is not exercised by the suite).
- Vue 3 `<script setup>` only; backend is the authority, frontend is UX.

**Toolchain (every backend command):**
```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null   # reads backend/.sdkmanrc (java 25)
```
Frontend (no `npm run test`): `cd frontend && npx vitest run <spec>` and `npx vue-tsc --noEmit`.

---

### Task 1: Lockout columns on the User entity + migration

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/user/User.java`
- Create: `backend/src/main/resources/db/changelog/016-user-lockout.yaml`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `backend/src/test/java/org/lolobored/tm/user/UserRepositoryTest.java`

**Interfaces:**
- Produces: `User.getFailedAttempts()/setFailedAttempts(int)` (default 0), `User.getLockedUntil()/setLockedUntil(Instant)` (nullable).

- [ ] **Step 1: Add the failing test to `UserRepositoryTest.java`**

Add this method (the class already has `@DataJpaTest`, `@Autowired UserRepository repository`, a `newUser(String)` helper, and imports `java.time.Instant` + AssertJ):
```java
    @Test
    void lockoutFields_defaultToUnlocked() {
        User saved = repository.save(newUser("lock@example.com"));
        User found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFailedAttempts()).isZero();
        assertThat(found.getLockedUntil()).isNull();
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.UserRepositoryTest'`
Expected: FAIL (`getFailedAttempts`/`getLockedUntil` do not exist).

- [ ] **Step 3: Add the two columns to `User.java`**

After the `mustChangePassword` field/accessors (and before or after `createdAt` — `Instant` is already imported), add the fields:
```java
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;
```
and the accessors (alongside the other getters/setters):
```java
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.UserRepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Create migration `016-user-lockout.yaml`**
```yaml
databaseChangeLog:
  - changeSet:
      id: 16a
      author: laurent
      comment: "Add account-lockout tracking columns to users"
      changes:
        - addColumn:
            tableName: users
            columns:
              - column:
                  name: failed_attempts
                  type: INT
                  defaultValueNumeric: 0
                  constraints: { nullable: false }
              - column:
                  name: locked_until
                  type: TIMESTAMP
```

- [ ] **Step 6: Register it in `db.changelog-master.yaml`**

Append after the `015-users.yaml` include:
```yaml
  - include:
      file: db/changelog/016-user-lockout.yaml
```

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test`
Expected: PASS (no regressions).

- [ ] **Step 8: Commit**
```bash
git add backend/src/main/java/org/lolobored/tm/user/User.java backend/src/main/resources/db/changelog/016-user-lockout.yaml backend/src/main/resources/db/changelog/db.changelog-master.yaml backend/src/test/java/org/lolobored/tm/user/UserRepositoryTest.java
git commit -m "feat(auth): add failedAttempts/lockedUntil columns for account lockout (#27)"
```

---

### Task 2: LoginAttemptService + config

**Files:**
- Create: `backend/src/main/java/org/lolobored/tm/user/LoginAttemptService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/org/lolobored/tm/user/LoginAttemptServiceTest.java`

**Interfaces:**
- Consumes: `User`, `UserRepository` (`findByEmail`), Task 1 lockout accessors.
- Produces: `LoginAttemptService` with `void recordFailure(String email)`, `void recordSuccess(String email)`, `long minutesRemaining(String email)`. Constructor `(UserRepository, int maxAttempts, long lockoutMinutes)`.

- [ ] **Step 1: Add config to `application.yml`**

Under the existing top-level `app:` key, add the `security.lockout` block (sibling of `app.admin`):
```yaml
app:
  admin:
    email: ${ADMIN_EMAIL:}
    password: ${ADMIN_PASSWORD:}
  security:
    lockout:
      max-attempts: 5
      minutes: 15
```
(Keep the existing `app.admin` entries; only add the `security` subtree.)

- [ ] **Step 2: Write the failing `LoginAttemptServiceTest.java`**

Pure Mockito — no Spring context. Constructs the service with `maxAttempts=5`, `lockoutMinutes=15`.
```java
package org.lolobored.tm.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        return u;
    }

    @Test
    void recordFailure_increments() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(1);
        assertThat(u.getLockedUntil()).isNull();
        verify(repo).save(u);
    }

    @Test
    void recordFailure_locksOnThreshold() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(4);
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        Instant before = Instant.now();
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(5);
        assertThat(u.getLockedUntil()).isNotNull();
        assertThat(u.getLockedUntil()).isAfter(before.plus(14, ChronoUnit.MINUTES));
    }

    @Test
    void recordFailure_afterExpiredLock_resetsThenIncrements() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES)); // expired
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordFailure("a@x.com");
        assertThat(u.getFailedAttempts()).isEqualTo(1);
        assertThat(u.getLockedUntil()).isNull();
    }

    @Test
    void recordFailure_unknownEmail_isNoOp() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findByEmail("missing@x.com")).thenReturn(Optional.empty());
        new LoginAttemptService(repo, 5, 15).recordFailure("missing@x.com");
        verify(repo, never()).save(any());
    }

    @Test
    void recordSuccess_resetsCounterAndLock() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setFailedAttempts(3);
        u.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordSuccess("a@x.com");
        assertThat(u.getFailedAttempts()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        verify(repo).save(u);
    }

    @Test
    void recordSuccess_noChange_skipsSave() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com"); // already 0 / null
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        new LoginAttemptService(repo, 5, 15).recordSuccess("a@x.com");
        verify(repo, never()).save(any());
    }

    @Test
    void minutesRemaining_ceilsToMinutes() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("a@x.com");
        u.setLockedUntil(Instant.now().plus(90, ChronoUnit.SECONDS)); // 1.5 min
        when(repo.findByEmail("a@x.com")).thenReturn(Optional.of(u));
        assertThat(new LoginAttemptService(repo, 5, 15).minutesRemaining("a@x.com")).isEqualTo(2);
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.LoginAttemptServiceTest'`
Expected: FAIL (`LoginAttemptService` does not exist).

- [ ] **Step 4: Create `LoginAttemptService.java`**
```java
package org.lolobored.tm.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final int maxAttempts;
    private final long lockoutMinutes;

    public LoginAttemptService(UserRepository users,
                               @Value("${app.security.lockout.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.lockout.minutes:15}") long lockoutMinutes) {
        this.users = users;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    public void recordFailure(String email) {
        User u = users.findByEmail(email).orElse(null);
        if (u == null) {
            return; // unknown email — cannot lock a non-existent account
        }
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && !u.getLockedUntil().isAfter(now)) {
            // previous lock has expired — start a fresh window
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        u.setFailedAttempts(u.getFailedAttempts() + 1);
        if (u.getFailedAttempts() >= maxAttempts) {
            u.setLockedUntil(now.plus(lockoutMinutes, ChronoUnit.MINUTES));
        }
        users.save(u);
    }

    public void recordSuccess(String email) {
        User u = users.findByEmail(email).orElseThrow();
        if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            users.save(u);
        }
    }

    /** Minutes (ceiling, ≥1) until the lock expires; falls back to the window if absent. */
    public long minutesRemaining(String email) {
        User u = users.findByEmail(email).orElse(null);
        if (u == null || u.getLockedUntil() == null) {
            return lockoutMinutes;
        }
        long seconds = u.getLockedUntil().getEpochSecond() - Instant.now().getEpochSecond();
        if (seconds <= 0) {
            return 1;
        }
        return (seconds + 59) / 60;
    }
}
```

- [ ] **Step 5: Run the service test, then the full suite**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.LoginAttemptServiceTest' && ./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/org/lolobored/tm/user/LoginAttemptService.java backend/src/main/resources/application.yml backend/src/test/java/org/lolobored/tm/user/LoginAttemptServiceTest.java
git commit -m "feat(auth): LoginAttemptService for failed-attempt tracking + lockout config (#27)"
```

---

### Task 3: Enforce lockout in the login path

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/user/AppUserDetailsService.java`
- Modify: `backend/src/main/java/org/lolobored/tm/user/AuthController.java`
- Test: `backend/src/test/java/org/lolobored/tm/user/LoginLockoutTest.java`

**Interfaces:**
- Consumes: `LoginAttemptService` (Task 2), `User.getLockedUntil()` (Task 1).
- Produces: login returns **423** for a locked account; records failures/successes.

- [ ] **Step 1: Map the lock in `AppUserDetailsService.java`**

Add `.accountLocked(...)` to the builder chain (after `.disabled(...)`):
```java
        return org.springframework.security.core.userdetails.User.builder()
                .username(u.getEmail())
                .password(u.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))
                .disabled(!u.isEnabled())
                .accountLocked(u.getLockedUntil() != null
                        && u.getLockedUntil().isAfter(java.time.Instant.now()))
                .build();
```

- [ ] **Step 2: Write the failing `LoginLockoutTest.java`**
```java
package org.lolobored.tm.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LoginLockoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private void seed(String email, String rawPassword) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        users.save(u);
    }

    private int login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @BeforeEach
    void clean() { users.deleteAll(); }

    @Test
    void fiveFailures_thenCorrectPassword_isLocked() throws Exception {
        seed("u@example.com", "Right-Password-1");
        for (int i = 0; i < 5; i++) {
            assertThat(login("u@example.com", "wrong")).isEqualTo(401);
        }
        // 6th attempt with the CORRECT password is rejected because the account is locked
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(423);
        assertThat(users.findByEmail("u@example.com").orElseThrow().getLockedUntil()).isNotNull();
    }

    @Test
    void successBeforeThreshold_resetsCounter() throws Exception {
        seed("u@example.com", "Right-Password-1");
        login("u@example.com", "wrong");
        login("u@example.com", "wrong");
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(200);
        assertThat(users.findByEmail("u@example.com").orElseThrow().getFailedAttempts()).isZero();
    }

    @Test
    void expiredLock_allowsLoginAndResets() throws Exception {
        seed("u@example.com", "Right-Password-1");
        User u = users.findByEmail("u@example.com").orElseThrow();
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES)); // already expired
        users.save(u);
        assertThat(login("u@example.com", "Right-Password-1")).isEqualTo(200);
        User after = users.findByEmail("u@example.com").orElseThrow();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.LoginLockoutTest'`
Expected: FAIL (no lockout wired — the 6th login returns 401/200, not 423).

- [ ] **Step 4: Wire `AuthController.java`**

Add the import:
```java
import org.springframework.security.authentication.LockedException;
```
Add the field + constructor param (inject `LoginAttemptService`):
```java
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users,
                          PasswordEncoder encoder, LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.encoder = encoder;
        this.loginAttemptService = loginAttemptService;
    }
```
Replace the `login` method's try/catch + success line. `LockedException` is an `AuthenticationException`, so its catch MUST come first:
```java
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.email(), body.password()));
        } catch (LockedException ex) {
            long mins = loginAttemptService.minutesRemaining(body.email());
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "Account temporarily locked due to too many failed login attempts. "
                + "Try again in about " + mins + " minute(s).");
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(body.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password");
        }
        loginAttemptService.recordSuccess(body.email());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        contextRepository.saveContext(context, request, response);

        User u = users.findByEmail(body.email()).orElseThrow();
        return new MeResponse(u.getEmail(), u.getRole(), u.isMustChangePassword());
```

- [ ] **Step 5: Run the lockout test, then the full suite**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.LoginLockoutTest' && ./gradlew test`
Expected: PASS. (Existing `AuthControllerTest` still green — its bad-password case stays 401, its valid logins still 200.)

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/org/lolobored/tm/user/AppUserDetailsService.java backend/src/main/java/org/lolobored/tm/user/AuthController.java backend/src/test/java/org/lolobored/tm/user/LoginLockoutTest.java
git commit -m "feat(auth): enforce account lockout at login, 423 on locked (#27)"
```

---

### Task 4: Admin unlock, reset-password clears lock, UserDto exposes lock

**Files:**
- Modify: `backend/src/main/java/org/lolobored/tm/user/UserController.java`
- Test: `backend/src/test/java/org/lolobored/tm/user/UserLockoutAdminTest.java`

**Interfaces:**
- Consumes: `User` lockout accessors (Task 1).
- Produces: `POST /api/users/{id}/unlock` (ADMIN) → 200 `UserDto`; `reset-password` clears lock; `UserDto` gains `Instant lockedUntil`.

- [ ] **Step 1: Write the failing `UserLockoutAdminTest.java`**
```java
package org.lolobored.tm.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserLockoutAdminTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private Long seedLocked(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode("temp"));
        u.setRole(Role.VIEW);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(Instant.now());
        u.setFailedAttempts(5);
        u.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        return users.save(u).getId();
    }

    @BeforeEach
    void clean() { users.deleteAll(); }

    @Test
    void unlock_clearsLockAndCounter() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/unlock")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        User after = users.findById(id).orElseThrow();
        assertThat(after.getFailedAttempts()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void unlock_isAdminOnly() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/unlock")
                        .with(user("w@example.com").roles("VIEW_WRITE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_clearsLock() throws Exception {
        Long id = seedLocked("u@example.com");
        mockMvc.perform(post("/api/users/" + id + "/reset-password")
                        .with(user("admin@example.com").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialPassword\":\"fresh1\"}"))
                .andExpect(status().isNoContent());
        User after = users.findById(id).orElseThrow();
        assertThat(after.getLockedUntil()).isNull();
        assertThat(after.getFailedAttempts()).isZero();
    }

    @Test
    void userDto_exposesLockedUntil() throws Exception {
        seedLocked("u@example.com");
        mockMvc.perform(get("/api/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lockedUntil").exists());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.UserLockoutAdminTest'`
Expected: FAIL (no `/unlock`, `UserDto` has no `lockedUntil`).

- [ ] **Step 3: Add `lockedUntil` to `UserDto` in `UserController.java`**

Update the record + factory:
```java
    public record UserDto(Long id, String email, Role role, boolean enabled,
                          boolean mustChangePassword, Instant lockedUntil, Instant createdAt) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.isMustChangePassword(), u.getLockedUntil(), u.getCreatedAt());
        }
    }
```

- [ ] **Step 4: Clear the lock in `resetPassword`**

Add the two setters before `users.save(u)`:
```java
        User u = find(id);
        u.setPasswordHash(encoder.encode(body.initialPassword()));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
```

- [ ] **Step 5: Add the `unlock` endpoint**

Add after `setEnabled` (before `delete`):
```java
    @PostMapping("/{id}/unlock")
    public UserDto unlock(@PathVariable Long id) {
        User u = find(id);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        return UserDto.from(users.save(u));
    }
```

- [ ] **Step 6: Run the admin test, then the full suite**

Run: `cd backend && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env >/dev/null && ./gradlew test --tests 'org.lolobored.tm.user.UserLockoutAdminTest' && ./gradlew test`
Expected: PASS. (Existing `UserControllerTest` still green — the added `lockedUntil` JSON field doesn't break its assertions.)

- [ ] **Step 7: Commit**
```bash
git add backend/src/main/java/org/lolobored/tm/user/UserController.java backend/src/test/java/org/lolobored/tm/user/UserLockoutAdminTest.java
git commit -m "feat(auth): admin unlock endpoint, reset-password clears lock, expose lockedUntil (#27)"
```

---

### Task 5: Frontend — lock message + admin unlock UI

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/views/UsersView.vue`
- Test: `frontend/src/__tests__/lockout.spec.ts`
- Modify: `frontend/src/__tests__/UsersView.spec.ts` (add `unlock` to the `userApi` mock)

**Interfaces:**
- Consumes: `userApi` (Task 4 endpoints), `AppUser`.
- Produces: `AppUser.lockedUntil?`, `userApi.unlock(id)`, login 423 message, Users-view Unlock button + badge.

- [ ] **Step 1: Add `lockedUntil` to `AppUser` in `types/index.ts`**
```ts
export interface AppUser {
  id: number
  email: string
  role: Role
  enabled: boolean
  mustChangePassword: boolean
  lockedUntil?: string | null
  createdAt: string
}
```

- [ ] **Step 2: Add `userApi.unlock` in `api/client.ts`**

In the `userApi` object, add (after `setEnabled`):
```ts
  unlock: (id: number) => api.post<AppUser>(`/users/${id}/unlock`).then(r => r.data),
```

- [ ] **Step 3: Write the failing `lockout.spec.ts`**

Backend hides exception messages by default, so the 423 message is a static frontend string (the body's `message`, if present, is preferred).
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const loginMock = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock, currentUser: null }),
}))

import LoginView from '@/views/LoginView.vue'

describe('LoginView lockout message', () => {
  beforeEach(() => { setActivePinia(createPinia()); loginMock.mockReset() })

  it('shows the lock message on 423', async () => {
    loginMock.mockRejectedValue({ response: { status: 423 } })
    const wrapper = mount(LoginView)
    await wrapper.findAll('input')[0].setValue('u@x.com')
    await wrapper.findAll('input')[1].setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="login-error"]').text().toLowerCase()).toContain('locked')
  })

  it('shows the generic message on 401', async () => {
    loginMock.mockRejectedValue({ response: { status: 401 } })
    const wrapper = mount(LoginView)
    await wrapper.findAll('input')[0].setValue('u@x.com')
    await wrapper.findAll('input')[1].setValue('pw')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="login-error"]').text()).toContain('Invalid email or password')
  })
})
```

- [ ] **Step 4: Branch the error in `LoginView.vue`**

Replace the `catch` block in `submit()`:
```ts
  } catch (e: any) {
    const status = e?.response?.status
    if (status === 423) {
      error.value = e?.response?.data?.message
        || 'Account temporarily locked due to too many failed login attempts. Try again in about 15 minutes.'
    } else {
      error.value = 'Invalid email or password'
    }
  } finally {
```
(The existing error `<p>` already carries `data-testid="login-error"`.)

- [ ] **Step 5: Run the login test to verify it passes**

Run: `cd frontend && npx vitest run src/__tests__/lockout.spec.ts`
Expected: PASS.

- [ ] **Step 6: Add the Unlock UI to `UsersView.vue`**

In `<script setup>`, add an `isLocked` helper and an `unlock` action:
```ts
function isLocked(u: AppUser): boolean {
  return !!u.lockedUntil && new Date(u.lockedUntil) > new Date()
}

async function unlock(u: AppUser) {
  await userApi.unlock(u.id)
  await load()
}
```
In the status cell of each row (next to the enabled/mustChangePassword indicators), add a locked badge:
```html
            <span v-if="isLocked(u)" class="locked" :data-testid="`locked-${u.id}`">
              · 🔒 locked until {{ new Date(u.lockedUntil!).toLocaleTimeString() }}
            </span>
```
In the actions cell, add the Unlock button (only when locked), before the Delete button:
```html
            <button v-if="isLocked(u)" :data-testid="`unlock-${u.id}`" @click="unlock(u)">Unlock</button>
```
Add a scoped style:
```css
.locked { color: #dc2626; font-size: 0.75rem; }
```

- [ ] **Step 7: Add the locked-row test to `lockout.spec.ts`**

Append a second `describe` (mock `@/stores/auth` for an admin and `@/api/client`'s `userApi`):
```ts
import UsersView from '@/views/UsersView.vue'

vi.mock('@/api/client', () => ({
  userApi: {
    list: vi.fn(),
    unlock: vi.fn().mockResolvedValue({}),
    create: vi.fn(), changeRole: vi.fn(), resetPassword: vi.fn(), setEnabled: vi.fn(), remove: vi.fn(),
  },
}))
import { userApi } from '@/api/client'

describe('UsersView unlock', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('shows Unlock for a locked user and calls userApi.unlock', async () => {
    const future = new Date(Date.now() + 15 * 60_000).toISOString()
    ;(userApi.list as any)
      .mockResolvedValueOnce([{ id: 7, email: 'locked@x.com', role: 'VIEW', enabled: true, mustChangePassword: false, lockedUntil: future, createdAt: '2026-06-01T00:00:00Z' }])
      .mockResolvedValue([])
    const wrapper = mount(UsersView)
    await flushPromises()
    const btn = wrapper.get('[data-testid="unlock-7"]')
    expect(wrapper.find('[data-testid="locked-7"]').exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()
    expect(userApi.unlock).toHaveBeenCalledWith(7)
  })

  it('does not show Unlock for an unlocked user', async () => {
    ;(userApi.list as any).mockResolvedValue([{ id: 8, email: 'ok@x.com', role: 'VIEW', enabled: true, mustChangePassword: false, lockedUntil: null, createdAt: '2026-06-01T00:00:00Z' }])
    const wrapper = mount(UsersView)
    await flushPromises()
    expect(wrapper.find('[data-testid="unlock-8"]').exists()).toBe(false)
  })
})
```
NOTE: `UsersView.vue` calls `useAuthStore()` for `isSelf`/`currentUser`. The existing `UsersView.spec.ts` mocks `@/stores/auth` — this new file must mock it too. Add at the top of `lockout.spec.ts`, before the `UsersView` import, an admin auth mock matching the existing one:
```ts
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock, currentUser: { email: 'admin@x.com', role: 'ADMIN', mustChangePassword: false } }),
}))
```
(Reuse the single `@/stores/auth` mock for both describes — the `login` mock is used by the LoginView tests, `currentUser` by UsersView. Define `loginMock` before the mock factory.)

- [ ] **Step 8: Add `unlock` to the existing `UsersView.spec.ts` userApi mock**

In `frontend/src/__tests__/UsersView.spec.ts`, add `unlock: vi.fn(),` to the mocked `userApi` object so the import resolves with the new method.

- [ ] **Step 9: Run the full frontend suite + type-check**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit`
Expected: PASS, no type errors.

- [ ] **Step 10: Commit**
```bash
git add frontend/src/types/index.ts frontend/src/api/client.ts frontend/src/views/LoginView.vue frontend/src/views/UsersView.vue frontend/src/__tests__/lockout.spec.ts frontend/src/__tests__/UsersView.spec.ts
git commit -m "feat(auth): locked-account login message and admin unlock UI (#27)"
```

---

## Self-Review

**Spec coverage:**
- `failedAttempts`/`lockedUntil` columns + derived lock → Task 1. ✓
- Threshold 5 / cooldown 15, configurable → Task 2 (`LoginAttemptService` + `application.yml`). ✓
- Reset on success; reset-counter-after-expiry → Task 2 (unit) + Task 3 (integration). ✓
- 423 explicit message; 401 generic; unknown-email no-op → Task 3. ✓
- `accountLocked` mapping (locked rejected before password) → Task 3. ✓
- Admin `unlock` (ADMIN-only); reset-password clears lock; `UserDto.lockedUntil` → Task 4. ✓
- Disable/enable does NOT clear lock → unchanged `setEnabled` (no lock code added). ✓
- Frontend lock message + Unlock button + badge + `userApi.unlock` → Task 5. ✓

**Type consistency:** `User` accessors (`getFailedAttempts`/`getLockedUntil`) used identically in Tasks 2–4; `LoginAttemptService` method names (`recordFailure`/`recordSuccess`/`minutesRemaining`) match Task 3 usage; `UserDto` field order `(…, mustChangePassword, lockedUntil, createdAt)` consistent; `userApi.unlock(id)` matches Task 5 view + tests; `AppUser.lockedUntil` shape (`string|null`) matches the `lockedUntil` ISO string from the backend `Instant`.

**Placeholder scan:** none — every code step carries complete code.

**Note on the 423 message:** Spring Boot hides exception reasons in error responses by default (`server.error.include-message` is not set), so the frontend uses a static lock message; the dynamic remaining-minutes text reaches the client only if message inclusion is enabled. This is intentional (avoids globally exposing error messages) and is handled by the `|| static` fallback in Task 5 Step 4.
