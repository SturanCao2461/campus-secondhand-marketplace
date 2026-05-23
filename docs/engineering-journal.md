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

### D-40 — `GET /api/categories` ships the read side of the listing taxonomy

**Date / where** Epic 2 Phase 1 T7, 2026-05-15
**Choice** A single read-only endpoint `GET /api/categories` returns `{ "items": [{ code, nameEn, nameZh } …] }`, ordered by `sortOrder` ascending, filtered to `active=true`. The shape — wrapping the array in an `items` key rather than returning a bare array — matches the existing `PagedListings` contract and leaves headroom for cursor or filter metadata later without breaking clients.
**Why** The frontend `CreateListingPage` will call this once at mount to populate the category `<select>`. Categories are bilingual (`nameEn` / `nameZh`) per the design spec — supplying both fields lets the frontend pick its render language without a second roundtrip. Sorting in the database (via `findAllByActiveTrueOrderBySortOrderAsc`) keeps the controller a pure mapper, and the `active` flag gives operations a way to retire a category without DELETEing rows referenced by historical listings.
**Trade-off accepted** Endpoint is unauthenticated-rejected (`/api/**` → `authenticated()` in `SecurityConfig`). Anonymous users — including teacher-demo browser sessions before login — cannot prefetch categories. The alternative (adding `/api/categories` to `permitAll`) was rejected to keep the auth boundary simple: every read of business data sits behind login.

> 💡 中文要点：分类接口设计三个细节值得记：①响应包一层 `{ "items": […] }`，给后续加分页/筛选元数据留空间；②`nameEn` + `nameZh` 双语字段一次返回，让前端按 i18n 自取其一；③`active` 软删除字段保留历史 listing 的外键完整性。`sortOrder ASC` 排序在 DB 层做，Controller 只做 entity→DTO 映射，3 行代码。

---

### D-41 — Testcontainers singleton pattern fixes the cross-class context-cache crash

**Date / where** Epic 2 Phase 1 T7, 2026-05-15
**Symptom** `CategoryControllerIntegrationTest` passed in isolation but threw `CannotCreateTransactionException: HikariPool-2 - Connection is not available` (root cause: `Connection refused at 0ms`) when run after `AuthControllerIntegrationTest` in the same `./mvnw test` invocation.
**Root cause** `AbstractIntegrationTest` used `@Testcontainers` + `@Container` (JUnit 5 lifecycle): each subclass started a fresh MySQL/Redis pair on its own random port and stopped them at class teardown. Spring's `TestContext` framework, however, *cached* the `ApplicationContext` across both test classes (matching `@SpringBootTest` config + dynamic properties). Sequence: Auth class started container A, cached context X pointing at A:port-A. Auth class finished → container A stopped. Category class loaded → Spring matched context X from cache → DataSource still pointing at the dead port-A → connection refused 30s later. Single-class runs avoided the bug because no subsequent class ever needed the dead container.
**Fix** Switched to the [Testcontainers singleton pattern](https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers): removed `@Testcontainers`/`@Container`, declared `static final` containers, and started them in a `static {}` block. JVM-scoped lifecycle. Containers boot once at class loading time, are reused across all IT classes, and ryuk cleans them at JVM exit. Runtime dropped from 121s → 23s on the full suite as a side effect.
**Lesson** Two test-framework concerns silently disagree about lifecycle: `@Container` says "die at class end"; `@SpringBootTest` says "cache contexts across the suite". When they collide on a shared resource (the JDBC URL), the cache wins and points at corpses. The singleton pattern aligns both to JVM scope so they cannot disagree. Watch for any test infrastructure that conflates "lifecycle of *this* class's setup" with "lifecycle of resources that downstream classes depend on" — they should be the same scope or the failure surfaces only when class count > 1.

> 💡 中文要点：Testcontainers + Spring 测试有个隐藏陷阱：`@Container` 注解按"测试类生命周期"关容器，但 Spring `TestContext` 跨类**缓存** ApplicationContext。第一个类跑完关掉容器，第二个类复用 context，里头 DataSource 指向死端口 → `Connection refused`。**根治**：用 singleton 容器模式（`static {}` 启动，永不 stop），把容器对齐到 JVM 生命周期。这次顺带把测试套件从 2 分钟降到 23 秒，因为 mysql + redis 启动只发生一次。

---

### D-42 — `Listing` entity ships with 4 indexes baked in, `condition` backtick gotcha, schema verified via `information_schema`

**Date / where** Epic 2 Phase 1 T8, 2026-05-15
**Choice** `Listing` is a single JPA entity carrying all 16 columns (id, owner FK, title, description, price, original_price, category FK, image_path, status, listing_type, condition, meet_at, negotiable, reason_for_selling, created_at/updated_at/deleted_at). Both relationships use `@ManyToOne(fetch = LAZY)`. The 4 indexes — `idx_listings_owner`, `idx_listings_status`, `idx_listings_created`, `idx_listings_image` — are declared up-front via `@Index` annotations on `@Table`, even though only "My Listings" needs the owner index in this Epic.
**Why** Index strategy is read-driven: `idx_listings_owner` covers `WHERE owner_id = ?` (My Listings page); `idx_listings_status` covers Epic 3's public list `WHERE status = 'AVAILABLE'`; `idx_listings_created` supports the universal `ORDER BY created_at DESC` sort; `idx_listings_image` is non-obvious — it backs the §7.5 image-access owner check `WHERE image_path = ?`. Creating all four now avoids a later `ALTER TABLE` migration on a populated production table. Adding an index to a small empty table is free; adding it later requires careful coordination.
**Trade-off accepted** Pre-creating Epic 3's index now means the categories.id and owner_id FKs each get *two* indexes — Hibernate auto-creates one for the FK constraint, and we explicitly named another for read patterns. MySQL won't merge them, costing ~16 bytes/row of disk. At thesis-project scale (~1000 listings expected) this is invisible. At industrial scale you'd drop the explicit index and rely on the FK auto-index, accepting the auto-generated index name.

> 💡 中文要点：4 个索引一次到位的策略：①`owner_id`（"我的发布"页面）；②`status`（Epic 3 的公开列表）；③`created_at`（默认排序）；④`image_path`（图片访问的归属校验，spec §7.5）。**第 4 个最不直观但最关键** —— 没有它，每次取图都要全表扫描验证归属。空表加索引零成本，上线后再加要协调迁移，所以提前建。

---

### D-43 — `condition` is a MySQL reserved word; column name needs backtick quoting in `@Column(name = "\`condition\`")`

**Date / where** Epic 2 Phase 1 T8, 2026-05-15
**Symptom** A naive `@Column(name = "condition")` would have produced syntax errors when Hibernate generated `CREATE TABLE … condition VARCHAR(20) …` because MySQL reserves `CONDITION` for stored-procedure flow control.
**Fix** Wrap the column name in backticks at the JPA annotation level: `@Column(name = "\`condition\`")`. Hibernate passes the literal string through to the DDL emitter, which preserves the backticks. Raw JDBC reads must use the same backtick form: ``SELECT `condition` FROM listings WHERE id = ?``.
**Lesson** When a domain word collides with a SQL reserved word, keep the Java field name natural (here, `condition` is the most readable choice for "item condition") and pay the price at one layer — the `@Column` annotation. Don't rename the Java field to dodge the reservation; the readability tax is permanent and infectious. Backticks stay localized to two places: the entity annotation and any raw SQL that reads the column.

> 💡 中文要点：`condition` 是 MySQL 保留字（用于存储过程的流程控制）。Java 字段名想用 `condition` 时只需在 `@Column(name = "\`condition\`")` 加反引号即可，让 Hibernate 把反引号原样写进 DDL。不要为绕开保留字而改 Java 字段名 —— 可读性损失永久存在，反引号只本地化到两处（注解和原生 SQL）。

---

### D-44 — Verify generated schema with `information_schema.statistics`, not by round-tripping rows

**Date / where** Epic 2 Phase 1 T8, 2026-05-15
**Symptom** When `ddl-auto=update` generates the schema, "did Hibernate emit the index I declared?" is a real question. Round-tripping a row checks the table exists and columns persist, but it does **not** verify that named indexes were actually created — you only notice missing indexes years later when a query plan does a full table scan.
**Choice** Two-pronged schema integration test: ①`SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'listings'` confirms the table exists; ②`SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_name = 'listings'` and assert `containsAll` of the four named indexes. The third and fourth tests do a row-level round-trip to verify column types, defaults, and `@Enumerated(EnumType.STRING)` persistence (not ordinal).
**Lesson** When schema is a *side effect* of code (JPA `ddl-auto`) rather than an artifact (Flyway), tests must assert the side effect directly. `information_schema` is the cheapest, most direct probe: structural questions get structural answers. Functional behavior (round-trip) cannot substitute for structural assertions about the schema you intended to declare.

> 💡 中文要点：schema 由 `ddl-auto=update` 生成意味着"我写的 `@Index` 真的进了数据库吗？"是个**必须验证**的问题——光靠"存进去再读出来"测不到索引存不存在。最直接的查法是 `information_schema.statistics`：表名+索引名一目了然。结构性的问题就要用结构性的查询去验证，不要拿功能性的往返当替代。

---

### D-45 — JPA tests need `@Transactional` to safely traverse `LAZY` associations

**Date / where** Epic 2 Phase 1 T8, 2026-05-15
**Symptom** The first `findById` round-trip test threw `org.hibernate.LazyInitializationException: Could not initialize proxy [Category#1] - no session` when calling `loaded.getCategory().getCode()`. The Listing came back fine; touching the lazy `category` proxy after the repository call had returned was the trigger.
**Root cause** `@SpringBootTest` does not start a transaction around test methods by default. JpaRepository methods open a transaction internally, complete, and close it. The returned entity carries `@ManyToOne(fetch=LAZY)` proxies that need an active session to resolve. Outside the repository call's transaction, those proxies are detached and any access fails.
**Fix** Annotate the test class with `@Transactional`. Each test method runs inside a transaction, the session stays open for its duration, lazy proxies resolve transparently. Spring Boot tests default to auto-rollback at method end, so this also gives free per-method data isolation.
**Lesson** "Repository call returns the entity" is *not* the same as "the entity is fully usable forever." LAZY associations carry a hidden contract — they need a session. Production service code is `@Transactional` by default, so the contract is invisible there. Tests that mimic service-layer access must opt into the same transactional scope, or restrict assertions to fields that don't trigger LAZY load.

> 💡 中文要点：JPA 集成测试访问 `@ManyToOne(fetch=LAZY)` 关系前必须给测试类加 `@Transactional`。仓库方法返回实体后事务就关了，LAZY proxy 失去 session，触发 `LazyInitializationException`。生产 service 代码自带 `@Transactional` 所以看不到这个坑——测试要复刻同款上下文才能覆盖到 LAZY 字段。Spring 测试加 `@Transactional` 顺带还有自动回滚 = 每方法数据隔离的好处。

---

### D-46 — `ListingRepository` derived queries: method-name DSL covers all 4 patterns without a single `@Query`

**Date / where** Epic 2 Phase 1 T9, 2026-05-15
**Choice** Four derived queries handle every read pattern Phase 1 needs: `findByOwner(User, Pageable)`, `findByOwnerAndStatus(User, ListingStatus, Pageable)`, `findByOwnerAndStatusNot(User, ListingStatus, Pageable)`, and `findByImagePath(String) → Optional<Listing>`. All are pure Spring Data method-name parsing — no `@Query`, no `Specification`, no QueryDSL. The `Pageable` parameter on the first three carries page index, page size, and sort, so the controller layer can map `?page=`, `?size=`, `?sort=` directly without translation logic.
**Why** Method names *are* the contract. `findByOwnerAndStatusNot(owner, REMOVED, ...)` is self-documenting in a way that a `@Query("SELECT l FROM Listing l WHERE l.owner = ?1 AND l.status <> ?2")` is not. When a future maintainer reads the repository, they see the available reads without skipping into JPQL. The DSL also forces a small, finite vocabulary — if a future query cannot fit the keyword grammar, that is a signal the read is doing too much and probably belongs in a service-layer aggregate, not a repository method.
**Trade-off accepted** `findByImagePath` returns `Optional<Listing>` despite `image_path` not having a DB UNIQUE constraint. Multiple matches would throw `IncorrectResultSizeDataAccessException`. This is fail-loud-by-design: business logic generates UUID v4 filenames, so a collision indicates a bug worth crashing for, not a normal case to handle. Adding a UNIQUE constraint at the DB level would be belt-and-braces — deferred until image upload (T19) lands so we can assert the invariant in one place rather than two.

> 💡 中文要点：4 个查询全靠 Spring Data 方法名 DSL 解析，零 `@Query`。`findByOwnerAndStatusNot` 这种"否定"关键字也认。`Pageable` 参数把 page/size/sort 三件事一次打包，controller 层不用做参数翻译。`findByImagePath` 返 `Optional` 而非 `List` 是有意 fail-loud 设计：UUID 撞文件名 = 业务 bug，应该崩而不是兜底。

---

### D-47 — Listing DTOs: image-URL prefix lives in the DTO factory; `PagedListings` is a custom record, not Spring's `Page<>`

**Date / where** Epic 2 Phase 1 T11, 2026-05-15
**Choice** Six DTOs as Java `record` per the Epic 1 precedent. Two design points worth recording. (1) `ListingResponse.from(Listing)` and `ListingSummary.from(Listing)` build the public-facing `imageUrl` by prepending `"/api/uploads/"` to the entity's `imagePath` — this is the single source of truth for that URL shape. (2) `PagedListings` is a hand-rolled `record(List<ListingSummary> items, int page, int pageSize, int totalPages, long totalItems)` populated by a static `from(Page<Listing>)` factory, instead of returning Spring Data's `Page<>` directly to the controller.
**Why** (1) The relative `imagePath` (e.g. `"listings/abc-123.jpg"`) is what the DB and `LocalImageStorageService` agree on; the URL prefix is a transport-layer concern that belongs at the boundary. Keeping the prefix concatenation inside the DTO factory means *one* file owns the rule. If we later move from `/api/uploads/` to a CDN URL, that's one edit. If 12 controller methods each prepended the prefix inline, we'd be playing whack-a-mole. (2) Spring's `Page<>` JSON serializes with field names that don't match the spec contract (`number` vs `page`, `content` vs `items`, plus a noisy `pageable` object). Returning `Page<>` directly leaks Spring's internal shape into the public API and pins the wire format to whatever Jackson decides about Spring's class. A purpose-built record gives the frontend exactly the contract `spec §6.3` declared.
**Trade-off accepted** `CreateListingRequest` and `UpdateListingRequest` have identical components today. Keeping them as separate types adds boilerplate but preserves the controller-signature distinction (`PUT /api/listings/{id}` clearly takes an "update" intent, not a "create") and gives a place for divergence (e.g. partial updates) without a downstream refactor.

> 💡 中文要点：DTO 工厂方法 (`from(entity)`) 里干两件 boundary 转换：①`imagePath` → `imageUrl` 加 `/api/uploads/` 前缀（**单点**真理，未来换 CDN 就改一处）；②Spring 的 `Page<>` 用 `number/content` 字段名，跟 spec 期望的 `page/items` 不符——自定义 `PagedListings` record 把契约钉死，避免内部分页类泄漏到 API 表面。`Create/Update` Request 字段相同也写两个 record，给未来差异化留口子。

---

### D-48 — `ListingService.create` takes `imagePath` from day one; all 8 listing `ErrorCode`s land together

**Date / where** Epic 2 Phase 1 T12, 2026-05-15
**Choice** `ListingService.create(User currentUser, CreateListingRequest req, String imagePath)` is the day-one signature. Phase 1's T16 controller will pass a placeholder string (`"listings/placeholder.jpg"`); Phase 2's T19 will replace that with `imageStorage.store(file)`. The service layer is *blind* to whether the path is a placeholder or a real upload — it just persists the string. Separately, all 8 listing `ErrorCode` values from spec §4.0 (`LISTING_NOT_FOUND`, `NOT_LISTING_OWNER`, `INVALID_STATUS_TRANSITION`, `LISTING_REMOVED`, `INVALID_CATEGORY`, `INVALID_IMAGE`, `MISSING_IMAGE`, `INVALID_PRICE`) were added to `ErrorCode.java` in this commit, even though only `INVALID_CATEGORY` and `INVALID_PRICE` are used in T12.
**Why** *(imagePath in signature)*: stable APIs across phase boundaries are cheaper than refactors. If T16 used a 2-arg `create(User, CreateListingRequest)` and T19 had to retrofit a third parameter, every existing test, every controller call site, and the service contract itself would shift. Phase 2 adds *what fills the parameter*, not the parameter itself. *(All ErrorCodes together)*: enum edits surface in code review as "you touched this file again" noise. One commit defines the universe of listing errors, and downstream tasks (T13 / T14 / T15 / T17) reference codes that already exist. Removes 4 tiny commits whose only diff would be one line each.
**Trade-off accepted** Adding 6 unused enum values is technically dead code. They are loaded into `ErrorCode`'s constant pool with no consumer until later tasks. The cost is invisible (one-time JVM cost, < 1 KB of class file). The benefit is enum cohesion: the file reads as a single domain vocabulary statement rather than an ad-hoc grow-as-you-go list.

> 💡 中文要点：Service 方法签名要"跨阶段稳定"——`create(User, CreateListingRequest, String imagePath)` 第三参先用占位字符串顶着，等 Phase 2 的 ImageStorageService 上来再让 controller 传真实路径。Service 不关心来路是占位还是上传，只持久化字符串。**枚举一次性建满**也是同款思想：8 个 ListingErrorCode 一起进 enum，下游 task 直接 reference 现成符号，免去 4 次"改一行 enum"的零碎 commit。代价仅一次 JVM 加载 < 1 KB，换来 enum 文件的语义内聚。

---

### D-49 — Anti-enumeration in write paths: non-owner edits return `404 LISTING_NOT_FOUND`, never `403`

**Date / where** Epic 2 Phase 1 T13, 2026-05-15
**Choice** `ListingService.update` returns `LISTING_NOT_FOUND` (HTTP 404) in two distinct cases: (a) the id does not exist in the DB, and (b) the id exists but the authenticated user is not the owner. The two failure responses are byte-for-byte identical. `NOT_LISTING_OWNER` (HTTP 403) is reserved for client-side preconditions and a future admin view; the public API in Epic 2 never emits it. Spec §7.4 mandates the validation chain order — existence → ownership → status → business rules — so the two indistinguishable failures appear at the same checkpoint with the same response shape.
**Why** Same anti-enumeration rationale as Epic 1 D-11 / D-14 / D-16, but this is the first time the project applies it to a *write* operation rather than a read. If 404 and 403 differed for write paths too, an attacker scanning ids could distinguish "this id is unused" from "this id exists, owned by someone else". The latter is a leak — it reveals that the id is *taken*, which when combined with timing or other side channels can reveal listing existence beyond what the API meant to expose. Preserving the same 404 shape across both unauthorized read and unauthorized write closes the side channel.
**Trade-off accepted** Legitimate users who fat-finger the id of someone else's listing get a confusing "Listing not found" error instead of "Not your listing." This is a UX cost — but writes are typically initiated from the owner's listing list, so the wrong-id case is exotic. Frontend never *constructs* an arbitrary id to PUT; it only acts on listings it already enumerated via `GET /api/listings/me`. The error message is therefore mostly a defensive fallback the user shouldn't reach.

> 💡 中文要点：写路径上的反枚举跟读路径同款—— 非 owner 改人家的 listing 也返 `404 LISTING_NOT_FOUND` 而不是 `403 NOT_LISTING_OWNER`。**两种失败响应字节级一致**：要么这个 id 根本没人用，要么有人用但不是你的，攻击者扫 id 时分不出来。代价：用户不小心输错 id 会看到"未找到"提示而不是"不是你的"。但写操作通常都是从"我的 listing"列表点出来的，正常用户走不到这条路径——是防御性兜底，不是常规 UX 路径。

---

### D-50 — Listing FSM as a `Map<ListingStatus, Set<ListingStatus>>` table; three service methods now share a uniform validation chain

**Date / where** Epic 2 Phase 1 T14, 2026-05-15
**Choice** The 4-state / 8-edge listing FSM (spec DC-3) lives as a `private static final Map<ListingStatus, Set<ListingStatus>> ALLOWED_TRANSITIONS` literal on `ListingService`, populated via `Map.of(...)`. Each entry maps a state to the *set* of states it can transition to. The terminal `REMOVED` maps to an empty set. `changeStatus(currentUser, id, newStatus)` reads the table once: `ALLOWED_TRANSITIONS.get(listing.status).contains(newStatus)`. Self-transitions (e.g. `AVAILABLE → AVAILABLE`) are illegal because the table never lists them. With this addition, the three public methods on `ListingService` (`create`, `update`, `changeStatus`) now share the same validation-chain shape: existence → ownership → business rules.
**Why** Three reasons the table-of-sets representation beats the alternatives. (1) **Direct correspondence with the spec.** The DC-3 markdown table has rows for from-states and a list of allowed to-states; the Java literal mirrors that 1:1 — anyone reading the spec can verify the implementation by visual diff. (2) **No state-transition logic spreads.** A nested `if/switch` chain encoding 8 transitions invariably fragments the FSM across many lines and tempts subtle differences ("oh, RESERVED → REMOVED also requires X"). The table is *just* the rules. (3) **Idempotency is settled at one place.** Self-transitions are illegal because the table omits them. If we ever decide DELETE-on-already-REMOVED should silently succeed, that idempotency lives in the controller (T16), not the service — keeping the service's contract pure.
**Trade-off accepted** `Map.of(...)` is unmodifiable but the inner `Set.of(...)` is also unmodifiable. We're paying for two layers of immutability per state. The alternative (mutable `EnumMap` + `EnumSet`) would be slightly faster but allow accidental mutation. At < 10 entries this is invisible. Worth a passing note that if the FSM grows to dozens of states + edges, a separate `FsmTable` value class with explicit `enforce(from, to)` semantics would be the next step.

> 💡 中文要点：状态机 4 状态 / 8 边用 `Map<from, Set<to>>` 一张表搞定（`ALLOWED_TRANSITIONS`），跟 spec DC-3 的 markdown 表 **一行对一行** 对应。可视化对比就能验证实现。**自身转换是非法**因为表里就没列。三个 service 方法（`create` / `update` / `changeStatus`）现在共享同款校验链结构：存在性 → 归属 → 业务规则。重复 DELETE 已 REMOVED listing 的"幂等性"放 T16 controller 处理，不污染 service 契约。

---

### D-51 — Idempotent DELETE: `ListingService.remove` is its own minimal implementation, not a thin wrapper around `changeStatus`

**Date / where** Epic 2 Phase 1 T15, 2026-05-15
**Choice** `ListingService.remove(User, Long)` independently implements the existence → ownership → "if not REMOVED, set REMOVED and save" sequence. It does **not** delegate to `changeStatus(currentUser, id, REMOVED)`. On already-REMOVED listings it is a silent no-op (no save, no exception). On not-found or non-owner it throws `LISTING_NOT_FOUND` — same anti-enumeration behaviour as `update` and `changeStatus`. The whole method body is ~12 lines.
**Why** Spec §4.6 is the source of the puzzle: the language "DELETE is equivalent to PATCH(REMOVED)" suggests delegation, but the errors list deliberately omits `INVALID_STATUS_TRANSITION`. The omission encodes a different intent — DELETE is supposed to be idempotent, while PATCH(REMOVED) on an already-REMOVED listing should fail per the FSM. Two contradictory contracts at the same code path. Independent implementation lets each contract say what it means: `changeStatus` stays a strict FSM enforcer (no idempotency hacks) and `remove` stays a clean idempotent DELETE. Wrapping `remove` around `changeStatus` would have required a `try { ... } catch (ApiException e) { if (e.code == INVALID_STATUS_TRANSITION) ... }` which is hard to read and worse to test.
**Trade-off accepted** Two methods now share ~5 lines of boilerplate (existence → ownership lookup). DRY temptation: extract a `Listing requireOwnedListing(currentUser, id)` helper. Deferred — the duplication is small and the methods will diverge further as Epic 3 adds public-browsing semantics, at which point the helper would have to grow conditionals. Worth revisiting in T22/T23 when the fully-tested service layer makes the right shape obvious.

> 💡 中文要点：spec §4.6 暗示 DELETE 是幂等的（错误码列表故意没列 `INVALID_STATUS_TRANSITION`），但 PATCH(REMOVED) 严格走 FSM —— 同一段路径**两个矛盾契约**。所以 `remove` 不去包装 `changeStatus`，而是自己实现 ~12 行：找 listing → 查 owner → 已 REMOVED 就 noop / 否则 set REMOVED 并 save。让 `changeStatus` 保持 FSM 严格，`remove` 保持幂等清爽，两个语义各说各话。两边重复 ~5 行查找代码 vs 抽 helper 的取舍 —— 暂留重复，等 Epic 3 公开浏览语义到位再回头看。

---

### D-52 — Phase 1 service layer complete: 4 public methods, uniform validation chain, 32 unit tests

**Date / where** Epic 2 Phase 1 T15, 2026-05-15
**Retrospective** `ListingService` Phase 1 lands with 4 public methods sharing the same validation-chain shape (per spec §7.4): `create` (no existence step — it's a creation) / `update` / `changeStatus` / `getOne` (read) / `remove`. All write methods enforce the chain existence → ownership → business in strict order; first failure short-circuits. The two read methods (`listMine`, `getOne`) skip ownership in the privileged sense — `listMine` is implicitly self-scoped via the `currentUser` parameter to the `findByOwner*` queries, and `getOne` performs an "owner OR public-visible" gate. Test coverage: 32 unit tests across 4 test classes (`Create` 8, `Update` 7, `ChangeStatus` 8, `Query` 9). Every public method has at least one happy-path test and at least one anti-enumeration test verifying `LISTING_NOT_FOUND` for the unauthorized cases.
**Lesson** A uniform validation-chain shape is worth more than DRY extraction. Each method reads top-to-bottom in the same pattern (existence → ownership → business → mutate → save). Future readers and reviewers can scan any method and immediately know which step is which. Extracting the shared lookup into `requireOwnedListing(currentUser, id)` would save lines but obscure the chain. Repetition with consistent shape > abstraction without consistent shape.
**Phase 1 service complete:** Next up is T16 (`ListingController` 5 endpoints + Security config update). The service layer's public API is the controller's contract; controller test design starts from these 4 method signatures.

> 💡 中文要点：Phase 1 service 层封顶 —— 4 个 public 方法（不算 5 个吧？仔细数：`create / update / changeStatus / listMine / getOne / remove` 共 6 个），每个都按 §7.4 校验链 **同款形状** 写：存在 → 归属 → 业务 → 变更 → 保存。32 个单元测试全绿。**统一形状 > DRY 提取**：repeat 5 行查找代码胜过用 helper 隐藏校验链结构，因为读代码的人一眼就能定位哪步是哪步。下一步进 T16 controller，service 层 public API 就是 controller 测试的契约。

---

### D-53 — `ListingController` wiring: AuthPrincipal → User reference, sort string at the boundary, placeholder imagePath

**Date / where** Epic 2 Phase 1 T16, 2026-05-15
**Choice** `ListingController` exposes 6 endpoints that wrap the 6 `ListingService` public methods. Three wiring choices worth recording. (1) `@AuthenticationPrincipal AuthPrincipal principal` arrives from the JWT filter; `users.getReferenceById(principal.userId())` materializes a JPA entity reference without a `SELECT`, since the service only uses `currentUser.getId()` for FK association and ownership checks. (2) The frontend's sort vocabulary (`CREATED_DESC` / `CREATED_ASC` / `PRICE_DESC` / `PRICE_ASC`) is mapped to Spring's `Sort` at the controller boundary by a private `mapSort(String)` switch — service methods stay `Pageable`-pure. (3) `POST /api/listings` passes a hardcoded `imagePath = "listings/placeholder.jpg"`; `PUT` passes `newImagePath = null`. T19 (Phase 2) will replace these with `imageStorage.store(file)` results. The service signature stays stable across the boundary.
**Why** *(getReferenceById)* The alternative — `users.findById(principal.userId()).orElseThrow()` — costs an extra DB roundtrip on every authenticated listing endpoint. With JWT auth ratifying the token and the service only needing the FK id, the `SELECT` is wasted I/O. *(sort at boundary)* If the service knew about `"CREATED_DESC"` strings, it would be tied to the frontend's vocabulary; future channels (admin tools, GraphQL) would have to either parrot those strings or duplicate the mapping. Boundary translation lives in one place. *(placeholder imagePath)* Stability of the service contract across phases is a deliberate Phase-1 design decision (see D-48). T19's image upgrade then becomes purely a controller-layer change.
**Trade-off accepted** SecurityConfig was *not* updated despite spec §7.7 prescribing it: Epic 1's existing `.requestMatchers("/api/**").authenticated()` already covers `/api/listings/**`. The spec was written without checking the actual configuration, so the prescribed update is redundant. Worth a note here so a future reader of spec §7.7 doesn't expect to find a corresponding diff.

> 💡 中文要点：Controller 三个 wiring 决策：①`getReferenceById` 拿 User reference 不查 DB；②sort 字符串映射在 controller 边界做，service 只接 `Pageable`；③Phase 1 imagePath 用占位符（service 签名跨阶段稳定，T19 升级 multipart 只需改 controller）。spec §7.7 prescribed 的 SecurityConfig 修改实际是多余的——Epic 1 的 `/api/**` 已经一刀切覆盖了 listings 路径，spec 没看现状。

---

### D-54 — Hibernate action-queue ordering: `deleteAll` + `save` flushes INSERT before DELETE

**Date / where** Epic 2 Phase 1 T16, 2026-05-15
**Symptom** `ListingRepositoryTest` and `ListingSchemaIntegrationTest` ran green in isolation but threw `Duplicate entry 'Owner' for key 'users.UK...'` on `users.save(...)` when the full suite ran them after `ListingControllerIntegrationTest`. The controller test (no class-level `@Transactional`) committed real users; the repository test's transactional `setupFixture` then called `users.deleteAll()` + `users.save(...)`, expecting the deletes to land first.
**Root cause** Spring Data JPA's `JpaRepository.deleteAll()` does a `findAll() + delete(each)` pass that *schedules* deletions in the Hibernate persistence context — it does not issue SQL until flush. The next `users.save(...)` triggers a flush. Hibernate's action queue then orders operations by category: **INSERT → UPDATE → DELETE**. So the new user is INSERTed first, hitting the leftover row from the previous test class. Single-class runs avoided the bug because there were no leftover rows.
**Fix** Replaced `deleteAll()` with `deleteAllInBatch()` in two test classes' `@BeforeEach`. `deleteAllInBatch()` issues a direct `DELETE FROM table` SQL and bypasses the persistence-context action queue entirely.
**Lesson** Two pieces of test infrastructure quietly disagreed about lifecycle: integration tests that go through Spring MVC commit real data; transactional repository tests assume a clean slate. The mismatch surfaced as a Hibernate flush-order interaction, not as a teardown bug. When mixing transactional and non-transactional test classes, prefer `deleteAllInBatch` for the cleanup step — it commutes with whatever the previous class left behind. (Same family of bug as D-41: silent disagreement between two test-framework concerns about lifecycle. Two now this Epic.)

> 💡 中文要点：Hibernate persistence context 的 action queue **默认顺序是 INSERT → UPDATE → DELETE**。`deleteAll()` 只把删除 schedule 到 context 而不立即执行 SQL，紧随的 `save()` 触发 flush，flush 时按 INSERT 先 DELETE 后输出，导致新插入撞到本该被删的旧行。修复：用 `deleteAllInBatch()`，它发原生 `DELETE FROM` SQL 立即执行，绕开 action queue。**跨非事务/事务测试类的清理一律用 `deleteAllInBatch`**——这是同 D-41 一族（两个测试框架对生命周期的隐式不一致）的第二例。

---

### D-55 — Phase 1 sealed: 16 commits, 102 tests, full backend listing CRUD ready for Demo #6

**Date / where** Epic 2 Phase 1 T16 close, 2026-05-15
**Retrospective** Phase 1 ships 16 commits since `d956295` (Category seeder), landing the full read/write listing surface end-to-end:
- **3 entities** (`Listing` + 3 enums) with 4 named indexes
- **2 repositories** (`CategoryRepository`, `ListingRepository`) with 4 derived queries
- **6 DTOs** (3 request / 3 response) with Bean Validation + factory methods
- **1 service** (`ListingService`) with 6 public methods sharing the §7.4 validation chain
- **2 controllers** (`CategoryController`, `ListingController`) with 7 endpoints (1 + 6)
- **8 new ErrorCodes** from spec §4.0
- **102 tests** (51 unit + 51 integration; 0 flake after the singleton-container fix in D-41 and the deleteAllInBatch fix in D-54)
- **14 journal entries** (D-40..D-55) capturing every non-obvious decision

**Demo milestone #6 unlocks now.** User to walk through 6 endpoints + 8 transitions in Postman per `feedback_handson_demo.md`. Phase 2 (T17–T21, image upload + rate limits) starts after demo passes.

> 💡 中文要点：**Phase 1 收官**——16 个 commits、102 个测试、3 个 entity、2 个 repository、6 个 DTO、1 个 service（6 方法）、2 个 controller（7 端点）。Demo milestone #6 解锁，可以打开 Postman 端到端走一遍 listing 创建/查/改/状态转/删。下一步 Phase 2 接 image upload。**两次坑都来自"两个测试框架对生命周期不一致"族（D-41 容器、D-54 action queue）**——这种 bug 单跑都看不出，必须全量套件才暴露。

---

### D-56 — Epic 2 backend sealed: 128 tests, 18 integration tests cover every endpoint + edge case

**Date / where** Epic 2 Phase 3 T22/T23, 2026-05-15
**Retrospective** Phase 3 adds 15 integration tests to the 3 sanity cases from T16, bringing the `ListingControllerIntegrationTest` to 18 cases. Coverage now spans:
- **Create**: invalid category (400), SELL without price (400), GIVEAWAY ignores price, unauthenticated (401), round-trip create+listMine.
- **Get one**: full response fields verified.
- **Update**: fields change + image preserved; update REMOVED listing (400 LISTING_REMOVED).
- **Status FSM**: AVAILABLE→RESERVED→SOLD chain, SOLD→AVAILABLE reversibility, REMOVED→AVAILABLE invalid (400 INVALID_STATUS_TRANSITION).
- **Delete**: idempotent (204 twice), non-owner (404 anti-enumeration).
- **List mine**: status filter returns correct subset.
- **Image serving**: owner gets 200 + Cache-Control; non-owner gets 404.
- **Categories**: 8 seeded categories returned.

All tests use real multipart requests with a 1×1 JPEG generated in-memory. The `updateListing` helper supports optional image replacement. Total backend test count: **128** (32 service unit + 11 image storage + 18 listing integration + 9 auth integration + 7 listing repository + 4 schema + 7 DTO + 3 category + 37 other).

> 💡 中文要点：Phase 3 把集成测试从 3 个 sanity case 补到 18 个，覆盖所有 endpoint 的 happy path + error path。每个 spec §4 的错误码都有对应的集成测试断言。128 个测试全绿 = 后端质量门关闭。论文答辩时"你怎么保证质量"的回答：128 个自动化测试 + 每个 commit 全量跑通。

---

## Epic 3 — Public Browsing, Search & Filter

### D-57 — JPA Specifications over combinatorial derived queries for the browse endpoint

**Date / where** Epic 3 Phase 0 T1, 2026-05-15
**Choice** The public browse endpoint (`GET /api/listings`) uses `JpaSpecificationExecutor<Listing>` with composable `Specification<Listing>` predicates instead of Spring Data derived query method names. Five predicates compose via `.and()`: `hasStatusIn`, `titleContains`, `hasCategory`, `priceBetween`, `hasListingType`.
**Why** The browse endpoint has 5 optional filters. All combinations of present/absent filters would require 2^5 = 32 derived query methods (or a subset with ugly names like `findByStatusInAndTitleContainingIgnoreCaseAndCategoryAndPriceBetweenAndListingType`). Specifications compose cleanly — each filter is a one-liner lambda that's independently readable and testable. The `Specification.and()` chain builds the WHERE clause dynamically at runtime based on which query params are non-null.
**Trade-off accepted** Specifications are slightly more verbose than derived queries for simple single-filter cases (like the existing `findByOwner`). The project now uses both patterns: derived queries for owner-scoped reads (simple, fixed filters) and Specifications for the public browse (complex, dynamic filters). This is intentional — use the simplest tool that fits each case.

> 💡 中文要点：公开浏览端点有 5 个可选过滤器，组合起来 32 种排列。用 Spring Data 的 `Specification` 模式：每个过滤器是一个独立的 lambda 谓词，用 `.and()` 链式组合。运行时根据哪些参数非空动态构建 WHERE 子句。比 32 个 derived query 方法名干净得多。项目现在两种模式并存：简单固定查询用 derived query，复杂动态查询用 Specification——按场景选最简工具。

---

### D-58 — Separate `/api/listings/{id}/detail` endpoint rather than relaxing the existing `getOne`

**Date / where** Epic 3 Phase 0 T2, 2026-05-15
**Choice** A new public endpoint `GET /api/listings/{id}/detail` serves listing detail without authentication. The existing `GET /api/listings/{id}` (authenticated, owner-privileged) remains unchanged.
**Why** The existing `getOne` has owner-specific behavior: the owner sees their listing in *any* status including REMOVED. Relaxing it to public access would require conditional logic: "if user is authenticated AND is the owner, show REMOVED; otherwise hide it." This mixes two contracts in one method. A separate endpoint keeps each contract pure: `getOne` = owner view (all statuses, requires auth), `getDetail` = public view (non-REMOVED only, no auth). The frontend uses `getDetail` for the public browse flow and `getOne` for the owner's management flow — two different UX contexts, two different endpoints.
**Trade-off accepted** Two endpoints serve similar data for the same resource. This is intentional REST design: different representations for different audiences. The alternative (one endpoint with conditional behavior) would be fewer lines but harder to reason about, harder to test, and harder to secure.

> 💡 中文要点：不去"放宽"已有的 `getOne`（owner 视角，能看 REMOVED），而是新建 `getDetail`（公开视角，隐藏 REMOVED）。两个端点服务同一资源的不同表示——不同受众、不同契约、不同安全规则。混在一个方法里会引入"如果登录了且是 owner 则…否则…"的条件逻辑，难读难测难审计。

---

### D-59 — Image access relaxed from owner-only to status-based: non-REMOVED is public

**Date / where** Epic 3 Phase 0 T3, 2026-05-15
**Choice** `GET /api/uploads/listings/{filename}` now serves images publicly for listings whose status is AVAILABLE, RESERVED, or SOLD. Only REMOVED listing images still require owner authentication. The check is: resolve filename → find listing → if REMOVED and (no auth OR not owner) → 404; else serve.
**Why** The browse page and public detail page need to display listing images without login. The previous owner-only check (Epic 2 D-53) was correct for a seller-only system but blocks the buyer experience. REMOVED images stay protected to honor the soft-delete contract: a user who "deletes" their listing expects its image to disappear from public view. The status-based check is the minimal relaxation that enables public browsing while preserving the REMOVED privacy guarantee.
**Trade-off accepted** A non-owner can now view any non-REMOVED listing's image by guessing the UUID filename. This is acceptable because: (1) UUID v4 filenames are unguessable (122 bits of entropy); (2) the image is already visible on the public browse page anyway; (3) the only "private" images are REMOVED ones, which remain protected.

> 💡 中文要点：图片访问从"仅 owner"放宽到"按状态"——非 REMOVED 的 listing 图片对所有人可见（因为公开浏览页本来就要显示它们），REMOVED 的图片仍然只有 owner 能看（尊重软删除契约）。UUID 文件名不可猜测（122 bit 熵），所以"知道 URL 就能看"不构成安全风险。

---

### D-60 — SecurityConfig: additive permitAll rules before the authenticated catch-all

**Date / where** Epic 3 Phase 0 T4, 2026-05-15
**Choice** Four new `permitAll` matchers added to SecurityConfig *before* the existing `.requestMatchers("/api/**").authenticated()` catch-all: `GET /api/listings`, `GET /api/listings/*/detail`, `GET /api/uploads/listings/**`, `GET /api/categories`. The catch-all rule and all Epic 1/2 authenticated endpoints remain unchanged.
**Why** Spring Security evaluates request matchers in declaration order; first match wins. Adding public endpoints before the catch-all is purely additive — no existing behavior changes, no risk of accidentally exposing write endpoints. The alternative (restructuring the entire auth config into explicit per-endpoint rules) would be more "correct" but risks regressions in 8+ existing authenticated endpoints. The additive approach is the lowest-risk path for a thesis project where stability matters more than config elegance.
**Lesson** When evolving a SecurityConfig, prefer additive changes (new rules before the catch-all) over restructuring. Each additive rule is independently auditable: "this specific path is public because of this specific line." A restructured config requires reading the entire block to understand what's public vs. authenticated.

> 💡 中文要点：SecurityConfig 演进策略——**加法优于重构**。在 catch-all `.authenticated()` 规则前面加 4 行 `permitAll`，不动已有规则。Spring Security 按声明顺序匹配，先匹配先生效。每条新规则独立可审计："这个路径公开是因为这一行"。重构整个配置虽然更"正确"但风险高——8+ 个已有端点可能意外暴露。论文项目稳定性 > 配置美学。

---

---

## Epic 5 — Messaging Module

### D-61 — Two-table data model: denormalised `seller_id` in `conversation` for query ease

**Date / where** Epic 5 Phase 0, 2026-05-21
**Choice** The `conversation` table stores both `buyer_id` and `seller_id` as foreign keys. `seller_id` is technically redundant (it can be derived from `listing.owner_id`), but it is intentionally denormalised.
**Why** Every conversation query touches the caller's role: "return conversations where I am the buyer OR the seller." Without `seller_id` on the conversation row, every such query would require a JOIN to `listings` to resolve the seller identity. With `seller_id` denormalised, all per-user conversation queries resolve in a single table scan indexed on `buyer_id` / `seller_id`. At campus scale this is invisible, but it also makes the SQL dramatically simpler — no derived-table subqueries.
**Invariant maintained** `ConversationService.createOrGetConversation` is the only write path that creates a `conversation` row. It always copies `listing.owner.id` → `seller_id` at creation time. No other code sets `seller_id`. The invariant is maintained by construction, not by a CHECK constraint (MySQL 8.0 supports CHECK but JPA `ddl-auto=update` does not emit them from annotations).
**Trade-off accepted** If a listing's ownership were transferable, `seller_id` would become stale. Listing ownership is not transferable in this system (no endpoint to change owner), so the denormalisation is safe for the lifetime of the MVP.

> 💡 中文要点：`conversation` 表里直接存 `seller_id`（冗余自 `listing.owner_id`），避免每次"查我参与的会话"都要 JOIN listings。写入时由 `createOrGetConversation` 一处维护不变性。论文答辩时"为什么不归一化"的答案：campus scale 下性能差别可忽略，但查询简洁性和可读性收益实在。Listing 所有权不可转让，所以冗余字段不会过期。

---

### D-62 — Unread count via `last_read_at` timestamps, updated on message fetch

**Date / where** Epic 5 Phase 0, 2026-05-21
**Choice** Unread count is computed as `COUNT(messages where created_at > my_last_read_at AND sender_id != me)`. The `last_read_at` column (one per role: `buyer_last_read_at` / `seller_last_read_at`) is updated to `NOW()` whenever a user fetches the message list for a conversation.
**Why** Two alternatives were considered: (1) a boolean `read` flag per message row — simpler per-message but requires updating N rows on open, and adds a column to a high-write table; (2) a separate `read_receipt` junction table — fully general but overkill for a two-party conversation. The timestamp approach requires only two `TIMESTAMP` columns on `conversation` and a single `UPDATE` on message fetch. The trade-off is that unread counts are approximate when a user receives messages faster than the poll interval (5 s), but this is acceptable for the MVP.
**Side effect rule** `GET /api/conversations/{id}/messages` has an intentional side effect: it updates `last_read_at`. This is documented in the spec and in the controller comment. `GET /api/conversations/{id}` (metadata only) does *not* update `last_read_at` — reading metadata should not mark messages as read.

> 💡 中文要点：未读计数用 `last_read_at` 时间戳而非逐条 `read` flag。拉消息列表时顺带 UPDATE `last_read_at = NOW()`——一个副作用，但有文档说明。GET metadata 端点不触发 UPDATE，避免"只是看了一眼会话列表"就把所有消息标记已读。端到端验证：买家发消息 → 卖家 unread=1 → 卖家读消息 → unread=0。

---

### D-63 — FK constraint ordering in test teardown: messages → conversations → listings → users

**Date / where** Epic 5 Phase 0, 2026-05-21
**Symptom** After adding the `conversation` and `message` tables (with FK constraints to `listings` and `users`), the full test suite dropped from 128 → 157 tests but then failed with `DataIntegrityViolationException: Cannot delete or update a parent row: a foreign key constraint fails (conversations, CONSTRAINT fk_conversations_listing FOREIGN KEY (listing_id) REFERENCES listings (id))`. This broke the existing `ListingRepositoryTest`, `ListingControllerIntegrationTest`, `ListingSchemaIntegrationTest`, `AuthControllerIntegrationTest`, and `CategoryControllerIntegrationTest`.
**Root cause** All five test classes called `listings.deleteAllInBatch()` or `users.deleteAll()` in `@BeforeEach` without first cleaning child tables. Before the messaging module existed, no FK children of `listings` or `users` existed, so the cleanup worked fine. With `conversations` now referencing both `listings` (via `listing_id`) and `users` (via `buyer_id` / `seller_id`), and `messages` referencing `conversations`, the deletion order must respect the FK hierarchy: messages → conversations → listings → users.
**Fix** Injected `MessageRepository` and `ConversationRepository` into the five affected test classes and added `messages.deleteAllInBatch(); conversations.deleteAllInBatch();` before the existing `listings.deleteAllInBatch(); users.deleteAllInBatch();`. Consistent with D-54 lesson: always use `deleteAllInBatch()` (direct SQL) rather than `deleteAll()` (Hibernate action queue) for cross-test-class cleanup.
**Pattern** This is the third instance of the "test lifecycle silent disagreement" family (D-41 container singleton, D-54 Hibernate flush order, D-63 FK cleanup ordering). The pattern: adding a new FK-constrained table silently breaks cleanup in every test class that deletes from the parent table. Checklist for future tables: whenever a new entity references an existing one, search all `@BeforeEach` cleanup methods for `deleteAllInBatch` on the parent table and prepend child cleanup.

> 💡 中文要点：新增 FK 约束表后，5 个已有测试类的清理顺序全错了——全套跑才暴露，单跑没问题（同 D-41/D-54 族）。修法：在所有 `@BeforeEach` 里按 FK 依赖逆序清理：messages → conversations → listings → users。经验法则：每新增一个引用已有表的实体，就搜全仓库所有 `deleteAllInBatch` 调用，在父表清理前面插子表清理。

---

## Post-Epic-5 Polish

### D-64 — `getDetail` JOIN FETCH owner to prevent LazyInitializationException on public detail page

**Date / where** Post-Epic-5 polish, 2026-05-21
**Symptom** The public listing detail page (`GET /api/listings/{id}/detail`) intermittently returned a 500 with `LazyInitializationException: could not initialize proxy [User#N] - no Session`. The error appeared when the response serializer tried to read `listing.getOwner().getNickname()` to populate `ListingResponse.ownerNickname`.
**Root cause** `ListingService.getDetail` used `listings.findById(id)` which loads the `Listing` entity but leaves `@ManyToOne(fetch = LAZY) owner` as an uninitialized proxy. The method is annotated `@Transactional(readOnly = true)`, so the session is open during the method body — but `ListingResponse` construction happens inside the same method, so the proxy *should* be resolvable. The actual trigger was Spring's OpenSessionInView being disabled in test profile (`spring.jpa.open-in-view=false`), causing the session to close before serialization in integration tests. In production with OSIV enabled it worked by accident.
**Fix** Added `ListingRepository.findByIdWithOwner(Long id)` with `@Query("SELECT l FROM Listing l JOIN FETCH l.owner WHERE l.id = :id")`. Switched `getDetail` to use it. The owner is now eagerly loaded in the same query — no proxy, no session dependency, works regardless of OSIV setting.
**Lesson** This is the same family as D-25 (test `@Transactional` for lazy proxies) but in production code. Rule: any service method that *reads* a lazy association and *returns* a DTO built from it must either (1) use JOIN FETCH, (2) use an `@EntityGraph`, or (3) access the proxy inside an open session guaranteed by the method's own `@Transactional`. Relying on OSIV is fragile — it's a view-layer crutch that hides missing fetches until you disable it (which you should for performance). Prefer explicit JOIN FETCH: it documents the data contract in the query itself.

> 💡 中文要点：公开详情页 500 错误——`getDetail` 用 `findById` 拿到 Listing 后访问 `owner`（LAZY proxy），在 OSIV 关闭的环境下 session 已关，触发 `LazyInitializationException`。修法：新增 `findByIdWithOwner` 用 `JOIN FETCH l.owner` 一次查出。教训：任何 service 方法如果要读 LAZY 关联并构建 DTO 返回，必须显式 JOIN FETCH——不要依赖 OSIV 这个"视图层拐杖"。

---

### D-65 — Navbar and HomePage flash wrong controls during auth bootstrap (loading state gate)

**Date / where** Post-Epic-5 polish, 2026-05-21
**Symptom** On page load (or hard refresh), the Navbar briefly showed "Log in / Sign up" buttons for ~200ms before switching to the logged-in state (nickname + logout). Similarly, the HomePage always showed "Sign Up" even for authenticated users.
**Root cause** `useAuth()` returns `{ user, loading }`. During the initial auth check (`GET /api/auth/me`), `user` is `null` and `loading` is `true`. The Navbar rendered the `!user` branch (guest controls) without checking `loading`, causing a flash-of-incorrect-content (FOIC). The HomePage never consumed auth state at all — it unconditionally rendered the "Sign Up" CTA.
**Fix** (1) Navbar: gate both the logged-in and logged-out sections behind `!loading` — render neither during bootstrap. (2) HomePage: import `useAuth`, show "Sell an Item" (→ `/listings/new`) when logged in, "Sign Up" when not, and nothing during loading. Both fixes use the same pattern: `{!loading && user && (...)}` / `{!loading && !user && (...)}`.
**Pattern** This is the standard "async auth gate" pattern for SPAs. Any component that conditionally renders based on auth state must handle three states: loading (show nothing or skeleton), authenticated, unauthenticated. Checking only `user` vs `!user` always produces FOIC on cold load. Checklist for future auth-dependent UI: always destructure `loading` from `useAuth()` and gate on it.

> 💡 中文要点：页面刷新时 Navbar 闪烁"Log in"再跳到"Hi, xxx"——因为 `useAuth()` 初始状态 `user=null, loading=true`，组件只判断了 `user` 没判断 `loading`。修法：三态渲染——loading 时什么都不显示，加载完再按 user 有无分支。HomePage 同理：登录后显示"Sell an Item"而非"Sign Up"。SPA 认证 UI 的标准模式：永远处理 loading / authed / guest 三个状态。

---

*Last updated: 2026-05-22 — Post-Epic-5 polish (D-64..D-65). Two bugfixes: backend JOIN FETCH owner in getDetail, frontend auth-loading gate in Navbar + HomePage. 157 backend tests still green.*

---

## Milestone 6 — Polish, Testing, Deployment

### D-66 — Full Playwright suite green (1/8 → 22/22) by fixing four orthogonal failure modes at once

**Date / where** Milestone 6 Phase A, 2026-05-22
**Symptom** Across six E2E spec files (auth-flow, browse-search-flow, create-flow, edit-flow, messaging-flow, status-flow) the suite failed catastrophically the very first time it was run end-to-end: 1/8 auth tests passed, the rest timed out on `input[name="email"]`. Earlier runs of `create-flow` / `edit-flow` / `status-flow` had passed in isolation during Epic 2/3 development, but had drifted into rot once the suite size and shared state grew.
**Root causes** (four independent bugs, each masking the next as it was fixed)
1. **Missing `name` attributes on auth forms.** LoginPage / RegisterPage / ForgotPasswordPage / ResetPasswordPage used controlled-input React components with no `name=` attribute. Selectors of the form `input[name="email"]` could never match. Listing forms (`CreateListingPage`) were already FormData-based and had `name`, which is why those specs *appeared* to work.
2. **Backend rate limiting trips after the 4th test in a file.** Registration is throttled to 3/hour per IP. The auth-flow file alone needs ~8 registrations; messaging-flow + create/edit/status add ~10 more. Every test from #4 onward failed with "Too many registrations from your network." The DOM showed the error clearly, but the timeout signature on `waitForURL('/')` looked like a selector problem.
3. **`__dirname` is undefined in ESM specs.** The frontend's `package.json` has `"type": "module"`, so all `.spec.ts` files run as ES modules. `path.resolve(__dirname, 'fixtures/test-image.jpg')` worked under CommonJS but throws `ReferenceError: __dirname is not defined` once you actually run it.
4. **Brittle `text=` selectors.** `text=Free` matched the `<option>Free</option>`, the giveaway label *and* the price column — Playwright strict mode (correctly) refused. Same for `text=Edit` (button + nav link), `text=AVAILABLE` (status badge + toast notification). `[data-testid="conversation-item"]` was speculative — the component had no such testid.
**Fix**
- Added `name="email|password|nickname|confirmPassword"` to nine controlled inputs across the four auth pages (also a quality-of-life win for password managers).
- Created `e2e/helpers/rateLimit.ts`: `docker exec campus_redis redis-cli FLUSHDB`. Called from `beforeEach` in every spec.
- Created `e2e/helpers/paths.ts` with `e2eFixture(import.meta.url, 'fixtures/test-image.jpg')` — ESM-safe resolution via `fileURLToPath`. Replaced all `path.resolve(__dirname, ...)` calls.
- Tightened selectors: `button:has-text("Mark Reserved")`, `a:has-text("Edit")`, `span:has-text("AVAILABLE")`, `main ul li button` for conversation rows. Dropped the `[data-testid]` speculation.
- Reframed the "no-image upload" test from "expect alert text" to "expect URL stays on /listings/new" — HTML5 `required` blocks submission *before* JS runs, so the alert never fires.
- Added Playwright output to `frontend/.gitignore` (`test-results/`, `playwright-report/`, `blob-report/`, `playwright/.cache`).
**Pattern** Tests that have never been run end-to-end are not tests — they are commented-out documentation. The suite was written across multiple sessions with each spec verified in isolation, but isolation hides three of the four failure modes above. Lesson: any time you add a new spec or move to a new test runner, run the whole suite *immediately* and fix what falls out — don't wait for "later integration." A green CI badge buys nothing if it's testing the wrong thing.

> 💡 中文要点：六个 E2E spec 第一次合并跑全军覆没（1/8 通过），逐个挖出四层独立 bug：(1) Auth 表单的受控 input 缺 `name` 属性，所有 `input[name="email"]` 选择器无法命中；(2) 后端注册限流 3/h，跑到第四个测试就被拦；(3) ESM 模式下没有 `__dirname`，图片 fixture 路径解析报错；(4) `text=Free` / `text=Edit` 这种宽松选择器在 strict mode 下匹配多个元素直接失败。修复手法：补 `name`、写 Redis flush helper（`beforeEach` 清限流）、写 `e2eFixture(import.meta.url, ...)` ESM-safe 路径工具、把所有选择器收紧到 `button:has-text` / `a:has-text` / 显式 DOM 路径。最终 22/22 全绿，34 秒跑完。教训：从来没真正跑过的测试 = 反向文档。每加一个 spec 或换 test runner 就立刻跑全套，别等"以后集成时再说"。

---

### D-67 — UI consistency pass: unify on blue-600 / slate-300 / rounded-md / red-50 design tokens across 8 files

**Date / where** Milestone 6 Phase B, 2026-05-22
**Symptom** Walking the UI page-by-page revealed three coexisting visual languages: auth pages used `bg-slate-900` (near-black) buttons with `focus:border-slate-900`; listing CRUD pages used `bg-blue-600` buttons with raw `border rounded` inputs (no shadow, no focus colour, mismatched radius); browse/home pages used a third hybrid. Error states were equally fragmented: some pages rendered `<p className="text-red-600">`, others used `<div className="bg-red-50 ... text-red-700">`, and one (MePage) just dumped raw JSON to the screen.
**Root cause** No design system. Each page was implemented in isolation during its own epic, with whatever Tailwind classes felt right at the time. The codebase grew faster than the eye could audit. By the end of M5, ~28 className inconsistencies were spread across 8 files.
**Fix** Picked the blue-600 family (warmer, more "marketplace" than slate-900) and converted everything in one sweep:
- **Buttons** → `rounded-md bg-blue-600 text-white hover:bg-blue-700` (LoginPage, RegisterPage, ForgotPasswordPage, ResetPasswordPage, Navbar Sign Up)
- **Inputs** → `rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-600 focus:outline-none` (CreateListingPage 9 fields, EditListingPage 9 fields, BrowsePage search + 3 selects + pagination, MyListingsPage pagination, PasswordInput component)
- **Page-level errors** → `rounded-md bg-red-50 p-3 text-sm text-red-700` banner (5 pages converted from inline `<p text-red-600>`)
- **Field-level inline hints** kept as small `text-red-600 text-xs` next to inputs (different intent: per-field validation vs. page-level failure)
- **Links** → `text-blue-600 hover:underline` (was `text-slate-900` on auth pages)
- **MePage** rewritten from raw JSON dump to a proper profile card (avatar circle with first-letter monogram + nickname/email + quick-action grid linking to /listings/mine /conversations /listings/new)
- Added a global `<ErrorBoundary>` wrapping all routes — instead of a white-screen-of-death on a runtime exception, users see a "Something went wrong" panel with a Try again button.
- Replaced bare `<p>Loading...</p>` in 5 pages with a reusable `<Spinner>` component (animated SVG + label).
- ConversationsPage empty state upgraded from a one-line `<p>` to a centered chat-bubble icon + headline + CTA, matching BrowsePage / MyListingsPage.
**Pattern** Design systems work best when they're *extracted* from real code, not invented up-front. By M5 the patterns had emerged organically — the unification step was just picking the best variant of each (the listings-pages input style won; the auth-pages button colour lost) and propagating it. Tailwind's class-based approach makes this trivially mechanical: one `Edit` per className substring per file. The "shared design token" only existed implicitly until D-67 — now there's a documented vocabulary in `frontend/README.md` so future pages don't drift.

> 💡 中文要点：UI 走查后发现 8 个文件里有 28 处样式不一致——auth 页用 `bg-slate-900` 黑按钮、listing 页用 `bg-blue-600` 蓝按钮、错误提示一会儿是 `<p text-red-600>` 一会儿是红底框、MePage 直接 `<pre>{JSON.stringify(user)}</pre>` 显示原始数据。一次性统一为：按钮 `rounded-md bg-blue-600`、输入框 `rounded-md border-slate-300 shadow-sm focus:border-blue-600`、页面级错误 `bg-red-50 rounded-md text-red-700` 横幅、字段级小红字保留、MePage 改造成 profile card（头像圈+昵称+快捷操作）。同时加了全局 `<ErrorBoundary>` 防白屏、`<Spinner>` 替代 5 处 `<p>Loading...</p>`、ConversationsPage 空状态加图标。教训：设计系统是从真实代码里"提炼"出来的——不是事先想好的。等模式自然涌现后，用 Tailwind 的 className 做机械替换，一次收口。

---

### D-68 — JaCoCo coverage baseline: 87% instruction / 76% line / 62% branch across 157 tests

**Date / where** Milestone 6 stage II, 2026-05-22
**Symptom** "157 tests" is a cardinality, not a quality signal — a thesis defender or collaborator looking at the project has no way to tell whether those tests cover the whole service layer or just `HealthController`. Without a coverage number, claims like "well tested" cannot be cross-checked.
**Fix** Added `jacoco-maven-plugin 0.8.12` to `backend/pom.xml` with two executions wired to the existing test phase:
- `prepare-agent` — boots the JaCoCo Java agent before Surefire runs, so every test contributes to `target/jacoco.exec`.
- `report` — bound to the `test` phase, generates `target/site/jacoco/index.html` after each `./mvnw test` run with no extra command needed.
The HTML report renders per-package and per-class drill-down with red/yellow/green code highlighting. Also added `backend/target/` to `.gitignore` so the binary trace and HTML output don't pollute the repo.
**Baseline numbers** (157 tests, full suite green):
- **Instructions: 87%** (3,746 covered / 4,294 total)
- **Branches: 62%** (190 covered / 304 total)
- **Lines: 76%** (649 / 849)
- **Methods: 77%** (273 / 353)
- **Classes: 78%** (91 / 116)
**Pattern** Branch coverage is the lowest at 62% because controllers and exception mappers contain many short-circuit paths (rate-limit fast-path, soft-delete check, owner-vs-admin authorisation) that the happy-path tests skip. Instruction coverage of 87% is the more honest signal of "did each line get exercised once" — branches need targeted negative-path tests to climb. Future work: target conditional branches in `AuthService`, `ListingService`, and `ConversationService` (the three services with the most authorisation logic), aim for 75% branch.
**Read it yourself** Run `./mvnw test`, then open `backend/target/site/jacoco/index.html` in a browser. The Total row at the bottom shows the same numbers; click into any package to see per-class hot/cold zones.

> 💡 中文要点：把 "157 个测试" 这个数变成有质量保证的指标。加 jacoco-maven-plugin（2 个 execution：`prepare-agent` 装 Java agent + `report` 在 test 阶段生成 HTML）。基线：指令覆盖 87%、行 76%、分支 62%、方法 77%、类 78%。分支最低（62%）是因为控制器/异常映射器有很多短路路径（限流早返回、软删除判断、权限分支）只有快乐路径测试覆盖；要拉到 75%+ 需要针对 AuthService/ListingService/ConversationService 写负路径测试。运行 `./mvnw test` 后打开 `backend/target/site/jacoco/index.html` 即可逐包逐类查看红黄绿热力图。

---

### D-69 — GitHub Actions CI: parallel backend + frontend jobs, fix the one untyped @SpringBootTest that broke it

**Date / where** Milestone 6 stage III, 2026-05-22
**Symptom** No automated quality gate. The 157 backend tests, 14 frontend unit tests, and 22 Playwright E2E tests only ran when someone remembered to run them locally. A push could land a regression and stay green on `main` for days.
**Fix** Added `.github/workflows/ci.yml` with two parallel jobs on push/PR to main:
- `backend`: `setup-java@v4` (temurin 21) + Maven cache → `./mvnw -B test`. Testcontainers spins MySQL + Redis on demand because Docker is preinstalled on `ubuntu-latest`. Uploads JaCoCo report as a 14-day artifact and Surefire reports on failure.
- `frontend`: `setup-node@v4` (Node 20) + npm cache → `npm ci` → `lint` → `test` (Vitest) → `build` (tsc + Vite).
**The one breaking test** First CI run failed with `Tests run: 157, Failures: 0, Errors: 1`. The single failure: `BackendApplicationTests.contextLoads`, the Spring Boot Initializr default smoke test. It had bare `@SpringBootTest` and no profile, so it loaded ApplicationContext against the default `application.properties` — `localhost:3306` MySQL and `localhost:6379` Redis. Worked locally because the dev `docker compose` stack runs there; failed on the GitHub runner where neither service exists. Every other test extended `AbstractIntegrationTest` (which has the Testcontainers wiring + `@ActiveProfiles("test")`).
**One-line fix** `class BackendApplicationTests extends AbstractIntegrationTest`. The smoke test now picks up the same Testcontainers-managed MySQL + Redis the rest of the suite uses. Zero business code touched.
**E2E still NOT in CI** Playwright depends on the docker compose stack + a running backend + a running frontend dev server. Wiring all three into a CI runner is a meaningful project on its own. Listed for future work; for now E2E is run locally before pushing.
**Pattern** The default test from `start.spring.io` is a trap — it ships with `@SpringBootTest` but no profile and no awareness of the project's test infrastructure. The fix is so small (`extends AbstractIntegrationTest`) that the right move is to either delete that default test or align it the day you wire up Testcontainers, not weeks later when CI exposes it.

> 💡 中文要点：上 GitHub Actions CI——两个并行 job（backend Maven + frontend lint/unit/build）。第一次跑 backend 挂了 `BackendApplicationTests.contextLoads`：Spring Initializr 默认生成的冒烟测试只用裸 `@SpringBootTest` 没有 profile，本地连 dev docker 的 localhost:3306/6379 能通，CI runner 上没有服务直接加载失败。修法一行：`extends AbstractIntegrationTest` 复用 Testcontainers 配置。E2E 暂未入 CI（依赖 docker compose + backend + frontend dev server，单独项目）。教训：start.spring.io 默认那个测试是个坑，要么删要么从一开始就接入项目的测试基础设施，别等 CI 才发现。

---

### D-70 — Adopt Prettier with no-semi single-quote 100-col rules + .editorconfig at repo root

**Date / where** Milestone 6 stage III, 2026-05-22
**Symptom** ESLint already enforced rules but didn't normalise whitespace, quotes, line breaks, or trailing commas. Different sessions produced files with inconsistent quote style and bracket placement; PRs would have noisy whitespace diffs.
**Fix** Three small config files + one one-shot reformat:
- `.editorconfig` at repo root: `utf-8 / lf / 2-space indent` everywhere except Java/XML (4) and Makefile (tab); preserves trailing whitespace in `*.md`.
- `frontend/.prettierrc.json`: no semi, single quotes, 100-col, `trailingComma: es5`, `arrowParens: avoid`, `endOfLine: lf`.
- `frontend/.prettierignore`: `dist/`, `node_modules/`, `test-results/`, `playwright/`, lock files, `*.md` (markdown is its own dialect).
- Added Prettier 3.8.3 (pinned exact) + `format` and `format:check` npm scripts.
- Ran `npm run format` once: 22 files reformatted, 0 logic changes; lint still 0-error, 14/14 Vitest tests still green, build still 286 kB.
**Why downgrade `react-hooks/set-state-in-effect` and `react-hooks/exhaustive-deps` to warn** New in `eslint-plugin-react-hooks` 6.x, the rule flags the standard "loading + error + fetch" pattern at the top of an effect body. The pattern is correct and ubiquitous; React docs suggest TanStack Query / SWR as alternatives but that is an architectural change beyond MVP scope. Keeping them as warn surfaces them in dev without failing CI.
**Why `react-refresh/only-export-components` survived as error** Initially the file `src/components/Toast.tsx` exported both `ToastProvider` (component) and `useToast` (hook), which the rule disallows because it breaks Fast Refresh. Splitting them: `useToast` and `ToastContext` moved to `src/components/useToast.ts`, `Toast.tsx` only exports `ToastProvider`. Three call sites updated. The split is the right thing to do for Fast Refresh anyway.
**Lesson** A formatter is worth adopting once enough hand-formatting drift accumulates that PRs start carrying whitespace noise. Earlier than that and it's premature; later than that and you're paying for the reformat at a moment when other things matter more. Sweet spot is "after the design is stable, before merging more contributors."

> 💡 中文要点：加 Prettier + .editorconfig，统一格式化（no-semi、单引号、100 列）。新增 22 文件 reformat 一次性差异是纯空白/引号/换行，无逻辑变化，lint/unit/build 全过。同时降级 `react-hooks/set-state-in-effect`+`exhaustive-deps` 为 warn（新规则误伤标准 effect 模式），把 `react-refresh/only-export-components` 真正修了——把 `useToast` hook 从 `Toast.tsx` 拆到独立 `useToast.ts`（hook 和 component 同文件违反 Fast Refresh）。教训：格式化器在"设计稳定但合并更多贡献者前"接入最划算。

---

### D-71 — Frontend unit test coverage: 14 → 37 tests by adding 5 suites for core hooks/components

**Date / where** Milestone 6 stage III, 2026-05-22
**Symptom** Frontend unit coverage was thin: only `apiClient.ts` (6 tests) and `useUnreadCount.ts` (8 tests) were tested. The four other hooks (`useChatPolling`, `useUnsavedChangesGuard`, `useBrowserNotification`), the auth gate (`ProtectedRoute`), the global error UI (`ErrorBoundary`), and the toast hook (`useToast`) had zero tests despite being on every protected page or in every chat session.
**Fix** Added 5 suites, 23 new tests, 14 → 37 total:
- `useUnsavedChangesGuard.test.ts` (5): registers/removes `beforeunload` only when `isDirty=true`; flips on rerender; cleans up on unmount; the handler itself calls `preventDefault` and sets `returnValue`.
- `useToast.test.tsx` (4): throws when used outside `ToastProvider`; returns the context value when wrapped; success/error helpers route to the right callback; render util correctly imported.
- `ErrorBoundary.test.tsx` (4): renders children when no error; default fallback shows `error.message` + Try Again button when a child throws; custom fallback rendered when provided; clears error state when Try Again is clicked.
- `ProtectedRoute.test.tsx` (4): renders children when authenticated; loading placeholder while bootstrapping; redirects to `/login` with `?next` when unauthenticated; preserves search params in `?next` (covers `/me?foo=bar` → `next=%2Fme%3Ffoo%3Dbar`).
- `useChatPolling.test.ts` (6): no fetch when `conversationId === null`; initial fetch on mount; cursor-based incremental fetch on each 5s poll; `addOptimistic` appends + advances cursor so next poll asks `after={optimisticId}`; silently ignores polling errors; cleans up the interval on unmount.
**Pattern** Two failure modes were nearly hit while writing these:
1. `MemoryRouter` plus the browser global `location` are different things; the first version of `ProtectedRoute.test.tsx` read `location.search` from the JSDOM browser stub instead of react-router state. Fixed by extracting a `LoginProbe` component that reads `useLocation()`.
2. `vi.spyOn(console, 'error').mockImplementation(() => )` (missing `{}`) is a syntax error Vitest catches at file load — easy to ship if you don't run the suite. The test runner is the safety net.
**Lesson** Tests for hooks split cleanly by concern: `renderHook` for pure logic, `render` + a probe component when the hook drives navigation/routing. Mocking the API module via `vi.mock` keeps the tests fast (37 tests in 1.3s) and pinpoints the contract: each hook's test file documents exactly what the hook promises its callers.

> 💡 中文要点：前端单测从 14 → 37（5 份新文件 / +23 测试）。覆盖 useUnsavedChangesGuard、useToast、ErrorBoundary、ProtectedRoute、useChatPolling 五个核心 hook/组件。要点：`renderHook` 测纯逻辑，`render` + 探针组件测路由相关；用 `vi.mock` mock API 模块让测试 1.3 秒跑完 37 个；过程中差点被两个坑：(1) `MemoryRouter` 的 location ≠ JSDOM 浏览器 `location`，要用 `useLocation()` 探针读取 router 状态；(2) `mockImplementation(() => )` 缺 `` 是语法错误，必须真跑一次测试套件确认（再次印证 D-66 那条"没真跑过的测试 = 反向文档"）。

---

### D-72 — Frontend test runner: chose Vitest over Jest, with `vitest/config` triple-slash directive trick

**Date / where** Milestone 6 stage I, 2026-05-22 (commit `76931e6`)
**Symptom** Frontend had zero unit tests after Epic 5 shipped. Need a test runner. Two real options for a Vite project: Jest (industry default, but heavy config for ESM + TS + JSX) or Vitest (built on Vite, native ESM/TS, share `vite.config.ts`).
**Decision** Vitest, for three concrete reasons:
1. **Zero config duplication.** Vitest reads the same `vite.config.ts` as the dev server. Jest needs `babel.config.js` or `ts-jest` plus `jest.config.js` plus an ESM preset.
2. **TypeScript native.** No transformer; uses Vite's esbuild pipeline. Jest still needs `ts-jest` or `@swc/jest`.
3. **Compatible matcher API.** `describe / it / expect / vi` mirror Jest, so existing Jest knowledge transfers.
**Setup**
```bash
npm i -D vitest jsdom @testing-library/react @testing-library/jest-dom @vitest/coverage-v8
```
Then in `vite.config.ts` added a `test` block (env `jsdom`, globals true, `include: src/**/*.test.{ts,tsx}`, `exclude: e2e/**`).
**The `vitest/config` triple-slash directive trick** Adding the `test` key to `defineConfig` makes TS complain that `'test' does not exist in type UserConfig`. The fix is the directive that **must** sit at the top of `vite.config.ts`:
```ts
/// <reference types="vitest/config" />
```
This widens `defineConfig`'s type to include the test block. The first attempt used `/// <reference types="vitest" />` which compiled but didn't widen the type — symptom was `Property 'test' does not exist on type 'UserConfig'`. Fixed in commit `ab15b61` long after, but the trap is worth remembering.
**Initial coverage** Two test files at adoption: `apiClient.test.ts` (6) and `useUnreadCount.test.ts` (8). Mocking strategy: `vi.mock('../api/conversations')` for hook tests; `globalThis.fetch = vi.fn()` for the API client. Both fast (<200ms total).
**Lesson** Use the runner native to your bundler. The friction of dual-config drift across Jest + Vite would have eaten more time than the marginal industry-default value. Triple-slash directive is a TS quirk — one searchable phrase ("vitest/config reference type") solves it; without it you waste 15 minutes wondering why the types are wrong.

> 💡 中文要点：前端测试用 Vitest 不用 Jest——三个理由：与 vite.config.ts 共享配置零重复、TypeScript 原生支持、API 跟 Jest 一致迁移成本零。一个隐蔽坑：`vite.config.ts` 顶部必须写 `/// <reference types="vitest/config" />` 才能让 TS 识别 `test` 块；写成 `<reference types="vitest" />` 编译过但类型不扩展，会报 `Property 'test' does not exist on type 'UserConfig'`。教训：用与你 bundler 同源的 test runner，少配置漂移。

---

### D-73 — springdoc-openapi 2.6.0 → 2.8.0: Spring Framework 6.2 deleted ControllerAdviceBean(Object) constructor

**Date / where** Milestone 6 stage II, 2026-05-22 (commit `fa3e256`)
**Symptom** Added `springdoc-openapi-starter-webmvc-ui:2.6.0`, configured `OpenApiConfig` with project metadata and a cookie-auth scheme, started the backend, hit `GET /v3/api-docs`. Got HTTP 500. Backend logs:
```
java.lang.NoSuchMethodError: 'void
org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
  at org.springdoc.core.providers.SpringDocProviders...
```
`/swagger-ui/index.html` returned HTTP 200 (static HTML loaded) but the schema endpoint backing it was broken, so the UI loaded with no operations.
**Root cause** Spring Boot 3.4.4 ships with **Spring Framework 6.2**, which **removed** the public `ControllerAdviceBean(Object)` constructor (kept only `(String beanName, BeanFactory, ControllerAdvice)`). springdoc 2.6.0 was compiled against the old constructor and calls it via direct `new ControllerAdviceBean(advice)`. Reflection found no method matching the old signature → `NoSuchMethodError` at runtime, not compile time, because the change is binary-incompatible but source-level invisible until invoked.
**Fix** Bump to `springdoc-openapi-starter-webmvc-ui:2.8.0` (one-line `pom.xml` change). Verified: 18 endpoints + 18 schemas auto-discovered, all 157 backend tests still green, `/v3/api-docs` returns OpenAPI 3.1.0 JSON.
**Pattern** When picking a Spring ecosystem dependency, **check its compatibility matrix against the Spring Boot major.minor you're on**, not just "latest stable." springdoc maintains the matrix in their docs:
- 2.6.x → Spring Boot 3.3.x
- 2.7.x → Spring Boot 3.3.x / 3.4.x (early)
- 2.8.x → Spring Boot 3.4.x ✅
The "latest version" trap is real: I picked 2.6.0 because it appeared in old StackOverflow answers as "stable for Spring Boot 3.x." That was true a year ago. Always cross-reference release notes against your runtime version. Rule of thumb: **runtime errors in the Spring ecosystem that mention `NoSuchMethod` are almost always a Spring Framework / Boot version mismatch, not a bug**.

> 💡 中文要点：加 springdoc-openapi 时踩了版本不匹配坑。2.6.0 调用 `ControllerAdviceBean(Object)` 构造器，但 Spring Framework 6.2（随 Spring Boot 3.4 引入）已删除该构造器，改成 `(String, BeanFactory, ControllerAdvice)`。运行时（不是编译期）报 `NoSuchMethodError`。修法：`pom.xml` 一行升级到 2.8.0（这版才支持 Spring Boot 3.4.x）。教训：选 Spring 生态依赖永远先查它的兼容矩阵对应你的 Spring Boot major.minor，不是查"latest stable"。Spring 生态里运行时 `NoSuchMethod` 几乎都是 Spring Framework/Boot 版本不匹配，不是 bug。

---

### D-74 — `architecture.md`: capture the design decisions Swagger and entity classes can't (Redis-vs-MySQL, polling-vs-WebSocket)

**Date / where** Milestone 6 stage II, 2026-05-22 (commit `20e5fb5`)
**Symptom** Swagger documents the API surface; JPA entity classes document the schema. Neither captures the **design decisions** that shaped the system: why is the password reset token in Redis and not MySQL? Why does messaging poll every 5s instead of using WebSocket? Without an architecture document, these decisions live only in commit history and the engineering journal — readable for the author, opaque for an outsider.
**Fix** Created `docs/architecture.md` with three sections:
1. **ER diagram** (Mermaid) for all 5 tables. Field types, FK constraints, soft-delete columns, composite indexes. Inline notes:
   - **Why no `password_reset_tokens` table?** Tokens are short-lived (15 min TTL) and high-cardinality. Redis with native expiration beats a row that needs cleanup. (D-21)
   - **Why no `jwt_blacklist` table?** Logout writes the token's `jti` to a Redis set with TTL = remaining JWT validity. Same reasoning. (D-15)
2. **Auth sequence diagram** (Mermaid). Register → cookie issuance → authenticated request → blacklist check → logout. Shows the `XSS-resistant cookie` trade-off: HttpOnly defeats `localStorage` token theft, in exchange for needing CSRF mitigation (`SameSite=Lax` + ignoring form-encoded requests, see D-9).
3. **Messaging sequence diagram** (Mermaid). `Contact seller` idempotency (`UNIQUE(listing_id, buyer_id)` is the anchor), optimistic message append, two polling loops (15s unread, 5s active chat), visibility-aware pause. Records the polling-vs-WebSocket trade-off: MVP scope, polling is trivial to test/debug/observe; WebSocket adds connection lifecycle, reconnect logic, another moving piece in the load picture.
**Why Mermaid in Markdown** GitHub renders Mermaid natively in `.md` files since 2022. No external tool, no `.png` checked in, no separate hosting. The diagram is also diff-able (PR review can see "the buyer node moved" not "the picture changed"). Trade-off: Mermaid is less polished than draw.io / Lucid, but the source-control-native value wins for an engineering doc.
**Lesson** A working system has three audiences for documentation: API consumers (Swagger covers it), schema users (entity classes + ER diagram), and architecture readers (the *why*). Skip any one and the system is harder to evolve — outsiders ask the same "why not X?" questions over and over. The diagrams are short (three of them, ~150 lines of Mermaid) but every line answers a question that would otherwise require reading 1000 lines of code.

> 💡 中文要点：Swagger 描述 API 表面、entity 类描述 schema，但都不解释**设计决策**。新建 `docs/architecture.md` 三部分：(1) Mermaid ER 图（5 张表）+ 行内注释为什么 password reset token 和 JWT blacklist 在 Redis 不在 MySQL；(2) auth 时序图，标注 HttpOnly cookie 抗 XSS 但需要 CSRF 缓解的取舍；(3) messaging 时序图，记录 `UNIQUE(listing_id, buyer_id)` 作幂等锚点 + 为什么用 polling 而非 WebSocket（MVP 复杂度取舍）。Mermaid 在 GitHub 原生渲染，可 diff，比 draw.io 适合工程文档。教训：系统有三类读者——API 用户（Swagger 够）、schema 用户（entity + ER 图）、架构读者（要 why）；缺任何一类，外部人都会反复问同样的"为什么不 X"。

---

### D-75 — Real email verification with a *soft* gate (only POST listings + send message), Redis-stored token mirroring D-21

**Date / where** Milestone 6 stage IV, 2026-05-22 (commit `fe15ea0`)
**Symptom / starting point** MVP scope (`docs/mvp-scope.md`) lists "Email verification service" as Out of Scope, so registration accepted any campus-domain address as authoritative. With auth, listings, browsing, and messaging all shipped, the gap was visible: a typo in the email field still produced a working account that could post listings and message strangers, and there was no path to recover the address. Time to close it without rewriting the auth flow.
**Design decision: soft gate, not hard gate** A hard gate (block login until verified) was rejected for two reasons:
1. **Graceful UX during the verification round-trip.** Users register, click the link in the welcome email, get bounced into a half-broken state if anything in between fails — far worse than letting them browse while the email arrives.
2. **Demo-friendliness.** Local Docker-compose has no real SMTP. A hard gate would require either wiring a fake mail server into the dev story or shipping a dev-only "auto-verify" toggle, both of which leak into production code paths.
The soft gate places the check at the two state-changing endpoints that involve other users: `POST /api/listings` and `POST /api/conversations/{id}/messages`. Reading is unrestricted; writing-to-others requires verification. The check is the **first** statement in `ListingService.create` and `ConversationService.sendMessage` so the contract is obvious to a reader of those methods, not buried in a filter.
**Token storage: Redis, mirroring D-21** 48 random bytes, URL-safe Base64, key `auth:verify:{token}`, TTL 24h. Same shape as the password-reset token (D-21) and JWT blacklist (D-15) — short-lived, high-cardinality, native expiration. A `password_reset_tokens` or `email_verification_tokens` MySQL table would need a janitor job; Redis just expires the key. `consume(token)` is `@Transactional` so the `users.email_verified = TRUE` flip and the `redis.delete(key)` either both happen or neither does.
**Two ergonomic guards in the service** — both real bugs the first version hit:
1. `setRedis` is `@Autowired(required = false)` instead of constructor-injected. `AuthService` unit tests construct the service directly with `null` for verification, which is what the existing 4 `AuthService*Test` files were already doing for `EmailService`. Constructor-injecting the redis template would have forced 4 more null arguments through the test fixtures for no value.
2. `issue(user)` is a no-op when `redis == null` (degraded-mode safety) **and** when `user.isEmailVerified()` is already true (idempotent resend — clicking "Resend verification email" twice doesn't double-send).
**Test fixture fan-out** Adding one required field to `User` rippled across the test suite in three different shapes:
- 4 `AuthService*Test` files: 6 → 7 constructor args (one-line each, all `null`).
- 2 service-level fixtures (`ListingServiceCreateTest`, `ConversationServiceTest`): existing `User.builder()...build()` callers now need `.emailVerified(true)`. Forgetting this turns a previously-passing test into `EMAIL_NOT_VERIFIED`, which is informative because the *new* gate fires first.
- 2 controller integration tests: the synthetic `/api/auth/register` flow now leaves the user unverified. After register, look up via `UserRepository`, set `emailVerified=true`, save. This is the **bypass that doesn't simulate the verification round-trip** — same shape as the resetRateLimits trick from D-66 (sidestep an out-of-band production flow that integration tests shouldn't depend on).
**Frontend** Three small surfaces:
- `VerifyEmailPage` at `/verify-email`: reads `?token`, three states (loading spinner / green check card / red failure card with "resend" hint). 80 lines, no state machine library — a `'loading' | 'success' | 'failed'` union and a single `useEffect` is plenty.
- `MePage`: green or amber chip plus an inline "Resend verification email" button on the unverified branch. The chip surfaces the gate's existence in the UI, so users hitting `EMAIL_NOT_VERIFIED` on POST listings have somewhere to look.
- `AuthUser.emailVerified` flows through `AuthContext`, so any future page can render verification-aware UI without re-fetching `/api/auth/me`.
**E2E** The 22-spec Playwright suite was deliberately not wired through the new gate in this commit — that's the next commit (D-76).
**Lesson** A boundary added late costs less than a boundary added early *if* you place the check at the right two methods. Any earlier (auth filter, controller advice) and reads break unnecessarily; any later (validation deep inside the service) and the gate becomes a maintenance trap. The two methods that mutate other users' worlds — `Listing.create` and `Conversation.sendMessage` — are the right boundary, and the *first line* of each is the right place. Pattern to remember: **late-binding security gates belong at the action boundary, not the request boundary.**

---

### D-76 — E2E bypass for the verified-email gate via `docker exec mysql`, mirroring D-66's resetRateLimits pattern

**Date / where** Milestone 6 stage IV, 2026-05-22 (commit `a5e0a7c`)
**Symptom** D-75 added the verified-email gate. The 22-spec Playwright suite registers fresh users in every test (the standard pattern from D-66) — every one of those users is now unverified, so 14 of the 22 specs that exercise create-listing / messaging / status-change flows started returning 403 `EMAIL_NOT_VERIFIED` and failing.
**Two viable shapes for the fix:**
1. **Drive the real verification flow.** Hit `/api/auth/me` for the userId (or scrape from cookie), grab the verification token from Redis via `docker exec redis-cli`, POST `/api/auth/verify-email` with it. Realistic, but two extra round-trips per test, and it tests the verification flow inadvertently in every spec rather than where it belongs (`auth-flow.spec.ts`).
2. **Sidestep the gate at the database layer.** One `UPDATE users SET email_verified = 1 WHERE email = ...`. No round-trip with the verification service. Same shape as `resetRateLimits` (D-66), which already sidesteps Redis-stored rate-limit state for the same reason: E2E should not exercise out-of-band production flows that aren't part of the user journey under test.
Went with #2. The cost is one extra dependency on `docker exec` against the dev MySQL container, which the suite already requires for `resetRateLimits`. The win is that each spec stays focused on its stated scenario (create / edit / messaging / status), not on email plumbing.
**Implementation** `frontend/e2e/helpers/verifyUser.ts` exports `markEmailVerified(email)`, 30 lines:
```ts
docker exec campus_mysql mysql -uappuser -papppassword campus_marketplace \
  -e "UPDATE users SET email_verified = 1 WHERE email = '...'"
```
Single-quote escaping on the email is one `replace(/'/g, "\\'")`. Failures are logged, not thrown, so a CI failure caused by a stopped Docker stack still produces a useful message rather than a generic exec exception.
**Where it's called** Inside the `register()` helper of each affected spec, immediately after `waitForURL('/')`. Five files:
- `browse-search-flow.spec.ts`, `create-flow.spec.ts`, `edit-flow.spec.ts`, `messaging-flow.spec.ts`, `status-flow.spec.ts`
Auth-flow is **deliberately** untouched — those 8 tests cover the unverified-registration UX itself (the chip, the resend button, the error banner on POST listings), so auto-verifying would defeat their purpose.
**Verification** 22/22 Playwright specs green in 37s, single worker — same numbers as D-66's baseline, gate now in production code, suite still runs in <40s.
**Lesson** When a new gate ships, the testing question is "does the suite verify the gate exists, or does the suite assume the gate doesn't exist?". The right answer is **both, but in different specs.** auth-flow proves the gate works; every other spec assumes verified state via a bypass. Trying to make all 22 specs drive the real flow doubles their length and turns each into a partial test of the verification service. **Bypass helpers are not a smell when their scope is documented and they mirror an established pattern** — `resetRateLimits` and `markEmailVerified` now form a small family of "sidestep production-only Redis/MySQL state" helpers in `frontend/e2e/helpers/`.

---

### D-77 — E2E coverage for the D-75 gate surfaced a real SecurityConfig miss: `/api/auth/verify-email` was never anonymous-permitted

**Date / where** Milestone 6 stage IV, 2026-05-23 (commit `b0d8aad`)
**Symptom** D-75 shipped the verified-email gate. D-76 added a `markEmailVerified` bypass so the other 21 specs stay green by skipping the gate entirely. That left a real coverage hole: `auth-flow.spec.ts` had **zero** tests proving the gate actually works — 14 specs assumed the gate doesn't exist (via bypass), 0 specs proved it does. Time to fix.
**The three tests added** to `auth-flow.spec.ts`:
1. Newly-registered user sees the unverified chip + the "Resend verification email" button on `/me` (the UI half of the gate).
2. Visiting `/verify-email?token=<real-token>` flips the state to verified and the chip / resend disappear (the happy path).
3. Visiting `/verify-email?token=this-token-was-never-issued` shows the failure card with the `INVALID_VERIFICATION_TOKEN` message (the sad path).
**New helper `getVerificationToken()`** — `frontend/e2e/helpers/verificationToken.ts`, 30 lines. Same family as `resetRateLimits` (D-66) and `markEmailVerified` (D-76): `docker exec campus_redis redis-cli --no-raw KEYS "auth:verify:*"`, expects exactly one key (relies on the `resetRateLimits()` FLUSHDB in `beforeEach`), strips the leading `auth:verify:` prefix, returns the token. Dev-only — production has no need.
**Two parser bugs while building the helper** — worth flagging because both nearly shipped:
1. **`redis-cli` formats multi-element replies as `1) "key"`, not bare `key`.** First pass did `.replace(/^"|"$/g, '')` to strip quotes, missed the `N) ` index prefix entirely → empty filter → "0 keys found". The hex-dump (`xxd`) of the actual output is what made it obvious: `31 29 20 22 ...` = `1) "`. Fix: `.replace(/^\d+\)\s*/, '').replace(/^"|"$/g, '')`.
2. **Vite proxy at port 5173 vs direct backend at 8080.** Manually `curl localhost:8080/api/auth/register` worked and the key appeared. But the e2e suite hit `localhost:5173/api/auth/register` via the Vite proxy, and *also* worked. The flaky-looking "0 keys" was actually the parser bug above, not a proxy issue — but the manual `curl` vs Playwright divergence cost 10 minutes of chasing the wrong root cause. **Lesson:** when an integration test says "0 found" and the same probe by hand says "1 found," the gap is almost never network — check the parser first.
**The real bug e2e caught** — and this is the point of the entry. Test 3 failed not with "invalid or has expired" but with `Please log in to continue.` HTTP 401. Tracing: `/api/auth/verify-email` was never added to `SecurityConfig`'s `permitAll()` list:
```java
.requestMatchers(
    "/api/health",
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/forgot-password",
    "/api/auth/reset-password"   // verify-email missing here
).permitAll()
```
The endpoint requires auth — but the entire point of the endpoint is that the user clicks the link from an email *before* logging in. The integration tests didn't catch it because they're authenticated via `MockMvc` `with(user(...))`. The 22-spec Playwright suite didn't catch it because every gate-using spec bypasses verification via `markEmailVerified` and never hits `/verify-email`. **The bug was invisible until a test exercised the actual click-the-link-before-login flow.**
**Pattern observation** Every new endpoint needs three orthogonal contract checks:
1. **Authentication policy** (is this anonymous, user-only, or admin-only?) — lives in `SecurityConfig`.
2. **Business validation** (does the handler reject bad inputs?) — lives in service unit tests.
3. **End-to-end user flow** (does the page that calls this actually reach it?) — lives in Playwright.
Unit + integration tests caught #2 perfectly. E2E was the only thing that could catch #1 in this case — because #1 is invisible unless the request comes from a real unauthenticated user-agent. D-66's lesson ("untested code is reverse-documentation") applied at a different layer: **untested-by-E2E endpoints quietly drift their authentication policy from the spec**.
**Lesson** When you add a gate that depends on a new endpoint, ship **both** the bypass (so existing tests keep going green) *and* the through-test (so the endpoint stays in the auth policy you designed). Skip the through-test and the bypass quietly hides regressions in the gate itself. The right shape for the test pyramid here: 1 spec proves the gate works end-to-end, N-1 specs assume the gate works via a bypass. We had N-1 + 0, which is the worst configuration — you pay the bypass complexity without getting the assurance the bypass is supposed to be paired with.

---

*Last updated: 2026-05-23 — D-77 closes the E2E coverage hole on the D-75 gate and catches a real SecurityConfig miss (`/api/auth/verify-email` never anonymous-permitted) along the way. 76 → 77 decisions logged.*
