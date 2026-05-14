# Engineering Journal — Campus Secondhand Marketplace

> A running record of problems hit, decisions made, tools chosen, and skills picked up across the project. Used as raw material for the final thesis report and for personal review.
>
> **Format**: English narrative + a one-line Chinese takeaway (`💡 中文要点：…`) for quick scanning when writing the report.
>
> **Scope rule**: Only entries the author can recall or reconstruct from durable evidence (git, code, docs) are recorded. We do not invent details we cannot verify.

---

## Table of Contents

1. [Problem Log](#1-problem-log)
2. [Decision Log](#2-decision-log)
3. [Tooling & Dependencies](#3-tooling--dependencies)
4. [Lessons Learned & Skills Acquired](#4-lessons-learned--skills-acquired)

---

## 1. Problem Log

### Problem #1 — Testcontainers cannot talk to Docker Engine 29

| Field | Value |
|---|---|
| **Date** | 2026-05-08 |
| **Task** | Task 6 — RateLimitService (RED → GREEN with Testcontainers Redis) |
| **Severity** | Blocker — no Redis-backed test could run |
| **Time spent** | ~25 min from first failure to green build |

**Symptom**

Running `./mvnw -Dtest=RateLimitServiceTest test` failed during the `@BeforeAll` phase with:

```
Could not find a valid Docker environment. Please check configuration.
Attempted configurations were:
  UnixSocketClientProviderStrategy: failed with exception BadRequestException
    (Status 400: {"message":"client version 1.32 is too old.
     Minimum supported API version is 1.40, please upgrade your client to a newer version"})
  DockerDesktopClientProviderStrategy: failed with NullPointerException
```

**Initial misreading**

The string `client version 1.32` looks like a Docker version number. It is not — it is the Docker REST API protocol version. This caused a brief detour considering whether to upgrade the host Docker installation, which would not have helped.

**Hypotheses tested (in order)**

| # | Hypothesis | Evidence checked | Result |
|---|---|---|---|
| 1 | User not in `docker` group → no socket permission | `groups $USER` | ✗ User is in `docker` group |
| 2 | Polluted environment forces wrong API version | `env \| grep -i docker` | ✗ No relevant env vars |
| 3 | Stale `~/.docker/config.json` | `cat ~/.docker/config.json` | ✗ File absent |
| 4 | Testcontainers 1.20.6 too old | Bumped to 1.21.3 via BOM, re-ran | ✗ Same error — internal `docker-java` only moved 3.4.1 → 3.4.2 |
| 5 | `docker-java` defaults to API version 1.32 | `DOCKER_API_VERSION=1.40 ./mvnw …` | ✓ **Tests passed** |

**Root cause**

`docker-java` (the HTTP client used inside Testcontainers) negotiates an API version with the Docker daemon at startup. In versions 3.4.x its default fallback is **API 1.32**. Docker Engine 29 raised the minimum supported API version to **1.40**, so the negotiation is rejected before any container can be started. The host Docker version (29.4.3, May 2026) was already current; the problem was entirely inside the Java client.

**Fix**

In `backend/pom.xml`:

1. Imported `testcontainers-bom:1.21.3` in `<dependencyManagement>` (version hygiene — not the root cause but worth pinning).
2. Added a `maven-surefire-plugin` configuration that sets the `api.version` system property to `1.40` for the test JVM:

   ```xml
   <systemPropertyVariables>
       <api.version>1.40</api.version>
   </systemPropertyVariables>
   ```

After this change `./mvnw -Dtest=RateLimitServiceTest test` reports `Tests run: 2, Failures: 0, Errors: 0`.

**Why the fix works**

`docker-java` reads the `api.version` system property at handshake time. Setting it to `1.40` (the minimum the daemon now supports) makes the protocol negotiation succeed without forcing every developer to set an environment variable.

**Lessons**

- An error message that *looks* like it's about a tool's version may actually be about a protocol version. Verify before reaching for an upgrade.
- Hierarchical dependency problems: a transitive dep (`docker-java`) buried inside a transitive dep (`testcontainers`) is invisible until you run `mvn dependency:tree`. Always do that before forming a hypothesis.
- A test-only fix belongs in the surefire plugin, not in production code or in `application-*.properties`.

> 💡 中文要点：错误里的 `version 1.32` 是 Docker API **协议号**，不是 Docker 版本。真凶是 `docker-java 3.4.x` 默认握手用 API 1.32，而 Docker Engine 29 起最低要求 1.40。修法：在 `maven-surefire-plugin` 里钉 `api.version=1.40`，**不要**升级机器上的 Docker。

---

### Problem #2 — `AuthService` startup fails: "no qualifying bean of type `String`"

| Field | Value |
|---|---|
| **Date** | 2026-05-08 |
| **Task** | Task 13 — first time the application is fully wired and started end-to-end |
| **Severity** | Blocker — application context refused to start |
| **Time spent** | ~3 min from error to green start (the fix was a one-line annotation) |

**Symptom**

`./mvnw spring-boot:run` failed with:

```
APPLICATION FAILED TO START
Description:
  Parameter 5 of constructor in nz.ac.waikato.campusmarketplace.service.AuthService
  required a bean of type 'java.lang.String' that could not be found.
Action:
  Consider defining a bean of type 'java.lang.String' in your configuration.
```

The 25 unit tests had been passing the entire time, because in tests the constructor was called manually with a literal string for `emailBaseUrl` (e.g. `"http://localhost:5173"`).

**Root cause**

The `AuthService` constructor declares `String emailBaseUrl` without any annotation. In tests we passed a literal; in production Spring's autowiring saw a constructor parameter of type `String` and tried to find a bean of type `String` to inject — there is no such bean (and conventionally never should be), hence the failure. The plan document called for `@Value("${app.email.base-url}")` on this parameter, but the annotation was missed when the constructor was first written in Task 7.

**Fix**

One-line change in `AuthService.java`:

```java
public AuthService(...,
                   EmailService email,
                   @Value("${app.email.base-url}") String emailBaseUrl) {
```

Plus the `import org.springframework.beans.factory.annotation.Value;`. The application started cleanly afterwards. All 25 unit tests still pass — they were never affected because they bypass Spring autowiring.

**Lessons**

- A unit-test green-light does *not* mean the application can start. Tests that construct the service directly skip Spring's wiring; the first end-to-end start is when missing `@Value` / `@Qualifier` annotations show up.
- Spring's failure message on missing string injection is excellent: it names the offending parameter and class precisely. Reading those two lines is faster than guessing.
- The matching `@Value("${app.email.base-url}")` had been present in the plan all along; this was a transcription miss, not a design issue. Always cross-check copied code against the source.

> 💡 中文要点：Spring 报"找不到 `String` 类型的 bean"，原因是 `AuthService` 构造函数里的 `String emailBaseUrl` 没加 `@Value("${app.email.base-url}")`。单测用字面量 mock 不会触发，但 Spring 启动时要真的注入。一行注解修好。**单测全绿不代表能启动 —— 单测不走 Spring 装配。**

---

## 2. Decision Log

These are deliberate design choices, not bugs. Each decision shaped the codebase and is worth defending in the thesis "design choices" section.

### D-1 — JWT in HttpOnly cookie, not session, not localStorage

**Date / where** Epic 1 auth design spec, 2026-05-08
**Choice** Stateless JWT (HS256) stored in an HttpOnly cookie.
**Alternatives considered** Server-side sessions (Spring Session + Redis); JWT in `localStorage`.
**Rationale**
- *Server-side sessions* would require Redis to be on the request path of every authenticated call. The MVP runs on a single small VPS; the simpler stateless model fits the deployment shape.
- *JWT in localStorage* exposes the token to any XSS payload that runs in the page. HttpOnly cookies are not reachable from JavaScript.
**Trade-offs accepted** We need a token blacklist (Redis) for logout, because stateless JWTs cannot truly be "destroyed". This is implemented via the `jti` claim and a Redis key with TTL = remaining token lifetime.

> 💡 中文要点：用 JWT + HttpOnly Cookie，不用 Session（部署简单）也不用 localStorage（防 XSS 偷 token）。代价是登出要靠 Redis 黑名单。

---

### D-2 — BCrypt for password hashing

**Date / where** Epic 1 auth design spec, 2026-05-08
**Choice** BCrypt with default cost factor (10).
**Alternatives considered** Argon2id (modern winner of password hashing competition); PBKDF2.
**Rationale** BCrypt has first-class Spring Security support (`PasswordEncoder` bean is `BCryptPasswordEncoder` by default). The user count for this app is hundreds, not millions; the marginal security gain of Argon2id does not justify pulling in a non-default encoder for a thesis MVP.
**Trade-offs accepted** Hash output is a fixed 60 characters → `password VARCHAR(60)` in the schema. If we ever migrate to Argon2id, the column needs widening.

> 💡 中文要点：选 BCrypt 不选 Argon2id，因为 Spring Security 默认支持，对 MVP 体量足够。

---

### D-3 — Rate limit via Redis counters (not a library)

**Date / where** Epic 1 auth design spec → Task 6
**Choice** Custom `RateLimitService` using `INCR` + `EXPIRE` on Redis keys.
**Alternatives considered** Bucket4j, Resilience4j RateLimiter, Spring Cloud Gateway rate-limit filter.
**Rationale** Two operations (`increment`, `exceeded`) are all the auth flow needs. Pulling in a full library to wrap two Redis commands adds dependencies and learning surface for no real benefit. The naive counter is also easier to defend in a thesis than a black-box library.
**Trade-offs accepted** No sliding-window or token-bucket smoothing — bursts can flush right at the window boundary. For login brute-force this is fine because the limit (5 fails / 15 min) is generous to legitimate users.

> 💡 中文要点：手写两行 Redis 计数比引一个限流库更简单可控，给 MVP 足够。

---

### D-4 — Email: interface + two implementations switched by Spring profile

**Date / where** Task 5
**Choice** `EmailService` interface with `ConsoleEmailService` (dev) and `SmtpEmailService` (prod), selected by `@ConditionalOnProperty(name="app.email.mode")`.
**Alternatives considered** Always SMTP, even in dev (with Mailtrap or similar); always log-only, with manual switch in prod.
**Rationale** Dev should never accidentally send real email (e.g. to a typo'd test address). `matchIfMissing=true` on the console implementation makes "console mode" the safe default — forgetting to set `app.email.mode` will not cause a spam incident.

> 💡 中文要点：开发环境邮件打印到控制台，生产用真 SMTP，由配置项决定加载哪个 Bean。开发不会误发邮件。

---

### D-5 — Testcontainers (real services in tests), not mocks

**Date / where** Backend dependency setup commit `95bbe2b`, used from Task 6 onward
**Choice** Tests that touch Redis or MySQL spin up the real service in a Docker container per test class.
**Alternatives considered** Mock the `StringRedisTemplate` and `JpaRepository` interfaces with Mockito.
**Rationale** Mocking Redis or JPA tests the contract *we wrote*, not the contract Redis or MySQL actually has. Bugs in `EXPIRE` semantics, JPA flush timing, MySQL collation — none are caught by mocks.
**Trade-offs accepted** First test run pulls Docker images (~30 s overhead). Tests cannot run in environments without Docker (acceptable for this project; Docker is already required for `infra/docker-compose.yml`).

> 💡 中文要点：测试连真的 Redis/MySQL（用 Testcontainers），不 mock。代价是要 Docker；收益是测出真实行为。

---

### D-6 — Campus email enforced at the application layer, not via OAuth

**Date / where** Epic 1 auth design spec
**Choice** Reject any email whose suffix is not `@students.waikato.ac.nz` during registration.
**Alternatives considered** Microsoft / Google SSO restricted to the Waikato tenant.
**Rationale** SSO requires a registered application with the university's IT department, which is not in scope for a thesis project. Suffix check is good enough as a "soft" gate; the forgot-password flow doubles as a real-email proof because a non-existent mailbox cannot receive the reset link.
**Trade-offs accepted** Anyone who can guess the suffix and supply a fake address gets through registration; the password reset flow is the actual liveness test.

> 💡 中文要点：用邮箱后缀做校园身份限制（写死 `@students.waikato.ac.nz`），不接 SSO（要找 IT 申请，超出毕设范围）。忘密功能事实上充当真邮箱验证。

---

### D-7 — Spring profile-driven configuration (`dev` vs `prod`)

**Date / where** Backend setup commit `95bbe2b`, Task 1
**Choice** Three property files: `application.properties` (shared), `application-dev.properties` (defaults for local), `application-prod.properties` (production).
**Rationale** Single source of truth per environment, with the active profile chosen by `SPRING_PROFILES_ACTIVE`. No conditionals scattered in code.

> 💡 中文要点：dev/prod 用不同的 properties 文件分开，由环境变量切换，避免代码里写 if/else。

---

### D-8 — Forgot-password reset token: opaque, single-use, 30-min TTL

**Date / where** Epic 1 auth design spec
**Choice** Cryptographically random token stored in Redis (key = token, value = userId, TTL = 30 min). Used once → key deleted.
**Alternatives considered** JWT-based reset token (self-contained, no Redis needed).
**Rationale** Single-use semantics are trivial in Redis (`DEL` after consume) and impossible in a stateless JWT (JWT is valid until expiry; you'd need a blacklist anyway). Since we *already* have Redis for the rate limiter and JWT logout blacklist, reusing it costs nothing.

> 💡 中文要点：忘密 token 存 Redis，一次性 + 30 分钟过期，不用 JWT（JWT 不能"用一次就废"）。

---

### D-9 — Pass IP into the service layer rather than reading it inside

**Date / where** Task 7 — `AuthService.register(rawEmail, password, nickname, ip)`
**Choice** The caller (controller) extracts the client IP from the HTTP request and passes it to the service as a plain `String` argument.
**Alternatives considered** Inject `HttpServletRequest` into the service and read `getRemoteAddr()` there.
**Rationale** The service has no other reason to know about HTTP. Pushing IP extraction into the controller keeps the service unit-testable without a mock servlet request — see how `AuthServiceRegisterTest` constructs the service with plain strings and zero web-layer dependencies.

> 💡 中文要点：IP 由 Controller 抽出来传给 Service，Service 不感知 HTTP。这样测试不用 mock Servlet 对象，直接传字符串。

---

### D-10 — Validate input inside the service, not only with `@Valid` on DTOs

**Date / where** Task 7 — `validateEmail / validatePassword / validateNickname` in `AuthService`
**Choice** The service runs its own validation (regex for email domain, length checks, etc.) even though Bean Validation annotations on the request DTO will also fire.
**Alternatives considered** Validate only at the DTO boundary.
**Rationale** Two reasons. First, the service might be called from places other than the HTTP controller (a CLI seeder, a test fixture, an internal job) — the DTO validator would not run there. Second, the rules ("email must end with `@students.waikato.ac.nz`", "password must contain a digit") are *business* rules, not data-format rules; they belong in the layer that owns the business logic.
**Trade-offs accepted** Minor duplication between DTO annotations (Task 13) and service checks. Worth it for defence-in-depth.

> 💡 中文要点：业务校验放在 Service 里（不只放在 DTO 注解上），因为 Service 可能被非 HTTP 入口调用，且业务规则本来就属于 Service 层。

---

### D-11 — Identical error message for "unknown email" and "wrong password"

**Date / where** Task 8 — `AuthService.login`
**Choice** Both branches throw `ApiException(BAD_CREDENTIALS, "Email or password is incorrect.")`.
**Alternatives considered** Tell the user specifically which field is wrong.
**Rationale** Distinct messages let an attacker enumerate which emails are registered (by observing whether the response says "wrong password" or "no such user"). One generic message keeps account existence private.
**Trade-offs accepted** Slightly less helpful UX for a legitimate user who typoed their email — they don't get told to check spelling.

> 💡 中文要点：邮箱不存在 vs 密码错误，返回**同一句话**。否则攻击者可以靠错误信息枚举哪些邮箱已注册。

---

### D-12 — Setter-injected `StringRedisTemplate` (instead of constructor-injected)

**Date / where** Task 8 — `AuthService.setRedis(...)` annotated `@Autowired(required = false)`
**Choice** Redis is injected via a setter, optional, rather than added to the constructor.
**Alternatives considered** Add `StringRedisTemplate` as a 7th constructor argument (alongside `users`, `encoder`, `rateLimit`, `jwt`, `email`, `emailBaseUrl`).
**Rationale** Two reasons. First, `register()` (Task 7) does not need Redis at all — making it constructor-mandatory would force every `AuthService` test (including the seven register tests) to mock Redis even when irrelevant. Second, with `required = false`, unit tests can construct the service with `null` redis and the `logout`/`isBlacklisted` methods short-circuit safely. In production, Spring auto-wires the bean.
**Trade-offs accepted** Setter injection is generally less preferred than constructor injection (mutability, less obvious dependencies). Here the trade-off favors test ergonomics for an optional collaborator.

> 💡 中文要点：Redis 用 setter 注入而不是构造函数，因为 `register` 用不上它。这样老的 register 测试不用 mock Redis；生产环境 Spring 仍会自动注入。

---

### D-13 — JWT logout via Redis blacklist with TTL = remaining token life

**Date / where** Task 8 — `AuthService.logout`
**Choice** On logout, write `jwt:blacklist:<jti>` to Redis with TTL equal to the token's remaining lifetime (computed at logout time). Future requests check this key.
**Alternatives considered**
- Maintain a server-side session and invalidate it (defeats the point of stateless JWT).
- Issue a new short-lived "access token" + long-lived "refresh token", revoke refresh on logout (more moving parts than the MVP needs).
**Rationale** The `jti` (JWT ID) claim makes each token uniquely identifiable. A Redis key with TTL set to the *remaining* lifetime self-cleans — no janitor job needed; once the token would have expired anyway, the blacklist entry vanishes, keeping Redis small.
**Trade-offs accepted** Logout requires Redis to be available. If Redis is down, logout silently no-ops (the early-return on `redis == null`). Acceptable because the token will still expire on its own.

> 💡 中文要点：登出 = 把 token 的 `jti` 写进 Redis 黑名单，TTL 等于这个 token 还能活多久。token 过期前黑名单也跟着过期，自动清理。Redis 挂了登出会失败，但 token 反正会过期。

---

### D-14 — `forgotPassword` is silent on unknown email

**Date / where** Task 10 — `AuthService.forgotPassword`
**Choice** When the email is not registered, the method **returns successfully** without sending an email or writing to Redis. The HTTP response is the same as for a registered email.
**Alternatives considered** Tell the caller "no such account".
**Rationale** Same threat model as the login error message (D-11): a different response would let an attacker enumerate which emails are registered. The endpoint behaves indistinguishably for registered and unregistered addresses.
**Trade-offs accepted** A user who typoed their email gets no feedback that the typo happened — they just don't receive an email. The reset page should hint at this in copy.

> 💡 中文要点：忘密接口对"邮箱不存在"**默默成功**（不报错也不发邮件）。否则就能用这个接口枚举哪些邮箱已注册。代价是用户打错邮箱时不会被提示。

---

### D-15 — Reset token: 384 bits of randomness, URL-safe Base64, 30-min TTL, single use

**Date / where** Task 10 — `forgotPassword` / `resetPassword`
**Choice** `SecureRandom.nextBytes(48)` → URL-safe Base64 without padding → stored in Redis under `auth:reset:<token>` with TTL 30 minutes; `resetPassword` deletes the key on first successful use.
**Alternatives considered**
- Shorter token (16 bytes / 128 bits) — would still be uncrackable but offers no real benefit at modern key sizes.
- Numeric OTP — requires a second factor (phone) we don't have.
- Self-contained JWT reset token — can't be revoked on first use without a blacklist anyway, so no win over Redis (see D-8).
**Rationale**
- 48 bytes (384 bits) is conventional overkill — guessing one is computationally infeasible, and the TTL caps the attack window anyway.
- URL-safe Base64 (`-_` instead of `+/`) means the token slots into a query string without escaping.
- `redis.delete(key)` after a successful reset is the "single-use" enforcement — the same link can't be replayed.

> 💡 中文要点：重置 token = 48 字节随机数 → URL-safe Base64 → 存 Redis（30 分钟过期 + 用一次就删）。三道闸门：随机性大到猜不中、过期时间短、一次性删除。

---

### D-16 — `validateEmail` is **not** called in `forgotPassword`

**Date / where** Task 10 — observed during implementation
**Choice** `forgotPassword` lower-cases the input and tries `findByEmail` directly, without running the `@students.waikato.ac.nz` regex check.
**Alternatives considered** Validate the suffix first and throw `INVALID_EMAIL` for other domains.
**Rationale** Validating would leak information: a non-Waikato email would get a clear "invalid email" error, while a non-existent Waikato email would get the silent success from D-14. An attacker comparing the two could infer the suffix policy. By treating any well-formed email the same way (look it up, do nothing if not found), the endpoint reveals nothing about who is or isn't a user.
**Trade-offs accepted** Slightly counter-intuitive — usually we validate input early. Here the *security* requirement overrides the usual hygiene.

> 💡 中文要点：忘密接口**不**校验邮箱后缀，因为校验会泄露信息（"非校园邮箱被拒"和"邮箱不存在被默默成功"是两种不同回应）。统一都默默处理，攻击者就什么都问不出来。

---

### D-17 — `OncePerRequestFilter` instead of plain `Filter`

**Date / where** Task 11 — `JwtAuthenticationFilter`
**Choice** Extend Spring's `OncePerRequestFilter` rather than implement `jakarta.servlet.Filter` directly.
**Alternatives considered** Implement the raw `Filter` interface; intercept at the controller level via a `HandlerInterceptor`.
**Rationale** A single HTTP request can pass through the filter chain more than once in some Spring scenarios (forwards, error dispatches, async). `OncePerRequestFilter` guarantees the filter body runs **at most once per request**, which avoids double-parsing the token, double-writing the SecurityContext, etc. The base class also gives us the typed `HttpServletRequest`/`HttpServletResponse` instead of the raw `ServletRequest`.
**Trade-offs accepted** None significant for our use case; this is the textbook recommendation for auth filters in Spring.

> 💡 中文要点：用 Spring 的 `OncePerRequestFilter` 不用裸 `Filter`，因为同一个请求可能被分发多次（forward、error、async），基类保证我们这段逻辑**每个请求只跑一次**，不会重复解析 token。

---

### D-18 — Bad/expired token = empty SecurityContext, not an exception

**Date / where** Task 11 — `catch (JwtException ignored)` branch
**Choice** When the cookie contains a token that is malformed, expired, or tampered with, we silently swallow the parse exception and leave `SecurityContext` empty. The request continues down the filter chain as if no token were present.
**Alternatives considered** Return 401 directly from the filter; clear the cookie via `Set-Cookie`.
**Rationale** Authorisation decisions belong to Spring Security and the controller layer, not to the auth filter. The filter's job is "if there's a valid token, mark the request as authenticated; otherwise do nothing". Public endpoints (`/auth/register`, `/auth/login`, the health check) should still work even with a stale cookie. Spring Security will return 401 automatically on protected endpoints when no `Authentication` is present.
**Trade-offs accepted** The user will see a 401 the next time they hit a protected endpoint and have to re-login, rather than getting an immediate 401 the moment their token expires. This is normal JWT UX.

> 💡 中文要点：Token 解析失败时**不要**直接返回 401。Filter 的职责是"如果 token 有效就标记为已登录"，鉴权决策交给 Spring Security 和 Controller。这样公开接口（注册、登录）即使带着坏 cookie 也能照常工作。

---

### D-19 — Disable CSRF (justified by SameSite=Lax + HttpOnly cookie)

**Date / where** Task 12 — `SecurityConfig` calls `.csrf(AbstractHttpConfigurer::disable)`
**Choice** Turn off Spring Security's CSRF protection.
**Alternatives considered** Keep CSRF enabled with the standard double-submit token; switch to header-based JWT (`Authorization: Bearer …`) which makes CSRF moot.
**Rationale** CSRF protection exists because a browser will *automatically* attach cookies to cross-site requests, letting attacker.com trick the user's browser into making authenticated requests to our site. Two layers we already have neutralise this:
1. **`SameSite=Lax`** on the auth cookie — modern browsers refuse to send the cookie on cross-site POSTs (Lax allows top-level GET navigations only).
2. **`HttpOnly`** on the auth cookie — JavaScript on a malicious page can't even read the token to forge a request.
Combined, these make the classic CSRF attack vector fail before it reaches our backend. Adding a CSRF token on top would be belt-and-braces but adds frontend complexity for marginal gain.
**Trade-offs accepted** Relies on browser behaviour for `SameSite=Lax`. Old browsers (pre-2020) may not honour it. For a thesis project targeting current Chrome/Firefox/Safari, acceptable.

> 💡 中文要点：关掉 CSRF，靠 Cookie 的 `SameSite=Lax` + `HttpOnly` 两层组合防御。`SameSite=Lax` 让浏览器在跨站 POST 时不带 cookie；`HttpOnly` 让恶意 JS 读不到 token。这两条等于 CSRF 攻击的入口被堵死了。

---

### D-20 — Stateless session (`SessionCreationPolicy.STATELESS`)

**Date / where** Task 12 — `SecurityConfig`
**Choice** Tell Spring Security never to create or use an `HttpSession`.
**Alternatives considered** Default `IF_REQUIRED` (Spring will create a session when needed).
**Rationale** Our auth state lives entirely in the JWT cookie + Redis blacklist. An `HttpSession` would be a third place state could hide, breaking the stateless contract and causing scaling problems (sticky sessions or session replication).
**Trade-offs accepted** Anything that *requires* server-side session (e.g. flash messages between redirects) won't work. Not relevant for a JSON API.

> 💡 中文要点：把 Spring Session 完全关掉。所有认证状态只存在于 JWT Cookie + Redis 黑名单里，绝不依赖服务端 Session。横向扩展时不需要 sticky session。

---

### D-21 — `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`

**Date / where** Task 12 — `SecurityConfig`
**Choice** Insert `JwtAuthenticationFilter` *before* Spring's built-in `UsernamePasswordAuthenticationFilter` in the chain.
**Rationale** By the time the request reaches Spring's authentication filters, our filter has already consumed the cookie and written `AuthPrincipal` into the SecurityContext. Spring's authn filters then see "ok, this request is already authenticated" and skip their work. Inserting *after* would be a no-op because Spring's filter would have already 401'd unauthenticated requests by then.

> 💡 中文要点：`addFilterBefore(...)` 把我们自己的 JWT Filter 插在 Spring 默认认证 Filter 之前。这样我们先把"这是谁"写进 SecurityContext，Spring 自带的 Filter 看到已认证就直接放行。

---

### D-22 — `permitAll` whitelist for public endpoints, default deny for `/api/**`

**Date / where** Task 12 — `authorizeHttpRequests` block
**Choice**
- Explicitly allow `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/forgot-password`, `/api/auth/reset-password`.
- Everything else under `/api/**` requires authentication.
- Anything outside `/api/**` (static files, future error pages) is open.
**Rationale** Default deny is safer than default allow — adding a new protected endpoint at `/api/listings` automatically inherits authentication without us remembering to register it. The whitelist is narrow and reviewable; if someone adds a new "public" auth endpoint later, they have to consciously add it to the list.

> 💡 中文要点：API 默认全部要登录，只显式开放健康检查 + 注册/登录/忘密/重置 5 个公开入口。新加的接口自动受保护，不会"不小心"暴露。

---

### D-23 — Hand-built `Set-Cookie` header instead of `ResponseCookie` builder

**Date / where** Task 13 — `AuthController.setTokenCookie / clearTokenCookie`
**Choice** Build the `Set-Cookie` header as a string and call `res.addHeader("Set-Cookie", cookie)`.
**Alternatives considered** Spring's `ResponseCookie.from(name, value)...build()` builder.
**Rationale** The builder API does not always emit `SameSite` consistently across versions, and we want exact control over the attribute order and presence (important when debugging in browser DevTools where headers are matched literally). A 3-line `String.format` is more transparent than chaining 5 builder methods, and easy to grep when a cookie problem comes up. This is one of the few times "do it manually" beats "use the library" for a thesis project.
**Trade-offs accepted** Slight risk of typos in attribute names. Mitigated by keeping it in one place (the helper methods) and by integration-testing the actual response header (Task 14).

> 💡 中文要点：Cookie 的 `Set-Cookie` 头手写字符串拼接，不用 Spring 的 `ResponseCookie` 构建器。理由是构建器在 SameSite 等属性上版本间行为不太稳定；手写更透明，调试浏览器 DevTools 时所见即所得。

---

### D-24 — `clientIp` reads `X-Forwarded-For` first

**Date / where** Task 13 — `AuthController.clientIp`
**Choice** Try `X-Forwarded-For` header before falling back to `req.getRemoteAddr()`.
**Rationale** When deployed behind a reverse proxy (nginx, Cloudflare, the VPS load balancer in our deployment plan), `getRemoteAddr()` returns the proxy's IP, not the real client's. The proxy injects the real IP via `X-Forwarded-For`. Reading the first comma-separated entry handles the proxy chain.
**Trade-offs accepted** The `X-Forwarded-For` header is **trivially spoofable** if there's no proxy in front — anyone can set the header to any value. The rate limiter would key off the spoofed IP. Acceptable for the MVP because (a) rate limiting is a soft control, not a hard one, and (b) a hardening pass would later add a "trusted proxy" allow-list.

> 💡 中文要点：拿客户端 IP 时优先看 `X-Forwarded-For`（反向代理会在这里塞真实 IP），fallback 才是 `req.getRemoteAddr()`。注意这个头**裸跑时可被伪造**——但 rate limit 是软防御，MVP 阶段可以接受。

---

### D-25 — DTOs as Java `record`s, not classes

**Date / where** Task 13 — `RegisterRequest`, `LoginRequest`, etc.
**Choice** All five request/response DTOs are declared as `record`s.
**Alternatives considered** Lombok-decorated classes; plain POJOs with hand-written getters.
**Rationale** Records (Java 16+) give you immutability, `equals`/`hashCode`/`toString`, and accessor methods for free in one line. They are also a *stronger signal* than a class: "this is a transport object with no behaviour and no mutation." Spring's Jackson and `@Valid` work with records out of the box.
**Trade-offs accepted** Records can't have non-final fields (a feature, not a bug for DTOs). They also can't extend other classes — irrelevant here.

> 💡 中文要点：DTO 全部用 Java `record`，一行就有不可变性 + equals + 自动 getter，比 class + Lombok 还简洁。Spring/Jackson 完全支持。

---

### D-26 — Defer custom `AuthenticationEntryPoint` (401 vs 403 cosmetic)

**Date / where** Task 13 manual demo, 2026-05-08
**Observation** When an unauthenticated request hits a protected endpoint (e.g. `/api/auth/me` after logout), Spring Security's default `AuthenticationEntryPoint` returns **403 Forbidden** instead of the more semantically correct **401 Unauthorized**.
**Why this happens** Spring Security's filter chain rejects the request *before* it reaches our controller. Our controller method has `if (principal == null) throw ApiException(UNAUTHENTICATED)`, but that code never executes because Spring Security's `.authenticated()` rule fires first and the default entry point returns 403.
**Choice** Leave it as 403 for now; do not add a custom `AuthenticationEntryPoint` in Task 13.
**Alternatives considered** Add an `AuthenticationEntryPoint` that delegates to `GlobalExceptionHandler` so all auth failures use our standard `ApiErrorResponse` format with a 401 status.
**Rationale** The behaviour proves the access control works (the user *is* locked out). The status code mismatch is cosmetic for a thesis MVP. Adding this in Task 14 (integration tests) is cleaner because the same test will assert the exact response shape.
**Trade-offs accepted** API consumers see two different error shapes (Spring's default for security blocks, our `ApiErrorResponse` for service-thrown errors). To be unified before any production launch.

> 💡 中文要点：登出后访问 `/me` 返回 403 不是 401 —— 这是 Spring Security 默认行为（Filter 层就拒了，没机会走到我们的 Controller）。**功能上没问题**，状态码语义稍偏。Task 14 集成测试时一起加自定义 `AuthenticationEntryPoint` 修这个细节。

---

### D-27 — Custom `AuthenticationEntryPoint` returning 401 + `ApiErrorResponse`

**Date / where** Task 14 — `SecurityConfig.apiAuthenticationEntryPoint`
**Choice** Register a Bean of type `AuthenticationEntryPoint` that, on any unauthenticated access to a protected endpoint, writes status `401` and a JSON body of shape `{"code":"UNAUTHENTICATED","message":"..."}` — the same `ApiErrorResponse` shape that `GlobalExceptionHandler` produces for thrown `ApiException`s.
**Why now** This is the follow-up to D-26 noted during the Postman demo. Folded into Task 14 because the integration test `meRequiresAuthAndReturnsCurrentUser` asserts `HttpStatus.UNAUTHORIZED` for unauthenticated `/api/auth/me`, which requires this fix.
**Trade-offs accepted** None significant. Eliminates the inconsistency where security blocks return Spring's default 403 (HTML) while service-thrown errors return our 401 (JSON). Now both paths return the same shape.

> 💡 中文要点：D-26 的后续修复。在 SecurityConfig 里加了一个 `AuthenticationEntryPoint` Bean，让 Filter 层拒绝时也返回标准的 `{"code":"UNAUTHENTICATED",...}` JSON + 401 状态码，跟我们 `GlobalExceptionHandler` 的格式一致。集成测试一上来就要求这个，所以顺手在 Task 14 修了。

---

### D-28 — Vite dev proxy `/api` → `localhost:8080` (instead of CORS in dev)

**Date / where** Task 15 — `frontend/vite.config.ts`
**Choice** Configure Vite's dev server to proxy any request starting with `/api` to `http://localhost:8080`. Frontend code makes requests like `fetch('/api/auth/login')` (no host).
**Alternatives considered** Have the frontend hit `http://localhost:8080/api/...` directly, relying on CORS to permit it.
**Rationale** Same-origin in the browser means the cookie set by login is *automatically* sent on every subsequent fetch — no `credentials: 'include'` ceremony, no CORS preflight surprises. The proxy makes the frontend think the backend is at the same origin even though they're separate processes. Production will deploy them under one nginx so this is also closer to prod behaviour.
**Trade-offs accepted** Only works when running through `vite dev`. Production builds need a real reverse proxy (nginx) to reproduce this pattern — already planned for Epic 5 deployment.

> 💡 中文要点：Vite dev server 把 `/api/*` 反向代理到 8080。前端 fetch 用相对路径 `/api/auth/login`，浏览器认为这是同源请求，cookie 自动带，没有 CORS 预检的麻烦。生产环境会用 nginx 实现同样的效果。

---

### D-29 — Tailwind v4 (CSS-first import) instead of v3 (`tailwind.config.js`)

**Date / where** Task 15 — `frontend/src/index.css` first line is `@import "tailwindcss";`
**Choice** Adopt Tailwind v4. Configure via the single CSS import + the `@tailwindcss/vite` plugin; no separate `tailwind.config.js` file in this MVP.
**Alternatives considered** Tailwind v3 (more references online, larger ecosystem of v3-targeted articles).
**Rationale** v4 ships *default theme inline*, has a much smaller config surface, and integrates with Vite via a first-party plugin (`@tailwindcss/vite`). For a thesis project where I will not be customising the colour palette or adding plugins, v4's "zero-config" mode beats v3's classic config file.
**Trade-offs accepted** Some online tutorials use v3 syntax (e.g. `tailwind.config.js`, `@tailwind base/components/utilities`); when copy-pasting examples I must check they are v4-compatible (the import line is `@import "tailwindcss";`, not the three `@tailwind ...` directives).

> 💡 中文要点：用 Tailwind v4 不用 v3 —— v4 用 CSS 里一行 `@import "tailwindcss"` + Vite 插件就搞定，不需要 `tailwind.config.js`。代价是网上很多教程是 v3 语法，复制时要留心。

---

### D-30 — Single `apiClient` instead of fetch sprinkled in components

**Date / where** Task 16 — `frontend/src/api/apiClient.ts`
**Choice** All HTTP calls in the frontend go through one tiny module exposing `api.get / post / put / delete`. The module:
1. Sets `credentials: 'include'` on every request (cookie sent and stored).
2. Adds `Content-Type: application/json` only when a body is present.
3. Parses 204 to `undefined` and other 2xx responses as JSON.
4. Throws `ApiError(status, code, message)` on non-2xx, mapping the backend's `{code, message}` shape into a typed exception.
**Alternatives considered** Use `fetch` directly in each component; pull in a heavier client like `axios` or `@tanstack/query`.
**Rationale** Bare `fetch` in every component duplicates 8 lines of boilerplate and forces every author to remember `credentials: 'include'`. Forgetting it once means cookies don't go and the bug looks like "everything fine in dev tools but the user keeps getting logged out." Axios/Query are great for larger apps but add weight; for six endpoints a 40-line wrapper is the right size.
**Trade-offs accepted** Hand-rolled, so no caching/deduping/retry helpers. We don't need them at this scale; if Epic 3+ needs it, this single module is the right place to add it (or migrate to `@tanstack/query` then).

> 💡 中文要点：所有 HTTP 调用统一走一个 40 行的 `apiClient` 模块，自动加 `credentials: 'include'`、自动 JSON、自动把后端错误抛成带 `code/message` 的 `ApiError`。这样每个组件不会忘记设置 cookie 行为，错误处理也集中。

---

### D-31 — `ApiError` carries the backend's `code` field

**Date / where** Task 16 — `class ApiError extends Error`
**Choice** Subclass `Error` with two extra properties: `status: number` (HTTP) and `code: string` (the `ErrorCode` enum name from D-19/`ErrorCode.java`). Components catch by `code`, not by message text.
**Rationale** Pages need to react differently to different errors:
- `INVALID_EMAIL` → highlight the email field.
- `EMAIL_EXISTS` → show "log in instead?" link.
- `TOO_MANY_ATTEMPTS` → show countdown.
Catching by `e.message` is fragile (text changes break it); catching by `e.code` is stable because the enum is the contract. This also means localising the `message` later doesn't break any component logic.

> 💡 中文要点：`ApiError` 把后端的 `code` 字段（枚举名，如 `INVALID_EMAIL`）当成"稳定 API"。前端按 `code` 分支处理，不按 `message` 文本，这样以后改文案/做翻译都不会破坏组件逻辑。

---

### D-32 — Auto-login after register (register → login in one click)

**Date / where** Task 18 — `AuthProvider.register` calls `login(email, password)` after the register API succeeds
**Choice** After a successful `POST /api/auth/register`, immediately call `login()` so the user lands in a logged-in state without having to fill the login form again.
**Alternatives considered** Redirect to `/login` and make the user type their credentials a second time.
**Rationale** Forcing a second login after registration is a UX anti-pattern — the user just proved they know the password 5 seconds ago. Auto-login reduces friction and matches what users expect from modern apps (Spotify, GitHub, etc. all do this).
**Trade-offs accepted** If the register endpoint ever returns a token directly (some APIs do), we could skip the second round-trip. For now we make two calls (register + login) which is ~100ms extra — imperceptible.

> 💡 中文要点：注册成功后自动调 `login()` 让用户直接进入已登录状态，不用再填一次登录表单。多一次 API 调用（~100ms），但 UX 好很多。

---

### D-33 — `AuthProvider` calls `/me` on mount to restore session

**Date / where** Task 18 — `useEffect` in `AuthProvider`
**Choice** On app load, `AuthProvider` immediately calls `GET /api/auth/me`. If the cookie is still valid, the user is restored into state without re-login. If 401, `user` stays `null` and `loading` becomes `false`.
**Alternatives considered** Store user info in `localStorage` and only call `/me` to verify.
**Rationale** The cookie is the single source of truth. If it's valid, `/me` returns the user; if expired/blacklisted, it returns 401. No stale localStorage to sync. The `loading` flag lets pages show a spinner until we know whether the user is logged in — preventing a flash of "please log in" on page refresh for authenticated users.
**Trade-offs accepted** Every page load makes one `/me` call even if the user is not logged in (returns 401 quickly). Acceptable overhead for the simplicity of "cookie = truth".

> 💡 中文要点：App 一启动就调 `/me`，如果 cookie 还活着就恢复登录态（不用重新登录）。`loading` 标志让页面在确认前显示 spinner，避免"闪一下登录页又跳走"的糟糕体验。

---

### D-34 — `ProtectedRoute` preserves the original URL via `?next=`

**Date / where** Task 19 — `ProtectedRoute.tsx`
**Choice** If the user isn't logged in, redirect to `/login?next=<encoded-original-path>`. After login, the login page reads `?next` and sends the user back where they came from.
**Alternatives considered** Always redirect to `/` after login regardless of the original destination.
**Rationale** If a user clicks a deep link "Check your message inbox" while logged out, the right UX is to send them back to that inbox after login, not dump them on the home page. URL-encoding the path handles query strings and special characters correctly.
**Trade-offs accepted** Small attack surface — a malicious site could craft `/login?next=https://evil.com` to redirect after login. To mitigate, the login page (Task 22) must validate that `next` starts with `/` (same-origin only), not an absolute URL.

> 💡 中文要点：未登录访问受保护页面时跳转到 `/login?next=<原路径>`，登录成功后跳回原处。要小心 `next` 参数的开放重定向攻击 —— 登录页必须验证它以 `/` 开头，不能是 `https://...`。

---

### D-35 — `ProtectedRoute` shows a loading state during the initial `/me` check

**Date / where** Task 19 — `if (loading) return <div>Loading...</div>`
**Rationale** When the app first loads, `AuthProvider` hasn't finished calling `/me` yet (`loading` is `true`). During this window, `user` is `null` but that doesn't mean "not logged in" — it means "we don't know yet". If `ProtectedRoute` immediately redirected to `/login`, an actually-logged-in user would see a quick flash of the login page before being bounced back to their destination. The explicit `loading` branch avoids that flicker.

> 💡 中文要点：App 刚启动时 `/me` 还没回来，`user===null` 但意思是"还不知道"不是"未登录"。`ProtectedRoute` 要先显示 Loading 等待答案回来，否则已登录用户会看到"闪一下登录页又跳走"的糟糕体验。

---

### D-36 — LoginPage validates `?next=` starts with `/` (open-redirect mitigation)

**Date / where** Task 22 — `LoginPage.onSubmit`
**Choice** After a successful login, read `?next=` from the URL. If it starts with `/`, navigate there. Otherwise force-navigate to `/`.
**Why** `ProtectedRoute` uses `?next=<original-path>` to bring users back after login (D-34). Without validation, a malicious page could craft a link like `/login?next=https://evil.com/phish` — after the user logs in, they'd be redirected to an attacker site that mimics ours. Requiring `next` to start with `/` limits redirects to the same origin. Sometimes called the "open redirect" vulnerability; it looks harmless but is a standard OWASP item.
**Trade-offs accepted** Can't accept absolute URLs even to legitimate subdomains. For this MVP (single origin) that's fine.

> 💡 中文要点：登录成功后用 `?next=` 参数跳转回原页面前，**必须校验**它以 `/` 开头。否则攻击者可以构造 `/login?next=https://evil.com` 来做开放重定向（Open Redirect）钓鱼。这是 OWASP 标准项，看着小但不能漏。

---

### D-37 — Extract `PasswordInput` with show/hide toggle as a shared component

**Date / where** Task 21 UX polish (between Tasks 21 and 22) — `frontend/src/components/PasswordInput.tsx`
**Choice** Wrap the standard password `<input>` in a small component that toggles `type` between `password` and `text` via a "Show/Hide" button.
**Why now** Three pages use a password field (Register, Login, ResetPassword). Extracting once to a component means the "show password" feature is consistent, and if we later want to swap to an eye-icon SVG we change one place instead of three.
**Trade-offs accepted** Every password field now has an extra interactive element; passing tests that assert on raw `<input type="password">` structure would need updating (none currently).

> 💡 中文要点：密码框带"显示/隐藏"切换是常见 UX 改进。把它抽成可复用组件 `PasswordInput`，注册/登录/重置密码三处都用，要改样式（比如换成眼睛图标）只改一处。

---

## 3. Tooling & Dependencies

A snapshot of what is in the project as of 2026-05-08, with the reason each item is there. Versions are read from the actual lockfiles, not memory.

### 3.1 Backend (`backend/pom.xml`)

| Dependency | Version | Why |
|---|---|---|
| `spring-boot-starter-parent` | 3.4.4 | Java 21 + matching Spring 6 baseline |
| `spring-boot-starter-web` | (managed) | REST controllers, embedded Tomcat |
| `spring-boot-starter-security` | (managed) | Auth filter chain, `PasswordEncoder` |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.6 | JWT signing & parsing (HS256) |
| `spring-boot-starter-data-jpa` | (managed) | Hibernate / JPA |
| `mysql-connector-j` | (managed) | MySQL driver |
| `spring-boot-starter-data-redis` | (managed) | Redis access (rate limiter, blacklist) |
| `spring-boot-starter-validation` | (managed) | `@Email`, `@Size`, `@NotBlank` on DTOs |
| `spring-boot-starter-mail` | (managed) | `JavaMailSender` for prod SMTP |
| `lombok` | (managed, optional) | Less getter/setter boilerplate |
| `spring-boot-devtools` | (managed, runtime) | Hot reload during dev |
| `spring-boot-starter-test` | (managed, test) | JUnit 5, Mockito, AssertJ |
| `spring-security-test` | (managed, test) | `@WithMockUser` etc. |
| `spring-boot-testcontainers` | (managed, test) | Spring lifecycle hooks for Testcontainers |
| `testcontainers-bom` | **1.21.3** (overridden) | Pins Testcontainers above the Spring-Boot default 1.20.6 — see Problem #1 |
| `testcontainers/junit-jupiter` | 1.21.3 (via BOM) | JUnit 5 `@Container`, `@Testcontainers` |
| `testcontainers/mysql` | 1.21.3 (via BOM) | MySQL container helper for integration tests |

Build-plugin notes:
- `maven-surefire-plugin` configured to set `api.version=1.40` (see Problem #1).

### 3.2 Frontend (`frontend/package.json`)

Currently the M1 baseline plus Task 15 additions:

| Dependency | Version | Why |
|---|---|---|
| `react` / `react-dom` | 19.2.4 | UI framework |
| **`react-router-dom`** | **7.15** (new) | Client-side routing for /login, /register, /me, etc. (used from Task 17) |
| `vite` | 8.0.1 | Dev server + build |
| `@vitejs/plugin-react` | 6.0.1 | JSX/Fast Refresh |
| **`tailwindcss`** | **4.2.4** (new, dev) | Utility-first styling (v4 — CSS-first import, no config file) |
| **`@tailwindcss/vite`** | **4.2.4** (new, dev) | Tailwind's first-party Vite plugin (replaces PostCSS config) |
| `typescript` | 5.9.3 | Type safety |
| `eslint` + plugins | 9.x | Linting |

Vite dev proxy now forwards `/api/**` → `http://localhost:8080` (D-28).

Tasks 16–17 will add an `apiClient.ts` wrapper, then a `BrowserRouter` setup. Currently the dev server starts and the existing health-check page renders with Tailwind base styles applied (different fonts vs Vite template default).

### 3.3 Infrastructure (`infra/docker-compose.yml`)

| Service | Image | Purpose |
|---|---|---|
| `campus_mysql` | `mysql:8.0` | Application database |
| `campus_redis` | `redis:7.2-alpine` | Token blacklist, rate-limit counters, password-reset tokens |

> 💡 中文要点：后端依赖一个 Spring Boot 3.4.4 + Java 21 + JWT + JPA + Redis + Testcontainers 的标准组合；前端目前只有 React 19 + Vite 8 的脚手架，UI 库等到 Task 15 再加。

---

## 4. Lessons Learned & Skills Acquired

These are personal reflections directly usable in the thesis "Reflection / Personal Development" chapter.

### Methodology

**Test-Driven Development on every backend service.** Each service (`JwtService`, `EmailService`, `RateLimitService`, …) was created by writing the failing test first, watching it go red, then implementing the minimum code to make it green. This proved more useful than expected:
- It forced clear thinking about the public contract (what does this method *do*?) before touching implementation details.
- The red-then-green cycle gave concrete confidence that each commit actually changes behavior.

**Systematic debugging beats guessing.** When the Testcontainers / Docker problem hit, the temptation was to upgrade Docker on the host machine (a destructive, slow guess). Instead, listing hypotheses, checking each with a small verification command, and only then proposing a fix took ~25 min — and produced a fix that actually addresses the root cause rather than masking it.

**Conventional commits + small atomic commits.** Each commit covers one Task and uses the `feat(scope): subject` form. This is making `git log --oneline` directly readable as a project narrative — useful both for retrospective writing and for the thesis appendix.

> 💡 中文要点：TDD 让接口设计更清晰；系统化排查比瞎试 Docker 升级快得多；按 Task 切小步提交让 git log 本身就是开发流水账。

### Concepts internalised through use

- **Spring Boot profiles & `@ConditionalOnProperty`** — the same code base behaves differently in dev vs prod with no `if` statements (see D-4, D-7).
- **Dependency injection through interfaces** — `AuthService` (future) will depend on `EmailService`, not on `ConsoleEmailService` or `SmtpEmailService`. This is textbook "program to an interface" but the textbook didn't make it real until I wrote the two implementations.
- **JWT internals (HS256, claims, `jti`)** — built `JwtService` from scratch rather than copy-pasting, so signing, expiry, and the `jti` claim that powers the logout blacklist are now mental furniture.
- **Bcrypt fixed-length output** — explains the schema choice `password VARCHAR(60)`. Small detail, but the kind that catches you in a real migration if you don't know.
- **Docker REST API protocol versioning** — until Problem #1 I didn't know the daemon and client negotiate a numeric protocol version separately from the product version. Now I do.
- **Testcontainers + `@DynamicPropertySource`** — the trick where the test runtime (not the static config) tells Spring where Redis lives, because the container's port is allocated at start time.

> 💡 中文要点：通过实际写代码内化了几个概念：Spring 多 Profile / 接口 + 多实现的依赖注入 / JWT 内部结构 / BCrypt 输出固定长度 / Docker API 协议号 / Testcontainers 动态注入端口。这些以前只是名词，现在是肌肉记忆。

### Process insights for the thesis report itself

- Every Task in the plan doc has a numbered checklist with `[ ]` boxes. As a project-management artefact this is more useful than a Gantt chart at this scale — each box is a concrete action with a verification command.
- Keeping the design spec and the implementation plan as **separate** documents (`specs/…-design.md` vs `plans/…auth.md`) means the "what we decided" survives even if the "how we built it" changes.
- This engineering journal is itself an example of the project's documentation discipline — design → plan → code → test → journal entry — and worth showing as evidence of methodology.

> 💡 中文要点：把"为什么这么设计"（spec）和"怎么一步步做"（plan）分两个文档，是项目能持续推进而不混乱的关键。这个 journal 本身也是方法论的一部分。

---

- **Mockito for unit tests, Testcontainers for integration tests** — `AuthServiceRegisterTest` mocks `UserRepository` and `RateLimitService` because the goal is to test *the logic in `AuthService`*, not the database. The encoder is the *real* `BCryptPasswordEncoder` because asserting "the saved password is bcrypt-encoded" requires a real encoder. Picking what to mock is itself a skill: mock collaborators whose behaviour you don't want to depend on; use the real thing when the assertion is *about* its behaviour.

> 💡 中文要点：Mock 还是用真的 —— 要看断言到底在测什么。`AuthServiceRegisterTest` mock 掉 Repository 和限流器（因为不关心数据库），但用**真的** BCryptPasswordEncoder（因为要断言密码确实被 bcrypt 哈希过）。

---

- **Mocking nested fluent APIs** — `redis.opsForValue().set(...)` is two calls. Mockito needs to be told that `redis.opsForValue()` returns a *mocked* `ValueOperations`, not null:
  ```java
  redis = mock(StringRedisTemplate.class);
  valueOps = mock(ValueOperations.class);
  when(redis.opsForValue()).thenReturn(valueOps);
  ```
  Forgetting the second mock makes the first call return `null` and the test fails with a `NullPointerException` that *looks* like a bug in the production code but is actually a mock setup gap. Mental model: every fluent step in a chain that you want to assert on needs its own mock.

> 💡 中文要点：Mock 链式调用（`redis.opsForValue().set(...)`）时，**每一节都要 mock**。少 mock 一节就报 NPE，会误以为是业务代码的 bug，其实是测试搭建漏了。

- **Stateless logout is not really "destroying" a token** — A JWT cannot be destroyed; it stays valid until expiry. "Logout" in this system means *we'll refuse to trust this token anymore* (blacklist by `jti`). Understanding this changed how I think about session lifecycles in stateless systems.

> 💡 中文要点：无状态 JWT 没法真"销毁"，只能在服务端记一笔"以后别信这个 jti"。"登出"在这种系统里是个**约定**，不是真把 token 抹掉。

---

- **`ArgumentCaptor` for verifying generated values** — When the value sent to a mock is generated inside the method (random reset tokens, the email body), you can't predict it for `eq(...)`. Mockito's `ArgumentCaptor` records what was actually passed, then you assert on its shape:
  ```java
  ArgumentCaptor<String> tokenCap = ArgumentCaptor.forClass(String.class);
  verify(valueOps).set(tokenCap.capture(), eq("1"), eq(Duration.ofMinutes(30)));
  assertTrue(tokenCap.getValue().startsWith("auth:reset:"));
  ```
  This pattern (assert on *shape*, not *exact value*) is the right tool whenever output depends on `Random`, `Instant.now()`, or another non-deterministic source.

> 💡 中文要点：测试随机生成的值（如 reset token），不能用 `eq(...)` 精确匹配；用 `ArgumentCaptor` 抓住实际值再断言它的"形状"（前缀对不对、长度合不合理）。

- **Anti-enumeration as a recurring design principle** — The same idea ("don't let response shape leak existence of an account") appeared in three independent decisions: D-11 (login error), D-14 (forgot password silent), D-16 (no email validation in forgotPassword). What looks like overcaution at one site becomes a coherent pattern when seen across the whole flow. This is worth highlighting as a "principle in action" example in the thesis.

> 💡 中文要点：D-11/D-14/D-16 三处独立决策其实都在做同一件事：**不让响应差异泄露账号存不存在**。论文里可以把这个串成一条"防枚举"主线。

---

- **The servlet filter chain is the place where "who is this?" becomes a concrete fact.** Controllers receive an already-authenticated request via `@AuthenticationPrincipal AuthPrincipal`; they never touch cookies or tokens themselves. This is the layered-architecture idea concretely: the filter does the one thing (cookie → principal), and the rest of the app sees only the result. Before Task 11 this was an abstract concept; seeing the filter plug into `SecurityContextHolder` made the layering click.

> 💡 中文要点：Filter 是"把 Cookie 翻译成'这是谁'"的地方。Controller 只拿到结果（`AuthPrincipal`），不碰 cookie。分层架构在这里变得具体：每一层只做一件事。

---

- **CORS and CSRF are different problems** — they sound similar and both involve "cross-something" but they protect against different threats. CORS controls *which origins are allowed to read responses* from us (browser-enforced); CSRF protects against *cross-site forged requests using the user's cookie*. Configuring CORS does not automatically protect against CSRF, and disabling CSRF (D-19) does not affect CORS. Keeping these mentally separate avoided a confused configuration.

> 💡 中文要点：CORS 和 CSRF 听着像，但解决不同问题。CORS 是"哪些前端域名能读我们的响应"，CSRF 是"防止别人借用户 cookie 偷偷发请求"。两者要分开配，不要混。

- **Configuration is code too** — `SecurityConfig` is just 25 lines, but it encodes a half-dozen security decisions (D-19 to D-22). Reading those 25 lines now feels much denser than reading 25 lines of business logic, because each line is a *policy*, not a step. This is one reason auditing security configs is its own skill in the industry — every change has high blast radius.

> 💡 中文要点：`SecurityConfig` 只有 25 行，但每一行都是一条**安全策略**，密度远高于普通业务代码。这也是为什么"审 SecurityConfig"在业界被当成专门的活——改一行可能影响全站。

---

- **The whole HTTP layer is a thin shell over services** — `AuthController` is ~100 lines but does almost no work. It deserialises JSON into a DTO, calls one `AuthService` method, serialises the result. No business logic. No validation. No persistence. This is the textbook "thin controller" pattern made concrete: when reading the controller, the reader can scan it as routing-only and trust that decisions live in the service layer. Achieved by Tasks 7–12 setting up the layers underneath.

> 💡 中文要点：`AuthController` 100 行代码里几乎没有"逻辑"——它只做 JSON ↔ DTO ↔ Service 的搬运。"瘦 Controller、胖 Service" 在这一步真正变得具体。这是分层架构能落地的成果，不是说出来的。

- **Backend API is now demoable in Postman** — Six endpoints (`/api/auth/{register,login,logout,me,forgot-password,reset-password}`) are wired end-to-end through filter, controller, service, repository, JPA, MySQL, Redis, JWT, BCrypt. This is the first checkpoint where the project can be shown to the teacher with a real interaction (filling a form in Postman, watching cookies appear, calling protected endpoints with the cookie). Marks the end of Phase A in the plan.

> 💡 中文要点：后端 6 个 API 全部接通了 —— Filter → Controller → Service → Repository → MySQL/Redis 整条链路活了。这是第一个能用 Postman 给老师演示的节点（Phase A 完工）。

---

- **Demo moment #1: Postman walk-through of all 6 endpoints (2026-05-08).** With the backend started locally against the docker-compose MySQL/Redis, every flow was exercised by hand:
  1. `GET /api/health` → 200 OK
  2. `POST /api/auth/register` → 201 Created with the new user
  3. `POST /api/auth/login` → 200 OK with `Set-Cookie: token=...; HttpOnly; SameSite=Lax`
  4. `GET /api/auth/me` → 200 OK (cookie auto-sent by Postman, filter parsed it, controller returned the user)
  5. `POST /api/auth/logout` → 204 No Content with `Set-Cookie: token=; Max-Age=0`
  6. `GET /api/auth/me` after logout → 403 Forbidden (see D-26)
  7. `POST /api/auth/forgot-password` → 204 No Content; `ConsoleEmailService` printed the reset email to the backend terminal
  8. `POST /api/auth/reset-password` with the captured token → 200 OK with `{"ok": true}`
  9. Re-login with the **old** password → 401 BAD_CREDENTIALS (proving rotation worked)
  10. Re-login with the **new** password → 200 OK
  11. Replaying the same reset-password request → 400 INVALID_TOKEN (proving D-15 single-use enforcement)
  This sequence is the demoable evidence that Phase A is complete. It exercises every layer (controller → filter → security config → service → repository → MySQL → Redis → email) in a single user-driven session. Worth recording verbatim as a runbook in the thesis appendix.

> 💡 中文要点：2026-05-08 用 Postman 手把手跑通了 6 个端点的全套流程（注册 → 登录 → /me → 登出 → 忘密 → 重置 → 旧密码失败 → 新密码成功 → token 不能复用），整条 Phase A 端到端在一次会话里被验证。这个 11 步流水可以原样进论文附录当 demo 脚本。

---

- **Two test layers, two different jobs.** Unit tests (Tasks 7–10) check *the logic in `AuthService`* by mocking everything around it. Integration tests (Task 14) check *the whole HTTP path* by spinning up a real `SpringBootTest` against real MySQL + real Redis (via Testcontainers) and hitting it with `TestRestTemplate`. The first run of the integration tests took **17 seconds**; that is *not* slow given they boot a full Spring context and pull two Docker images, but it is a different category from the millisecond-scale unit tests. Both layers earn their keep: unit tests run on every save and catch logic bugs; integration tests run on push and catch wiring/security/DB-mapping bugs that mocks miss.

> 💡 中文要点：单元测试和集成测试是两个不同层次的活。单测毫秒级跑，专注业务逻辑；集成测试 17 秒跑一次，但启动整个 Spring + 真 MySQL + 真 Redis，能抓到单测漏掉的"装配 / 安全 / 数据库映射"类问题。两个都不能少。

- **`@ServiceConnection` is the magic that makes Testcontainers + Spring Boot 3 actually convenient.** Spring Boot reads metadata off the `MySQLContainer` and **automatically** sets `spring.datasource.url/username/password` to point at the container — no `@DynamicPropertySource` needed. Redis still needs the manual property registration because there is no `@ServiceConnection` for `GenericContainer<redis>`. Worth knowing for future projects.

> 💡 中文要点：`@ServiceConnection` 注解让 Spring Boot 3 自动读取 Testcontainers 容器信息，自动配 datasource URL/用户/密码 —— 完全不用手写 `@DynamicPropertySource`。Redis 没有官方 `@ServiceConnection` 支持，所以那一段还得手动注。

- **Phase A is now defended at three layers.** (1) `AuthServiceXxxTest` — 19 unit tests, mocked dependencies. (2) `RateLimitServiceTest` + `ConsoleEmailServiceTest` — 3 component tests, real Redis via Testcontainers. (3) `AuthControllerIntegrationTest` — 8 end-to-end tests, real MySQL + Redis + full Spring context + HTTP. **Total: 36 tests passing, all green.** Any future change that breaks the auth flow will be caught at one of these layers before hitting production. This is what "test pyramid" looks like in practice.

> 💡 中文要点：Phase A 现在被三层测试守着 —— 19 个单测（mock）+ 3 个组件测（真 Redis）+ 8 个集成测（真 MySQL+Redis+全 Spring）= 36 个测试全绿。这就是教科书"测试金字塔"在真实项目里长成什么样。

---

- **The frontend toolchain is small and explicit by 2026 standards.** Adding a router and a styling system to a Vite + React + TypeScript project takes two `npm install` commands, one CSS import line, and three lines of config. No PostCSS config, no Babel, no ejected webpack. Compared to a CRA-style 2019 project this is a different planet — worth noting because it makes the frontend Tasks 15–24 small and self-contained.

> 💡 中文要点：2026 年的前端工具链已经非常精简 —— 加路由和样式只要 2 条 npm + 1 行 CSS + 3 行配置，没有 PostCSS / Babel / ejected webpack 的复杂性。这让前端 10 个 Task 可以一直保持小步推进。

---

- **A 40-line file replaces a hundred ad-hoc fetch calls.** `apiClient.ts` is short enough to scan in 30 seconds, but it removes the entire class of "I forgot `credentials: 'include'` again" bugs, and standardises error handling so every component catches the same `ApiError` shape. This is the kind of small infrastructure investment that pays off the moment the second consumer is written — and disastrous to skip until late, because by then 10 components have 10 slightly different fetch patterns. Worth lifting out of Tasks 18+ proactively (we did, here).

> 💡 中文要点：`apiClient.ts` 只有 40 行，但它消除了所有"忘记带 cookie"和"错误处理各搞一套"的隐患。这种"小基础设施"必须**在第二个消费者出现之前**就抽出来 —— 拖到后面才做意味着要回去改 10 个组件的 fetch 写法。

- **React Context as the "right size" state primitive for auth.** `AuthContext` + `AuthProvider` + `useAuth` is ~70 lines combined. It gives every component in the tree `const { user, login, logout } = useAuth()`, without any global event bus, no Redux, no Zustand, no prop drilling. For data that genuinely is global and cross-cutting (who is logged in?), Context is exactly what it's for. The `useAuth` wrapper adds one small benefit: it throws if used outside `AuthProvider`, catching a mis-mount early instead of at render time with a confusing null error.

> 💡 中文要点：认证这种"全局、跨层"状态用 React Context 就够了 —— 70 行干掉 login/logout/register/refresh 四个动作 + 全局 user 状态。不需要 Redux/Zustand。`useAuth` 钩子还顺带检查"用在 Provider 外"的错误，早发现早改。

- **The shape of `AuthContextValue` is the frontend's "auth contract".** Any page that wants to know the current user or trigger auth actions imports `AuthContextValue` and gets a typed surface. This is the type-safety mirror of the backend's `AuthController` — two sides of the same contract, both encoded in types, both verified at compile time. If `AuthUser` changes (say we add `avatarUrl`), every component using `user.avatarUrl` is flagged by `tsc` immediately.

> 💡 中文要点：`AuthContextValue` 类型是前端侧的"认证契约"，跟后端 `AuthController` 对称。改一处类型，所有用到的组件都会被 TypeScript 当场报错 —— 契约式开发的好处。

- **Composition in action: `<BrowserRouter><AuthProvider><Navbar /><Routes /></AuthProvider></BrowserRouter>`.** The App component is now ~20 lines and composes four independent pieces: router (URL→component), auth provider (session state), navbar (UI that consumes auth), and routes (page tree). Each was written in a separate Task. The ordering matters: `AuthProvider` must be *inside* `BrowserRouter` because `Navbar.logout` uses `useNavigate()` which needs router context; `Navbar` must be *inside* `AuthProvider` because it calls `useAuth()`. Getting this right the first time is not luck — it's reading the API docs and understanding the nesting contract each provider declares.

> 💡 中文要点：`App.tsx` 20 行代码组装 4 个独立组件：Router / AuthProvider / Navbar / Routes，**嵌套顺序很重要** —— AuthProvider 必须在 BrowserRouter 里面（因为要用 `useNavigate`），Navbar 必须在 AuthProvider 里面（因为要用 `useAuth`）。这是 Provider 模式的必修课。

- **Two-state navbar is a common pattern; ternary rendering covers it cleanly.** `{user ? <LoggedInView /> : <LoggedOutView />}` — same ~20 lines of JSX handle both states. The *same* Navbar instance auto-swaps when auth state changes because it consumes `useAuth()`. Click "Log out" → `logout()` mutates context → React re-renders Navbar with `user === null` → you see "Log in" / "Sign up" again. No manual DOM work.

> 💡 中文要点：Navbar 用 `{user ? 已登录视图 : 未登录视图}` 三目表达式切换。点"登出"后 Context 更新，Navbar 自动重渲染变回"登录/注册"按钮 —— **React 声明式渲染的力量**，不用自己操作 DOM。

- **Demo moment #2 unlocked: login + register are now browsable end-to-end in a browser.** A visitor can go to `localhost:5173`, click Sign up → fill the form → be auto-logged in → see Navbar update to "Hi, …" → click Log out → click Log in → fill the form → Navbar updates again. Every click calls a real backend endpoint (through the Vite proxy), cookies flow HttpOnly-correctly, and the UI reflects state transitions immediately. This is the first "can be shown to someone non-technical" milestone of the project — Task 22 was the target since day one of Epic 1.

> 💡 中文要点：Task 22 完成 = **第一次能在浏览器里端到端演示给非技术人看的时刻**。注册 → 自动登录 → Navbar 变化 → 登出 → 登录 → Navbar 变化，每一步都是真接后端。这是 Epic 1 设计之初就瞄准的演示节点。

---

- **ForgotPasswordPage and ResetPasswordPage complete the auth UI.** Both pages follow the same pattern as Login/Register: call `api.post(...)`, catch `ApiError` by code, show inline error or success state. `ResetPasswordPage` reads `?token=` from the URL and shows an "Invalid link" guard when the token is absent — a small but important defensive branch. Both reuse `PasswordInput` (D-37), so show/hide works consistently across all password fields in the app.

> 💡 中文要点：忘密和重置密码页复用了 `PasswordInput` 和 `apiClient`，遵循和登录/注册页完全一样的模式：调接口 → 捕获 `ApiError` → 展示错误或成功状态。`ResetPasswordPage` 还加了"没有 token 就显示 Invalid link"的边界保护。

---

- **Demo moment #3 — Full browser E2E for Epic 1 auth (2026-05-08).** All 7 sections of `docs/manual-e2e-epic1.md` passed in one sitting: registration edge cases, login, cookie attributes (HttpOnly/SameSite/Secure/Expires), protected route + `?next=` restore, logout blacklist replay, rate-limit lockout, forgot/reset password full flow, and anti-enumeration check (nonexistent email returns same "Check your email" page). This is the final teacher-demo milestone for Epic 1.

> 💡 中文要点：2026-05-08 在浏览器里跑完了 `manual-e2e-epic1.md` 全部 7 个区块，Epic 1 的第四个（最后一个）老师演示节点正式解锁。整个认证系统从后端 API 到前端页面全部端到端验证完毕。

---

### D-38 — `Retry-After` header on rate-limit responses + TTL-aware `RateLimitService.check`

**Date / where** Epic 2 Phase 0, 2026-05-14
**Choice** `ApiException` carries an optional `retryAfterSeconds`. `GlobalExceptionHandler` emits the `Retry-After` HTTP header when present. `RateLimitService.check()` returns a `RateLimitDecision(boolean exceeded, long retryAfterSeconds)` record so callers can populate the field. Legacy `exceeded()` boolean retained as a thin delegate.
**Why** Industry standard for `429 Too Many Requests` is to include `Retry-After` so clients can implement intelligent retry instead of polling blind. The TTL-aware decision is the cheapest way to surface this — Redis already tracks the key TTL via `EXPIRE`; one `TTL` call recovers it. AuthService's two rate-limit call sites (login + register) were migrated to throw with the populated retry-after.
**Trade-off accepted** `exceeded()` boolean API is kept compiling for any future external caller, but production code (AuthService) now uses `check()` exclusively. Slated for removal once verified no other consumers depend on the boolean form.

> 💡 中文要点：限流响应不只返回 429，还要给客户端 `Retry-After` 头告诉它"还有多少秒可以再来"。`RateLimitService.check()` 拿 Redis 的 TTL 当 retry-after，Epic 1 已经有 EXPIRE 写入所以 0 额外成本。前端可据此做指数退避——工业标准做法。

---

### D-39 — Mockito default-value lesson: stub `RateLimitService.check()` in `@BeforeEach`

**Date / where** Epic 2 Phase 0 T3, 2026-05-14
**Symptom** After migrating `AuthService` from `rateLimit.exceeded()` (returns `boolean`) to `rateLimit.check()` (returns `RateLimitDecision`), seven previously-green unit tests started failing with `NullPointerException` instead of the expected `ApiException`.
**Root cause** When a mocked method returns a *reference type* (here `RateLimitDecision`), Mockito's default is `null` — not a sensible zero-value record. So `rateLimit.check(...)` returned `null`, and `decision.exceeded()` blew up. With the old boolean API, Mockito's default `false` happened to be correct for unrelated tests.
**Fix** Add `when(rateLimit.check(any(), any(Long.class), any())).thenReturn(RateLimitDecision.allowed());` to each test's `@BeforeEach`. Specific tests still override with `RateLimitDecision.blocked(...)` as needed.
**Lesson** Whenever you replace a mock's primitive-returning method with a reference-returning one, **every test must be checked** — primitives have safe defaults (false/0); references default to null and detonate downstream.

> 💡 中文要点：把 `mock` 方法的返回类型从 `boolean` 改成对象（`RateLimitDecision`）时，Mockito 默认返 `null` 而不是"零值对象"——后续调 `.exceeded()` 当场 NPE。改契约后必须在 `@BeforeEach` 里加一行默认 stub（返 `allowed()`）。**基础类型有安全默认值，对象没有**——这是迁移返回类型的隐藏陷阱。

---

*Last updated: 2026-05-14 — Epic 2 Phase 0 complete (D-38, D-39). 44 backend tests green; `Retry-After` header now emitted on rate-limit responses.*
