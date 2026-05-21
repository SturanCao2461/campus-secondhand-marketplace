import { test, expect } from '@playwright/test'
import { resetRateLimits } from './helpers/rateLimit'
import { e2eFixture } from './helpers/paths'

const uniqueEmail = () => `e2e-status-${Date.now()}@students.waikato.ac.nz`

test.describe('Listing Status Flow', () => {
  let email: string

  test.beforeEach(async ({ page }) => {
    resetRateLimits()
    email = uniqueEmail()
    await page.goto('/register')
    await page.fill('input[name="email"]', email)
    await page.fill('input[name="password"]', 'Pass1234')
    await page.fill('input[name="confirmPassword"]', 'Pass1234')
    await page.fill('input[name="nickname"]', `Status${Date.now()}`)
    await page.click('button[type="submit"]')
    await page.waitForURL('/')

    // Create a listing
    await page.goto('/listings/new')
    await page.fill('input[name="title"]', 'Status Test Item')
    await page.fill('textarea[name="description"]', 'For status transition testing')
    await page.selectOption('select[name="categoryCode"]', 'ELECTRONICS')
    await page.selectOption('select[name="listingType"]', 'SELL')
    await page.fill('input[name="price"]', '50.00')
    const testImage = e2eFixture(import.meta.url, 'fixtures/test-image.jpg')
    await page.setInputFiles('input[name="image"]', testImage)
    await page.click('button[type="submit"]')
    await page.waitForURL('/listings/mine')
  })

  test('full status cycle: Available -> Reserved -> Sold -> Available -> Removed', async ({
    page,
  }) => {
    await page.click('text=Status Test Item')
    await page.waitForURL(/\/listings\/\d+/)

    // AVAILABLE -> RESERVED
    await page.click('button:has-text("Mark Reserved")')
    await expect(page.locator('span:has-text("RESERVED")').first()).toBeVisible()

    // RESERVED -> SOLD
    await page.click('button:has-text("Mark Sold")')
    await expect(page.locator('span:has-text("SOLD")').first()).toBeVisible()

    // SOLD -> AVAILABLE (relist)
    await page.click('button:has-text("Relist")')
    await expect(page.locator('span:has-text("AVAILABLE")').first()).toBeVisible()

    // AVAILABLE -> REMOVED (delete)
    page.on('dialog', dialog => dialog.accept())
    await page.click('button:has-text("Delete")')
    await page.waitForURL('/listings/mine')
  })
})
