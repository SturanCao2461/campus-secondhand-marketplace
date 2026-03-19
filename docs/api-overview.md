# API Overview

Base URL: `http://localhost:8080/api`

All endpoints return JSON. Authentication uses JWT Bearer tokens (not yet implemented).

---

## Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health` | None | Service health check |

---

## Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | None | Register a new campus user |
| POST | `/auth/login` | None | Login and receive JWT token |
| POST | `/auth/logout` | Bearer | Logout / invalidate token |

### POST /auth/register
```json
{
  "name": "Jane Smith",
  "email": "jane@university.edu",
  "password": "securepassword"
}
```

### POST /auth/login
```json
{
  "email": "jane@university.edu",
  "password": "securepassword"
}
```

---

## Listings

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/listings` | None | Get all active listings |
| GET | `/listings/{id}` | None | Get listing by ID |
| GET | `/listings/my` | Bearer | Get current user's listings |
| POST | `/listings` | Bearer | Create a new listing |
| PUT | `/listings/{id}` | Bearer | Update a listing |
| DELETE | `/listings/{id}` | Bearer | Delete a listing |

### POST /listings (request body)
```json
{
  "title": "Calculus Textbook 3rd Ed.",
  "description": "Good condition, some highlights.",
  "price": 25.00,
  "condition": "GOOD",
  "category": "Books"
}
```

---

## Favorites

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/favorites` | Bearer | Get user's favorite listings |
| POST | `/favorites/{listingId}` | Bearer | Add listing to favorites |
| DELETE | `/favorites/{listingId}` | Bearer | Remove from favorites |

---

## Messages

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/messages` | Bearer | Get all conversations |
| GET | `/messages/{conversationId}` | Bearer | Get messages in conversation |
| POST | `/messages` | Bearer | Send a message |

### POST /messages (request body)
```json
{
  "receiverId": 42,
  "listingId": 7,
  "content": "Is this still available?"
}
```

---

## Error Responses

All errors return:
```json
{
  "error": "Description of what went wrong",
  "status": 400
}
```

TODO: Standardise error response format with a global exception handler.
