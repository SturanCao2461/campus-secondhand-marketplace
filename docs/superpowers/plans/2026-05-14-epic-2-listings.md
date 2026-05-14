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

### Task T1 — `ErrorCode → HttpStatus` mapping mechanism

Extend the `ErrorCode` enum so every error code can carry a non-default HTTP status; teach `GlobalExceptionHandler` to honour the override; map `TOO_MANY_ATTEMPTS` to `429 Too Many Requests`.

*Detailed steps to be expanded.*

### Task T2 — `ApiException` carries `retryAfterSeconds`; handler emits `Retry-After`

Add an optional `retryAfterSeconds` field to `ApiException`; `GlobalExceptionHandler` writes the `Retry-After` response header when present.

*Detailed steps to be expanded.*

### Task T3 — `RateLimitService.exceeded()` returns TTL; new `incrementBy(key, n, ttl)`

Return the remaining window TTL alongside the boolean so callers can populate `Retry-After`. Add `incrementBy` for byte-counting limits.

*Detailed steps to be expanded.*

### Task T4 — Carry-over test: login rate limit returns 429 + Retry-After

Add integration tests asserting the new behaviour on the existing `/api/auth/login` flow.

*Detailed steps to be expanded.*

**Demo milestone #5:** Postman — six wrong logins; the sixth response is `429 Too Many Requests` with `Retry-After: 900`.

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
