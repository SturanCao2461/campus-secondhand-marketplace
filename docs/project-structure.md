# Project Structure — Campus Secondhand Marketplace

> Last updated: 2026-05-22 (after Epic 5 — Messaging — and post-Epic-5 polish)

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Backend | Spring Boot + JPA + MySQL + Redis | 3.4.4 / Java 21 / MySQL 8 / Redis 7 |
| Frontend | React + Vite + Tailwind CSS | React 19 / Vite 8 / Tailwind 4 |
| Testing | JUnit 5 + Testcontainers + Playwright | Testcontainers 1.21 / Playwright latest |
| Infrastructure | Docker Compose | MySQL + Redis containers |

## Directory Tree

```
campus-secondhand-marketplace/
├── backend/                          # Spring Boot backend (Java 21)
├── frontend/                         # React SPA (TypeScript)
├── infra/                            # Docker Compose for local dev
├── docs/                             # Project documentation
└── README.md                         # Project overview
```

---

## Backend (`backend/`)

### Source (`src/main/java/nz/ac/waikato/campusmarketplace/`)

| Path | Purpose | Created |
|---|---|---|
| `BackendApplication.java` | Spring Boot entry point | Milestone 1 |
| **config/** | | |
| `BeanConfig.java` | Shared beans (PasswordEncoder, etc.) | Epic 1 |
| `CorsConfig.java` | CORS allowed origins/methods | Epic 1 |
| `SecurityConfig.java` | Spring Security filter chain (auth rules, JWT filter) | Epic 1, updated Epic 3 |
| **bootstrap/** | | |
| `CategorySeeder.java` | ApplicationRunner: seeds 8 default categories on startup | Epic 2 Phase 1 |
| **controller/** | | |
| `HealthController.java` | `GET /api/health` — liveness probe | Milestone 1 |
| `AuthController.java` | 5 auth endpoints (register/login/logout/forgot/reset) | Epic 1 |
| `CategoryController.java` | `GET /api/categories` — list active categories | Epic 2 Phase 1 |
| `ListingController.java` | 8 listing endpoints (browse/detail/create/listMine/getOne/update/status/delete) | Epic 2 Phase 1, updated Epic 3 |
| `ImageController.java` | `GET /api/uploads/listings/{filename}` — serve images with access control | Epic 2 Phase 2, updated Epic 3 |
| `ConversationController.java` | 6 conversation/message endpoints (create/list/detail/messages/send/unread) | Epic 5 |
| **dto/** | | |
| `RegisterRequest.java` | Registration input validation | Epic 1 |
| `LoginRequest.java` | Login input | Epic 1 |
| `ForgotPasswordRequest.java` | Forgot password input | Epic 1 |
| `ResetPasswordRequest.java` | Reset password input | Epic 1 |
| `UserResponse.java` | User data returned to frontend | Epic 1 |
| `CategoryResponse.java` | Category data (code, nameEn, nameZh) | Epic 2 Phase 1 |
| `CreateListingRequest.java` | Listing creation input (10 fields + Bean Validation) | Epic 2 Phase 1 |
| `UpdateListingRequest.java` | Listing edit input (same shape as create) | Epic 2 Phase 1 |
| `ChangeStatusRequest.java` | Status transition input (newStatus enum) | Epic 2 Phase 1 |
| `ListingResponse.java` | Full listing detail (16 fields + factory `from(Listing)`) | Epic 2 Phase 1 |
| `ListingSummary.java` | Slim listing for list views (8 fields) | Epic 2 Phase 1 |
| `PagedListings.java` | Paginated response wrapper (items + page metadata) | Epic 2 Phase 1 |
| `BrowseQuery.java` | Public browse filter parameters | Epic 3 |
| `CreateConversationRequest.java` | Conversation creation input (listingId) | Epic 5 |
| `SendMessageRequest.java` | Send message input (content) | Epic 5 |
| `ConversationSummary.java` | Slim conversation for list view (with unread + last message preview) | Epic 5 |
| `ConversationDetail.java` | Full conversation metadata (peer + listing snapshot) | Epic 5 |
| `MessageResponse.java` | Message data (id, sender, content, createdAt) | Epic 5 |
| `UnreadCountResponse.java` | Aggregate unread count `{count}` | Epic 5 |
| **entity/** | | |
| `User.java` | JPA entity: users table (email, password, nickname, timestamps) | Epic 1 |
| `Category.java` | JPA entity: categories table (code, nameEn, nameZh, sortOrder, active) | Epic 2 Phase 1 |
| `Listing.java` | JPA entity: listings table (16 columns, 4 indexes, 2 FK relationships) | Epic 2 Phase 1 |
| `ListingStatus.java` | Enum: AVAILABLE / RESERVED / SOLD / REMOVED | Epic 2 Phase 1 |
| `ListingType.java` | Enum: SELL / GIVEAWAY | Epic 2 Phase 1 |
| `Condition.java` | Enum: NEW / LIKE_NEW / GOOD / FAIR / POOR | Epic 2 Phase 1 |
| `Conversation.java` | JPA entity: conversations (buyerId, sellerId, listingId, lastReadAt per role) | Epic 5 |
| `Message.java` | JPA entity: messages (conversationId, senderId, content, createdAt) | Epic 5 |
| `Conversation.java` | JPA entity: conversations (buyerId, sellerId, listingId, lastReadAt per role) | Epic 5 |
| `Message.java` | JPA entity: messages (conversationId, senderId, content, createdAt) | Epic 5 |
| **exception/** | | |
| `ApiException.java` | Custom runtime exception with ErrorCode + optional retryAfterSeconds | Epic 1, extended Epic 2 |
| `ErrorCode.java` | Error codes mapped to HTTP status (auth + listing + conversation codes) | Epic 1, extended Epic 2/5 |
| `ApiErrorResponse.java` | Standard error response shape `{code, message}` | Epic 1 |
| `GlobalExceptionHandler.java` | `@ControllerAdvice`: maps exceptions to HTTP responses + Retry-After header | Epic 1, extended Epic 2 |
| **filter/** | | |
| `AuthPrincipal.java` | Record: userId, email, nickname (set by JWT filter) | Epic 1 |
| `JwtAuthenticationFilter.java` | OncePerRequestFilter: extracts JWT from cookie, sets SecurityContext | Epic 1 |
| **repository/** | | |
| `UserRepository.java` | JPA repository for User (existsByEmail, existsByNickname, findByEmail) | Epic 1 |
| `CategoryRepository.java` | JPA repository for Category (findByCode, findAllByActiveTrueOrderBySortOrderAsc) | Epic 2 Phase 1 |
| `ListingRepository.java` | JPA repository + JpaSpecificationExecutor (4 derived queries + dynamic specs + JOIN FETCH owner) | Epic 2 Phase 1, extended Epic 3, post-Epic-5 D-64 |
| `ListingSpecifications.java` | Composable JPA Specification predicates for browse filtering | Epic 3 |
| `ConversationRepository.java` | JPA repository for Conversation (find by participants, by user, by listing) | Epic 5 |
| `MessageRepository.java` | JPA repository for Message (cursor pagination, unread count queries) | Epic 5 |
| **service/** | | |
| `AuthService.java` | Registration, login, logout, password reset, rate-limit integration | Epic 1 |
| `JwtService.java` | JWT generation + validation + blacklist (Redis) | Epic 1 |
| `RateLimitService.java` | Redis-based rate limiting (increment, incrementBy, check with TTL) | Epic 1, extended Epic 2 |
| `RateLimitDecision.java` | Record: exceeded + retryAfterSeconds | Epic 2 Phase 0 |
| `EmailService.java` | Interface for email sending | Epic 1 |
| `ConsoleEmailService.java` | Dev implementation: prints emails to console | Epic 1 |
| `SmtpEmailService.java` | Production implementation: sends via SMTP | Epic 1 |
| `ListingService.java` | 6 public methods: create/update/changeStatus/listMine/getOne/remove + browse/getDetail | Epic 2 Phase 1, extended Epic 3, post-Epic-5 D-64 |
| `ImageStorageService.java` | Interface: store(MultipartFile) + load(relativePath) | Epic 2 Phase 2 |
| `LocalImageStorageService.java` | Implementation: 5-step validation, UUID filenames, path-traversal defense | Epic 2 Phase 2 |
| `ConversationService.java` | 6 methods: createOrGetConversation, listForUser, getDetail, getMessages (updates lastReadAt), sendMessage, getUnreadCount | Epic 5 |

### Configuration (`src/main/resources/`)

| File | Purpose |
|---|---|
| `application.properties` | Shared config (JPA, JWT, cookie, CORS, multipart, upload root) |
| `application-dev.properties` | Dev profile (local MySQL/Redis, console email, insecure cookie) |
| `application-prod.properties` | Production profile (env vars for secrets) |

### Tests (`src/test/java/`)

| File | Tests | Type |
|---|---|---|
| `AbstractIntegrationTest.java` | — | Base class: singleton Testcontainers (MySQL + Redis) |
| `controller/AuthControllerIntegrationTest.java` | 9 | Integration: auth endpoints |
| `controller/CategoryControllerIntegrationTest.java` | 2 | Integration: categories |
| `controller/CategoryControllerTest.java` | 3 | Unit: category controller |
| `controller/ListingControllerIntegrationTest.java` | 18 | Integration: all listing endpoints |
| `dto/ListingDtoTest.java` | 7 | Unit: DTO factories + validation |
| `entity/ListingSchemaIntegrationTest.java` | 4 | Integration: schema + index verification |
| `exception/ApiExceptionTest.java` | 2 | Unit: exception basics |
| `exception/ApiExceptionRetryAfterTest.java` | 2 | Unit: retryAfterSeconds |
| `exception/GlobalExceptionHandlerRetryAfterTest.java` | 2 | Unit: Retry-After header |
| `repository/ListingRepositoryTest.java` | 7 | Integration: derived queries |
| `service/AuthServiceLoginLogoutTest.java` | 5 | Unit: login/logout |
| `service/AuthServiceRegisterTest.java` | 7 | Unit: registration |
| `service/AuthServiceGetCurrentTest.java` | 2 | Unit: getCurrentUser |
| `service/AuthServiceResetTest.java` | 5 | Unit: password reset |
| `service/ConsoleEmailServiceTest.java` | 1 | Unit: email service |
| `service/JwtServiceTest.java` | 3 | Unit: JWT |
| `service/RateLimitServiceTest.java` | 5 | Unit: rate limiting |
| `service/ListingServiceCreateTest.java` | 8 | Unit: listing create |
| `service/ListingServiceUpdateTest.java` | 7 | Unit: listing update |
| `service/ListingServiceChangeStatusTest.java` | 8 | Unit: FSM transitions |
| `service/ListingServiceQueryTest.java` | 9 | Unit: listMine/getOne/remove |
| `service/LocalImageStorageServiceTest.java` | 11 | Unit: image validation |
| `service/ConversationServiceTest.java` | 14 | Unit: conversation/message service |
| `controller/ConversationControllerIntegrationTest.java` | 15 | Integration: all conversation endpoints |
| **Total** | **157** | |

---

## Frontend (`frontend/`)

### Source (`src/`)

| Path | Purpose | Created |
|---|---|---|
| `main.tsx` | React entry point | Milestone 1 |
| `App.tsx` | Root component: BrowserRouter + AuthProvider + ToastProvider + Routes | Epic 1, extended Epic 2/3 |
| `index.css` | Tailwind CSS imports | Milestone 1 |
| **api/** | | |
| `apiClient.ts` | HTTP client: get/post/put/patch/delete/postForm/putForm + ApiError class | Epic 1, extended Epic 2 |
| `categories.ts` | Category type + categoriesApi.list() | Epic 2 |
| `listings.ts` | Listing types + listingsApi (browse/getDetail/create/listMine/getOne/update/changeStatus/remove) | Epic 2, extended Epic 3 |
| `conversations.ts` | Conversation/message types + conversationsApi (create/list/detail/messages/send/unread) | Epic 5 |
| `conversations.ts` | Conversation/message types + conversationsApi (create/list/detail/messages/send/unread) | Epic 5 |
| **auth/** | | |
| `AuthContext.tsx` | React Context type definition (AuthUser, login, logout, register) | Epic 1 |
| `AuthProvider.tsx` | Context provider: manages auth state, auto-refresh on mount | Epic 1 |
| `ProtectedRoute.tsx` | Route guard: redirects to /login if unauthenticated | Epic 1 |
| `useAuth.ts` | Hook: consumes AuthContext | Epic 1 |
| **components/** | | |
| `Navbar.tsx` | Top navigation: Browse, Messages (with unread badge), My Listings, greeting, logout — gated on auth loading | Epic 1, extended Epic 2/3/5, post-Epic-5 D-65 |
| `PasswordInput.tsx` | Reusable password field with show/hide toggle | Epic 1 |
| `Toast.tsx` | ToastProvider + useToast: success/error notifications, auto-dismiss 3s | Epic 2 polish |
| **hooks/** | | |
| `useUnsavedChangesGuard.ts` | beforeunload guard for dirty forms | Epic 2 polish |
| `useUnreadCount.ts` | 15s visibility-aware polling for global unread badge | Epic 5 |
| `useChatPolling.ts` | 5s polling for active chat (cursor-based message fetch) | Epic 5 |
| `useBrowserNotification.ts` | Browser Notification API wrapper (permission + show) | Epic 5 |
| **pages/** | | |
| `HomePage.tsx` | Landing page with hero + Browse/contextual CTA (Sell/SignUp depending on auth) | Epic 1, redesigned Epic 3, post-Epic-5 D-65 |
| `RegisterPage.tsx` | Registration form | Epic 1 |
| `LoginPage.tsx` | Login form | Epic 1 |
| `ForgotPasswordPage.tsx` | Forgot password form | Epic 1 |
| `ResetPasswordPage.tsx` | Reset password form (reads ?token from URL) | Epic 1 |
| `MePage.tsx` | User profile (authenticated) | Epic 1 |
| `CreateListingPage.tsx` | Listing creation form (multipart, categories dropdown, image picker) | Epic 2 |
| `MyListingsPage.tsx` | Owner's listing grid (status filter, pagination, skeleton loading) | Epic 2 |
| `ListingDetailPage.tsx` | Owner detail view (status actions, edit/delete buttons) | Epic 2 |
| `EditListingPage.tsx` | Edit form (prefilled, optional image replacement) | Epic 2 |
| `BrowsePage.tsx` | Public browse grid (search, category/type/sort filters, pagination, URL state) | Epic 3 |
| `PublicDetailPage.tsx` | Public read-only detail (no edit/delete/status buttons) + Contact seller CTA | Epic 3, extended Epic 5 |
| `ConversationsPage.tsx` | Conversation list (unread dots, last message preview, relative time) | Epic 5 |
| `ChatPage.tsx` | Chat view (message bubbles, day separators, char counter, Enter-to-send, auto-scroll) | Epic 5 |

### E2E Tests (`e2e/`)

| File | Purpose | Created |
|---|---|---|
| `create-flow.spec.ts` | Playwright: create listing + verify in My Listings | Epic 2 Phase 6 |
| `edit-flow.spec.ts` | Playwright: edit listing title, verify change | Epic 2 Phase 6 |
| `status-flow.spec.ts` | Playwright: full FSM cycle (Available→Reserved→Sold→Available→Removed) | Epic 2 Phase 6 |
| `fixtures/test-image.jpg` | 100x100 blue JPEG for upload tests | Epic 2 Phase 6 |

### Configuration

| File | Purpose |
|---|---|
| `package.json` | Dependencies + scripts (dev/build/lint/e2e) |
| `vite.config.ts` | Vite config: React plugin + Tailwind + proxy /api → localhost:8080 |
| `playwright.config.ts` | Playwright config: Chromium headless, webServer for backend+frontend |
| `tsconfig.json` | TypeScript project references |
| `tsconfig.app.json` | App TypeScript config |
| `tsconfig.node.json` | Node (Vite config) TypeScript config |

---

## Infrastructure (`infra/`)

| File | Purpose |
|---|---|
| `docker-compose.yml` | MySQL 8 + Redis 7.2 containers for local development |

---

## Documentation (`docs/`)

| File | Purpose | Content |
|---|---|---|
| `dev-roadmap.md` | Milestone tracker | 6 milestones, 5 complete (M1–M5) |
| `mvp-scope.md` | MVP feature scope definition | What's in/out for thesis |
| `engineering-journal.md` | Decision log | 65 D-N entries (D-1..D-65) |
| `manual-e2e-epic1.md` | Manual test checklist | Epic 1 auth flow verification |
| `manual-e2e-epic2.md` | Manual test checklist | Epic 2 listing flow verification |
| `superpowers/specs/2026-05-08-epic-1-auth-design.md` | Design spec | Epic 1 authentication system |
| `superpowers/specs/2026-05-14-epic-2-listings-design.md` | Design spec | Epic 2 listing CRUD + image + FSM |
| `superpowers/specs/2026-05-15-epic-3-browse-design.md` | Design spec | Epic 3 public browsing + search |
| `superpowers/specs/2026-05-21-milestone-5-messaging-design.md` | Design spec | Epic 5 / Milestone 5 messaging module |
| `superpowers/plans/2026-05-08-epic-1-auth.md` | Implementation plan | Epic 1 task breakdown |
| `superpowers/plans/2026-05-14-epic-2-listings.md` | Implementation plan | Epic 2 44-task TDD plan |
| `superpowers/plans/2026-05-15-epic-3-browse.md` | Implementation plan | Epic 3 task breakdown |

---

## Key Metrics (as of Epic 5 + post-Epic-5 polish)

| Metric | Value |
|---|---|
| Total commits on main | ~50 |
| Backend test count | 157 |
| Frontend pages | 13 |
| API endpoints | 21 (5 auth + 8 listing + 1 category + 1 health + 6 conversation) |
| Journal decisions | 65 (D-1..D-65) |
| Lines of code (backend src) | ~2500 |
| Lines of code (frontend src) | ~1800 |
| Lines of documentation | ~3500 |
