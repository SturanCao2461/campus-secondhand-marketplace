# Manual E2E Checklist — Epic 4: Public Browse, Search, Filter

> Verifies the public `/browse` page: server-side filtering, pagination, URL-driven state, sort behaviour, and the empty / error states.

Run through this checklist with backend, frontend, MySQL, and Redis all running locally.

## Setup
- [ ] `cd infra && docker compose up -d`
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] `cd frontend && npm run dev`
- [ ] Open `http://localhost:5173` in a browser

## Prerequisites — seed data
- [ ] Register a seller account, e.g. `seeder@students.waikato.ac.nz` / `Pass1234` / `Seeder`
- [ ] Create at least 15 listings with varied attributes:
  - Mix of categories (BOOKS, ELECTRONICS, FURNITURE, CLOTHING, SPORTS)
  - Mix of types (most SELL with prices spanning $0.50 to $500+, some GIVEAWAY)
  - Mix of conditions (NEW, LIKE_NEW, GOOD, FAIR)
  - At least one with a long title (~80 chars), one with the shortest allowed (3 chars)
  - At least one in RESERVED status, at least one in SOLD status (so you can verify they're hidden)
- [ ] Note the IDs / titles of one specific listing per category for searching later

## Anonymous access
- [ ] Open `http://localhost:5173/browse` while logged out
- [ ] Page renders without redirecting to /login
- [ ] At least one listing card is visible (assuming AVAILABLE listings exist)

## Status filtering (server-side)
- [ ] Browse page only shows listings with status AVAILABLE
- [ ] No card shows a "RESERVED" or "SOLD" badge
- [ ] No card shows a soft-deleted (REMOVED) listing

## Keyword search
- [ ] Pick a unique word from one of your listings — type it in the search input → press Enter
- [ ] URL updates with `?q=...`
- [ ] Only matching listings render
- [ ] Type a word that no listing contains → empty state appears with "No listings found" + filter reset hint
- [ ] Clear the search → all listings come back

## Category filter
- [ ] Select Category = Electronics → only ELECTRONICS-tagged listings shown
- [ ] URL contains `categoryCode=ELECTRONICS`
- [ ] Switch to Books → list updates, URL changes
- [ ] Reset to "All categories" → URL drops the parameter, all listings return

## Listing-type filter
- [ ] Select Type = Giveaway → only GIVEAWAY listings shown, all priced "Free" (or with $0/null price)
- [ ] Select Type = Sell → only listings with a non-null price shown
- [ ] Reset to "All types" → all return

## Sort
- [ ] Sort = Newest (default) → cards in descending createdAt order
- [ ] Switch Sort = Price ascending → giveaways/cheapest first; verify by reading the prices
- [ ] Switch Sort = Price descending → most expensive first
- [ ] URL reflects the sort parameter

## Pagination
- [ ] With 12+ listings on page 1, "Next" button is enabled
- [ ] Click Next → URL gets `page=2`, new set of listings render
- [ ] "Previous" is now enabled, "Page 2 of N" indicator appears
- [ ] Click Previous → back to page 1, Previous becomes disabled

## URL state restoration
- [ ] Apply: keyword "test" + category "BOOKS" + type "SELL" + sort "PRICE_ASC" + page 2
- [ ] Copy the full URL
- [ ] Open that URL in a new tab → all 5 filters/sort/page should be restored from URL
- [ ] Hit browser Back button → previous filter state restored

## Combined filters
- [ ] Search "book" + category Books → only listings matching both
- [ ] Search "book" in Electronics category → empty state (assuming no books in electronics)
- [ ] No-results empty state shows the same message + filter reset CTA

## Error / empty states
- [ ] Stop the backend (`Ctrl+C` in the backend terminal)
- [ ] Refresh `/browse` → frontend shows a friendly error banner (not a blank page or stack trace)
- [ ] Restart backend → click "Try again" or refresh → page recovers

## Accessibility / keyboard
- [ ] Tab through the search input + filter selects + pagination buttons → all are focusable
- [ ] Enter key in the search input triggers a search (not a form submit causing a page reload)

## Listing detail entry from browse
- [ ] Click any card → navigate to `/listings/{id}/detail`
- [ ] Detail page loads with all expected fields
- [ ] Browser Back → returns to /browse with all your filter state intact

## What this checklist intentionally does NOT cover
- Owner-side CRUD — see Epic 2/3 manual checklists
- Conversation / Contact seller — see Epic 5 manual checklist
- Image security beyond browsing — see Epic 3 manual checklist
