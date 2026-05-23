# Manual E2E Checklist — Epic 2: Listings

Run through this checklist with backend, frontend, MySQL, and Redis all running locally.

## Setup
- [ ] `cd infra && docker compose up -d`
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] `cd frontend && npm run dev`
- [ ] Open `http://localhost:5173` in a browser

## Prerequisites
- [ ] Register a new user (e.g. `listing-test@students.waikato.ac.nz` / `Pass1234` / `ListTester`)
- [ ] Verify navbar shows "My Listings" + "Hi, ListTester"
- [ ] **Verify the email** (D-75 gate): copy the `/verify-email?token=...` URL from the backend log → paste into the browser → success card → /me shows green "Email verified" chip. Without this step, the next section will fail with `EMAIL_NOT_VERIFIED`.

## Create Listing
- [ ] Click "My Listings" → click "+ New Listing"
- [ ] Fill: Title="Test Textbook", Description="Great condition", Category=Books, Type=Sell, Price=25.00
- [ ] Select a JPEG/PNG image (< 5 MB)
- [ ] Click "Create Listing" → redirects to My Listings
- [ ] Verify the new listing card appears with title, price, image thumbnail, status "AVAILABLE"

## Create Listing — Validation
- [ ] Try creating without an image → expect "Please select an image" error
- [ ] Try creating SELL type without price → expect "Selling listings must have a positive price" error
- [ ] Try creating with a non-existent category (via DevTools) → expect "Category is not available" error

## My Listings — Filtering
- [ ] Create 2+ listings with different statuses (use status buttons on detail page)
- [ ] On My Listings page, click "Reserved" filter → only reserved listings shown
- [ ] Click "All" → all listings shown again
- [ ] Click "Removed" → only deleted listings shown (if any)

## Listing Detail
- [ ] Click a listing card → detail page shows all fields (title, description, price, image, category, condition, meet-at, negotiable badge)
- [ ] Image loads correctly (not broken)

## Status Transitions (FSM)
- [ ] From AVAILABLE: click "Mark Reserved" → status changes to RESERVED
- [ ] From RESERVED: click "Mark Sold" → status changes to SOLD
- [ ] From SOLD: click "Relist" → status changes back to AVAILABLE
- [ ] From AVAILABLE: click "Mark Reserved" then "Back to Available" → returns to AVAILABLE
- [ ] From any non-REMOVED: click "Delete" → confirm dialog → redirects to My Listings

## Edit Listing
- [ ] From detail page, click "Edit" → edit form prefilled with current values
- [ ] Change title and price → click "Save Changes" → redirects to detail with updated values
- [ ] Verify image is preserved when not uploading a new one
- [ ] Upload a new image → verify the image URL changes on the detail page

## Delete (Idempotent)
- [ ] Delete a listing → verify it disappears from default My Listings view
- [ ] Switch to "Removed" filter → verify it appears there
- [ ] Click into the removed listing → verify "This listing has been removed" banner
- [ ] Verify no edit/status buttons are shown for removed listings

## Anti-Enumeration (requires 2 users)
- [ ] User A creates a listing, then marks it REMOVED
- [ ] User B (different browser/incognito) tries to access User A's removed listing URL directly
- [ ] Expect redirect to My Listings (frontend handles 404 by navigating away)

## Image Security
- [ ] User A creates a listing with an image
- [ ] Copy the imageUrl from the response (e.g. `/api/uploads/listings/uuid.jpg`)
- [ ] User B tries to access that URL directly → expect 404 (owner check blocks)

## Rate Limiting
- [ ] Create 20 listings rapidly (script or fast clicking) → 21st should return 429
- [ ] Verify the error message mentions rate limit

## Edge Cases
- [ ] Try editing a REMOVED listing (navigate directly to `/listings/{id}/edit`) → expect "removed" banner, no form
- [ ] Try accessing a non-existent listing ID → expect redirect to My Listings
- [ ] (D-75) Log out and visit `/listings/new` → redirected to `/login?next=...`. Register a fresh account, do NOT verify the email, try to submit a listing → 403 `EMAIL_NOT_VERIFIED`. Verify via `/verify-email?token=...` from backend log → submitting now succeeds.
