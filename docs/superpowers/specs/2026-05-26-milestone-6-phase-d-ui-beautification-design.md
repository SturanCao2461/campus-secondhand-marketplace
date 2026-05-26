# Milestone 6 Phase D — UI Beautification Design

**Date:** 2026-05-26
**Status:** Draft → awaiting user review
**Epic:** Milestone 6 — Polish (Phase D, augmenting completed Phase B)
**Predecessor:** Phase B (2026-05-22) — neutralized class-name inconsistencies + added ErrorBoundary/Spinner. Visual style remained "blue-600 + slate utility admin".

## Goal

Refresh the frontend's visual identity from the current "neutral admin utility" (blue-600 + slate borders, system sans, rounded-md) to a **"Warm Marketplace"** identity (Peach + Plum palette, Plus Jakarta Sans, generous radii, soft shadows, subtle radial-gradient glow in hero areas). All 15 pages and 5 shared components inherit the new look. No information architecture or routing changes.

## Decision summary (from brainstorming session)

| Axis | Choice | Reason |
|---|---|---|
| Direction (A/B/C/D) | **A — full design-token refresh** | Highest visual ROI, no structural risk. Phase B already did "B — local polish". |
| Style language | **A2 Warm Marketplace** | Marketplace is a consumer product; warm palette signals "browse and discover", not "operate a system". |
| Palette | **P3 Peach + Plum** | Reverse-contrast button (plum on peach) reads as more modern than coral-on-cream; sage/mustard secondaries give status variety. |
| Typography | **T3 Plus Jakarta Sans only** | Single-family is lower-risk than serif/sans pair; humanist sans provides warmth without committing to "magazine" aesthetic. |
| Decoration | **D2 Subtle Glow** | Radial-gradient blobs in hero corners. Adds depth without commiting to illustrations or stickers (which can date quickly). |
| Implementation route | **Route 1 — Token-first, 3 stages** | Each stage independently shippable and reviewable. Stage 1 (tokens + shared components) instantly upgrades all 16 pages. Subsequent stages refine high-impact pages. |

## Scope

### In scope

- New `index.css` design tokens (CSS custom properties for palette / type-scale / radius / shadow / spacing).
- Plus Jakarta Sans loaded via `@import` at top of `index.css` (Google Fonts, `display=swap`).
- Tailwind v4 `@theme` block mapping tokens to utility classes (`bg-surface`, `text-plum`, `bg-coral`, `rounded-card`, `shadow-card`, etc.).
- Updated shared components: `Navbar.tsx`, `Spinner.tsx`, `Toast.tsx`, `ErrorBoundary.tsx`, `PasswordInput.tsx`.
- Per-page polish for 4 high-impact pages: `HomePage`, `BrowsePage`, `PublicDetailPage`, `ListingDetailPage`.
- Per-page token migration (className swap only, no layout changes) for remaining 11 pages.
- Image-placeholder gradients (peach/mustard/sage gradient backgrounds) replacing flat gray for listing cards lacking a real image.
- Existing E2E + unit tests stay green. Selectors are semantic (text content, `data-testid` where used) and survive className changes.

### Out of scope

- Logo / wordmark redesign (deferred — the text "Campus Marketplace" stays but renders in new font + colour).
- Dark mode (no current support; not adding).
- Animation library (Framer Motion etc.). Transitions stay CSS-only: `hover` / `focus-visible` color shifts + transform-on-hover for cards.
- Illustration / sticker / emoji decoration (D3 was rejected).
- Information architecture changes (page count, routes, nav structure all unchanged).
- Mobile-specific layout rework (separate D-track if user asks later; current breakpoint behaviour preserved).
- Removing existing `data-testid` attributes (E2E selectors depend on them).

## Design tokens

### Color

| Token | Hex | Tailwind utility | Usage |
|---|---|---|---|
| `--color-surface` | `#FFF4ED` | `bg-surface` | Page background, body |
| `--color-surface-raised` | `#FFFFFF` | `bg-card` | Cards, panels, modals |
| `--color-plum` | `#3D405B` | `bg-plum` / `text-plum` | Primary buttons, body text, headlines |
| `--color-plum-ink` | `#2A2C40` | `text-ink` | Highest-contrast text (replaces `text-slate-900`) |
| `--color-coral` | `#E07A5F` | `bg-coral` / `text-coral` | Prices, hover links, primary accent |
| `--color-coral-soft` | `rgba(224,122,95,0.12)` | `bg-coral/10` | Coral tag fill |
| `--color-sage` | `#81B29A` | `bg-sage` | Status: AVAILABLE; success toasts |
| `--color-mustard` | `#F2CC8F` | `bg-mustard` | Status: RESERVED; warning toasts; category tags |
| `--color-muted` | `#8B7B8E` | `text-muted` | Timestamps, metadata, helper text |
| `--color-border-soft` | `rgba(61,64,91,0.1)` | `border-soft` | Default 1px borders |
| `--color-error` | `#C44536` | `bg-error` / `text-error` | Error toasts, validation messages (replaces `bg-red-50`/`text-red-700`) |

**Replaces:** every existing `blue-600`, `slate-300`, `slate-600`, `slate-700`, `slate-900`, `red-50`, `red-700`, `gray-200`, `gray-300`, `gray-400`, `gray-500`, `gray-100` reference.

### Typography

Font family: `'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif` (loaded via `@import` from Google Fonts at top of `index.css`, `display=swap`).

| Token | Size / Weight / Tracking | Tailwind utility | Usage |
|---|---|---|---|
| `--type-hero` | 44px / 800 / -0.04em | `text-hero` | Homepage H1 only |
| `--type-h1` | 28px / 700 / -0.02em | `text-h1` | Page titles (Browse, MyListings, etc.) |
| `--type-h2` | 20px / 700 / -0.01em | `text-h2` | Section headings, modal titles |
| `--type-card` | 14px / 600 / 0 | `text-card-title` | Card titles, button labels |
| `--type-body` | 14px / 500 / 0 | `text-body` | Body copy, form labels |
| `--type-small` | 12px / 500 / 0 | `text-small` | Metadata, helper text |
| `--type-eyebrow` | 11px / 700 / 0.15em uppercase | `text-eyebrow` | Section labels above hero |
| `--type-price` | 22px / 800 / -0.025em | `text-price` | Listing card / detail price (replaces `text-blue-600`) |

### Radius

| Token | Value | Tailwind utility | Usage |
|---|---|---|---|
| `--radius-sm` | 8px | `rounded-sm` (override) | Inputs, chips, small buttons |
| `--radius-md` | 14px | `rounded-card` | Cards, panels (replaces `rounded-md`) |
| `--radius-lg` | 18px | `rounded-panel` | Modals, hero panels |
| `--radius-full` | 999px | `rounded-full` | Buttons, pills, status chips |

### Shadow

All shadows use Plum-Navy tint (rgba 61,64,91), not neutral black — softer, on-brand.

| Token | Value | Tailwind utility | Usage |
|---|---|---|---|
| `--shadow-sm` | `0 1px 3px rgba(61,64,91,0.06)` | `shadow-sm` | Default subtle elevation |
| `--shadow-card` | `0 4px 14px rgba(61,64,91,0.08)` | `shadow-card` | Listing cards |
| `--shadow-panel` | `0 8px 24px rgba(61,64,91,0.10)` | `shadow-panel` | Modals, dropdowns |
| `--shadow-button` | `0 4px 14px rgba(61,64,91,0.2)` | `shadow-button` | Primary button rest state |

### Decoration

Two reusable utility classes (defined in `index.css`):

- `.glow-coral` — `radial-gradient(circle, rgba(224,122,95,0.4) 0%, transparent 65%)` positioned absolute, 280×280, top-right corner of hero containers
- `.glow-sage` — `radial-gradient(circle, rgba(129,178,154,0.35) 0%, transparent 65%)` positioned absolute, 200×200, bottom-left corner of hero containers

Applied only to: `HomePage` hero, `BrowsePage` filter row container, empty-state containers.

## Page-level treatments

### Stage 1 — Token foundation (no per-page work)

Touches:
- `frontend/src/index.css` — new `@theme` block + CSS custom properties + font import + decoration utilities.
- `frontend/src/components/Navbar.tsx` — replace `blue-600` / `slate-*` classNames with new utilities. Add subtle backdrop-blur for fixed-state.
- `frontend/src/components/Spinner.tsx` — coral spinner colour.
- `frontend/src/components/Toast.tsx` — sage for success / coral for error / mustard for warning / plum for info.
- `frontend/src/components/ErrorBoundary.tsx` — replace `bg-red-50` / `text-red-700` with new error tokens.
- `frontend/src/components/PasswordInput.tsx` — focus ring switches to coral.

**Outcome of Stage 1:** all 15 pages instantly inherit the new palette via Tailwind utility class swaps in shared components. Buttons, links, focus states, error states all become "on-brand" without per-page edits. This is the minimum-viable visual upgrade and shippable on its own.

### Stage 2 — Polish high-impact pages (4 pages)

- **`HomePage.tsx`** — hero rebuild: eyebrow label, 44px headline ("Find your next thing."), softer subhead, coral primary CTA (plum-navy fill, peach text), mustard ghost CTA. Add `.glow-coral` top-right + `.glow-sage` bottom-left.
- **`BrowsePage.tsx`** — filter row becomes horizontal pill list (selected = plum-navy filled, unselected = white with soft border). Cards adopt new shadow + radius. Empty-state icon recoloured. Skeleton uses peach-tinted shimmer instead of gray.
- **`PublicDetailPage.tsx`** — price gets `text-price` (large, coral). Status chip becomes proper coloured pill (sage/mustard). "Contact seller" CTA matches Home primary button. Image container gets soft gradient placeholder when image fails.
- **`ListingDetailPage.tsx`** — same as `PublicDetailPage` but with the status-transition controls restyled as a pill group.

### Stage 3 — Token migration for remaining 11 pages

Each page gets a className-only pass replacing legacy colours/radii/shadows with new tokens. No layout changes. Pages:
- `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `VerifyEmailPage` — auth forms (5)
- `CreateListingPage`, `EditListingPage`, `MyListingsPage` — listing management (3)
- `ConversationsPage`, `ChatPage` — messaging (2)
- `MePage` — profile (1)

## Implementation strategy

### Route 1 detail — 3 atomic stages

| Stage | Files touched | Test impact | Commit message style |
|---|---|---|---|
| 1 — Tokens | `index.css` + 5 components | E2E should pass unchanged; unit tests update snapshots if any | `feat(ui): introduce Peach+Plum design tokens` |
| 2 — Hero pages | 4 page components | E2E should pass unchanged (selectors are semantic); manual visual review | `feat(ui): rebuild Home/Browse/Detail with new design system` |
| 3 — Migration | 11 page components | E2E should pass unchanged | `feat(ui): migrate remaining 11 pages to design tokens` |

Each stage is one PR (or one commit on main, given local-demo workflow) — independently revertable.

### Tailwind v4 mechanics

Frontend uses Tailwind v4 (`@tailwindcss/vite`). Tokens go in `index.css` via the `@theme` directive:

```css
@import 'tailwindcss';
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

@theme {
  --color-surface: #FFF4ED;
  --color-plum: #3D405B;
  --color-coral: #E07A5F;
  --color-sage: #81B29A;
  --color-mustard: #F2CC8F;
  --font-family-sans: 'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif;
  --radius-card: 14px;
  --shadow-card: 0 4px 14px rgba(61,64,91,0.08);
}
```

This makes `bg-surface`, `text-plum`, `bg-coral`, `font-sans`, `rounded-card`, `shadow-card` available everywhere with no extra config.

## Error / edge-case handling

- **Font load failure** — `display=swap` fallback to system sans. Layout shift is acceptable (one-time, on first visit, ~100ms).
- **Image missing** — current `<img>` tags fall back to broken-image icon. After redesign, parent container has gradient background (peach/mustard) so missing image still looks intentional rather than broken.
- **Status chip for unrecognized status** — default to neutral plum-on-soft-plum so unknown FSM states don't blow up the layout.
- **High-contrast / a11y** — Plum `#3D405B` on Surface `#FFF4ED` = AAA. Coral `#E07A5F` on Surface = AA Large only (4.0:1) — restrict to large text (≥18px) and decorative use. Body text never goes coral.
- **Reduced motion** — no animations triggered, but if added later, gate behind `@media (prefers-reduced-motion: no-preference)`.

## Testing strategy

- **Existing 25 Playwright E2E + 46 Vitest unit tests must all pass unchanged.** Selectors are semantic (`getByRole`, `getByText`, `data-testid`) — class-name changes don't affect them.
- **No new tests required for Stage 1** (token swap is non-behavioural).
- **Stage 2 visual verification** is manual: dev server running, walk through Home → Browse → Detail → MyListings → Chat, confirm new palette renders consistently, no regression in interactivity (filter pills clickable, search keystrokes still work, etc.).
- **Lighthouse smoke check** post-stage-2: no regression in accessibility score (currently un-measured; Phase D establishes a baseline).

## Known constraints

- Tailwind v4 `@theme` block syntax is newer; verify font + radius tokens propagate to utility classes as expected before mass migration. **Sanity-test Stage 1 with a single component (Spinner) before doing the rest.**
- Plus Jakarta Sans is a Google-Fonts dependency. Frontend now depends on Google CDN availability at runtime. For thesis-defense scenario where the network might be unreliable, consider self-hosting the font (download `.woff2` files into `frontend/public/fonts/`) — listed as a follow-up, not blocker.
- E2E specs currently pass with the existing palette in a few assertions (e.g., looking for `.bg-blue-600`). **Audit before Stage 1** — if any specs reference colour class names, update or remove the assertion.

## Deliverable order

1. Stage 1 — tokens + shared components → manual visual smoke → commit
2. Stage 2 — 4 high-impact pages → manual visual smoke per page → commit per page or single commit
3. Stage 3 — 12 remaining pages → automated test run + commit
4. Engineering journal entry (D-78) — what changed, before/after screenshots, what was deferred
5. Update `docs/dev-roadmap.md` — add "Phase D — Visual Identity Refresh" entry under Milestone 6

## Risks & mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Token names collide with Tailwind v4 internals | Medium | Sanity-test on Spinner first (Stage 1 internal checkpoint); rename if collision occurs. |
| E2E spec references old colour classNames | Low (selectors are mostly semantic) | Pre-flight `grep` for `bg-blue-600` / `slate-` in `e2e/` directory before starting Stage 1. |
| Font CDN unavailable during defense | Low | Acceptable degradation via `display=swap` system fallback. Self-hosting deferred. |
| Visual regression in unrelated pages after token swap | Medium | Stage 3 is the safety net — each remaining page gets explicit token migration, no page left with leaked legacy classes. |
| Stage 2 hero changes break selector that existed in E2E | Low | Run E2E after Stage 2 before declaring done. |
