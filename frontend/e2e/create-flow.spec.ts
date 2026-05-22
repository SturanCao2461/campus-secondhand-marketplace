import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { e2eFixture } from './helpers/paths'
import { markEmailVerified } from './helpers/verifyUser'

const uniqueEmail = () => `e2e-${Date.now()}@students.waikato.ac.nz`

test.describe('Listing Create Flow', () => {
  let email: string

  test.beforeEach(async ({ page }) => {
    resetRateLimits()
    email = uniqueEmail()
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `User${Date.now()}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')
    markEmailVerified(email)
  })

  test('create a listing and see it in My Listings', async ({ page }) => {
    await page.goto('/listings/new')
    await page.fill('input[name="title"]', 'E2E Textbook')
    await page.fill('textarea[name="description"]', 'A test listing created by Playwright')
    await page.selectOption('select[name="categoryCode"]', 'BOOKS')
    await page.selectOption('select[name="listingType"]', 'SELL')
    await page.fill('input[name="price"]', '25.00')

    const testImage = e2eFixture(import.meta.url, 'fixtures/test-image.jpg')
    await page.setInputFiles('input[name="image"]', testImage)

    await page.click('button[type="submit"]')
    await page.waitForURL('/listings/mine')

    await expect(page.locator('text=E2E Textbook')).toBeVisible()
  })

  test('create listing without image is blocked by HTML5 validation', async ({ page }) => {
    await page.goto('/listings/new')
    await page.fill('input[name="title"]', 'No Image')
    await page.fill('textarea[name="description"]', 'Missing image test')
    await page.selectOption('select[name="categoryCode"]', 'BOOKS')
    await page.selectOption('select[name="listingType"]', 'SELL')
    await page.fill('input[name="price"]', '10.00')

    await page.click('button[type="submit"]')

    // HTML5 required attribute prevents submission — URL stays on /listings/new
    expect(page.url()).toContain('/listings/new')
  })
})
