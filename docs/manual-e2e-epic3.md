# Manual E2E Checklist — Epic 3: Listings (CRUD ownership-aware extensions)

> Epic 2 covered raw CRUD. Epic 3 layers in ownership-aware behaviour: the public detail page (read-only for non-owners), owner-only mutation gates, soft-delete enumeration defence, and 429 rate-limit signalling. Walk through this list with at least two browser profiles (or one regular + one incognito).

Run through this checklist with backend, frontend, MySQL, and Redis all running locally.

## Setup
- [ ] `cd infra && docker compose up -d`
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] `cd frontend && npm run dev`
- [ ] Open `http://localhost:5173` in browser A (regular)
- [ ] Open `http://localhost:5173` in browser B (incognito or different profile)

## Prerequisites
- [ ] In browser A, register `seller-uat@students.waikato.ac.nz` / `Pass1234` / `SellerUAT`
- [ ] In browser B, register `viewer-uat@students.waikato.ac.nz` / `Pass1234` / `ViewerUAT`
- [ ] Browser A creates one AVAILABLE listing — note its ID from the URL (e.g. `/listings/42`)

## Public detail page (non-owner view)
- [ ] Browser B (logged in but not the owner) navigates to `/listings/42/detail`
- [ ] Page loads with title, description, price, image, category, condition, owner nickname
- [ ] No "Edit" button visible
- [ ] No "Mark Reserved / Mark Sold / Relist / Delete" buttons visible
- [ ] "Contact seller" button is visible

## Public detail page (owner view of own listing)
- [ ] Browser A (the owner) navigates to `/listings/42/detail`
- [ ] No "Contact seller" button is visible (you can't message yourself)

## Public detail — anonymous (logged out)
- [ ] Open a third tab in incognito mode (no login at all)
- [ ] Navigate directly to `/listings/42/detail`
- [ ] Page loads (does NOT redirect to /login)
- [ ] No "Contact seller" button (logged-out users can't message)
- [ ] No mutation buttons (logged-out users definitely can't edit/delete)

## Owner-only mutation enforcement (API-level)
- [ ] Browser B opens DevTools → Network tab
- [ ] Browser B issues `PUT /api/listings/42` (any payload) — expect 403 FORBIDDEN with `code=NOT_LISTING_OWNER`
- [ ] Browser B issues `PATCH /api/listings/42/status?status=SOLD` — expect 403
- [ ] Browser B issues `DELETE /api/listings/42` — expect 403

## Soft-delete enumeration defence
- [ ] Browser A: from detail page, click "Delete" → confirm
- [ ] Browser B (already had the URL `/listings/42/detail` cached) refreshes the page
- [ ] Public detail returns 404 (or the page shows a "not found" / redirects), not the soft-deleted record
- [ ] Browser B tries `/api/listings/42/detail` directly via DevTools → 404 NOT_FOUND
- [ ] Browser A switches "Removed" filter on `/listings/mine` → the listing reappears (only for the owner)

## Image access control
- [ ] Browser A uploads a new listing with an image — note its imageUrl (e.g. `/api/uploads/listings/uuid.jpg`)
- [ ] While the listing is AVAILABLE, browser B can `GET` that image URL — expect 200 (public listings expose images)
- [ ] Browser A marks the listing REMOVED
- [ ] Browser B refreshes that image URL — expect 404 (image hidden once owner soft-deletes)

## Rate limiting feedback
- [ ] Browser A rapidly creates 20+ listings (script via `fetch` in DevTools, or fast UI clicks)
- [ ] 21st request returns HTTP 429 with `code=RATE_LIMITED` and `retryAfterSeconds`
- [ ] Frontend toast / error banner shows a friendly message including the retry hint
- [ ] Wait for the retry window — listing creation works again

## Hard-paths and edge cases
- [ ] Navigate to `/listings/999999/detail` (non-existent ID) → 404 page or redirect
- [ ] Navigate to `/listings/42/edit` while logged out → redirects to `/login?next=...`
- [ ] Navigate to `/listings/42/edit` as a non-owner → redirected away or sees "not your listing" error
- [ ] Try `/listings/abc/detail` (non-numeric) → 404 / route mismatch (no app crash)

## What this checklist intentionally does NOT cover
- Owner-side CRUD happy path — already covered in Epic 2 manual checklist
- Public browse / search / pagination — see Epic 4 manual checklist
- Conversation / messaging — see Epic 5 manual checklist
