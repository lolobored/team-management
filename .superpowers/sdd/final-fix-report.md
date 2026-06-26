# Final Fix Report — Auth/RBAC Two-Finding Wave

Date: 2026-06-26

---

## Finding 1 (CRITICAL) — Docker healthcheck now 401s, killing prod

### Changes

1. **Created** `backend/src/main/java/org/lolobored/tm/config/HealthController.java`
   — New `@RestController` exposing `GET /api/health` returning `{"status":"ok"}`.

2. **Modified** `backend/src/main/java/org/lolobored/tm/config/SecurityConfig.java`
   — Added `.requestMatchers(HttpMethod.GET, "/api/health").permitAll()` immediately after the login `permitAll` rule, before the `GET /api/**` → `hasRole("VIEW")` rule (first-match wins; health is now publicly accessible).

3. **Modified** `docker-compose.yml` line 31
   — Changed healthcheck test from `curl -f http://localhost:8080/api/team-members` to `curl -f http://localhost:8080/api/health`.

### Test added

`backend/src/test/java/org/lolobored/tm/user/AuthorizationMatrixTest.java` — added:
```java
@Test
void health_anonymous_ok() throws Exception {
    mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
}
```
Also added `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;`.

---

## Finding 2 (IMPORTANT) — App shell renders over login/set-password screens

### Changes

1. **Replaced** `frontend/src/App.vue`
   — Added computed `showShell` that is true only when `route.meta.public !== true && auth.isAuthenticated && !auth.currentUser?.mustChangePassword`.
   — Template: `<AppLayout v-if="showShell" />` else `<RouterView v-else />`.

### Test added

**Created** `frontend/src/__tests__/App.spec.ts` — 3 cases:
- (a) `meta.public: true` → `AppLayout` NOT rendered, `RouterView` IS rendered
- (b) authenticated, non-public, `mustChangePassword: false` → `AppLayout` rendered
- (c) authenticated, `mustChangePassword: true` → `AppLayout` NOT rendered

---

## Test Evidence

### Backend (`./gradlew test`)

```
> Task :test
BUILD SUCCESSFUL in 10s
4 actionable tasks: 3 executed, 1 up-to-date
```

### Frontend (`npx vitest run`)

```
Test Files  16 passed (16)
     Tests  88 passed (88)
  Duration  2.06s
```

### TypeScript (`npx vue-tsc --noEmit`)

No errors output — clean.
