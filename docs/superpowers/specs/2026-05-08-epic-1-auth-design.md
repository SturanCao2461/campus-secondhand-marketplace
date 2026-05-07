# Epic 1: Authentication — Design Spec

- **Date**: 2026-05-08
- **Status**: Draft, pending user review
- **Owner**: Josh (sole developer)
- **Estimated effort**: 2–2.5 weeks
- **Depends on**: Epic 0 (skeleton, complete)
- **Blocks**: Epic 2 (Listing CRUD), Epic 3 (Browse), Epic 4a/4b (Messaging, Comments)

---

## 1. Overview

第一个 Epic 实现校园二手交易平台的用户身份系统。覆盖**注册、登录、登出、查询当前用户、忘记密码、重置密码**六个能力。这是后续所有 Epic 的依赖前置 —— 没有 `user_id`，商品、消息、评论都没有归属。

### 1.1 In Scope

- 用户注册（邮箱后缀限制为 `@students.waikato.ac.nz`）
- 用户登录（邮箱 + 密码）
- 用户登出（JWT 黑名单）
- 查询当前登录用户信息
- 忘记密码（发送重置链接到邮箱）
- 重置密码（凭 30 分钟内有效的一次性 token）
- BCrypt 密码哈希
- 登录/注册接口限频
- 前端注册页、登录页、忘密页、重置密码页
- 前端路由守卫（受保护页面拦截）
- 前端 Auth 全局状态、统一 API 客户端

### 1.2 Out of Scope（推迟到 V2 或永远不做）

- ❌ 邮箱真实性验证（注册后发激活链接）—— 由"忘密功能"提供事实上的真邮箱过滤
- ❌ 第三方登录（Google / Microsoft / 学号 SSO）
- ❌ 修改密码（已登录状态下）
- ❌ 修改昵称、头像、个人简介
- ❌ 注销账号
- ❌ 多设备登录管理（强制踢下线）
- ❌ 验证码（图形 / 滑块 / OTP）
- ❌ 双因素认证

### 1.3 Dependencies

- Epic 0 已完成：Spring Boot 3.4.4 + Java 21、React 19 + Vite 8、MySQL 8、Redis 7、`/api/health` 健康检查闭环。
- 上线决策（路径 A 全云免费层 vs 路径 B 自托管 VPS）暂记为路径 B（保留 MySQL）。Epic 5 时正式拍板。

---

## 2. User Stories

### 2.1 Register
> 作为一个怀大学生，我用 `xxx@students.waikato.ac.nz` 邮箱、一个密码、一个昵称完成注册，之后我可以用这个邮箱登录。其他后缀的邮箱被拒绝。

### 2.2 Login
> 作为已注册用户，我用邮箱+密码登录，登录后浏览器自动带上凭证，能访问受保护页面。

### 2.3 Logout
> 作为已登录用户，我点击登出后，凭证立即失效。即便有人偷到我之前的 cookie 也用不了。

### 2.4 Get Current User
> 前端任何时候可以查询"我现在是谁"，用于显示头像/昵称、判断显示哪个菜单。

### 2.5 Forgot Password
> 作为忘记密码的用户，我提交邮箱后能收到一封含重置链接的邮件，点开后能输入新密码。链接 30 分钟过期，用一次失效。

### 2.6 Reset Password
> 通过 forgot-password 邮件中的链接，我能在 30 分钟内设置新密码。设置成功后旧密码立即失效。

---

## 3. Data Model

### 3.1 MySQL `users` table

```sql
CREATE TABLE users (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    email        VARCHAR(120) NOT NULL UNIQUE,         -- 全小写
    password     VARCHAR(60)  NOT NULL,                -- BCrypt 哈希（固定 60 字符）
    nickname     VARCHAR(40)  NOT NULL UNIQUE,         -- 2–20 字符，公开显示
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at   DATETIME     NULL                     -- 软删除（与全局策略一致，V2 才会真用到）
);
CREATE INDEX idx_users_email ON users(email);
```

**字段约束**：
- `email` 全小写、`trim()`后存储；正则 `^[a-z0-9._%+-]+@students\.waikato\.ac\.nz$`
- `password` 永远存 BCrypt 哈希，禁止任何路径写入明文
- `nickname` 长度 2–20，禁止空格开头/结尾，全局唯一

### 3.2 Redis Keys

| Key 模式 | 用途 | 值 | TTL |
|---|---|---|---|
| `jwt:blacklist:<jti>` | 登出后的 token 黑名单 | `1` | 等于 token 剩余有效期 |
| `auth:reset:<token>` | 忘记密码的重置 token | `<user_id>` | 30 分钟 |
| `ratelimit:login:<email>` | 登录失败计数 | 失败次数 | 15 分钟（滑动窗口） |
| `ratelimit:register:<ip>` | 注册次数计数 | 注册次数 | 1 小时（滑动窗口） |

---

## 4. API Contract

所有响应用 JSON。错误格式统一：

```json
{ "code": "<error_code>", "message": "<user_facing_message>" }
```

### 4.1 `POST /api/auth/register`

| | |
|---|---|
| **Auth** | 不需要 |
| **Request** | `{ "email": "...", "password": "...", "nickname": "..." }` |
| **201 Created** | `{ "id": 7, "email": "alice@students.waikato.ac.nz", "nickname": "Alice" }` |

错误：
- `400 INVALID_EMAIL` — 邮箱后缀不对
- `400 INVALID_PASSWORD` — 不满足 8–64 位+至少 1 字母+至少 1 数字
- `400 INVALID_NICKNAME` — 长度 / 空格 / 字符规则不对
- `409 EMAIL_EXISTS` — 邮箱已注册
- `409 NICKNAME_TAKEN` — 昵称已被使用
- `429 TOO_MANY_REGISTRATIONS` — 同 IP 1 小时内超过 3 次

### 4.2 `POST /api/auth/login`

| | |
|---|---|
| **Auth** | 不需要 |
| **Request** | `{ "email": "...", "password": "..." }` |
| **200 OK** | `{ "user": { "id": 7, "email": "...", "nickname": "Alice" } }`<br>同时设置 `Set-Cookie: token=<JWT>; HttpOnly; SameSite=Lax; Path=/; Max-Age=604800; Secure(prod)` |

> ⚠️ **响应体不包含 JWT 字符串本身** —— token 只通过 httpOnly Cookie 下发，前端 JS 永远拿不到。这是 §6 中 XSS 防护的关键不变量。

错误：
- `401 BAD_CREDENTIALS` — 邮箱或密码错误（**不区分**是哪个错，防止枚举）
- `429 TOO_MANY_ATTEMPTS` — 同邮箱 15 分钟内失败超过 5 次

### 4.3 `POST /api/auth/logout`

| | |
|---|---|
| **Auth** | 必须已登录 |
| **Request** | 无 |
| **204 No Content** | 服务端将当前 token 的 `jti` 写入黑名单，TTL 为 token 剩余有效期。响应清空 cookie（`Set-Cookie: token=; Max-Age=0`）|

错误：
- `401 UNAUTHENTICATED` — 没带 token / token 已失效

### 4.4 `GET /api/auth/me`

| | |
|---|---|
| **Auth** | 必须已登录 |
| **Request** | 无 |
| **200 OK** | `{ "id": 7, "email": "...", "nickname": "Alice" }` |

错误：
- `401 UNAUTHENTICATED`

### 4.5 `POST /api/auth/forgot-password`

| | |
|---|---|
| **Auth** | 不需要 |
| **Request** | `{ "email": "..." }` |
| **204 No Content** | **永远返回 204**，无论邮箱是否存在 |

行为：
- 如果邮箱存在：生成 64 字符 URL-safe 随机 token，写入 `auth:reset:<token>` (TTL 30 分钟)，发送重置邮件
- 如果邮箱不存在：什么都不做，但同样返回 204（防止枚举）

### 4.6 `POST /api/auth/reset-password`

| | |
|---|---|
| **Auth** | 不需要 |
| **Request** | `{ "token": "...", "newPassword": "..." }` |
| **200 OK** | `{ "ok": true }`<br>token 立即从 Redis 删除（一次性）|

错误：
- `400 INVALID_TOKEN` — token 不存在 / 已用过 / 已过期
- `400 INVALID_PASSWORD` — 新密码不满足规则

---

## 5. Frontend Pages & Components

### 5.1 路由

| 路径 | 页面 | 是否要登录 | 已登录访问 |
|---|---|---|---|
| `/` | Home（暂时只是 placeholder，Epic 3 会替换为商品列表） | ❌ | 正常显示 |
| `/login` | LoginPage | ❌ | 跳转 `/` |
| `/register` | RegisterPage | ❌ | 跳转 `/` |
| `/forgot-password` | ForgotPasswordPage | ❌ | 跳转 `/` |
| `/reset-password?token=xxx` | ResetPasswordPage | ❌ | 跳转 `/` |
| `/me` (Epic 1 临时调试用) | 显示当前 user JSON | ✅ | — |

未登录访问受保护页 → 跳转 `/login?next=<原路径>`，登录后跳回。

### 5.2 共享组件

- `<AuthProvider>` —— React Context，挂在 App 根。提供 `user, loading, login(), register(), logout(), refresh()`。挂载时自动调 `GET /api/auth/me` 还原会话。
- `<ProtectedRoute>` —— 包裹任何受保护路由。`loading` 时显示 spinner；未登录时跳转 `/login?next=...`。
- `<Navbar>` —— 顶部导航栏。未登录显示「登录」「注册」；已登录显示昵称 + 「登出」。
- `apiClient.ts` —— 包装 `fetch`，自动带 `credentials: 'include'`（让 cookie 跟着走），统一错误形态，401 自动调用 `logout()` 并跳转登录页。

### 5.3 注册页字段顺序与提示

```
[小提示] Use a real email — you'll need it to reset your password later.

Email             [_________________________] @students.waikato.ac.nz only
Password          [_________________________] 8–64 chars, with letter + digit
Confirm Password  [_________________________]
Nickname          [_________________________] 2–20 chars, shown publicly

[ Create account ]   Already have an account? [Log in]
```

---

## 6. Security Policy

| 项 | 决策 |
|---|---|
| 密码哈希 | BCrypt，cost = 10（Spring Security 默认） |
| 密码字段 | 永远不在 API 响应中返回 |
| 邮箱处理 | `.toLowerCase().trim()` 后存储，所有比较用小写 |
| JWT 算法 | HS256 |
| JWT 密钥 | 读环境变量 `JWT_SECRET`（至少 32 字节 base64）；缺失则**应用拒绝启动** |
| JWT 载荷 | `sub`(user id), `email`, `nickname`, `iat`, `exp`, `jti` |
| JWT 有效期 | 7 天 |
| Token 存储 | httpOnly Cookie，`SameSite=Lax`，生产加 `Secure` |
| 登出 | `jti` 写入 Redis 黑名单，TTL = token 剩余有效期 |
| 登录限频 | 同 email 失败 5 次/15 分钟 → 锁 15 分钟 |
| 注册限频 | 同 IP 注册 3 次/1 小时 |
| CORS | 只允许前端 origin（dev: `http://localhost:5173`；prod: 真域名） |
| CSRF | 依赖 SameSite=Lax 和 httpOnly cookie，无需额外 token |
| HTTPS | 本地 dev 用 HTTP；prod 强制 HTTPS（Caddy 自动 Let's Encrypt） |
| 密钥/SMTP 配置 | 全部走环境变量，禁止硬编码 |
| 错误信息 | 不区分"邮箱不存在"和"密码错误"（防枚举） |

---

## 7. Email Policy

| 项 | 决策 |
|---|---|
| 库 | `spring-boot-starter-mail` |
| 抽象 | `EmailService` 接口；dev 注入 `ConsoleEmailService`（println），prod 注入 `SmtpEmailService` |
| 模板 | 纯文本，硬编码字符串，不引模板引擎 |
| 重置邮件标题 | `Reset your Campus Marketplace password` |
| 重置邮件正文 | 含一行 `{base_url}/reset-password?token=xxx`、有效期说明、忽略提示 |
| Profile 切换 | Spring profile `dev` → console；profile `prod` → smtp |
| Prod SMTP 厂商 | **延迟到 Epic 5 决定**（候选：Resend / SendGrid / Postmark），但代码层只依赖 SMTP 标准协议 |

---

## 8. Tech Stack

### 8.1 Backend 新依赖

| 依赖 | 用途 |
|---|---|
| `spring-boot-starter-security` | 鉴权框架 |
| `io.jsonwebtoken:jjwt-api/impl/jackson` | JWT 编解码 |
| `spring-boot-starter-mail` | SMTP 邮件 |
| (已有) `spring-boot-starter-data-redis` | 黑名单 / 限频 / 重置 token |
| (已有) `spring-boot-starter-validation` | 请求体校验 |

### 8.2 Frontend 新依赖

| 依赖 | 用途 |
|---|---|
| `react-router-dom@^7` | 客户端路由 |
| `tailwindcss@^4` + `@tailwindcss/vite` | CSS 框架 |

### 8.3 Backend 文件结构（新增）

```
backend/src/main/java/nz/ac/waikato/campusmarketplace/
├── BackendApplication.java                     (existing)
├── controller/
│   ├── HealthController.java                   (existing)
│   └── AuthController.java                     (NEW)
├── service/
│   ├── AuthService.java                        (NEW)
│   ├── JwtService.java                         (NEW)
│   ├── EmailService.java                       (NEW interface)
│   ├── ConsoleEmailService.java                (NEW @Profile("dev"))
│   ├── SmtpEmailService.java                   (NEW @Profile("prod"))
│   └── RateLimitService.java                   (NEW)
├── repository/
│   └── UserRepository.java                     (NEW)
├── entity/
│   └── User.java                               (NEW)
├── dto/
│   ├── RegisterRequest.java                    (NEW)
│   ├── LoginRequest.java                       (NEW)
│   ├── ForgotPasswordRequest.java              (NEW)
│   ├── ResetPasswordRequest.java               (NEW)
│   └── UserResponse.java                       (NEW)
├── config/
│   ├── SecurityConfig.java                     (NEW)
│   └── CorsConfig.java                         (NEW)
├── filter/
│   └── JwtAuthenticationFilter.java            (NEW)
└── exception/
    ├── ApiException.java                       (NEW)
    └── GlobalExceptionHandler.java             (NEW @ControllerAdvice)
```

### 8.4 Frontend 文件结构（新增）

```
frontend/src/
├── App.tsx                                     (REWRITE — wire router & AuthProvider)
├── main.tsx                                    (existing)
├── index.css                                   (REWRITE — Tailwind directives)
├── api/
│   └── apiClient.ts                            (NEW — fetch wrapper)
├── auth/
│   ├── AuthContext.tsx                         (NEW)
│   ├── AuthProvider.tsx                        (NEW)
│   ├── useAuth.ts                              (NEW hook)
│   └── ProtectedRoute.tsx                      (NEW)
├── pages/
│   ├── HomePage.tsx                            (NEW placeholder)
│   ├── LoginPage.tsx                           (NEW)
│   ├── RegisterPage.tsx                        (NEW)
│   ├── ForgotPasswordPage.tsx                  (NEW)
│   ├── ResetPasswordPage.tsx                   (NEW)
│   └── MePage.tsx                              (NEW debug)
└── components/
    └── Navbar.tsx                              (NEW)
```

---

## 9. Error Code Catalog

每个错误对应一个稳定的 `code`（前端可用来匹配做精细化提示），加一段对应的人话 `message`。

| code | HTTP | message (中文版本仅供前端文案参考；UI 使用英文) |
|---|---|---|
| `INVALID_EMAIL` | 400 | "Only `@students.waikato.ac.nz` emails are allowed." |
| `INVALID_PASSWORD` | 400 | "Password must be 8–64 characters with at least one letter and one digit." |
| `INVALID_NICKNAME` | 400 | "Nickname must be 2–20 characters, no leading/trailing spaces." |
| `EMAIL_EXISTS` | 409 | "This email is already registered. Log in instead?" |
| `NICKNAME_TAKEN` | 409 | "This nickname is taken. Try another." |
| `BAD_CREDENTIALS` | 401 | "Email or password is incorrect." |
| `UNAUTHENTICATED` | 401 | "Please log in to continue." |
| `INVALID_TOKEN` | 400 | "This reset link is invalid or has expired." |
| `TOO_MANY_ATTEMPTS` | 429 | "Too many attempts. Try again in 15 minutes." |
| `TOO_MANY_REGISTRATIONS` | 429 | "Too many registrations from your network. Try again later." |

---

## 10. Testing Strategy

### 10.1 Backend 单元 / 集成测试

- `AuthServiceTest` —— 注册成功 / 邮箱重复 / 密码弱 / 邮箱后缀错 / 昵称重复
- `JwtServiceTest` —— 签发 / 解析 / 过期 / 篡改检测
- `RateLimitServiceTest` —— 计数器递增 / 窗口过期重置 / 边界
- `AuthControllerIntegrationTest` —— Spring Boot Test + Testcontainers (MySQL + Redis)，覆盖 6 个端点 happy path 和主要错误
- `JwtAuthenticationFilterTest` —— 黑名单命中 / token 无效 / token 缺失

### 10.2 Frontend 测试（MVP 最小化）

- 表单基本校验：注册页空提交、密码不一致、邮箱后缀错
- AuthProvider 在挂载时调用 `/api/auth/me` 的行为
- ProtectedRoute 跳转逻辑

### 10.3 手工 E2E

写一份 `docs/manual-e2e-epic1.md` 检查清单，覆盖 6 条用户故事的端到端流程。

---

## 11. Acceptance Criteria

Epic 1 完成的判断标准：

- [ ] 后端所有 6 个端点按本文档 §4 行为正确实现
- [ ] 用 `@gmail.com` 等非校园邮箱注册被拒
- [ ] BCrypt 密码哈希落库（DB 看不到明文）
- [ ] JWT 在 Cookie 中签发，浏览器开发者工具能看到 `HttpOnly` 标志
- [ ] 登出后该 token 立即失效（重放被拒）
- [ ] 登录连续失败 5 次后被锁 15 分钟
- [ ] 同 IP 1 小时注册第 4 次被拒
- [ ] dev 环境运行时，"忘密"流程在控制台打印重置链接，复制粘贴可重置成功
- [ ] 重置 token 30 分钟后失效；重置成功后该 token 立即失效
- [ ] 前端：未登录访问 `/me` 跳转 `/login?next=/me`，登录后跳回 `/me`
- [ ] 前端：登出后 navbar 立即变化、`/me` 不再可访问
- [ ] 单元 + 集成测试全部通过
- [ ] 手工 E2E 清单全过

---

## 12. Open Questions

无。所有产品 / 技术决策已收敛。生产 SMTP 厂商选型推迟到 Epic 5（不影响 Epic 1 代码结构）。

---

## 13. Glossary（术语速查）

- **JWT (JSON Web Token)**: 自带签名的访问凭证，服务端验签即信任。
- **JTI (JWT ID)**: token 唯一标识，用于黑名单去重。
- **BCrypt**: 行业默认密码哈希算法，自带盐，慢算抗暴力。
- **httpOnly Cookie**: 浏览器 JS 读不到的 Cookie，能挡住 XSS 偷 token。
- **SameSite=Lax**: Cookie 仅在同站请求和顶层导航中携带，挡住绝大多数 CSRF。
- **Soft Delete**: 在记录上打 `deleted_at` 时间戳，查询时过滤；保留历史与外键完整性。
- **Rate Limiting**: 在固定窗口内限制操作次数（用 Redis 计数器实现）。
- **CORS**: 浏览器对跨域请求的安全策略；服务器声明哪些 origin 可访问。
