# Epic 2: Listings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the seller-side listing system end-to-end — backend CRUD + image upload + status FSM + rate limiting, frontend pages with full UX polish (optimistic updates, accessibility, i18n preparation), automated tests across 5 layers (unit / component / integration / E2E / manual), and the carry-over Epic 1 fix that makes rate-limit responses comply with HTTP standards.

**Architecture:** Backend Spring Boot 3.4.4 + JPA + MySQL + Redis. Listings, categories, image storage, and rate limiting are wired through the layered architecture established in Epic 1 (Controller → Service → Repository), with a new `ImageStorageService` interface mirroring Epic 1's `EmailService` pattern. Frontend React 19 + Vite + Tailwind v4 + React Router 7. New testing stack introduced for the frontend: Vitest + React Testing Library + MSW + Playwright.

**Tech Stack:** Spring Boot 3.4.4 · Java 21 · MySQL 8 · Redis 7 · JJWT 0.12.6 · Testcontainers 1.21.3 · React 19 · Vite 8 · Tailwind 4 · Vitest · React Testing Library · MSW · Playwright.

**Spec:** `docs/superpowers/specs/2026-05-14-epic-2-listings-design.md` (1192 lines, 8 sections). All decisions reference this spec by section number.

---

## Phase Overview

The plan is organised into 6 phases with 44 tasks total. Each phase ends at a demoable checkpoint that can be shown to the supervisor.

| Phase | Tasks | Focus | Demo |
|---|---|---|---|
| 0 — Infrastructure + Epic 1 fixes | T1–T4 | Global error→HTTP mapping, `Retry-After`, RateLimit extensions | #5 (login returns 429) |
| 1 — Backend listing CRUD | T5–T16 | DB tables, entities, services, 5 endpoints (no image yet) | #6 (Postman CRUD) |
| 2 — Image upload + rate limits | T17–T21 | Multipart, validation chain, file serving, RL on listing endpoints | #7 (real image + 429 on 21st create) |
| 3 — Backend integration tests + journal | T22–T24 | 18-test suite, decision log D-38.. | #8 (60+ green tests) |
| 4 — Frontend infrastructure + shared components | T25–T32 | Test stack, apiClient extension, 8 shared components, 2 hooks | — |
| 5 — Frontend pages | T33–T40 | 4 pages + ListingForm + ListingCard wired to routes | **#9 — full browser flow** |
| 6 — E2E + documentation | T41–T44 | Playwright specs, manual checklist, journal close-out | #10 (Epic 2 sealed) |

---

## Phase 0 — Infrastructure + Epic 1 fixes

> **Reality check**: Epic 1 already wired `ErrorCode → HttpStatus` mapping (commit `56dc479`). `TOO_MANY_ATTEMPTS` already returns 429. What is missing is the `Retry-After` header, the TTL-aware `RateLimitService.exceeded()` return, and the new `incrementBy` helper. Phase 0 fixes those three gaps and adds the verification test.

### Task T1 — `ApiException` carries `retryAfterSeconds`; handler emits `Retry-After`

**Files:**
- Modify: `backend/src/main/java/nz/ac/waikato/campusmarketplace/exception/ApiException.java`
- Modify: `backend/src/main/java/nz/ac/waikato/campusmarketplace/exception/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/nz/ac/waikato/campusmarketplace/exception/ApiExceptionRetryAfterTest.java`
- Create: `backend/src/test/java/nz/ac/waikato/campusmarketplace/exception/GlobalExceptionHandlerRetryAfterTest.java`

- [ ] **Step 1: Write the failing test for the new constructor**

```java
package nz.ac.waikato.campusmarketplace.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionRetryAfterTest {

    @Test
    void defaultConstructorHasNoRetryAfter() {
        ApiException ex = new ApiException(ErrorCode.BAD_CREDENTIALS, "wrong");
        assertThat(ex.getRetryAfterSeconds()).isNull();
    }

    @Test
    void constructorWithRetryAfterStoresValue() {
        ApiException ex = new ApiException(ErrorCode.TOO_MANY_ATTEMPTS, "slow down", 900L);
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(900L);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
        assertThat(ex.getMessage()).isEqualTo("slow down");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && ./mvnw -Dtest=ApiExceptionRetryAfterTest test
```
Expected: FAIL — `cannot find symbol: method getRetryAfterSeconds()` and "no matching constructor".

- [ ] **Step 3: Add the field + constructor + getter to `ApiException.java`**

```java
package nz.ac.waikato.campusmarketplace.exception;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getCode() { return code; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw -Dtest=ApiExceptionRetryAfterTest test
```
Expected: PASS — Tests run: 2, Failures: 0.

- [ ] **Step 5: Write the failing test for the handler emitting `Retry-After`**

Create `GlobalExceptionHandlerRetryAfterTest.java`:

```java
package nz.ac.waikato.campusmarketplace.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerRetryAfterTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionWithRetryAfterAddsHeader() {
        HttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        ApiException ex = new ApiException(ErrorCode.TOO_MANY_ATTEMPTS, "wait", 900L);

        ResponseEntity<ApiErrorResponse> response = handler.handleApi(ex, req);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("900");
        assertThat(response.getBody().code()).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void apiExceptionWithoutRetryAfterOmitsHeader() {
        HttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        ApiException ex = new ApiException(ErrorCode.BAD_CREDENTIALS, "nope");

        ResponseEntity<ApiErrorResponse> response = handler.handleApi(ex, req);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }
}
```

- [ ] **Step 6: Run handler test to verify it fails**

```bash
./mvnw -Dtest=GlobalExceptionHandlerRetryAfterTest test
```
Expected: FAIL — first test fails because the handler doesn't add the header yet.

- [ ] **Step 7: Update `GlobalExceptionHandler.handleApi` to emit `Retry-After`**

```java
@ExceptionHandler(ApiException.class)
public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
    log.debug("ApiException at {}: {} {}", req.getRequestURI(), ex.getCode(), ex.getMessage());
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getCode().getStatus());
    if (ex.getRetryAfterSeconds() != null) {
        builder.header("Retry-After", ex.getRetryAfterSeconds().toString());
    }
    return builder.body(ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
}
```

- [ ] **Step 8: Run all exception tests**

```bash
./mvnw -Dtest='nz.ac.waikato.campusmarketplace.exception.*Test' test
```
Expected: PASS.

- [ ] **Step 9: Run the full test suite**

```bash
./mvnw test
```
Expected: existing 36 tests + 4 new = 40 tests green.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/nz/ac/waikato/campusmarketplace/exception/ApiException.java \
        backend/src/main/java/nz/ac/waikato/campusmarketplace/exception/GlobalExceptionHandler.java \
        backend/src/test/java/nz/ac/waikato/campusmarketplace/exception/ApiExceptionRetryAfterTest.java \
        backend/src/test/java/nz/ac/waikato/campusmarketplace/exception/GlobalExceptionHandlerRetryAfterTest.java
git commit -m "feat(backend): ApiException supports retryAfterSeconds; handler emits Retry-After header"
```

---

### Task T2 — `RateLimitService.check()` returns `RateLimitDecision`; new `incrementBy(key, n, ttl)`

**Files:**
- Create: `backend/src/main/java/nz/ac/waikato/campusmarketplace/service/RateLimitDecision.java`
- Modify: `backend/src/main/java/nz/ac/waikato/campusmarketplace/service/RateLimitService.java`
- Modify: `backend/src/test/java/nz/ac/waikato/campusmarketplace/service/RateLimitServiceTest.java`

- [ ] **Step 1: Create `RateLimitDecision` record**

```java
package nz.ac.waikato.campusmarketplace.service;

public record RateLimitDecision(boolean exceeded, long retryAfterSeconds) {
    public static RateLimitDecision allowed() { return new RateLimitDecision(false, 0L); }
    public static RateLimitDecision blocked(long retryAfterSeconds) { return new RateLimitDecision(true, retryAfterSeconds); }
}
```

- [ ] **Step 2: Add 3 failing tests to `RateLimitServiceTest`**

(Append inside the existing test class — keeps the 2 existing tests untouched.)

```java
@Test
void checkReturnsAllowedDecisionWhenUnderLimit() {
    String key = "ratelimit:test:underlimit";
    rateLimit.increment(key, Duration.ofMinutes(5));
    RateLimitDecision d = rateLimit.check(key, 5, Duration.ofMinutes(5));
    assertThat(d.exceeded()).isFalse();
    assertThat(d.retryAfterSeconds()).isEqualTo(0L);
}

@Test
void checkReturnsBlockedDecisionWithRetryAfterWhenOver() {
    String key = "ratelimit:test:overlimit";
    for (int i = 0; i < 5; i++) rateLimit.increment(key, Duration.ofMinutes(15));
    RateLimitDecision d = rateLimit.check(key, 5, Duration.ofMinutes(15));
    assertThat(d.exceeded()).isTrue();
    assertThat(d.retryAfterSeconds()).isBetween(1L, 900L);
}

@Test
void incrementByAddsBytes() {
    String key = "ratelimit:test:bytes";
    rateLimit.incrementBy(key, 5_000_000L, Duration.ofHours(1));
    rateLimit.incrementBy(key, 3_000_000L, Duration.ofHours(1));
    assertThat(rateLimit.check(key, 50_000_000L, Duration.ofHours(1)).exceeded()).isFalse();
    rateLimit.incrementBy(key, 43_000_000L, Duration.ofHours(1));
    assertThat(rateLimit.check(key, 50_000_000L, Duration.ofHours(1)).exceeded()).isTrue();
}
```

- [ ] **Step 3: Run to confirm failures**

```bash
./mvnw -Dtest=RateLimitServiceTest test
```
Expected: FAIL — `check` and `incrementBy` don't exist yet.

- [ ] **Step 4: Replace `RateLimitService.java`**

```java
package nz.ac.waikato.campusmarketplace.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long increment(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count == null ? 0 : count;
    }

    public long incrementBy(String key, long delta, Duration window) {
        Long total = redis.opsForValue().increment(key, delta);
        if (total != null && total == delta) {
            redis.expire(key, window);
        }
        return total == null ? 0 : total;
    }

    /** TTL-aware decision API. */
    public RateLimitDecision check(String key, long limit, Duration window) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return RateLimitDecision.allowed();
        long current = Long.parseLong(raw);
        if (current < limit) return RateLimitDecision.allowed();
        Long ttlSeconds = redis.getExpire(key, TimeUnit.SECONDS);
        long retry = ttlSeconds == null || ttlSeconds <= 0 ? window.toSeconds() : ttlSeconds;
        return RateLimitDecision.blocked(retry);
    }

    /** Legacy boolean API — thin delegate. */
    public boolean exceeded(String key, long limit, Duration window) {
        return check(key, limit, window).exceeded();
    }
}
```

- [ ] **Step 5: Run rate-limit tests**

```bash
./mvnw -Dtest=RateLimitServiceTest test
```
Expected: PASS — 5 tests.

- [ ] **Step 6: Run full suite**

```bash
./mvnw test
```
Expected: 43 tests green (no auth-test regressions).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/nz/ac/waikato/campusmarketplace/service/RateLimitDecision.java \
        backend/src/main/java/nz/ac/waikato/campusmarketplace/service/RateLimitService.java \
        backend/src/test/java/nz/ac/waikato/campusmarketplace/service/RateLimitServiceTest.java
git commit -m "feat(backend): RateLimitService.check() returns Decision with TTL; add incrementBy for byte limits"
```

---

### Task T3 — `AuthService` register / login throw with `retryAfterSeconds`

**Files:**
- Modify: `backend/src/main/java/nz/ac/waikato/campusmarketplace/service/AuthService.java`
- Modify: `backend/src/test/java/nz/ac/waikato/campusmarketplace/service/AuthServiceLoginLogoutTest.java`
- Modify: `backend/src/test/java/nz/ac/waikato/campusmarketplace/service/AuthServiceRegisterTest.java`

- [ ] **Step 1: Update login test to assert retryAfterSeconds**

In `AuthServiceLoginLogoutTest`, replace the existing TOO_MANY_ATTEMPTS test with:

```java
@Test
void loginRateLimitBlocksAndIncludesRetryAfter() {
    when(rateLimit.check(eq("ratelimit:login:" + EMAIL), eq(5L), any()))
        .thenReturn(RateLimitDecision.blocked(900L));

    assertThatThrownBy(() -> svc.login(EMAIL, "anything"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
            assertThat(api.getRetryAfterSeconds()).isEqualTo(900L);
        });
}
```

- [ ] **Step 2: Same change for register test**

```java
@Test
void registerRateLimitBlocksAndIncludesRetryAfter() {
    when(rateLimit.check(eq("ratelimit:register:" + IP), eq(3L), any()))
        .thenReturn(RateLimitDecision.blocked(3600L));

    assertThatThrownBy(() -> svc.register("alice@students.waikato.ac.nz", "Pass1234", "Alice", IP))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.getCode()).isEqualTo(ErrorCode.TOO_MANY_REGISTRATIONS);
            assertThat(api.getRetryAfterSeconds()).isEqualTo(3600L);
        });
}
```

- [ ] **Step 3: Run to confirm failures**

```bash
./mvnw -Dtest='AuthServiceLoginLogoutTest,AuthServiceRegisterTest' test
```
Expected: FAIL — production still uses old boolean API.

- [ ] **Step 4: Update `AuthService.register` rate-limit branch**

Replace:
```java
if (rateLimit.exceeded("ratelimit:register:" + ip, 3, Duration.ofHours(1))) {
    throw new ApiException(ErrorCode.TOO_MANY_REGISTRATIONS,
            "Too many registrations from your network. Try again later.");
}
```
With:
```java
RateLimitDecision regDecision = rateLimit.check("ratelimit:register:" + ip, 3, Duration.ofHours(1));
if (regDecision.exceeded()) {
    throw new ApiException(ErrorCode.TOO_MANY_REGISTRATIONS,
            "Too many registrations from your network. Try again later.",
            regDecision.retryAfterSeconds());
}
```

- [ ] **Step 5: Update `AuthService.login` rate-limit branch**

Replace:
```java
String key = "ratelimit:login:" + email;
if (rateLimit.exceeded(key, 5, Duration.ofMinutes(15))) {
    throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
            "Too many attempts. Try again in 15 minutes.");
}
```
With:
```java
String key = "ratelimit:login:" + email;
RateLimitDecision loginDecision = rateLimit.check(key, 5, Duration.ofMinutes(15));
if (loginDecision.exceeded()) {
    throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
            "Too many attempts. Try again in 15 minutes.",
            loginDecision.retryAfterSeconds());
}
```

Add `import nz.ac.waikato.campusmarketplace.service.RateLimitDecision;` if needed (same package so likely not).

- [ ] **Step 6: Run auth tests**

```bash
./mvnw -Dtest='AuthServiceLoginLogoutTest,AuthServiceRegisterTest' test
```
Expected: PASS.

- [ ] **Step 7: Run full suite**

```bash
./mvnw test
```
Expected: 43+ tests green.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/nz/ac/waikato/campusmarketplace/service/AuthService.java \
        backend/src/test/java/nz/ac/waikato/campusmarketplace/service/AuthServiceLoginLogoutTest.java \
        backend/src/test/java/nz/ac/waikato/campusmarketplace/service/AuthServiceRegisterTest.java
git commit -m "feat(backend): AuthService throws TOO_MANY_ATTEMPTS/REGISTRATIONS with retryAfterSeconds from RateLimitDecision"
```

---

### Task T4 — Integration test: login rate-limit returns 429 + `Retry-After` header

**Files:**
- Modify: `backend/src/test/java/nz/ac/waikato/campusmarketplace/controller/AuthControllerIntegrationTest.java`
- Modify: `docs/engineering-journal.md`

- [ ] **Step 1: Add integration test**

Append to `AuthControllerIntegrationTest`:

```java
@Test
@DisplayName("login: rate limit returns 429 with Retry-After header")
void loginRateLimitReturns429WithRetryAfter() {
    registerTestUser("ratelimit-target@students.waikato.ac.nz", "Pass1234", "RLTarget");

    for (int i = 0; i < 5; i++) {
        ResponseEntity<String> r = postJson("/api/auth/login",
                Map.of("email", "ratelimit-target@students.waikato.ac.nz",
                       "password", "WrongPass" + i));
        assertThat(r.getStatusCode().value()).isEqualTo(401);
    }

    ResponseEntity<String> blocked = postJson("/api/auth/login",
            Map.of("email", "ratelimit-target@students.waikato.ac.nz",
                   "password", "WrongPass6"));

    assertThat(blocked.getStatusCode().value()).isEqualTo(429);
    String retryAfter = blocked.getHeaders().getFirst("Retry-After");
    assertThat(retryAfter).isNotNull();
    long seconds = Long.parseLong(retryAfter);
    assertThat(seconds).isBetween(1L, 900L);
    assertThat(blocked.getBody()).contains("TOO_MANY_ATTEMPTS");
}
```

> If helpers `registerTestUser` / `postJson` don't exist with these signatures, read the existing integration test and adapt to the existing style.

- [ ] **Step 2: Run the test alone**

```bash
./mvnw -Dtest=AuthControllerIntegrationTest#loginRateLimitReturns429WithRetryAfter test
```
Expected: PASS.

- [ ] **Step 3: Run full integration test class**

```bash
./mvnw -Dtest=AuthControllerIntegrationTest test
```
Expected: 9 tests green (8 existing + 1 new).

- [ ] **Step 4: Run the entire suite**

```bash
./mvnw test
```
Expected: 44+ tests green.

- [ ] **Step 5: Append D-38 to engineering journal**

```markdown
### D-38 — `Retry-After` header on rate-limit responses + TTL-aware `RateLimitService.check`

**Date / where** Epic 2 Phase 0, 2026-05-14
**Choice** `ApiException` carries an optional `retryAfterSeconds`. `GlobalExceptionHandler` emits the `Retry-After` HTTP header when present. `RateLimitService.check()` returns a `RateLimitDecision(boolean exceeded, long retryAfterSeconds)` record so callers can populate the field.
**Why** Industry standard for `429 Too Many Requests` is to include `Retry-After` so clients can implement intelligent retry instead of polling blind. The TTL-aware decision is the cheapest way to surface this — Redis already tracks the key TTL via `EXPIRE`; one `TTL` call recovers it.
**Trade-off accepted** The legacy boolean `exceeded()` is retained as a thin delegate so older call sites keep compiling. Slated for removal once all consumers move to `check()`.

> 💡 中文要点：限流响应不只返回 429，还要给客户端 `Retry-After` 头告诉它"还有多少秒可以再来"。`RateLimitService.check()` 拿 Redis 的 TTL 当 retry-after，Epic 1 已经有 EXPIRE 写入所以 0 额外成本。前端可据此做指数退避——工业标准做法。
```

Bump the journal's closing line:
```markdown
*Last updated: 2026-05-14 — Epic 2 Phase 0 complete (D-38). Retry-After header now emitted on rate-limit responses.*
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/nz/ac/waikato/campusmarketplace/controller/AuthControllerIntegrationTest.java \
        docs/engineering-journal.md
git commit -m "test(backend): verify login rate limit returns 429 + Retry-After; journal D-38"
```

**Demo milestone #5 — your turn:**
Start the backend (`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`) and use Postman to:
1. Register a user.
2. POST `/api/auth/login` with wrong password — repeat **6 times**.
3. On the 6th response, verify:
   - Status: **429 Too Many Requests**
   - Header: **`Retry-After: <seconds>`** (some number 1–900)
   - Body: `{"code":"TOO_MANY_ATTEMPTS","message":"Too many attempts. Try again in 15 minutes."}`

---

## Phase 1 — Backend listing CRUD (no image yet)

### Task T5 — DB migration: `categories` table + 8 seed rows
### Task T6 — `Category` JPA entity + `CategoryRepository`
### Task T7 — `GET /api/categories` endpoint + unit + integration test
### Task T8 — DB migration: `listings` table + 4 indexes
### Task T9 — `Listing` JPA entity + 3 enums (`ListingStatus`, `ListingType`, `Condition`)
### Task T10 — `ListingRepository` + custom queries (`findByOwner`, `findByImagePath`, `Pageable` queries)
### Task T11 — DTOs: `CreateListingRequest`, `UpdateListingRequest`, `ChangeStatusRequest`, `ListingResponse`, `ListingSummary`, `PagedListings`
### Task T12 — `ListingService.create` + 8 unit tests
### Task T13 — `ListingService.update` + 7 unit tests
### Task T14 — `ListingService.changeStatus` + FSM table + 8 unit tests
### Task T15 — `ListingService.listMine` + `getOne` + `remove` + 7 unit tests
### Task T16 — `ListingController` 5 endpoints (no image yet) + Security config update

*All tasks above to be expanded with TDD steps, exact file paths, code blocks, run commands, and commit messages.*

**Demo milestone #6:** Postman walks the 5 endpoints — create (no image yet), list, get one, change status (all 7 legal transitions), delete.

---

## Phase 2 — Image upload + rate limits

### Task T17 — `ImageStorageService` interface + `LocalImageStorageService` implementation (5-step validation, magic-number, UUID filenames, traversal defenses)
### Task T18 — `LocalImageStorageServiceTest` — unit + component tests covering all validation branches
### Task T19 — Upgrade `ListingController` POST/PUT to multipart; wire image storage
### Task T20 — `GET /api/uploads/listings/{filename}` — owner check, cache headers, traversal guard
### Task T21 — Wire 5 rate-limit checks into write endpoints (create / update / status / delete / image-bytes)

*Detailed steps to be expanded.*

**Demo milestone #7:** Real image uploaded via Postman; browser GET renders the image; 21 consecutive create requests trigger `429`.

---

## Phase 3 — Backend integration tests + journal

### Task T22 — `ListingControllerIntegrationTest` first batch — create / list / get / update (8 tests)
### Task T23 — `ListingControllerIntegrationTest` second batch — status / delete / image / categories (10 tests)
### Task T24 — Engineering journal append D-38..D-50 + Phase A retrospective

*Detailed steps to be expanded.*

**Demo milestone #8:** `./mvnw test` reports 60+ tests passing.

---

## Phase 4 — Frontend infrastructure + shared components

### Task T25 — Install Vitest + React Testing Library + MSW + Playwright; create config files and a smoke test
### Task T26 — Extend `apiClient` with `postForm` / `putForm` for multipart + unit tests
### Task T27 — `api/listings.ts` + `api/categories.ts` — types and 7 endpoint wrappers + unit tests
### Task T28 — `validateImageClientSide` helper + `i18n/listings.ts` copy table + unit tests
### Task T29 — `StatusBadge` + `Spinner` + `EmptyState` + `Pagination` shared components + component tests
### Task T30 — `ImagePicker` component (5 MB / MIME pre-check, thumbnail, replace) + component tests
### Task T31 — `ConfirmDialog` shared component (focus trap, Esc, a11y) + component tests
### Task T32 — `useOptimisticStatus` + `useListings` hooks + unit tests

*Detailed steps to be expanded.*

---

## Phase 5 — Frontend pages

### Task T33 — `ListingForm` shared component (Create / Edit / readonly modes, validation, dirty-detection, unsaved-changes guard) + component tests
### Task T34 — `ListingCard` (4-status quick-action sets, optimistic update via `useOptimisticStatus`) + component tests
### Task T35 — `CreateListingPage` + component test + route wiring
### Task T36 — `MyListingsPage` (loading / empty / error / items, paging, filtering) + component test + route wiring
### Task T37 — `ListingDetailPage` (owner read-only view, large image, status toolbar) + component test + route wiring
### Task T38 — `EditListingPage` (prefill via `getOne`, REMOVED read-only banner) + component test + route wiring
### Task T39 — `Navbar` add "My Listings" link; full route assembly in `App.tsx`
### Task T40 — Frontend component-test review + coverage report generation

*Detailed steps to be expanded.*

**Demo milestone #9 (key):** Full browser flow — register → login → create listing with image → My Listings → detail → edit → status changes → delete. This is the supervisor demo for Epic 2.

---

## Phase 6 — E2E + documentation

### Task T41 — Playwright specs: `create-flow` + `edit-flow` + `status-flow`
### Task T42 — Playwright specs: `permission` (IDOR) + `image-leak` (§7.5)
### Task T43 — `docs/manual-e2e-epic2.md` mirroring Epic 1's style
### Task T44 — Engineering journal close-out + ROADMAP / dev-roadmap.md mark M3 complete + prepare merge to main

*Detailed steps to be expanded.*

**Demo milestone #10 (sealing):** `npm run e2e` — all 5 Playwright specs green; manual checklist passes. Epic 2 sealed and ready to merge into main.

---

## Notes for the implementer

- **TDD throughout** — every backend service test is written *before* the implementation. The test runs, fails red, then the implementation is added until it passes green. Commit at green.
- **One commit per task** — keep `git log --oneline` readable as a project narrative; matches Epic 1's cadence (24 task-shaped commits per Epic 1).
- **Engineering journal** — append a new `D-N` decision entry whenever a non-obvious choice is made (Epic 1 collected D-1..D-37; Epic 2 starts at D-38). Bump the journal pointer commit after the task that produced new entries.
- **Demo milestones** — at each milestone, hand control to the user for the manual demo (per `feedback_handson_demo.md`). Don't auto-run Postman / browser tests at these checkpoints.
- **Comprehensive over minimal** — when in doubt, choose the more complete option (per `feedback_prefer_complete.md`).

## Plan rollout strategy

This file currently contains the **task outline** for all 44 tasks. Each task body will be expanded one phase at a time, in dialogue with the user, before that phase's implementation begins. The expansion fills in: exact file paths, full code blocks for every step, exact run commands with expected output, and explicit commit messages.

Expansion order: Phase 0 → user reviews → Phase 0 implementation → Phase 1 expansion → Phase 1 implementation → ... and so on.
