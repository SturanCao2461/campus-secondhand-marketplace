# Development Roadmap

## Milestone 1
Monorepo skeleton, frontend bootstrap, backend bootstrap, Docker infra, health check

## Milestone 2
User model, auth basics, campus email restriction

## Milestone 3 ✅
Listing CRUD and status management
- Completed: 2026-05-15
- Backend: 6 endpoints, FSM (4 states / 8 transitions), image upload with 5-step validation, rate limiting
- Frontend: 4 pages (Create / MyListings / Detail / Edit), full status management UI
- Tests: 128 backend (unit + integration) + 3 Playwright E2E specs
- Journal: D-40..D-56 (17 decision entries)

## Milestone 4 ✅
Browse, search, filter, detail page
- Completed: 2026-05-15
- Backend: public browse endpoint with JPA Specifications (keyword/category/price/type filters), public detail, image access relaxed for non-REMOVED
- Frontend: BrowsePage (grid + search + filters + pagination + URL state), PublicDetailPage, public routes
- SecurityConfig: GET /api/listings, /api/listings/*/detail, /api/uploads/listings/**, /api/categories all permitAll

## Milestone 5 ✅
Messaging module
- Completed: 2026-05-21
- Backend: 2 entities (Conversation + Message), 6 DTOs, 2 repositories, ConversationService (6 methods), ConversationController (6 endpoints), 4 new ErrorCodes, rate limits (30/min send, 10/min create)
- Frontend: ConversationsPage, ChatPage, useUnreadCount (15s polling), useChatPolling (5s polling), useBrowserNotification, Navbar unread badge, Contact seller CTA on PublicDetailPage
- Tests: 157 backend tests (128 → 157, +29 new: 14 service unit + 15 controller integration)

## Milestone 6 🚧
Polish, testing, deployment

### Phase A — Testing 补全 ✅ (2026-05-22)
- E2E: 6 Playwright spec 文件，22/22 绿（auth/browse-search/create/edit/messaging/status）— 修了 4 类独立 bug，详见 D-66
- 单测: Vitest + jsdom 落地，14 个测试（apiClient 6 + useUnreadCount 8）
- E2E helpers: `rateLimit.ts`（清 Redis）+ `paths.ts`（ESM-safe fixture 路径）

### Phase B — UI Polish ✅ (2026-05-22)
- 全局 ErrorBoundary 替代 white-screen-of-death
- 设计系统统一：blue-600 主色 + slate-300 边框 + rounded-md + bg-red-50 错误横幅
- 28 处 className 不一致一次性收口（8 个文件，详见 D-67）
- Spinner 组件 + ConversationsPage 空状态图标
- MePage 重写为 profile card（移除 raw JSON 占位）

### Stage II — 必备文档 ✅ (2026-05-22)
- LICENSE (MIT) — 项目门面级
- OpenAPI / Swagger UI — 18 endpoints + 18 schemas 自动发现，`/swagger-ui/index.html`
- `docs/architecture.md` — Mermaid ER 图（5 张表）+ auth 时序图 + messaging 时序图
- JaCoCo 覆盖率基线 — 87% 指令 / 76% 行 / 62% 分支（详见 D-68）
- 3 份 manual UAT checklist（epic3 ownership / epic4 browse / epic5 messaging）
- README + frontend/README 重写为 GitHub 项目门面级 + 模块导览

### Stage III — 工程基础 ✅ (2026-05-22)
- GitHub Actions CI — backend (Maven + Testcontainers + JaCoCo upload) + frontend (lint/unit/build) 并行 jobs，详见 D-69
- 修复了 `BackendApplicationTests.contextLoads` 让 CI 全绿（一行 `extends AbstractIntegrationTest` 复用 Testcontainers）
- Prettier 3.8.3 + `.editorconfig` + 一次性 reformat 22 文件，详见 D-70
- 前端单测 14 → 37（5 份新 suite：useUnsavedChangesGuard / useToast / ErrorBoundary / ProtectedRoute / useChatPolling），详见 D-71
- ⏭️ Flyway 数据库迁移 deliberately deferred — 本地演示场景下 ROI 不足（无多人协作、无生产数据、无频繁 schema 演化）

### Phase C — Deployment ⏭️ DEFERRED
- 跳过部署（本地演示路线）— `.env.example` / Docker compose prod / 反向代理 / VPS 全部未实施
- E2E 也未入 CI（依赖 docker compose + backend dev server + frontend dev server，自成项目）