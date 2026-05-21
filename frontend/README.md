# Frontend — Campus Secondhand Marketplace

React 19 SPA in TypeScript, Vite-bundled, Tailwind-styled. Talks to the Spring Boot backend at `/api/*` (proxied in dev via `vite.config.ts`).

For project-wide context, quick start, and architecture, see the [root README](../README.md).

---

## Module map

```
src/
├── api/                 typed fetch clients (apiClient, listings, conversations, ...)
├── auth/                AuthProvider, useAuth, ProtectedRoute
├── components/          Navbar, ErrorBoundary, Spinner, Toast, PasswordInput
├── hooks/               useUnreadCount, useChatPolling, useBrowserNotification, useUnsavedChangesGuard
├── pages/               13 route components (Home, Browse, Listing*, Conversation*, ...)
├── App.tsx              router + provider tree
└── main.tsx             entry
e2e/                     22 Playwright specs + helpers (rate-limit reset, ESM paths)
```

---

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Vite dev server on `:5173`, proxies `/api` → backend `:8080` |
| `npm run build` | Type-check + production bundle to `dist/` |
| `npm run preview` | Serve the built bundle locally |
| `npm run lint` | ESLint over `src/` and `e2e/` |
| `npm run test` | Vitest unit tests (jsdom) |
| `npm run test:watch` | Vitest watch mode |
| `npm run e2e` | Playwright suite — needs backend, frontend dev server, and `infra/` Docker stack running |
| `npm run e2e:headed` | Same, with visible browser |

---

## Conventions

- **Auth state** flows through `<AuthProvider>` and `useAuth()`. Components that conditionally render based on auth must handle three states: `loading`, `user`, `!user`. Skipping `loading` causes flash-of-incorrect-content (see decision D-65).
- **API errors** throw `ApiError` from `api/apiClient.ts`. Surface them via `useToast()` or a top-of-page `bg-red-50 rounded-md` banner — never inline `alert()`.
- **Forms** use the unified blue-600 button + slate-300 input + `rounded-md` style. Inputs in auth/listing flows expose `name="..."` so Playwright selectors and password managers can target them.
- **Route protection** wraps routes with `<ProtectedRoute>` which preserves `?next=...` for post-login redirect.
- **Optimistic vs. authoritative** — chat send is optimistic (push then reconcile via polling); listing status changes are authoritative (await server, then refresh).

---

## Tooling notes

- TS strict mode is on; type-only imports use `import type { ... }` (Vite + Vitest both enforce verbatimModuleSyntax).
- Vitest config lives inside `vite.config.ts` under the `test` key (uses `vitest/config` reference type).
- Tailwind v4 — no `tailwind.config.js`, classes are scanned automatically via the Vite plugin.
- Playwright `test-results/`, `playwright-report/`, `blob-report/`, and `playwright/.cache/` are gitignored.
