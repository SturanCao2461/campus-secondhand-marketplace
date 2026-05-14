# Manual E2E Checklist — Epic 1: Authentication

Run through this checklist with both backend and frontend running locally.

## Setup
- [ ] `cd infra && docker compose up -d`
- [ ] `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] `cd frontend && npm run dev`
- [ ] Open `http://localhost:5173` in a browser

## Registration
- [ ] Click "Sign up" — should land on `/register`
- [ ] Try registering with `xxx@gmail.com` — expect red error "Only @students.waikato.ac.nz emails are allowed."
- [ ] Try password "abc" — expect red error "Password must be 8–64 characters with at least one letter and one digit."
- [ ] Try mismatched confirm — expect "Passwords don't match."
- [ ] Register `alice@students.waikato.ac.nz` / `Pass1234` / `Alice` — expect redirect to `/`, navbar shows "Hi, Alice"
- [ ] Try registering same email again — expect "This email is already registered."

## Login
- [ ] Log out (button in navbar)
- [ ] Click "Log in", enter wrong password — expect "Email or password is incorrect."
- [ ] Enter correct credentials — expect redirect to `/`, navbar shows "Hi, Alice"

## Cookie inspection
- [ ] Open browser DevTools → Application → Cookies → `http://localhost:5173`
- [ ] Find cookie named `token`. Verify:
  - [ ] **HttpOnly** is checked
  - [ ] **SameSite** is "Lax"
  - [ ] **Secure** is unchecked (we're on HTTP locally)
  - [ ] **Expires/Max-Age** is ~7 days from now

## Protected route
- [ ] Log out
- [ ] Visit `http://localhost:5173/me` directly — expect redirect to `/login?next=%2Fme`
- [ ] Log in — expect redirect to `/me`, see your user JSON

## Logout invalidates cookie
- [ ] While logged in, copy your `token` cookie value (DevTools → Application → Cookies)
- [ ] Click "Log out"
- [ ] In DevTools, manually re-set `token` to that copied value
- [ ] Visit `/me` — expect redirect to `/login` (token jti was blacklisted)

## Rate limiting
- [ ] Log out
- [ ] Submit login with wrong password 5 times for `alice@students.waikato.ac.nz`
- [ ] On the 6th try, expect "Too many attempts. Try again in 15 minutes."

## Forgot/Reset password
- [ ] Click "Forgot password?" on the login page
- [ ] Enter `alice@students.waikato.ac.nz` and submit — expect "Check your email" page
- [ ] **Look at the backend terminal** — find lines starting with `===== EMAIL (console mode) =====`. Copy the token from the URL in the body.
- [ ] Visit `http://localhost:5173/reset-password?token=<token>` — expect "Set a new password" page
- [ ] Set a new password `NewPass99`
- [ ] Try logging in with old password `Pass1234` — expect "Email or password is incorrect."
- [ ] Try logging in with `NewPass99` — expect success

## Anti-enumeration check
- [ ] Submit forgot-password for `nonexistent@students.waikato.ac.nz` — expect the same "Check your email" page (no clue that the email doesn't exist)

## Cleanup
- [ ] Stop frontend (Ctrl+C)
- [ ] Stop backend (Ctrl+C)
- [ ] (Optional) `cd infra && docker compose down`
