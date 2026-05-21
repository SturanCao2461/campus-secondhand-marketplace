# Manual E2E Checklist — Epic 5: Messaging

> Verifies the conversation + message flow: idempotent conversation creation, cursor-based message pagination, unread counts, visibility-aware polling, browser notifications, and rate limiting.

Run through this checklist with backend, frontend, MySQL, and Redis all running locally. Most cases need TWO browser profiles (or one regular + one incognito) so a buyer and seller can interact in real time.

## Setup
- [ ] `cd infra && docker compose up -d`
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] `cd frontend && npm run dev`
- [ ] Open `http://localhost:5173` in browser A (regular)
- [ ] Open `http://localhost:5173` in browser B (incognito or different profile)

## Prerequisites
- [ ] Browser A — register `seller-msg@students.waikato.ac.nz` / `Pass1234` / `MsgSeller`
- [ ] Browser B — register `buyer-msg@students.waikato.ac.nz` / `Pass1234` / `MsgBuyer`
- [ ] Browser A — create one AVAILABLE listing, e.g. "Calculus Textbook" — note its public detail URL `/listings/{id}/detail`

## Initiate conversation (Contact seller)
- [ ] Browser B — log in, navigate to `/listings/{id}/detail`
- [ ] "Contact seller" button is visible
- [ ] Click "Contact seller" → URL navigates to `/conversations/{conversationId}` (numeric)
- [ ] Page renders ChatPage with the listing snapshot at the top, empty message list, and input bar at the bottom

## Idempotency — opening "Contact seller" twice
- [ ] Browser B — go back to `/listings/{id}/detail`
- [ ] Click "Contact seller" again
- [ ] Land on the SAME conversation (same numeric ID in URL), not a new one
- [ ] Backend log shows the existing conversation was returned (no INSERT)

## Cannot contact yourself
- [ ] Browser A (the seller / owner) — navigate to own `/listings/{id}/detail`
- [ ] No "Contact seller" button visible

## Anonymous (logged out) cannot contact
- [ ] Open a third tab in incognito (not logged in)
- [ ] Navigate to `/listings/{id}/detail`
- [ ] No "Contact seller" button visible (or button click redirects to /login)

## Send a message (buyer side)
- [ ] Browser B — in the chat, type "Is this still available?" → click Send (or press Enter)
- [ ] Message bubble appears immediately on the right (optimistic update)
- [ ] Within 5–6 seconds, the message is reconciled with the server-assigned ID and timestamp
- [ ] Character counter updates as you type (e.g. "14 / 1000")
- [ ] Try sending an empty message → button disabled or no-op
- [ ] Try sending a 1001-character message → 400 / validation error visible

## Receive a message (seller side, real time-ish)
- [ ] Browser A — navigate to `/conversations`
- [ ] Within 15 seconds of B sending, the navbar Messages badge shows "1" unread
- [ ] Conversation list shows the conversation with the listing thumbnail, last message preview, and an unread indicator
- [ ] Click into the conversation → ChatPage opens, B's message visible on the LEFT (received) side
- [ ] After opening, the unread badge in the navbar drops to 0 within 15 seconds
- [ ] Chat polls every 5s while open (verify in DevTools Network tab — `GET /api/conversations/{id}/messages?after=...`)

## Bidirectional flow
- [ ] Browser A (in chat) — type a reply "Yes still available, $25 firm" → Send
- [ ] Browser A's message appears on the right
- [ ] Browser B (still on chat) — within ~5s, A's message appears on the LEFT
- [ ] Send several short messages back-and-forth — all appear in chronological order with day separators if dates change

## Unread count semantics
- [ ] Browser A — leave the chat (navigate to home)
- [ ] Browser B — send 3 more messages
- [ ] Browser A's navbar badge shows "1" (NOT 3 — unread is per-conversation, not per-message)
- [ ] Open browser A's `/conversations` → conversation shows the unread dot
- [ ] Click in → after a moment, badge clears, dot clears

## Message pagination (cursor-based)
- [ ] Send 35+ messages in one conversation (script via `fetch` if needed)
- [ ] Reload the chat page — only the newest 30 are loaded initially
- [ ] Scroll to the top of the message list → older messages load (cursor `before=`)
- [ ] Verify no duplicates appear, ordering is stable

## Visibility-aware polling
- [ ] Open browser A on `/conversations` (or any messaging page)
- [ ] Open browser DevTools → Network tab → filter by "unread-count"
- [ ] Switch to a different browser tab (so the marketplace tab is hidden)
- [ ] Wait 30+ seconds → no `unread-count` requests should fire while hidden
- [ ] Switch back to the marketplace tab → polling resumes immediately

## Browser notification (optional, requires permission grant)
- [ ] Browser A — first time on a chat page, browser asks for Notification permission → grant it
- [ ] Browser A — switch to a different tab (so chat is hidden)
- [ ] Browser B — send a new message
- [ ] Within 5s, browser A receives a desktop notification with the sender + content preview
- [ ] Click the notification → focus jumps to the marketplace chat tab

## Rate limiting (per user, per minute)
- [ ] Browser B — send messages as fast as possible (script `fetch` loop)
- [ ] After ~10 messages in a minute, expect 429 with `code=RATE_LIMITED`
- [ ] Frontend shows toast / banner "You're sending messages too fast"
- [ ] Wait the retry window — sending works again

## Conversation list — empty state
- [ ] Open a fresh user's `/conversations` page (one who has never messaged anyone)
- [ ] Empty state visible: chat-bubble icon + "No conversations yet" + CTA link to /browse

## Edge cases
- [ ] Try `/conversations/999999` (a conversation you're not part of) → 404 or 403, not the chat
- [ ] Mark the listing SOLD as the seller → buyer can still see and continue the conversation (sale lifecycle does not close threads)
- [ ] Soft-delete the listing → buyer can still load the existing conversation (chat history is preserved), but new conversations on that listing are blocked

## What this checklist intentionally does NOT cover
- Listing CRUD or status transitions — see Epic 2/3 manual checklists
- Browse / Search — see Epic 4 manual checklist
- Real-time WebSocket-style push — explicitly out of scope; messaging uses polling (see `docs/architecture.md` for the trade-off)
