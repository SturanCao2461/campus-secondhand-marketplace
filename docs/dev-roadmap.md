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

### Phase C — Deployment ⏳
- TBD（本地演示路线，部署延后）

### 文档收尾
- README.md 重写（项目门面：架构图 + 快速启动 + 测试 + 文档索引）
- frontend/README.md 重写（替换 Vite 模板 → 模块导览 + 约定）
- 工程日志: D-66 (E2E 修复), D-67 (UI 一致化)