# Manual E2E Full Walkthrough

> A linear-narrative pass that hits every feature in the MVP exactly once, told as a story between two users (Alice the seller and Bob the buyer). Allow ~60 minutes. Use two browser profiles (or one normal window + one incognito) so both sessions stay logged in simultaneously.
>
> For per-feature deep dives, see the five epic-scoped checklists (`manual-e2e-epic1.md` .. `manual-e2e-epic5.md`). This document is the **integration narrative** — it proves the features compose into a coherent product, not just that each one works in isolation.

---

## Phase 0 — Setup (5 min)

- [ ] `cd infra && docker compose up -d` — MySQL + Redis containers up
- [ ] `docker ps` — confirm `campus_mysql` and `campus_redis` are running
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — backend on `:8080`
- [ ] `cd frontend && npm run dev` — frontend on `:5173`
- [ ] Open **Profile A** (Alice — normal window) → `http://localhost:5173`
- [ ] Open **Profile B** (Bob — incognito or second profile) → `http://localhost:5173`
- [ ] Keep the backend terminal visible — verification emails and password-reset tokens log there in dev mode

**Optional clean slate:**

```bash
docker exec campus_mysql mysql -uroot -proot -e "DROP DATABASE IF EXISTS campus; CREATE DATABASE campus;"
docker exec campus_redis redis-cli FLUSHDB
```

Then restart the backend to let JPA recreate the schema.

---

## Phase 1 — Alice registers and verifies (5 min)

**Profile A (Alice):**

- [ ] Click "Sign up" → `/register`
- [ ] Try `alice@gmail.com` → expect red error "Only @students.waikato.ac.nz emails are allowed."
- [ ] Try password `abc` → expect "Password must be 8–64 characters with at least one letter and one digit."
- [ ] Register `alice@students.waikato.ac.nz` / `Pass1234` / `Alice` → redirected to `/`, navbar shows "Hi, Alice"
- [ ] Visit `/me` → amber chip "Email not verified" + yellow banner with "Resend verification email" button
- [ ] In the **backend terminal**, find the `===== EMAIL (console mode) =====` block. Copy the `/verify-email?token=...` URL
- [ ] Paste the URL into Alice's browser → green "Email verified" success card → click "Continue to home"
- [ ] Visit `/me` → chip is now green "Email verified", banner gone

---

## Phase 2 — Alice posts two listings (10 min)

**Profile A (Alice):**

### Listing 1 — paid item with image

- [ ] Click "Post a listing" → `/listings/new`
- [ ] Fill: Title `MacBook Pro 13" 2020`, Description `Lightly used, 256GB SSD, great battery.`, Category `Electronics`, Condition `Like new`, Type `Sell`, Price `850`
- [ ] Upload an image (any JPG/PNG under the size limit)
- [ ] Submit → redirect to `/listings/:id`
- [ ] Verify the page shows: image, title, price `NZ$850.00`, status `Available`, your name, and edit/status/delete controls (owner view)

### Listing 2 — giveaway

- [ ] `/listings/new` again
- [ ] Title `Free Engineering Textbook`, Description `MATH102 textbook, taking up shelf space.`, Category `Books`, Condition `Used`, Type `Giveaway` (price field should disable / hide)
- [ ] Skip image upload
- [ ] Submit → detail page shows `Giveaway` (no price)

---

## Phase 3 — Bob registers (with the soft gate) (5 min)

**Profile B (Bob):**

- [ ] Register `bob@students.waikato.ac.nz` / `Pass1234` / `Bob` → land on `/`
- [ ] **Without verifying yet**, try `/listings/new` and submit a complete form → expect a red error banner mentioning email verification (soft gate, D-75)
- [ ] Visit `/me`, click "Resend verification email" → button changes to "Sending…" → green "Sent — check your inbox"
- [ ] Backend terminal prints a second email block — copy the `/verify-email?token=...` URL
- [ ] Paste into Bob's browser → green success card → "Continue to home"
- [ ] `/me` chip is now green

---

## Phase 4 — Bob browses and searches (10 min)

**Profile B (Bob):**

- [ ] Visit `/` (or click "Browse") → see both of Alice's listings
- [ ] Search box: type `macbook` → only the MacBook shows
- [ ] Clear search; filter Category `Books` → only the textbook shows; URL updates to include `?category=Books`
- [ ] Filter Type `Giveaway` → only the textbook shows
- [ ] Clear filters; sort by `Price: low to high` → giveaway first (or however your sort handles null price), then MacBook
- [ ] Sort by `Newest` → textbook first (it was posted second)
- [ ] **Copy the current URL** (with filters/sort applied) → open in a new tab → state restores from URL
- [ ] Pagination: if you have < 12 listings, skip; otherwise post a few more from Alice to test page 2 navigation
- [ ] Click the MacBook → land on `/listings/:id`
- [ ] As a non-owner, verify you see: image, title, price, status, seller name, and a **"Message seller"** button — but no edit/status/delete controls

---

## Phase 5 — Bob messages Alice (10 min)

**Profile B (Bob):**

- [ ] On the MacBook detail page, click "Message seller" → conversation page opens with the listing pinned at the top
- [ ] Send: `Hi! Is the MacBook still available? Could I see it on campus?`
- [ ] Verify the message appears immediately on the right side (sender = you)

**Profile A (Alice):**

- [ ] Navbar should show a red unread badge on the "Messages" link within ~5 seconds (visibility-aware polling)
- [ ] Click "Messages" → conversation list shows Bob's conversation with unread indicator
- [ ] Click the conversation → see Bob's message; the unread badge clears
- [ ] Reply: `Yes, still available! How about Thursday 2pm at S Block?`

**Profile B (Bob):**

- [ ] Unread badge appears on Messages within ~5 seconds
- [ ] Open conversation → see Alice's reply
- [ ] Reply: `Sounds good. See you then.`

### Visibility-aware polling check

- [ ] In Profile A, switch to a different browser tab for ~30 seconds → polling pauses (no network calls in DevTools → Network)
- [ ] Switch back → polling resumes immediately and any pending messages are fetched

### Browser notifications (optional)

- [ ] In Profile A on the Messages page, accept the browser notification permission prompt if it appears
- [ ] Send another message from Bob → Alice should get a native browser notification (if the tab is unfocused)

---

## Phase 6 — Alice manages her listing lifecycle (5 min)

**Profile A (Alice):**

- [ ] `/me/listings` (or however the "my listings" view is reached) → see both of your listings
- [ ] Open the MacBook → click "Edit" → change price to `800` → save → detail page shows updated price
- [ ] Click status control → change to `Reserved` → status chip updates

**Profile B (Bob):**

- [ ] Refresh the MacBook detail page → see `Reserved` chip (document whether "Message seller" is still shown — depends on your business rule)

**Profile A (Alice):**

- [ ] Change MacBook status to `Sold` → chip updates
- [ ] On the textbook listing, click "Delete" → confirm → listing disappears from the browse page (soft-deleted = `Removed` status, hidden from public list)

**Profile B (Bob):**

- [ ] Refresh browse page → textbook is gone (document whether `Sold` MacBook is still listed)

---

## Phase 7 — Negative paths and security (5 min)

### Forbidden actions for non-owners

**Profile B (Bob):**

- [ ] Try `/listings/<alice-listing-id>/edit` directly in the URL → expect 403 or redirect (not an edit form)

### Rate limiting

**Profile B (Bob), log out first:**

- [ ] On `/login`, submit wrong password for `alice@students.waikato.ac.nz` 5 times in a row
- [ ] On the 6th attempt → expect "Too many attempts. Try again in 15 minutes."
- [ ] To recover: `docker exec campus_redis redis-cli FLUSHDB` and refresh

### Cookie security

- [ ] DevTools → Application → Cookies → `localhost:5173` → `token` cookie:
  - [ ] `HttpOnly` checked
  - [ ] `SameSite=Lax`
  - [ ] `Secure` unchecked (we're on HTTP locally)
  - [ ] `Expires` ~7 days out

### Logout revocation

- [ ] Log in as Alice. Copy the `token` cookie value
- [ ] Click "Log out"
- [ ] In DevTools, manually re-set the `token` cookie to the copied value
- [ ] Visit `/me` → redirected to `/login` (jti is blacklisted in Redis)

---

## Phase 8 — Password reset (5 min)

**Profile A (Alice), logged out:**

- [ ] Click "Forgot password?"
- [ ] Submit `alice@students.waikato.ac.nz` → "Check your email" page
- [ ] Backend terminal prints the reset email — copy the `/reset-password?token=...` URL
- [ ] Open the URL → "Set a new password" form
- [ ] Set `NewPass99` → success
- [ ] Try logging in with old `Pass1234` → fails
- [ ] Log in with `NewPass99` → success

### Anti-enumeration

- [ ] Log out. Forgot-password for `nobody@students.waikato.ac.nz` (does not exist) → identical "Check your email" page (no leak)

---

## Phase 9 — API surface (optional, 5 min)

- [ ] Visit `http://localhost:8080/swagger-ui/index.html`
- [ ] Browse the auto-generated endpoint list — confirm Auth, Listings, Messages, Users groups are present
- [ ] (If logged in via the SPA, the cookie is shared) try `GET /api/listings` from the Swagger "Try it out" button → 200 with paginated JSON
- [ ] Try `POST /api/listings` without auth → 401

---

## Wrap-up

- [ ] All checkboxes above completed without unexpected behavior
- [ ] Note any surprises, papercuts, or UI quirks in a scratch file — these become the input for the next UI polish pass
- [ ] Stop frontend / backend (Ctrl+C)
- [ ] (Optional) `cd infra && docker compose down`

---

## What this walkthrough proves

| Feature area | Phase | Notes |
|---|---|---|
| Campus-only registration + validation | 1, 3 | Both happy path and validation errors |
| Email verification soft gate | 1, 3 | Including resend + unverified blocked from POST |
| Listing CRUD + image upload | 2, 6 | Both paid and giveaway types |
| Public browse / search / filter / sort / URL state | 4 | URL preserves filter state |
| Ownership-aware detail page | 2, 4 | Owner sees controls; non-owner sees "Message seller" |
| Messaging (polling + unread + visibility) | 5 | Unread badges, visibility-aware polling, notifications |
| Status lifecycle (Available → Reserved → Sold → Removed) | 6 | All four transitions observed |
| Authorization (non-owner cannot edit) | 7 | Direct-URL access blocked |
| Rate limiting | 7 | 5-attempt login throttle |
| Cookie security (HttpOnly, SameSite, expiry) | 7 | Manual inspection |
| JWT blacklist on logout | 7 | Re-injected cookie rejected |
| Password reset + anti-enumeration | 8 | Token flow + no email leak |
| API documentation | 9 | Swagger UI present and exercised |
