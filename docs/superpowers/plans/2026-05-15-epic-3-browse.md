# Epic 3: Public Browsing — Implementation Plan

> **Status**: Executed and merged to main (2026-05-15)

## Phase Overview

| Phase | Tasks | Focus |
|---|---|---|
| 0 — Backend | T1–T4 | Specifications, service methods, controller endpoints, SecurityConfig |
| 1 — Frontend | T5–T8 | BrowsePage, PublicDetailPage, routes, Navbar |

## Phase 0 — Backend

### T1 — ListingRepository + ListingSpecifications
- Added `JpaSpecificationExecutor<Listing>` to repository interface
- Created `ListingSpecifications` with 5 composable predicates:
  - `hasStatusIn(List<ListingStatus>)` — always filters to AVAILABLE/RESERVED/SOLD
  - `titleContains(String)` — case-insensitive LIKE
  - `hasCategory(Category)` — exact match
  - `priceBetween(BigDecimal min, BigDecimal max)` — range (handles null min or max)
  - `hasListingType(ListingType)` — SELL or GIVEAWAY

### T2 — ListingService.browse + getDetail
- `browse(BrowseQuery, Pageable)`: builds Specification chain from non-null query fields, always includes status filter, returns PagedListings
- `getDetail(Long id)`: findById → LISTING_NOT_FOUND if absent or REMOVED

### T3 — Controller endpoints + ImageController relaxation
- `GET /api/listings`: public browse with all query params
- `GET /api/listings/{id}/detail`: public detail
- `ImageController`: changed owner-only check to status-based (REMOVED requires owner; others public)
- Created `BrowseQuery` record DTO

### T4 — SecurityConfig
- Added 4 permitAll rules for GET endpoints before the authenticated catch-all
- Updated 2 integration tests that expected old behavior (categories 401 → 200, image non-owner 404 → 200)

## Phase 1 — Frontend

### T5 — API layer
- Added `BrowseQuery` interface and `browse()` / `getDetail()` to `listingsApi`

### T6 — BrowsePage
- 4-column grid, search/category/type/sort filters, URL params sync, skeleton loading, empty state

### T7 — PublicDetailPage
- Read-only detail, error handling for removed/missing, back-to-browse link

### T8 — Routes + Navbar + HomePage
- `/browse` and `/listings/:id/detail` as public routes (no ProtectedRoute)
- "Browse" link in Navbar for everyone
- HomePage redesigned with CTA buttons

## Verification
- 128 backend tests green
- TypeScript zero errors
- Browser: browse without login, search, filter, click detail — all verified
