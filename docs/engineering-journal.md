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

Currently the M1 baseline only:

| Dependency | Version | Why |
|---|---|---|
| `react` / `react-dom` | 19.2.4 | UI framework |
| `vite` | 8.0.1 | Dev server + build |
| `@vitejs/plugin-react` | 6.0.1 | JSX/Fast Refresh |
| `typescript` | 5.9.3 | Type safety |
| `eslint` + plugins | 9.x | Linting |

Tasks 15–17 will add: `react-router`, `tailwindcss` v4, an HTTP client wrapper.

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

*Last updated: 2026-05-08 after Task 6 completion. Next entry: Task 7 — AuthService registration.*
