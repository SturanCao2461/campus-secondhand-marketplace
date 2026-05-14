# Epic 3: Public Browsing, Search & Filter — Design Specification

> **Status**: Implemented and merged to main (2026-05-15)

## 1. Scope

### 1.1 In Scope
- Public browse endpoint: any user (authenticated or not) can view AVAILABLE/RESERVED/SOLD listings
- Keyword search (title LIKE match, case-insensitive)
- Category filter, listing type filter (SELL/GIVEAWAY), price range filter
- Sort options: newest, oldest, price ascending, price descending
- Pagination (12 items per page, 0-indexed)
- Public detail page: full listing info without edit/delete capabilities
- Image access relaxation: non-REMOVED listing images publicly accessible
- Categories endpoint made public (browse page needs it without login)

### 1.2 Out of Scope (deferred)
- Full-text search (MySQL FULLTEXT or Elasticsearch) — LIKE is sufficient at thesis scale
- Saved searches / alerts
- Seller profile pages
- Messaging between buyer and seller (Epic 4)
- Location-based filtering

## 2. Architecture Decisions

### DC-7 — JPA Specifications over combinatorial derived queries

**Choice**: Use `JpaSpecificationExecutor<Listing>` with composable `Specification<Listing>` predicates instead of derived query method names.

**Why**: The browse endpoint has 5 optional filters (keyword, category, price range, listing type, status). Combinatorial derived queries would produce unreadable method names like `findByStatusInAndTitleContainingIgnoreCaseAndCategoryAndPriceBetweenAndListingType`. Specifications compose cleanly with `.and()` chaining — each filter is a one-liner predicate that's independently testable.

**Trade-off**: Specifications are slightly more verbose than derived queries for simple cases. But the browse endpoint is not a simple case — it's the most complex query in the system.

### DC-8 — Public endpoints use permitAll before the authenticated catch-all

**Choice**: SecurityConfig adds explicit `permitAll` matchers for public GET endpoints *before* the existing `.requestMatchers("/api/**").authenticated()` catch-all. The catch-all remains unchanged.

**Why**: Spring Security evaluates matchers in order; first match wins. Adding public endpoints before the catch-all is additive (no existing behavior changes). The alternative — restructuring the entire auth config — would risk regressions in Epic 1/2 endpoints.

### DC-9 — Image access: status-based rather than owner-based

**Choice**: `GET /api/uploads/listings/{filename}` now serves images publicly for non-REMOVED listings. Only REMOVED listing images still require owner authentication.

**Why**: The browse page and public detail page need to display listing images without login. The previous owner-only check was appropriate for Epic 2 (seller-only system) but blocks the buyer experience. REMOVED images stay protected to honor the soft-delete contract — a removed listing should not leak its image to the public.

### DC-10 — Separate public detail endpoint (`/api/listings/{id}/detail`) rather than relaxing the existing `getOne`

**Choice**: A new endpoint `/api/listings/{id}/detail` serves public detail. The existing `/api/listings/{id}` (authenticated, owner-privileged) remains unchanged.

**Why**: The existing `getOne` has owner-specific behavior (owner sees REMOVED listings). Relaxing it to public would require conditional logic based on whether the user is authenticated AND is the owner. A separate endpoint keeps the contracts clean: `getOne` = owner view (all statuses), `getDetail` = public view (non-REMOVED only).

## 3. API Contract

### 3.1 `GET /api/listings` — Public browse

**Authentication**: None required (permitAll)

**Query params**:
| Param | Type | Default | Notes |
|---|---|---|---|
| `page` | int | 0 | 0-indexed |
| `keyword` | string | — | Case-insensitive title LIKE match |
| `categoryCode` | string | — | Must match an active category code |
| `minPrice` | decimal | — | Inclusive lower bound |
| `maxPrice` | decimal | — | Inclusive upper bound |
| `listingType` | enum | — | SELL or GIVEAWAY |
| `sort` | string | CREATED_DESC | CREATED_DESC / CREATED_ASC / PRICE_DESC / PRICE_ASC |

**Response**: `200 OK` with `PagedListings` (same shape as `/api/listings/me`)

**Status filter**: Always returns only AVAILABLE + RESERVED + SOLD. REMOVED is never included.

### 3.2 `GET /api/listings/{id}/detail` — Public detail

**Authentication**: None required (permitAll)

**Response**: `200 OK` with full `ListingResponse`

**Errors**: `LISTING_NOT_FOUND` (404) if id doesn't exist or status is REMOVED.

### 3.3 `GET /api/uploads/listings/{filename}` — Image serving (relaxed)

**Authentication**: None required for non-REMOVED listings. REMOVED listing images require owner auth.

### 3.4 `GET /api/categories` — Now public

**Authentication**: None required (was authenticated in Epic 2, relaxed for browse page).

## 4. Frontend Pages

### 4.1 `BrowsePage` (`/browse`)
- 4-column responsive grid (1/2/3/4 cols at sm/md/lg/xl breakpoints)
- Filter toolbar: search input (Enter to submit), category dropdown, type dropdown, sort dropdown
- URL params synced (browser back/forward preserves filter state)
- Loading: 8-card skeleton with pulse animation
- Empty state: search icon + "No listings found" + "Try adjusting your search"
- Pagination: Previous/Next buttons + "Page X of Y"

### 4.2 `PublicDetailPage` (`/listings/:id/detail`)
- Read-only listing detail (no edit/delete/status buttons)
- Shows: image, title, price, status badge, category, condition, negotiable badge, description, original price, meet-at, reason, listed date
- Error state: "This listing is no longer available" + back-to-browse link
- Back link to `/browse`

### 4.3 Navigation
- Navbar: "Browse" link visible to all users (authenticated and unauthenticated)
- HomePage: redesigned with centered hero + "Browse Listings" CTA + "Sign Up" CTA

## 5. Security

- Public endpoints only expose non-REMOVED data
- No user PII is leaked (ownerNickname deferred to future iteration)
- REMOVED listings return identical 404 whether they exist-but-removed or don't-exist (anti-enumeration preserved)
- Rate limiting not applied to public read endpoints (industry standard; DoS protection belongs at nginx/CDN layer)

## 6. Testing

- 128 backend tests green (existing tests updated for new public access rules)
- TypeScript zero errors
- Browser verification: browse without login, search, filter, detail view
