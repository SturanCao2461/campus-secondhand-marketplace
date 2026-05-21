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

## Milestone 6
Testing, polish, deployment