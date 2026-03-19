# MVP Scope

## What is Campus Marketplace?

A campus-only second-hand marketplace web app. It allows university students, staff, and employees to buy and sell second-hand items within the campus community.

**Core principles:**
- Trust and safety: only verified campus members
- Simplicity: easy to list, easy to browse
- Local: in-person pickup only — no shipping, no payments
- Privacy: minimal personal data collected

---

## ✅ In Scope (MVP)

| Feature | Description |
|---------|-------------|
| User registration & login | Campus email required (JWT auth) |
| Browse listings | View all active listings with basic filters |
| Listing detail | View item info, price, condition, seller |
| Create/edit/delete listing | Sellers manage their own items |
| Favorites | Save listings for later |
| Messaging | Simple in-app messages between buyer and seller |
| Responsive UI | Mobile-friendly layout |

---

## ❌ Out of Scope (MVP)

| Feature | Reason |
|---------|--------|
| Online payment | Out of scope — campus trust model |
| Shipping / delivery | Out of scope — local pickup only |
| Admin panel | Not needed for course MVP |
| Image upload | Complex — TODO for later phase |
| Email verification | TODO for later phase |
| Rating/reviews | TODO for later phase |
| Real-time notifications | TODO for later phase |
| Mobile app | Out of scope |

---

## Milestones (12-week course project)

| Week | Goal |
|------|------|
| 1–2 | Project setup, monorepo, CI, DB design |
| 3–4 | User auth (register, login, JWT) |
| 5–6 | Listings CRUD + browse/search |
| 7–8 | Favorites + messaging |
| 9–10 | UI polish, validation, error handling |
| 11 | Testing (unit + integration) |
| 12 | Demo, final cleanup, documentation |
