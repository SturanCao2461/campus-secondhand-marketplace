# UI Beautification — M6 Phase D Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the frontend's visual identity from the current "blue-600 + slate-utility admin" palette to the approved "Warm Marketplace" identity (Peach + Plum palette, Plus Jakarta Sans, generous radii, soft shadows, radial-gradient hero glows) across `index.css`, 5 shared components, and 15 pages — without changing routing, information architecture, or any test selectors.

**Architecture:** Route-1 token-first strategy in 3 stages. Stage 1 introduces design tokens in `index.css` (Tailwind v4 `@theme` block) + migrates the 5 shared components so all pages instantly inherit on-brand buttons/spinners/toasts. Stage 2 rebuilds the 4 high-impact pages (Home / Browse / PublicDetail / ListingDetail) with new typography, hero glows, and chip system. Stage 3 sweeps remaining 11 pages with a mechanical className find/replace.

**Tech Stack:** React 19, Tailwind v4 (`@tailwindcss/vite`), TypeScript 5.9, Vite 8, Vitest, Playwright. Plus Jakarta Sans loaded via Google Fonts `@import`. No new runtime dependencies.

**Spec:** `docs/superpowers/specs/2026-05-26-milestone-6-phase-d-ui-beautification-design.md`

---

## File structure

| File | Action | Responsibility |
|---|---|---|
| `frontend/src/index.css` | Modify | Token source of truth (Tailwind v4 `@theme` block + decoration utilities + font import) |
| `frontend/src/components/Spinner.tsx` | Modify | Coral spinner colour |
| `frontend/src/components/Navbar.tsx` | Modify | Plum text / coral hover / pill Sign-up button |
| `frontend/src/components/Toast.tsx` | Modify | Sage success / coral error / pill shape |
| `frontend/src/components/ErrorBoundary.tsx` | Modify | New error tokens + plum button |
| `frontend/src/components/PasswordInput.tsx` | Modify | Coral focus border |
| `frontend/src/pages/HomePage.tsx` | Rebuild | Hero with glow + eyebrow + 44px headline + dual CTA |
| `frontend/src/pages/BrowsePage.tsx` | Rebuild | Pill filters + new card system + peach skeleton |
| `frontend/src/pages/PublicDetailPage.tsx` | Rebuild | Large coral price + status chips + plum CTA |
| `frontend/src/pages/ListingDetailPage.tsx` | Rebuild | Status chips + restyled action button group |
| `frontend/src/pages/MyListingsPage.tsx` | Migrate | Status badges use sage/mustard/plum/muted |
| `frontend/src/pages/CreateListingPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/EditListingPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/LoginPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/RegisterPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/ForgotPasswordPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/ResetPasswordPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/VerifyEmailPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/MePage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/ConversationsPage.tsx` | Migrate | className swap to new tokens |
| `frontend/src/pages/ChatPage.tsx` | Migrate | Plum sender bubble + coral link + new composer styling |
| `docs/dev-roadmap.md` | Modify | Add Phase D entry under M6 |
| `docs/engineering-journal.md` | Modify | Append D-78 entry |

No new files. No deletions.

---

## Pre-flight findings (already audited, kept here as reference)

- Zero `data-testid` selectors in the codebase — all E2E + unit tests use semantic selectors (`getByRole`, `getByText`).
- Zero color-class assertions in E2E specs or unit tests — `grep -rn "bg-blue\|text-blue\|bg-slate" frontend/e2e/ frontend/src/**/*.test.tsx` returns empty.
- Conclusion: className changes will not break tests. No test updates required.

---

## Stage 1 — Token foundation

### Task 1: Add design tokens to `index.css`

**Files:**
- Modify: `frontend/src/index.css`

- [ ] **Step 1: Replace `frontend/src/index.css` entirely with the new token source**

```css
@import 'tailwindcss';
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

@theme {
  /* Surfaces */
  --color-surface: #FFF4ED;
  --color-card: #FFFFFF;

  /* Brand */
  --color-plum: #3D405B;
  --color-ink: #2A2C40;
  --color-coral: #E07A5F;
  --color-sage: #81B29A;
  --color-mustard: #F2CC8F;

  /* Text */
  --color-muted: #8B7B8E;

  /* Semantic */
  --color-error: #C44536;

  /* Borders */
  --color-border-soft: rgba(61, 64, 91, 0.1);

  /* Font family */
  --font-sans: 'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif;

  /* Radii */
  --radius-card: 14px;
  --radius-panel: 18px;

  /* Shadows (plum-tinted, not neutral black) */
  --shadow-card: 0 4px 14px rgba(61, 64, 91, 0.08);
  --shadow-panel: 0 8px 24px rgba(61, 64, 91, 0.10);
  --shadow-button: 0 4px 14px rgba(61, 64, 91, 0.2);
}

html,
body,
#root {
  height: 100%;
  margin: 0;
}

body {
  background-color: var(--color-surface);
  color: var(--color-ink);
  font-family: var(--font-sans);
  font-feature-settings: 'cv11', 'ss01';
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Decoration: radial-gradient glows for hero containers */
.glow-coral::before {
  content: '';
  position: absolute;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(224, 122, 95, 0.4) 0%, transparent 65%);
  top: -80px;
  right: -60px;
  pointer-events: none;
  z-index: 0;
}

.glow-sage::after {
  content: '';
  position: absolute;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(129, 178, 154, 0.35) 0%, transparent 65%);
  bottom: -60px;
  left: -40px;
  pointer-events: none;
  z-index: 0;
}
```

- [ ] **Step 2: Sanity-test that Tailwind v4 picked up the tokens**

Run (from `frontend/`):
```bash
npm run dev
```

Expected: dev server starts on port 5173 with no Tailwind/Vite error. Open http://localhost:5173/ — body background should now be peach `#FFF4ED` instead of `slate-50`. Text colour should be ink near-black `#2A2C40`.

If you see Tailwind v4 complaining about `@theme` keys, double-check the token names use snake-case dashes (`--color-coral`, not `--color-Coral`). Stop the dev server with Ctrl+C before proceeding.

- [ ] **Step 3: Commit**

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/src/index.css
git commit -m "$(cat <<'EOF'
feat(ui): introduce Peach+Plum design tokens in index.css

Tailwind v4 @theme block defines the Warm Marketplace palette
(surface peach, plum, coral, sage, mustard), Plus Jakarta Sans,
plum-tinted shadows, and two glow utilities for hero decoration.

Spec: docs/superpowers/specs/2026-05-26-milestone-6-phase-d-ui-beautification-design.md

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Migrate Spinner (single-component sanity check)

**Files:**
- Modify: `frontend/src/components/Spinner.tsx`

- [ ] **Step 1: Replace Spinner.tsx with the new version**

```tsx
export function Spinner({ label = 'Loading...' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-muted">
      <svg
        className="animate-spin h-8 w-8 text-coral mb-3"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
      >
        <circle
          className="opacity-25"
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="4"
        />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
      </svg>
      <p className="text-sm">{label}</p>
    </div>
  )
}
```

- [ ] **Step 2: Verify Spinner renders coral**

Run (from `frontend/`):
```bash
npm run dev
```

Visit http://localhost:5173/browse (loading flicker, may need to throttle). Spinner SVG should appear coral `#E07A5F`. If it still appears blue, the `@theme` migration in Task 1 did not register `text-coral` — investigate Tailwind v4 token mapping before continuing.

Stop the dev server with Ctrl+C.

- [ ] **Step 3: Run unit tests to confirm no regression**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass (unchanged from baseline).

- [ ] **Step 4: Commit**

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/src/components/Spinner.tsx
git commit -m "feat(ui): migrate Spinner to coral token

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 3: Migrate Navbar

**Files:**
- Modify: `frontend/src/components/Navbar.tsx`

- [ ] **Step 1: Replace Navbar.tsx with the new version**

```tsx
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useUnreadCount } from '../hooks/useUnreadCount'

export function Navbar() {
  const { user, logout, loading } = useAuth()
  const nav = useNavigate()
  const unread = useUnreadCount(!!user)

  const onLogout = async () => {
    await logout()
    nav('/')
  }

  return (
    <header className="border-b border-border-soft bg-card/80 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-bold text-plum tracking-tight">
          Campus Marketplace
        </Link>
        <nav className="flex items-center gap-5 text-sm font-medium">
          <Link to="/browse" className="text-plum hover:text-coral transition-colors">
            Browse
          </Link>
          {!loading && user && (
            <>
              <Link
                to="/conversations"
                className="relative text-plum hover:text-coral transition-colors"
              >
                Messages
                {unread > 0 && (
                  <span className="absolute -top-1.5 -right-3 min-w-[18px] h-[18px] rounded-full bg-coral text-card text-[10px] font-semibold flex items-center justify-center px-1">
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </Link>
              <Link to="/listings/mine" className="text-plum hover:text-coral transition-colors">
                My Listings
              </Link>
              <span className="text-muted">Hi, {user.nickname}</span>
              <button
                onClick={onLogout}
                className="rounded-full border border-border-soft px-4 py-1.5 text-plum hover:bg-surface transition-colors"
              >
                Log out
              </button>
            </>
          )}
          {!loading && !user && (
            <>
              <Link to="/login" className="text-plum hover:text-coral transition-colors">
                Log in
              </Link>
              <Link
                to="/register"
                className="rounded-full bg-plum px-4 py-1.5 text-surface font-semibold hover:bg-ink transition-colors shadow-button"
              >
                Sign up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
```

- [ ] **Step 2: Run unit tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/Navbar.tsx
git commit -m "feat(ui): migrate Navbar to plum + coral tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 4: Migrate Toast

**Files:**
- Modify: `frontend/src/components/Toast.tsx`

- [ ] **Step 1: Replace Toast.tsx with the new version**

```tsx
import { useState, useCallback, type ReactNode } from 'react'
import { ToastContext } from './useToast'

interface Toast {
  id: number
  message: string
  type: 'success' | 'error'
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  let nextId = 0

  const add = useCallback((message: string, type: 'success' | 'error') => {
    const id = ++nextId
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 3000)
  }, [])

  const success = useCallback((msg: string) => add(msg, 'success'), [add])
  const error = useCallback((msg: string) => add(msg, 'error'), [add])

  return (
    <ToastContext.Provider value={{ success, error }}>
      {children}
      <div className="fixed top-4 right-4 z-50 space-y-2" aria-live="polite">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`px-4 py-2 rounded-full shadow-panel text-sm font-medium text-card transition-opacity ${
              t.type === 'success' ? 'bg-sage' : 'bg-error'
            }`}
          >
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
```

- [ ] **Step 2: Run unit tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/Toast.tsx
git commit -m "feat(ui): migrate Toast to sage/error tokens with pill shape

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 5: Migrate ErrorBoundary

**Files:**
- Modify: `frontend/src/components/ErrorBoundary.tsx`

- [ ] **Step 1: Replace render() method's fallback JSX in ErrorBoundary.tsx**

Replace lines 33-44 (the default fallback block inside `render()`):

```tsx
      return (
        <div className="flex flex-col items-center justify-center min-h-[40vh] p-8 text-center">
          <h2 className="text-xl font-bold text-plum mb-2">Something went wrong</h2>
          <p className="text-sm text-muted mb-4">{this.state.error?.message}</p>
          <button
            onClick={() => this.setState({ hasError: false, error: undefined })}
            className="rounded-full bg-plum px-5 py-2 text-surface text-sm font-semibold hover:bg-ink transition-colors shadow-button"
          >
            Try again
          </button>
        </div>
      )
```

- [ ] **Step 2: Run unit tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass. `ErrorBoundary.test.tsx` verifies behaviour (render fallback on error), not specific classNames.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ErrorBoundary.tsx
git commit -m "feat(ui): migrate ErrorBoundary fallback to plum + muted tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 6: Migrate PasswordInput

**Files:**
- Modify: `frontend/src/components/PasswordInput.tsx`

- [ ] **Step 1: Replace PasswordInput.tsx with the new version**

```tsx
import { useState, type InputHTMLAttributes } from 'react'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

export function PasswordInput(props: Props) {
  const [show, setShow] = useState(false)
  return (
    <div className="relative mt-1">
      <input
        {...props}
        type={show ? 'text' : 'password'}
        className="block w-full rounded-card border border-border-soft bg-card px-3 py-2 pr-14 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20"
      />
      <button
        type="button"
        onClick={() => setShow(s => !s)}
        className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-semibold text-muted hover:text-plum transition-colors"
      >
        {show ? 'Hide' : 'Show'}
      </button>
    </div>
  )
}
```

- [ ] **Step 2: Run unit tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/PasswordInput.tsx
git commit -m "feat(ui): migrate PasswordInput to coral focus ring

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 7: Stage 1 verification

**Files:** none (verification only)

- [ ] **Step 1: Run full frontend test suite**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 vitest pass, lint 0 errors (existing 7 warnings unchanged).

- [ ] **Step 2: Start dev server and visually smoke-test**

```bash
cd frontend && npm run dev
```

Visit each of these and confirm visual consistency (Stage 1 = expect "blue admin → peach with on-brand Navbar/Spinner" — pages still have legacy styling for cards/text inside them; that's expected and fixed in Stages 2 & 3):
- http://localhost:5173/ — Navbar is plum text + coral hover, Sign-up is plum pill.
- http://localhost:5173/browse — page background peach, Spinner is coral.
- http://localhost:5173/login — page still has blue button (Stage 3 will fix).

Stop dev server with Ctrl+C.

- [ ] **Step 3: Run E2E to confirm no regression**

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace/infra && docker compose up -d
cd ../frontend && npm run e2e
```

Expected: 25/25 pass.

If the backend isn't running, also start it in a second terminal:
```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- [ ] **Step 4: Commit verification status (no code change)**

If the previous tasks already committed individual changes, no commit needed here. Otherwise stage any pending changes from sanity testing and commit:

```bash
git status
# if clean, skip; otherwise:
# git add -A && git commit -m "chore(ui): Stage 1 verification — Navbar/Spinner/Toast/ErrorBoundary/PasswordInput on-brand"
```

---

## Stage 2 — High-impact page rebuilds

### Task 8: Rebuild HomePage

**Files:**
- Modify: `frontend/src/pages/HomePage.tsx`

- [ ] **Step 1: Replace HomePage.tsx with the new version**

```tsx
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function HomePage() {
  const { user, loading } = useAuth()

  return (
    <main className="relative overflow-hidden">
      <section className="relative glow-coral glow-sage mx-auto max-w-5xl px-6 py-24 sm:py-32">
        <div className="relative z-10 max-w-2xl">
          <p className="text-eyebrow text-coral mb-4" style={{ letterSpacing: '0.15em' }}>
            <span className="text-[11px] font-bold uppercase tracking-[0.15em]">
              For Waikato Students
            </span>
          </p>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-plum tracking-tight leading-[1.05]">
            Find your
            <br />
            next thing.
          </h1>
          <p className="mt-5 text-base sm:text-lg text-muted font-medium max-w-lg">
            Buy and sell secondhand items with other University of Waikato students.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/browse"
              className="rounded-full bg-plum px-6 py-3 text-surface text-base font-bold hover:bg-ink transition-colors shadow-button"
            >
              Browse listings →
            </Link>
            {!loading &&
              (user ? (
                <Link
                  to="/listings/new"
                  className="rounded-full bg-card border border-mustard px-6 py-3 text-plum text-base font-semibold hover:bg-mustard/20 transition-colors"
                >
                  Sell an item
                </Link>
              ) : (
                <Link
                  to="/register"
                  className="rounded-full bg-card border border-mustard px-6 py-3 text-plum text-base font-semibold hover:bg-mustard/20 transition-colors"
                >
                  Sign up
                </Link>
              ))}
          </div>
        </div>
      </section>
    </main>
  )
}
```

- [ ] **Step 2: Run unit tests + lint**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 pass, lint clean.

- [ ] **Step 3: Visual smoke**

```bash
cd frontend && npm run dev
```

Visit http://localhost:5173/. Expect: eyebrow label "FOR WAIKATO STUDENTS" in coral, large plum headline "Find your / next thing.", coral glow blob top-right, sage glow blob bottom-left, plum pill primary CTA, white pill secondary CTA with mustard border. Stop dev server.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/HomePage.tsx
git commit -m "feat(ui): rebuild HomePage hero with glow + dual CTA

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 9: Rebuild BrowsePage

**Files:**
- Modify: `frontend/src/pages/BrowsePage.tsx`

- [ ] **Step 1: Replace BrowsePage.tsx with the new version**

```tsx
import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  listingsApi,
  type ListingSummary,
  type PagedListings,
  type ListingType,
} from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'

export function BrowsePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [data, setData] = useState<PagedListings | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const page = Number(searchParams.get('page') || '0')
  const keyword = searchParams.get('keyword') || ''
  const categoryCode = searchParams.get('category') || ''
  const sort = searchParams.get('sort') || 'CREATED_DESC'
  const listingType = (searchParams.get('type') || '') as ListingType | ''

  useEffect(() => {
    categoriesApi
      .list()
      .then(r => setCategories(r.items))
      .catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi
      .browse({
        page,
        keyword: keyword || undefined,
        categoryCode: categoryCode || undefined,
        listingType: listingType || undefined,
        sort: sort as 'CREATED_DESC',
      })
      .then(setData)
      .catch(() => setError('Failed to load listings.'))
      .finally(() => setLoading(false))
  }, [page, keyword, categoryCode, sort, listingType])

  function updateParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setSearchParams(next)
  }

  const inputBase =
    'rounded-full border border-border-soft bg-card px-4 py-2 text-sm font-medium shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20'

  return (
    <main className="max-w-6xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-plum tracking-tight mb-6">Browse Listings</h1>

      <div className="flex flex-wrap gap-3 mb-8">
        <input
          type="text"
          placeholder="Search MacBook, textbooks, lamp..."
          defaultValue={keyword}
          onKeyDown={e => {
            if (e.key === 'Enter') updateParam('keyword', (e.target as HTMLInputElement).value)
          }}
          className={`${inputBase} w-56`}
        />
        <select
          value={categoryCode}
          onChange={e => updateParam('category', e.target.value)}
          className={inputBase}
        >
          <option value="">All Categories</option>
          {categories.map(c => (
            <option key={c.code} value={c.code}>
              {c.nameEn}
            </option>
          ))}
        </select>
        <select
          value={listingType}
          onChange={e => updateParam('type', e.target.value)}
          className={inputBase}
        >
          <option value="">All Types</option>
          <option value="SELL">For Sale</option>
          <option value="GIVEAWAY">Free</option>
        </select>
        <select
          value={sort}
          onChange={e => updateParam('sort', e.target.value)}
          className={inputBase}
        >
          <option value="CREATED_DESC">Newest</option>
          <option value="CREATED_ASC">Oldest</option>
          <option value="PRICE_ASC">Price: Low to High</option>
          <option value="PRICE_DESC">Price: High to Low</option>
        </select>
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map(i => (
            <div
              key={i}
              className="bg-card rounded-card overflow-hidden shadow-card animate-pulse"
            >
              <div className="w-full h-40 bg-mustard/30" />
              <div className="p-4 space-y-2">
                <div className="h-4 bg-coral/20 rounded w-3/4" />
                <div className="h-3 bg-coral/10 rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-16 text-muted">
          <svg
            className="mx-auto h-16 w-16 text-coral/40 mb-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <p className="text-lg font-semibold text-plum">No listings found</p>
          <p className="text-sm mt-1">Try adjusting your search or filters</p>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link
                key={item.id}
                to={`/listings/${item.id}/detail`}
                className="bg-card rounded-card overflow-hidden shadow-card hover:-translate-y-0.5 hover:shadow-panel transition-all"
              >
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className="w-full h-40 object-cover bg-mustard/30"
                />
                <div className="p-4">
                  <h3 className="font-semibold text-sm text-plum truncate">{item.title}</h3>
                  <div className="flex justify-between items-baseline mt-1">
                    <p className="text-xl font-extrabold text-coral tracking-tight">
                      {item.listingType === 'GIVEAWAY'
                        ? 'Free'
                        : item.price != null
                          ? `$${item.price.toFixed(2)}`
                          : ''}
                    </p>
                  </div>
                  <p className="text-xs text-muted mt-1 font-medium">{item.category.nameEn}</p>
                </div>
              </Link>
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-8">
              <button
                onClick={() => {
                  const p = new URLSearchParams(searchParams)
                  p.set('page', String(page - 1))
                  setSearchParams(p)
                }}
                disabled={page === 0}
                className="rounded-full border border-border-soft bg-card px-4 py-1.5 text-sm font-medium text-plum hover:bg-surface disabled:opacity-30 transition-colors"
              >
                Previous
              </button>
              <span className="px-4 py-1.5 text-sm text-muted font-medium">
                Page {page + 1} of {data.totalPages}
              </span>
              <button
                onClick={() => {
                  const p = new URLSearchParams(searchParams)
                  p.set('page', String(page + 1))
                  setSearchParams(p)
                }}
                disabled={page >= data.totalPages - 1}
                className="rounded-full border border-border-soft bg-card px-4 py-1.5 text-sm font-medium text-plum hover:bg-surface disabled:opacity-30 transition-colors"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
```

- [ ] **Step 2: Run unit tests + lint**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 pass, lint clean.

- [ ] **Step 3: Visual smoke**

Start dev server, visit http://localhost:5173/browse. Confirm: page title plum + tracking-tight, pill-shaped inputs, cards have soft plum-tinted shadows, hover lifts card 2px, prices in large coral extrabold, empty state shows coral magnifier icon. Stop dev server.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/BrowsePage.tsx
git commit -m "feat(ui): rebuild BrowsePage with pill filters + new card system

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 10: Rebuild PublicDetailPage

**Files:**
- Modify: `frontend/src/pages/PublicDetailPage.tsx`

- [ ] **Step 1: Replace PublicDetailPage.tsx with the new version**

```tsx
import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { listingsApi, type Listing } from '../api/listings'
import { conversationsApi } from '../api/conversations'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { Spinner } from '../components/Spinner'

const STATUS_CHIP: Record<string, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

export function PublicDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const nav = useNavigate()
  const [listing, setListing] = useState<Listing | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [contacting, setContacting] = useState(false)

  useEffect(() => {
    if (!id) return
    listingsApi
      .getDetail(Number(id))
      .then(setListing)
      .catch(err => {
        if (err instanceof ApiError && err.code === 'LISTING_NOT_FOUND') {
          setError('This listing is no longer available.')
        } else {
          setError('Failed to load listing.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  if (error)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <p className="text-muted mb-4">{error}</p>
        <Link to="/browse" className="text-coral font-semibold hover:underline">
          &larr; Back to Browse
        </Link>
      </main>
    )
  if (!listing) return null

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      <img
        src={listing.imageUrl}
        alt={listing.title}
        className="w-full h-72 object-cover rounded-panel bg-mustard/30 mb-6 shadow-card"
      />

      <div className="flex justify-between items-start mb-4 gap-4">
        <h1 className="text-3xl font-bold text-plum tracking-tight leading-tight">
          {listing.title}
        </h1>
        <span className="text-2xl font-extrabold text-coral tracking-tight whitespace-nowrap">
          {listing.listingType === 'GIVEAWAY'
            ? 'Free'
            : listing.price != null
              ? `$${listing.price.toFixed(2)}`
              : ''}
        </span>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        <span
          className={`text-xs font-bold uppercase tracking-wide px-3 py-1 rounded-full ${STATUS_CHIP[listing.status] ?? 'bg-plum/10 text-plum'}`}
        >
          {listing.status}
        </span>
        <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
          {listing.category.nameEn}
        </span>
        {listing.condition && (
          <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
            {listing.condition.replace('_', ' ')}
          </span>
        )}
        {listing.negotiable && (
          <span className="text-xs font-bold px-3 py-1 rounded-full bg-sage/20 text-sage">
            Negotiable
          </span>
        )}
      </div>

      <p className="text-plum mb-4 whitespace-pre-wrap leading-relaxed">{listing.description}</p>

      {listing.originalPrice != null && (
        <p className="text-sm text-muted mb-2">
          Original price: ${listing.originalPrice.toFixed(2)}
        </p>
      )}
      {listing.meetAt && <p className="text-sm text-muted mb-2">Meet at: {listing.meetAt}</p>}
      {listing.reasonForSelling && (
        <p className="text-sm text-muted mb-4">Reason: {listing.reasonForSelling}</p>
      )}

      <div className="border-t border-border-soft pt-4 mt-4">
        <p className="text-sm text-muted">
          Listed on {new Date(listing.createdAt).toLocaleDateString()}
        </p>
      </div>

      {user && user.id !== listing.ownerId && (
        <button
          onClick={async () => {
            setContacting(true)
            try {
              const conv = await conversationsApi.create(listing.id)
              nav(`/conversations/${conv.id}`)
            } catch {
              setContacting(false)
            }
          }}
          disabled={contacting}
          className="mt-6 w-full rounded-full bg-plum px-6 py-3 text-surface text-base font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {contacting ? 'Opening...' : 'Contact seller'}
        </button>
      )}

      <Link to="/browse" className="text-coral font-semibold hover:underline text-sm mt-6 inline-block">
        &larr; Back to Browse
      </Link>
    </main>
  )
}
```

- [ ] **Step 2: Run unit tests + lint**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 pass, lint clean.

- [ ] **Step 3: Visual smoke**

Visit a real listing detail at http://localhost:5173/listings/194/detail (MacBook Pro 14, the surviving listing). Confirm: large title plum, price coral extrabold, status chip sage (AVAILABLE), category/condition pills white-on-soft-border, Contact-seller plum pill CTA. Stop dev server.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/PublicDetailPage.tsx
git commit -m "feat(ui): rebuild PublicDetailPage with chip system + plum CTA

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 11: Rebuild ListingDetailPage

**Files:**
- Modify: `frontend/src/pages/ListingDetailPage.tsx`

- [ ] **Step 1: Replace ListingDetailPage.tsx with the new version**

```tsx
import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { listingsApi, type Listing, type ListingStatus } from '../api/listings'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/useToast'
import { Spinner } from '../components/Spinner'

const STATUS_CHIP: Record<ListingStatus, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

export function ListingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const toast = useToast()
  const [listing, setListing] = useState<Listing | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    if (!id) return
    listingsApi
      .getOne(Number(id))
      .then(setListing)
      .catch(err => {
        if (err instanceof ApiError && err.code === 'LISTING_NOT_FOUND') {
          navigate('/listings/mine')
        } else {
          setError('Failed to load listing.')
        }
      })
      .finally(() => setLoading(false))
  }, [id, navigate])

  async function handleStatusChange(newStatus: ListingStatus) {
    if (!listing) return
    setActionError('')
    try {
      const updated = await listingsApi.changeStatus(listing.id, newStatus)
      setListing(updated)
      toast.success(`Status changed to ${newStatus}`)
    } catch (err) {
      if (err instanceof ApiError) setActionError(err.message)
    }
  }

  async function handleDelete() {
    if (!listing || !confirm('Are you sure you want to remove this listing?')) return
    try {
      await listingsApi.remove(listing.id)
      toast.success('Listing removed')
      navigate('/listings/mine')
    } catch (err) {
      if (err instanceof ApiError) setActionError(err.message)
    }
  }

  if (loading)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  if (error)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
          {error}
        </div>
      </main>
    )
  if (!listing) return null

  const isRemoved = listing.status === 'REMOVED'
  const actionBtn = 'rounded-full px-4 py-1.5 text-sm font-semibold transition-colors'

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      {isRemoved && (
        <div className="bg-error/10 border border-error/30 text-error px-4 py-2.5 rounded-card mb-4 text-sm font-medium">
          This listing has been removed.
        </div>
      )}

      <img
        src={listing.imageUrl}
        alt={listing.title}
        className="w-full h-72 object-cover rounded-panel bg-mustard/30 mb-6 shadow-card"
      />

      <div className="flex justify-between items-start mb-4 gap-4">
        <h1 className="text-3xl font-bold text-plum tracking-tight leading-tight">
          {listing.title}
        </h1>
        <span className="text-2xl font-extrabold text-coral tracking-tight whitespace-nowrap">
          {listing.listingType === 'GIVEAWAY'
            ? 'Free'
            : listing.price != null
              ? `$${listing.price.toFixed(2)}`
              : ''}
        </span>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        <span
          className={`text-xs font-bold uppercase tracking-wide px-3 py-1 rounded-full ${STATUS_CHIP[listing.status]}`}
        >
          {listing.status}
        </span>
        <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
          {listing.category.nameEn}
        </span>
        {listing.condition && (
          <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
            {listing.condition.replace('_', ' ')}
          </span>
        )}
        {listing.negotiable && (
          <span className="text-xs font-bold px-3 py-1 rounded-full bg-sage/20 text-sage">
            Negotiable
          </span>
        )}
      </div>

      <p className="text-plum mb-4 whitespace-pre-wrap leading-relaxed">{listing.description}</p>

      {listing.originalPrice != null && (
        <p className="text-sm text-muted mb-2">
          Original price: ${listing.originalPrice.toFixed(2)}
        </p>
      )}
      {listing.meetAt && <p className="text-sm text-muted mb-2">Meet at: {listing.meetAt}</p>}
      {listing.reasonForSelling && (
        <p className="text-sm text-muted mb-4">Reason: {listing.reasonForSelling}</p>
      )}

      {actionError && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {actionError}
        </div>
      )}

      {!isRemoved && (
        <div className="flex flex-wrap gap-2 mb-6">
          {listing.status === 'AVAILABLE' && (
            <>
              <button
                onClick={() => handleStatusChange('RESERVED')}
                className={`${actionBtn} bg-mustard text-plum hover:bg-mustard/80`}
              >
                Mark Reserved
              </button>
              <button
                onClick={() => handleStatusChange('SOLD')}
                className={`${actionBtn} bg-plum text-surface hover:bg-ink`}
              >
                Mark Sold
              </button>
            </>
          )}
          {listing.status === 'RESERVED' && (
            <>
              <button
                onClick={() => handleStatusChange('AVAILABLE')}
                className={`${actionBtn} bg-sage text-card hover:bg-sage/80`}
              >
                Back to Available
              </button>
              <button
                onClick={() => handleStatusChange('SOLD')}
                className={`${actionBtn} bg-plum text-surface hover:bg-ink`}
              >
                Mark Sold
              </button>
            </>
          )}
          {listing.status === 'SOLD' && (
            <button
              onClick={() => handleStatusChange('AVAILABLE')}
              className={`${actionBtn} bg-sage text-card hover:bg-sage/80`}
            >
              Relist
            </button>
          )}
          <Link
            to={`/listings/${listing.id}/edit`}
            className={`${actionBtn} bg-card border border-border-soft text-plum hover:bg-surface`}
          >
            Edit
          </Link>
          <button
            onClick={handleDelete}
            className={`${actionBtn} bg-error text-card hover:bg-error/80`}
          >
            Delete
          </button>
        </div>
      )}

      <Link to="/listings/mine" className="text-coral font-semibold hover:underline text-sm">
        &larr; Back to My Listings
      </Link>
    </main>
  )
}
```

- [ ] **Step 2: Run unit tests + lint**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 pass, lint clean.

- [ ] **Step 3: Visual smoke**

Log in, visit your own listing detail page (e.g., http://localhost:5173/listings/2 — listing `iji` owned by your seed user). Confirm: same chip system as PublicDetailPage, action buttons are pills with status-mapped colours (Reserved = mustard, Sold = plum, etc.).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/ListingDetailPage.tsx
git commit -m "feat(ui): rebuild ListingDetailPage with chip system + pill actions

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 12: Stage 2 verification

**Files:** none (verification only)

- [ ] **Step 1: Run full frontend test suite**

```bash
cd frontend && npm run test && npm run lint
```

Expected: 46/46 vitest pass, lint clean.

- [ ] **Step 2: Run E2E**

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace/infra && docker compose up -d
cd ../frontend && npm run e2e
```

Expected: 25/25 pass. Pay particular attention to `browse-search-flow.spec.ts` and `status-flow.spec.ts` which exercise Stage 2 pages most heavily.

If any spec fails because text content shifted (e.g., button label changed), update the spec to match. **Do not** revert visual changes to make a spec pass — the spec was outdated.

- [ ] **Step 3: Manual mobile smoke (browser DevTools, 375px viewport)**

Open Chrome DevTools, toggle device toolbar to iPhone SE (375px). Visit Home, Browse, and a detail page. Confirm content is readable and CTAs are tappable. Expect some overflow that we explicitly deferred to a future mobile-rework phase — note any obviously broken layout for the engineering journal but do not fix here.

---

## Stage 3 — Token migration for remaining 11 pages

> **Strategy:** Each remaining page gets the same className mapping applied mechanically. The mapping table below covers every legacy class → new class. Apply the table to each file, then verify lint + tests after each.

### Token migration mapping (apply to every Stage-3 file)

| Legacy className | New className |
|---|---|
| `bg-blue-600` | `bg-plum` |
| `hover:bg-blue-700` | `hover:bg-ink` |
| `bg-blue-500` | `bg-plum` |
| `hover:bg-blue-100` | `hover:bg-plum/10` |
| `text-blue-600` | `text-coral` |
| `text-blue-700` | `text-coral` |
| `text-blue-200` | `text-surface/70` |
| `bg-blue-100` | `bg-plum/10` |
| `text-blue-800` | `text-plum` |
| `focus:ring-blue-500` | `focus:ring-coral/30` |
| `focus:border-blue-600` | `focus:border-coral` |
| `text-white` (when atop plum/coral/sage bg) | `text-surface` (when atop plum) / `text-card` (when atop coral, sage, error) |
| `text-slate-900` / `text-slate-800` | `text-plum` |
| `text-slate-700` / `text-slate-600` | `text-plum` (body) or `text-muted` (helper) |
| `text-slate-500` | `text-muted` |
| `border-slate-300` | `border-border-soft` |
| `bg-slate-50` | `bg-surface` |
| `bg-slate-100` | `bg-plum/5` |
| `hover:bg-slate-50` | `hover:bg-surface` |
| `bg-red-50` | `bg-error/10` |
| `text-red-700` | `text-error` |
| `border-red-200` | `border-error/30` |
| `bg-red-500` | `bg-error` |
| `bg-green-100` | `bg-sage/20` |
| `text-green-800` | `text-sage` |
| `text-green-700` | `text-sage` |
| `bg-green-500` | `bg-sage` |
| `bg-green-600` | `bg-sage` |
| `bg-yellow-500` | `bg-mustard` |
| `bg-yellow-100` | `bg-mustard/30` |
| `text-yellow-800` | `text-plum` |
| `bg-gray-100` | `bg-card border border-border-soft` (when chip) / `bg-plum/5` (when surface) |
| `bg-gray-200` | `bg-mustard/30` (skeleton image) / `bg-coral/20` (skeleton text) |
| `text-gray-700` | `text-plum` |
| `text-gray-600` | `text-muted` |
| `text-gray-500` | `text-muted` |
| `text-gray-400` | `text-muted` |
| `text-gray-300` | `text-coral/40` |
| `text-gray-100` | `text-card` |
| `text-gray-900` | `text-plum` |
| `rounded` | `rounded-card` |
| `rounded-md` | `rounded-card` |
| `rounded-lg` | `rounded-card` (cards) or `rounded-panel` (modals) or `rounded-full` (buttons — case by case) |

Buttons that look like primary actions (`bg-blue-600 text-white px-4 py-2 rounded`) become pills: `rounded-full bg-plum px-5 py-2 text-surface font-semibold hover:bg-ink shadow-button transition-colors`.

---

### Task 13: Migrate MyListingsPage

**Files:**
- Modify: `frontend/src/pages/MyListingsPage.tsx`

- [ ] **Step 1: Replace MyListingsPage.tsx with the new version**

```tsx
import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import {
  listingsApi,
  type ListingSummary,
  type ListingStatus,
  type PagedListings,
} from '../api/listings'

const STATUS_CHIP: Record<ListingStatus, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

export function MyListingsPage() {
  const [data, setData] = useState<PagedListings | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<ListingStatus | 'ALL'>('ALL')

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi
      .listMine({ page, status: statusFilter, sort: 'CREATED_DESC' })
      .then(setData)
      .catch(() => setError('Failed to load listings.'))
      .finally(() => setLoading(false))
  }, [page, statusFilter])

  function statusBadge(s: ListingStatus) {
    return (
      <span className={`text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-full ${STATUS_CHIP[s]}`}>
        {s}
      </span>
    )
  }

  return (
    <main className="max-w-4xl mx-auto px-6 py-10">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold text-plum tracking-tight">My Listings</h1>
        <Link
          to="/listings/new"
          className="rounded-full bg-plum text-surface px-5 py-2 font-semibold hover:bg-ink transition-colors shadow-button"
        >
          + New Listing
        </Link>
      </div>

      <div className="flex gap-2 mb-6 flex-wrap">
        {(['ALL', 'AVAILABLE', 'RESERVED', 'SOLD', 'REMOVED'] as const).map(s => (
          <button
            key={s}
            onClick={() => {
              setStatusFilter(s)
              setPage(0)
            }}
            className={`px-4 py-1.5 rounded-full text-sm font-semibold transition-colors ${
              statusFilter === s
                ? 'bg-plum text-surface'
                : 'bg-card border border-border-soft text-plum hover:bg-surface'
            }`}
          >
            {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="bg-card rounded-card overflow-hidden shadow-card animate-pulse">
              <div className="w-full h-40 bg-mustard/30" />
              <div className="p-4 space-y-2">
                <div className="h-4 bg-coral/20 rounded w-3/4" />
                <div className="h-3 bg-coral/10 rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-16 text-muted">
          <svg
            className="mx-auto h-16 w-16 text-coral/40 mb-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"
            />
          </svg>
          <p className="text-lg font-semibold text-plum mb-2">No listings yet</p>
          <p className="text-sm mb-4">Start selling by creating your first listing</p>
          <Link
            to="/listings/new"
            className="inline-block rounded-full bg-plum text-surface px-5 py-2 font-semibold hover:bg-ink transition-colors shadow-button"
          >
            + Create Listing
          </Link>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link
                key={item.id}
                to={`/listings/${item.id}`}
                className="bg-card rounded-card overflow-hidden shadow-card hover:-translate-y-0.5 hover:shadow-panel transition-all"
              >
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className="w-full h-40 object-cover bg-mustard/30"
                />
                <div className="p-4">
                  <div className="flex justify-between items-start mb-1 gap-2">
                    <h3 className="font-semibold text-sm text-plum truncate">{item.title}</h3>
                    {statusBadge(item.status)}
                  </div>
                  <p className="text-lg font-extrabold text-coral tracking-tight">
                    {item.listingType === 'GIVEAWAY'
                      ? 'Free'
                      : item.price != null
                        ? `$${item.price.toFixed(2)}`
                        : ''}
                  </p>
                  <p className="text-xs text-muted mt-1 font-medium">{item.category.nameEn}</p>
                </div>
              </Link>
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-8">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded-full border border-border-soft bg-card px-4 py-1.5 text-sm font-medium text-plum hover:bg-surface disabled:opacity-30 transition-colors"
              >
                Previous
              </button>
              <span className="px-4 py-1.5 text-sm text-muted font-medium">
                Page {page + 1} of {data.totalPages}
              </span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page >= data.totalPages - 1}
                className="rounded-full border border-border-soft bg-card px-4 py-1.5 text-sm font-medium text-plum hover:bg-surface disabled:opacity-30 transition-colors"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
```

- [ ] **Step 2: Run tests + commit**

```bash
cd frontend && npm run test && npm run lint
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/src/pages/MyListingsPage.tsx
git commit -m "feat(ui): migrate MyListingsPage to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 14: Migrate LoginPage

**Files:**
- Modify: `frontend/src/pages/LoginPage.tsx`

- [ ] **Step 1: Replace LoginPage.tsx with the new version**

```tsx
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { PasswordInput } from '../components/PasswordInput'

export function LoginPage() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      const next = params.get('next') ?? '/'
      if (!next.startsWith('/')) {
        nav('/')
      } else {
        nav(next)
      }
    } catch (e) {
      if (e instanceof ApiError) setError(e.message)
      else setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <h1 className="text-3xl font-bold text-plum tracking-tight">Welcome back</h1>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        <label className="block text-sm">
          <span className="font-semibold text-plum">Email</span>
          <input
            type="email"
            name="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20"
            autoComplete="email"
          />
        </label>

        <label className="block text-sm">
          <span className="font-semibold text-plum">Password</span>
          <PasswordInput
            name="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>

        {error && (
          <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-full bg-plum py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {submitting ? 'Logging in...' : 'Log in'}
        </button>

        <div className="flex items-center justify-between text-sm">
          <Link to="/forgot-password" className="text-muted hover:text-coral transition-colors">
            Forgot password?
          </Link>
          <Link to="/register" className="font-semibold text-coral hover:underline">
            Create an account
          </Link>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run test && npm run lint
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/src/pages/LoginPage.tsx
git commit -m "feat(ui): migrate LoginPage to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 15: Migrate RegisterPage

**Files:**
- Modify: `frontend/src/pages/RegisterPage.tsx`

- [ ] **Step 1: Read current file, apply mapping table from "Token migration mapping" section**

Read `frontend/src/pages/RegisterPage.tsx`. For each occurrence in the file, apply the mapping table. Pattern: the page is structured identically to LoginPage (title + form + submit) so apply the same transformations:
- Page wrapper: `mx-auto max-w-md px-6 py-12`
- H1: `text-3xl font-bold text-plum tracking-tight`
- Form spacing: `mt-8 space-y-5`
- Each `<input>`: `mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20`
- Each label span: `font-semibold text-plum`
- Error div: `rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error`
- Submit button: `w-full rounded-full bg-plum py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button`
- Footer links: muted/coral split as in LoginPage

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run test && npm run lint
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/src/pages/RegisterPage.tsx
git commit -m "feat(ui): migrate RegisterPage to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 16: Migrate ForgotPasswordPage + ResetPasswordPage + VerifyEmailPage

**Files:**
- Modify: `frontend/src/pages/ForgotPasswordPage.tsx`
- Modify: `frontend/src/pages/ResetPasswordPage.tsx`
- Modify: `frontend/src/pages/VerifyEmailPage.tsx`

- [ ] **Step 1: Apply LoginPage-derived patterns to all three files**

These three pages share the same form/CTA structure as Login & Register. Apply the same className rules:
- Page wrapper: `mx-auto max-w-md px-6 py-12`
- H1: `text-3xl font-bold text-plum tracking-tight`
- Input: as in Task 15
- Submit button: as in Task 15
- Success/info notice (if any): `rounded-card bg-sage/10 border border-sage/30 p-3 text-sm text-sage`
- Error notice: `rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error`
- Body text: `text-muted` for helper, `text-plum` for emphasized
- Secondary links: `text-coral hover:underline font-semibold`

- [ ] **Step 2: Run tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 (includes `VerifyEmailPage.test.tsx` × 4). If a test asserts specific text that you've not changed, it should still pass. If a test asserts a className (it shouldn't, per pre-flight), update the assertion.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ForgotPasswordPage.tsx frontend/src/pages/ResetPasswordPage.tsx frontend/src/pages/VerifyEmailPage.tsx
git commit -m "feat(ui): migrate password/verify auth pages to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 17: Migrate CreateListingPage + EditListingPage

**Files:**
- Modify: `frontend/src/pages/CreateListingPage.tsx`
- Modify: `frontend/src/pages/EditListingPage.tsx`

- [ ] **Step 1: Apply mapping table to both forms**

Both pages are forms with multiple inputs (title, price, category, condition, image upload, etc.). Apply uniformly:
- Page wrapper: `max-w-2xl mx-auto px-6 py-10`
- H1: `text-3xl font-bold text-plum tracking-tight mb-6`
- All `<input>`, `<select>`, `<textarea>`: `mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20`
- Label span: `font-semibold text-plum`
- Helper text: `text-xs text-muted mt-1`
- Submit button: `rounded-full bg-plum px-6 py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button`
- Cancel/secondary link: `text-coral hover:underline font-semibold text-sm`
- Error: `rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error`
- Image upload preview container: `bg-mustard/30 rounded-card`

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run test && npm run lint
git add frontend/src/pages/CreateListingPage.tsx frontend/src/pages/EditListingPage.tsx
git commit -m "feat(ui): migrate Create/Edit listing pages to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 18: Migrate ConversationsPage + MePage

**Files:**
- Modify: `frontend/src/pages/ConversationsPage.tsx`
- Modify: `frontend/src/pages/MePage.tsx`

- [ ] **Step 1: Apply mapping table to both**

For `ConversationsPage`:
- List items: `bg-card rounded-card shadow-card hover:shadow-panel transition-all px-4 py-3`
- Unread indicator: `bg-coral text-card rounded-full`
- Listing thumb: `bg-mustard/30 rounded-card` placeholder
- Empty state: same pattern as BrowsePage empty state
- Header: `text-3xl font-bold text-plum tracking-tight`

For `MePage`:
- Profile card: `bg-card rounded-panel shadow-card p-6`
- Field labels: `text-xs font-bold uppercase tracking-wide text-muted`
- Field values: `text-base font-semibold text-plum`
- Edit / verify-email CTA: `rounded-full bg-plum px-4 py-1.5 text-surface font-semibold text-sm hover:bg-ink transition-colors`
- Verified email chip: `bg-sage/20 text-sage rounded-full px-3 py-0.5 text-xs font-bold uppercase`
- Unverified chip: `bg-mustard/30 text-plum rounded-full px-3 py-0.5 text-xs font-bold uppercase`

- [ ] **Step 2: Run tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 (includes `MePage.test.tsx` × 5).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/ConversationsPage.tsx frontend/src/pages/MePage.tsx
git commit -m "feat(ui): migrate Conversations + Me pages to Peach+Plum tokens

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 19: Migrate ChatPage (special — has sender bubbles)

**Files:**
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: Replace ChatPage.tsx with the new version**

```tsx
import { useState, useEffect, useRef } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { conversationsApi, type ConversationDetail } from '../api/conversations'
import { useAuth } from '../auth/useAuth'
import { useChatPolling } from '../hooks/useChatPolling'
import { useBrowserNotification } from '../hooks/useBrowserNotification'
import { Spinner } from '../components/Spinner'

export function ChatPage() {
  const { id } = useParams<{ id: string }>()
  const convId = id ? Number(id) : null
  const { user } = useAuth()
  const nav = useNavigate()
  const [conv, setConv] = useState<ConversationDetail | null>(null)
  const [loadingConv, setLoadingConv] = useState(true)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { messages, loading: loadingMsgs, addOptimistic } = useChatPolling(convId)
  const { notify } = useBrowserNotification()
  const prevCountRef = useRef(0)

  useEffect(() => {
    if (!convId) return
    conversationsApi
      .getOne(convId)
      .then(setConv)
      .catch(() => nav('/conversations'))
      .finally(() => setLoadingConv(false))
  }, [convId, nav])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  useEffect(() => {
    if (messages.length > prevCountRef.current && prevCountRef.current > 0) {
      const latest = messages[messages.length - 1]
      if (latest.senderId !== user?.id && conv) {
        notify(conv.counterpartNickname, latest.content, () => nav(`/conversations/${convId}`))
      }
    }
    prevCountRef.current = messages.length
  }, [messages.length, user?.id, conv, notify, nav, convId])

  const handleSend = async () => {
    if (!convId || !input.trim() || sending) return
    const content = input.trim()
    setInput('')
    setSending(true)
    try {
      const msg = await conversationsApi.sendMessage(convId, content)
      addOptimistic(msg)
    } catch {
      setInput(content)
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  if (loadingConv || loadingMsgs) {
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  }
  if (!conv) return null

  return (
    <main className="max-w-2xl mx-auto flex flex-col h-[calc(100vh-57px)]">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border-soft bg-card sticky top-0 z-10">
        <Link to="/conversations" className="text-muted hover:text-plum transition-colors">
          &larr;
        </Link>
        <img
          src={conv.listingImageUrl}
          alt=""
          className="w-10 h-10 rounded-card object-cover bg-mustard/30"
        />
        <div className="min-w-0">
          <p className="font-semibold text-sm text-plum truncate">{conv.counterpartNickname}</p>
          <Link
            to={`/listings/${conv.listingId}/detail`}
            className="text-xs text-coral hover:underline truncate block font-medium"
          >
            {conv.listingTitle}
          </Link>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-2">
        {messages.map((msg, i) => {
          const isOwn = msg.senderId === user?.id
          const showDate = i === 0 || !sameDay(messages[i - 1].createdAt, msg.createdAt)
          return (
            <div key={msg.id}>
              {showDate && (
                <p className="text-center text-xs text-muted my-3 font-medium">
                  {new Date(msg.createdAt).toLocaleDateString()}
                </p>
              )}
              <div className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                <div
                  className={`max-w-[75%] px-4 py-2 rounded-panel text-sm whitespace-pre-wrap shadow-sm ${
                    isOwn ? 'bg-plum text-surface' : 'bg-card text-plum border border-border-soft'
                  }`}
                >
                  {msg.content}
                  <p className={`text-[10px] mt-1 ${isOwn ? 'text-surface/70' : 'text-muted'}`}>
                    {new Date(msg.createdAt).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </p>
                </div>
              </div>
            </div>
          )
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Composer */}
      <div className="border-t border-border-soft bg-card px-4 py-3 flex gap-2 items-end">
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message..."
          maxLength={1000}
          rows={1}
          className="flex-1 resize-none border border-border-soft bg-surface rounded-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-coral/30 focus:border-coral"
        />
        <button
          onClick={handleSend}
          disabled={!input.trim() || sending}
          className="rounded-full bg-plum px-5 py-2 text-surface text-sm font-bold hover:bg-ink disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-button"
        >
          Send
        </button>
      </div>
      {input.length > 900 && (
        <p className="text-xs text-muted px-4 pb-1 text-right">{input.length}/1000</p>
      )}
    </main>
  )
}

function sameDay(a: string, b: string): boolean {
  return new Date(a).toDateString() === new Date(b).toDateString()
}
```

- [ ] **Step 2: Commit**

```bash
cd frontend && npm run test && npm run lint
git add frontend/src/pages/ChatPage.tsx
git commit -m "feat(ui): migrate ChatPage with plum sender bubbles + new composer

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Final verification + documentation

### Task 20: Full-stack regression test

**Files:** none

- [ ] **Step 1: Ensure backend + redis + mysql are running**

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace/infra && docker compose up -d
# In a separate terminal:
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- [ ] **Step 2: Run E2E**

```bash
cd frontend && npm run e2e
```

Expected: 25/25 pass.

If any spec fails:
- If failure is due to changed button labels or text: update spec.
- If failure is due to layout change breaking a click target: investigate and fix the page.
- If failure is unrelated to UI changes: flag as a pre-existing issue.

- [ ] **Step 3: Run unit tests**

```bash
cd frontend && npm run test
```

Expected: 46/46 pass.

- [ ] **Step 4: Run lint + format check**

```bash
cd frontend && npm run lint && npm run format:check
```

Expected: 0 errors, 7 existing warnings unchanged. If `format:check` fails, run `npm run format` to auto-fix and commit:

```bash
cd /home/sturan/IdeaProjects/campus-secondhand-marketplace
git add frontend/
git commit -m "chore(ui): prettier format after Peach+Plum migration"
```

---

### Task 21: Update docs

**Files:**
- Modify: `docs/dev-roadmap.md`
- Modify: `docs/engineering-journal.md`

- [ ] **Step 1: Add Phase D entry to `docs/dev-roadmap.md`**

Insert this block immediately before the existing `### Phase C — Deployment ⏭️ DEFERRED` line:

```markdown
### Phase D — Visual Identity Refresh ✅ (2026-05-26)
- Replaced "blue admin" palette with "Warm Marketplace" identity: Peach + Plum + Coral + Sage + Mustard, Plus Jakarta Sans throughout, plum-tinted shadows, soft radial glows in hero areas
- Tailwind v4 `@theme` token block in `index.css` as single source of truth
- 5 shared components migrated (Navbar, Spinner, Toast, ErrorBoundary, PasswordInput)
- 4 high-impact pages rebuilt (HomePage, BrowsePage, PublicDetailPage, ListingDetailPage) — new hero, chip system, pill CTAs
- 11 remaining pages migrated via mechanical className mapping
- All 25 Playwright + 46 Vitest tests stayed green throughout (semantic selectors)
- Spec: `docs/superpowers/specs/2026-05-26-milestone-6-phase-d-ui-beautification-design.md`

```

- [ ] **Step 2: Append D-78 to `docs/engineering-journal.md`**

Append to the end of the file:

```markdown

### D-78 — Visual Identity Refresh (2026-05-26)

**Decision:** Migrate from "blue-600 + slate utility admin" palette to "Warm Marketplace" identity (Peach + Plum + Coral + Sage + Mustard, Plus Jakarta Sans, plum-tinted shadows, radial glow decoration) via Tailwind v4 `@theme` tokens.

**Why now:** M6 Phase B (2026-05-22) closed the "polish" bucket as defined at the time, but the visual identity was still neutral-admin. With backend and feature work done, the highest remaining ROI in this thesis project is making the product look like a product the user actually wants to use.

**Approach:** Route-1 token-first in 3 stages. Stage 1 introduces design tokens in `index.css` + migrates 5 shared components (Navbar/Spinner/Toast/ErrorBoundary/PasswordInput) so every page instantly inherits on-brand buttons and feedback states. Stage 2 rebuilds 4 high-impact pages (Home/Browse/PublicDetail/ListingDetail) with new typography, hero glows, and chip system. Stage 3 sweeps 11 remaining pages via mechanical className mapping.

**What it cost:** ~20 atomic commits, zero test failures (pre-flight audit confirmed E2E and unit tests use semantic selectors, not classNames, so the migration was test-safe). No new runtime dependencies; Plus Jakarta Sans loaded via Google Fonts CDN with `display=swap` fallback.

**What we kept out:** Logo redesign deferred (text "Campus Marketplace" stays in new font). Dark mode out. Mobile-specific layout rework out (separate phase if needed). Illustration/sticker decoration rejected during brainstorming as too dating-prone for a thesis demo.

**What surprised us:** Originally feared the colour migration would shatter E2E specs. Pre-flight `grep` confirmed zero colour-class assertions in tests — the codebase had already been disciplined about semantic selectors in earlier phases. Stage 1 + 2 + 3 all ran with test suite green from the first attempt.
```

- [ ] **Step 3: Commit docs**

```bash
git add docs/dev-roadmap.md docs/engineering-journal.md
git commit -m "$(cat <<'EOF'
docs(journal): D-78 + roadmap Phase D for visual identity refresh

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Done

End state: 15 pages + 5 components on the new design system. All tests green. Two doc updates committed. Total commits: ~20 atomic commits, each one independently revertable.
