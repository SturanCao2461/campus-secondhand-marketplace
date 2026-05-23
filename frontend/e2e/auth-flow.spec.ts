import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { getVerificationToken } from './helpers/verificationToken'

const uniqueEmail = (prefix: string) => `${prefix}-${Date.now()}@students.waikato.ac.nz`

test.describe('Auth Flow', () => {
  test.beforeEach(() => {
    resetRateLimits()
  })

  test('register rejects non-campus email', async ({ page }) => {
    await page.goto('/register')
    await page.fill('input[name="email"]', `bad-${Date.now()}@gmail.com`)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `User${Date.now()}`)
    await page.click('button[type="submit"]')

    await expect(page.locator('text=/Only.*waikato\\.ac\\.nz/i')).toBeVisible()
  })

  test('register rejects weak password', async ({ page }) => {
    await page.goto('/register')
    await page.fill('input[name="email"]', uniqueEmail('weak'))
    // Use 8+ chars to bypass HTML5 minLength, but lacking digit -> backend rejects
    await page.fill('input[name="password"]', 'abcdefgh')
    await page.fill('input[name="confirmPassword"]', 'abcdefgh')
    await page.fill('input[name="nickname"]', `User${Date.now()}`)
    await page.click('button[type="submit"]')

    await expect(page.locator('text=/Password must/i')).toBeVisible()
  })

  test('register rejects mismatched confirm password', async ({ page }) => {
    await page.goto('/register')
    await page.fill('input[name="email"]', uniqueEmail('mismatch'))
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Different99')
    await page.fill('input[name="nickname"]', `User${Date.now()}`)
    await page.click('button[type="submit"]')

    await expect(page.locator("text=/Passwords don't match/i")).toBeVisible()
  })

  test('register succeeds with valid input then logs out and logs back in', async ({ page }) => {
    const ts = Date.now()
    const email = uniqueEmail(`flow-${ts}`)
    const nickname = `Flow${ts}`

    // Register
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', nickname)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Navbar shows greeting
    await expect(page.locator(`text=Hi, ${nickname}`)).toBeVisible()

    // Log out
    await page.click('button:has-text("Log out")')
    await page.waitForURL('/')
    await expect(page.locator(`text=Hi, ${nickname}`)).not.toBeVisible()
    await expect(page.locator('a:has-text("Log in")')).toBeVisible()

    // Log back in
    await page.goto('/login')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.click('button[type="submit"]')
    await page.waitForURL('/')
    await expect(page.locator(`text=Hi, ${nickname}`)).toBeVisible()
  })

  test('register rejects duplicate email', async ({ page }) => {
    const email = uniqueEmail(`dup-${Date.now()}`)

    // First registration succeeds
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `First${Date.now()}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Log out
    await page.click('button:has-text("Log out")')
    await page.waitForURL('/')

    // Second registration with same email fails
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `Second${Date.now()}`)
    await page.click('button[type="submit"]')

    await expect(page.locator('text=/email is already registered/i')).toBeVisible()
  })

  test('login rejects wrong password', async ({ page }) => {
    const ts = Date.now()
    const email = uniqueEmail(`wrong-${ts}`)

    // Register first
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `Wrong${ts}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Log out
    await page.click('button:has-text("Log out")')
    await page.waitForURL('/')

    // Try wrong password
    await page.goto('/login')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'WrongPass1')
    await page.click('button[type="submit"]')

    await expect(page.locator('text=/Email or password is incorrect/i')).toBeVisible()
  })

  test('protected route redirects to login when not authenticated', async ({ page }) => {
    await page.goto('/me')
    await page.waitForURL(/\/login/)
    expect(page.url()).toMatch(/\/login/)
    // next param preserves target
    expect(page.url()).toContain('next')
  })

  test('login from protected redirect lands on original target', async ({ page }) => {
    const ts = Date.now()
    const email = uniqueEmail(`redirect-${ts}`)
    // Nickname max length 20 — Date.now() is 13 chars, so prefix max 7 chars
    const nickname = `R${ts}`

    // Register first
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', nickname)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Log out
    await page.click('button:has-text("Log out")')
    await page.waitForURL('/')

    // Visit protected page directly
    await page.goto('/me')
    await page.waitForURL(/\/login/)

    // Log in
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.click('button[type="submit"]')

    // Should land on /me, not /
    await page.waitForURL('/me')
  })

  test('newly-registered user sees unverified chip on MePage', async ({ page }) => {
    const ts = Date.now()
    const email = uniqueEmail(`unv-${ts}`)

    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `Unv${ts}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    await page.goto('/me')
    await expect(page.locator('text=Email not verified')).toBeVisible()
    await expect(page.locator('button:has-text("Resend verification email")')).toBeVisible()
  })

  test('verify-email flips state to verified using real token from Redis', async ({ page }) => {
    const ts = Date.now()
    const email = uniqueEmail(`verify-${ts}`)

    // Register — backend issues a verification token to Redis
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `V${ts}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Pull the token straight out of Redis (no email infra in dev)
    const token = getVerificationToken()

    // Visit the link the user would have clicked in their inbox
    await page.goto(`/verify-email?token=${token}`)
    await expect(page.locator('h1:has-text("Email verified")')).toBeVisible()

    // MePage now shows the verified chip and the resend button is gone
    await page.goto('/me')
    await expect(page.locator('text=Email verified').first()).toBeVisible()
    await expect(page.locator('text=Email not verified')).toHaveCount(0)
    await expect(page.locator('button:has-text("Resend verification email")')).toHaveCount(0)
  })

  test('verify-email shows failure for invalid token', async ({ page }) => {
    await page.goto('/verify-email?token=this-token-was-never-issued')
    await expect(page.locator('h1:has-text("Verification failed")')).toBeVisible()
    await expect(page.locator('text=/invalid or has expired/i')).toBeVisible()
  })
})
