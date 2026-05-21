# Milestone 5 — Messaging Module Design

**Date:** 2026-05-21
**Status:** Approved
**Epic:** Milestone 5 (Messaging)

## Goal

Enable buyers and sellers to communicate about specific listings through an in-app messaging system. No real-time WebSocket — visibility-aware REST polling provides "good enough" near-real-time behavior for the MVP.

## Scope

### In scope
- Per-listing conversations between a buyer and the listing's seller
- Multiple buyers per listing (each buyer-seller pair has one conversation per listing)
- Plain text messages (max 1000 chars)
- Unread count badge in navbar + browser notifications
- Page-visibility-aware polling (pauses when tab hidden)
- Messages remain accessible regardless of listing status

### Out of scope
- WebSocket / push protocols
- Image, file, or voice messages
- Message deletion / recall
- Read receipts beyond unread counts
- Typing indicators
- Group conversations
- Message search

## User Stories

1. As a buyer, I can click "Contact seller" on a listing detail page to start (or resume) a conversation about that listing.
2. As a seller, I can see all conversations grouped per listing, distinguishing different buyers.
3. As either party, I can see a navbar badge showing total unread messages across all my conversations.
4. As either party, while a conversation is open and the tab is visible, new messages from the other side appear automatically within ~5 seconds.
5. As either party, when I receive a new message while the tab is in the background, I get a browser notification (if permission granted).
6. As either party, I can continue to view conversation history after the listing is sold or removed.

## Data Model

### `conversation` table

| Column                 | Type        | Notes                                        |
|------------------------|-------------|----------------------------------------------|
| `id`                   | BIGINT PK   | Auto-increment                               |
| `listing_id`           | BIGINT FK   | References `listing.id`                      |
| `buyer_id`             | BIGINT FK   | References `user.id` (the conversation initiator) |
| `seller_id`            | BIGINT FK   | References `user.id` (denormalised from listing for query ease) |
| `buyer_last_read_at`   | TIMESTAMP   | Updated when buyer opens this conversation   |
| `seller_last_read_at`  | TIMESTAMP   | Updated when seller opens this conversation  |
| `created_at`           | TIMESTAMP   | Conversation creation time                   |
| `updated_at`           | TIMESTAMP   | Time of the most recent message              |

Indexes:
- `UNIQUE (listing_id, buyer_id)` — enforces one conversation per buyer per listing
- `INDEX (buyer_id)`
- `INDEX (seller_id)`

### `message` table

| Column            | Type        | Notes                              |
|-------------------|-------------|------------------------------------|
| `id`              | BIGINT PK   | Auto-increment                     |
| `conversation_id` | BIGINT FK   | References `conversation.id`       |
| `sender_id`       | BIGINT FK   | References `user.id`               |
| `content`         | VARCHAR(1000) | Non-blank, max 1000 chars        |
| `created_at`      | TIMESTAMP   | Send time                          |

Indexes:
- `INDEX (conversation_id, created_at)` — supports message pagination and cursor lookup

### Unread count semantics

For a user `U` in conversation `C`:
- If `U` is the buyer: unread = `COUNT(message)` where `conversation_id = C.id`, `sender_id != U.id`, `created_at > C.buyer_last_read_at`
- If `U` is the seller: same logic against `C.seller_last_read_at`
- `last_read_at` is updated to "now" when the user fetches messages for that conversation

## API

All endpoints require authentication via existing JWT filter.

### Conversations

| Method | Path                                  | Description                                                                                  |
|--------|---------------------------------------|----------------------------------------------------------------------------------------------|
| POST   | `/api/conversations`                  | Body: `{listingId}`. Returns existing conversation if one exists; otherwise creates it. Rejects if caller is the listing's seller. |
| GET    | `/api/conversations`                  | Returns the caller's conversations (both buyer and seller side), each with last message preview, unread count, listing summary. Sorted by `updated_at` DESC. |
| GET    | `/api/conversations/{id}`             | Returns the conversation's metadata (listing summary, counterpart info). Does **not** update `last_read_at` — the messages endpoint owns that side effect. |

### Messages

| Method | Path                                            | Description                                                                                                          |
|--------|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| GET    | `/api/conversations/{id}/messages`              | Query params: `after` (message id, optional), `limit` (default 50, max 100). Returns messages ordered ASC. Also updates `last_read_at`. |
| POST   | `/api/conversations/{id}/messages`              | Body: `{content}`. Returns the created `MessageResponse`. Updates conversation `updated_at`.                         |

### Unread count

| Method | Path                                  | Description                                                          |
|--------|---------------------------------------|----------------------------------------------------------------------|
| GET    | `/api/conversations/unread-count`     | Returns `{total: number}` — sum of unread across all conversations. |

### Authorization rules
- `GET/POST` on a specific conversation: caller must be `buyer_id` or `seller_id`; otherwise 403 `CONVERSATION_FORBIDDEN`.
- `POST /api/conversations`: caller must not be the listing's seller; otherwise 400 `CANNOT_MESSAGE_SELF`.

### Error codes (added to `ErrorCode` enum)

| Code                        | HTTP | Trigger                                                  |
|-----------------------------|------|----------------------------------------------------------|
| `CONVERSATION_NOT_FOUND`    | 404  | Conversation id does not exist                           |
| `CONVERSATION_FORBIDDEN`    | 403  | Caller is neither buyer nor seller of the conversation   |
| `CANNOT_MESSAGE_SELF`       | 400  | Listing's seller tries to start a conversation on it     |
| `LISTING_NOT_FOUND`         | 404  | (Reuse existing) listingId in POST does not exist        |
| `MESSAGE_CONTENT_INVALID`   | 400  | Content blank or > 1000 chars (also covered by bean validation) |

### Rate limits (reuse existing `RateLimitService`)
- Send message: 30 / minute / user
- Create conversation: 10 / minute / user

## Backend Structure

```
nz.ac.waikato.campusmarketplace
├── controller/
│   └── ConversationController.java
├── dto/
│   ├── CreateConversationRequest.java
│   ├── SendMessageRequest.java
│   ├── ConversationSummary.java          // for list view
│   ├── ConversationDetail.java           // for single conversation view
│   ├── MessageResponse.java
│   └── UnreadCountResponse.java
├── entity/
│   ├── Conversation.java
│   └── Message.java
├── repository/
│   ├── ConversationRepository.java
│   └── MessageRepository.java
└── service/
    └── ConversationService.java
```

### Service responsibilities

- `createOrGetConversation(listingId, buyerId)` — atomic find-or-create; validates listing exists and buyer is not the seller.
- `getConversationsForUser(userId)` — returns conversations where user is buyer OR seller, joined with last message + unread count + listing summary.
- `getConversationDetail(conversationId, userId)` — verifies participation, returns metadata DTO. Does not touch `last_read_at`.
- `getMessages(conversationId, userId, afterId, limit)` — verifies participation, fetches messages via cursor (`after`), updates caller's `last_read_at` to "now".
- `sendMessage(conversationId, senderId, content)` — verifies participation, applies rate limit, inserts message, updates `conversation.updated_at`.
- `getUnreadCount(userId)` — sums unread across all conversations for the user.

### Security
- `SecurityConfig`: `/api/conversations/**` requires authentication (default behaviour, no permitAll needed).
- All authorization enforced in `ConversationService` using the principal's user id.

## Frontend Structure

### New routes

| Route                         | Component             | Description                            |
|-------------------------------|-----------------------|----------------------------------------|
| `/conversations`              | `ConversationsPage`   | List of conversations                  |
| `/conversations/:id`          | `ChatPage`            | Single conversation view               |

Both routes are protected (wrap in existing `ProtectedRoute`).

### Entry points
- **PublicDetailPage / ListingDetailPage:** Add "Contact seller" button — visible only when authenticated and current user is not the seller. On click: `POST /api/conversations` then `navigate(/conversations/${id})`.
- **Navbar:** Add a "Messages" link with an unread-count badge. Polls `/api/conversations/unread-count` every 15 s while page is visible.

### Components

```
frontend/src/
├── api/conversations.ts
├── pages/
│   ├── ConversationsPage.tsx
│   └── ChatPage.tsx
├── components/
│   ├── ConversationListItem.tsx
│   ├── MessageBubble.tsx
│   └── MessageComposer.tsx
└── hooks/
    ├── useUnreadCount.ts        // global navbar polling
    ├── useChatPolling.ts        // per-conversation message polling
    └── useBrowserNotification.ts
```

### Polling strategy
- **Global unread count:** `useUnreadCount` hook, mounted in `Navbar`. Uses `document.visibilityState` and `visibilitychange` event. Polls every 15 s when `visible`. Stops when `hidden`.
- **Chat messages:** `useChatPolling` hook, mounted in `ChatPage`. Polls `/messages?after={lastMessageId}` every 5 s when visible. Stops when hidden. Resumes immediately on `visibilitychange` to `visible`.

### Browser notification
- `useBrowserNotification` requests permission on first mount of `ConversationsPage` or `ChatPage`.
- If permission is `granted` AND `document.hidden === true` AND a new message arrives whose `senderId !== currentUserId`, fire `new Notification(...)`.
- If permission is `denied`, silently fall back to navbar badge only.
- Clicking a notification focuses the window and navigates to the conversation.

### UI

- **ConversationsPage:** Vertical list, each row shows listing thumbnail (60×60), counterpart's `nickname`, last message preview (truncated to ~60 chars), relative time, blue dot if unread > 0.
- **ChatPage:** Sticky header (counterpart `nickname` + listing title clickable to listing detail). Scrollable message list, own messages right-aligned blue bubbles, counterpart messages left-aligned grey bubbles, day separators. Sticky composer at bottom: textarea + Send button. Enter sends, Shift+Enter newline. Char counter when approaching 1000.

## Testing Strategy

### Backend
Target ~40 new tests, modelled on Milestone 3 structure.

- **Unit tests (`ConversationServiceTest`):**
  - `createOrGetConversation` is idempotent for the same `(listingId, buyerId)`
  - Seller cannot start a conversation on their own listing → `CANNOT_MESSAGE_SELF`
  - Non-participant cannot access conversation → `CONVERSATION_FORBIDDEN`
  - Unread count correctly excludes messages sent by self
  - Unread count correctly resets to 0 after fetching messages
  - Sending a message updates `conversation.updated_at`
  - Rate limits enforced

- **Integration tests (`ConversationControllerIT`):**
  - All endpoints: 200 happy path
  - 401 without JWT
  - 403 for non-participant
  - 400 for invalid content (blank, too long)
  - 404 for missing conversation / listing
  - Conversation list ordering by `updated_at` DESC
  - Cursor pagination via `after` returns only newer messages

### Frontend
- **Playwright E2E spec** (`tests/messaging.spec.ts`):
  - Buyer logs in, opens a listing, clicks "Contact seller", lands in chat
  - Buyer sends a message
  - Seller logs in in a second context, sees unread badge in navbar
  - Seller opens conversations page, sees unread dot, opens chat, replies
  - Buyer's chat (still open) polls and shows the new message
  - Both unread counts reach 0 after viewing

- **Manual E2E checklist** (`docs/manual-e2e-epic5.md`):
  - Browser notification permission flow (granted / denied / dismissed)
  - Notification fires when tab hidden, does not fire when visible
  - Polling stops when tab hidden, resumes when visible
  - 1000-char limit UI behaviour
  - Network error during send shows toast, message is not lost

## Operational Concerns

- **Schema migration:** Two new tables, additive only. JPA `ddl-auto: update` creates them on startup (consistent with existing convention).
- **Rollback:** Drop the two tables; no impact on existing data.
- **Capacity:** Campus scale (single university), expected message volume well within MySQL single-instance capacity. No sharding or archival needed.
- **Privacy:** Messages are stored in plain text. This matches the MVP threat model (no E2E encryption requirement).

## Open Questions

None — all design points were resolved during brainstorming. Items deliberately deferred to a future milestone:
- WebSocket upgrade path (current REST endpoints will be reused)
- Image/file attachments
- Message search
- Push notifications via service worker
- Block / report user

## Acceptance Criteria (Definition of Done)

1. All API endpoints implemented and authenticated.
2. Backend test suite (~40 tests) passes; total project test count grows accordingly.
3. Playwright E2E messaging spec passes.
4. ConversationsPage and ChatPage navigable, both protected.
5. Navbar shows live unread badge with visibility-aware polling.
6. Browser notification works when permission granted and tab hidden; gracefully degrades otherwise.
7. Listing detail pages show "Contact seller" CTA correctly (hidden for owner / unauthenticated).
8. Engineering journal entries added (decisions, surprises).
9. `dev-roadmap.md` updated to mark Milestone 5 complete.
